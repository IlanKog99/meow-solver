package com.ilan.meowsolver.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import com.ilan.meowsolver.core.AutoSolver
import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.BoardNotFound
import com.ilan.meowsolver.core.BoardReader
import com.ilan.meowsolver.core.Cell
import com.ilan.meowsolver.core.DebugLog
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.OverlaySettings
import com.ilan.meowsolver.core.PanelButton
import com.ilan.meowsolver.core.overlaySettings
import com.ilan.meowsolver.core.Solver
import com.ilan.meowsolver.core.Tap
import com.ilan.meowsolver.ui.Bubble
import com.ilan.meowsolver.ui.Marks
import com.ilan.meowsolver.ui.MarksLayer
import com.ilan.meowsolver.ui.PanelCard
import com.ilan.meowsolver.ui.PanelState
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class OverlayService : AccessibilityService() {
    companion object {
        var instance: OverlayService? = null
            private set
        const val PREFS = "meow"
        const val K_REVEAL = "answerOn"
        const val K_DOUBLE_MS = "doubleTapMs"
        private const val K_X = "panelX"
        private const val K_Y = "panelY"
        private const val CAPTURE_DELAY_MS = 150L
        private const val RETRY_MS = 400L
        private const val THROTTLE_RETRY_MS = 1000L // takeScreenshot is rate-limited to ~1/s
        private const val HINT_MS = 5000L
        private const val AUTO_SETTLE_MS = 400L // let the game animate before re-reading
        private const val STOP_GUARD_MS = 1000L // covers a slow press on Stop (cancel fires on finger-down, click on finger-up)
        private const val MAX_FIX_ROUNDS = 3
        private const val NO_SOLUTION = "No solution — a placed cat is wrong"
        private const val READ_FAIL = "Couldn't read the screen. Try again"
        private const val UNSTABLE = "Board keeps changing — tap again"
    }

    private enum class Mode { CARD, BUBBLE }

    private val handler = Handler(Looper.getMainLooper())
    private var owner = OverlayOwner()
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var taps: TapRunner
    private lateinit var panelLp: LP
    private var panelView: ComposeView? = null
    private var marksView: ComposeView? = null

    private var mode by mutableStateOf(Mode.CARD)
    private var panel by mutableStateOf(PanelState("Ready", busy = false, revealOn = false))
    private var marks by mutableStateOf(Marks())
    private var settings = OverlaySettings()
    // Held as a field: SharedPreferences keeps listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applySettings() }
    private var hintToken = 0
    private var stoppedAt = 0L // a finger-down cancels in-flight gestures; ignore the Stop tap's own click after that
    private var session = 0 // bumped by close()/unbind: in-flight work from an older session is dropped
    private var dragDownX = 0f; private var dragDownY = 0f
    private var dragWinX = 0; private var dragWinY = 0
    private var dragMoved = false
    private var longPressed = false // long-press already fired for this touch: its finger-up is not a tap
    private var onLongPress: (() -> Unit)? = null
    private val longPressRunnable = Runnable {
        if (dragMoved) return@Runnable
        longPressed = true
        panelView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onServiceConnected() {
        DebugLog.init(filesDir)
        DebugLog.log("Overlay service connected")
        wm = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        taps = TapRunner(this, handler)
        panel = panel.copy(revealOn = prefs.getBoolean(K_REVEAL, false))
        prefs.registerOnSharedPreferenceChangeListener(prefListener); applySettings()
        DebugLog.log("Settings: $settings, doubleTapMs=${prefs.getInt(K_DOUBLE_MS, MovePlanner.GAP_MS.toInt())}")
        owner = OverlayOwner().also { it.start() }
        instance = this
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        session++
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        removeViews()
        owner.stop()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        session++
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        removeViews()
        owner.stop()
        return super.onUnbind(intent)
    }

    private fun applySettings() {
        settings = prefs.overlaySettings()
        // Card size follows layout + visible buttons: re-clamp on screen (expanding the bubble settles on its own).
        if (mode == Mode.CARD && (settings.layout != panel.layout || settings.visibleButtons != panel.buttons)) settle()
        panel = panel.copy(buttons = settings.visibleButtons, layout = settings.layout, quick = settings.activeQuickAction)
        if (PanelButton.REVEAL !in settings.buttons && panel.revealOn) {
            panel = panel.copy(revealOn = false)
            marks = marks.copy(rings = emptyList())
            prefs.edit().putBoolean(K_REVEAL, false).apply()
        }
        if (PanelButton.ANSWER !in settings.buttons && panel.autoRunning) stopAnswer()
    }

    // ---------- windows ----------

    fun showOverlay() {
        mode = Mode.CARD
        if (panelView != null) return
        marksView = composeView { MarksLayer(marks) }.also {
            wm.addView(it, LP(LP.MATCH_PARENT, LP.MATCH_PARENT, LP.TYPE_ACCESSIBILITY_OVERLAY,
                LP.FLAG_NOT_TOUCHABLE or LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT))
        }
        val d = resources.displayMetrics.density
        panelLp = LP(LP.WRAP_CONTENT, LP.WRAP_CONTENT, LP.TYPE_ACCESSIBILITY_OVERLAY, LP.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            x = prefs.getInt(K_X, (12 * d).toInt()).coerceIn(0, max(0, dm.widthPixels - (52 * d).toInt()))
            y = prefs.getInt(K_Y, (80 * d).toInt()).coerceIn(0, max(0, dm.heightPixels - (52 * d).toInt()))
        }
        panelView = composeView { PanelHost() }.also { wm.addView(it, panelLp) }
        settle() // re-clamp with the real card width once laid out
    }

    private fun close() {
        session++
        removeViews()
        marks = Marks()
        panel = panel.copy(status = "Ready", busy = false, autoRunning = false)
    }

    private fun removeViews() {
        panelView?.let { wm.removeView(it) }
        marksView?.let { wm.removeView(it) }
        panelView = null; marksView = null
    }

    private fun composeView(content: @Composable () -> Unit) =
        ComposeView(this).apply { owner.attach(this); setContent(content) }

    private fun setOverlayVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.INVISIBLE
        panelView?.visibility = vis; marksView?.visibility = vis
    }

    /** Injected taps must not land on our own panel. */
    private fun setPanelTouchable(t: Boolean) {
        val view = panelView ?: return
        panelLp.flags = if (t) panelLp.flags and LP.FLAG_NOT_TOUCHABLE.inv() else panelLp.flags or LP.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(view, panelLp)
    }

    /** Raw screen coordinates: the window moves under the finger, so local deltas would jitter. */
    private fun dragTouch(e: MotionEvent, onClick: (() -> Unit)?, longPress: (() -> Unit)? = null): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragDownX = e.rawX; dragDownY = e.rawY; dragWinX = panelLp.x; dragWinY = panelLp.y; dragMoved = false; longPressed = false
                onLongPress = longPress
                handler.removeCallbacks(longPressRunnable)
                if (longPress != null && !panel.autoRunning) handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (panel.autoRunning || longPressed) { handler.removeCallbacks(longPressRunnable); return true } // panel must not move under injected taps
                val dx = e.rawX - dragDownX; val dy = e.rawY - dragDownY
                if (!dragMoved && hypot(dx, dy) > ViewConfiguration.get(this).scaledTouchSlop) { dragMoved = true; handler.removeCallbacks(longPressRunnable) }
                val view = panelView
                if (dragMoved && view != null) {
                    panelLp.x = dragWinX + dx.toInt(); panelLp.y = dragWinY + dy.toInt()
                    wm.updateViewLayout(view, panelLp)
                }
            }
            MotionEvent.ACTION_UP -> { handler.removeCallbacks(longPressRunnable); if (dragMoved) endDrag() else if (!longPressed) onClick?.invoke() }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longPressRunnable); if (dragMoved) endDrag() }
        }
        return true
    }

    /** Keep on screen; the bubble also snaps to the nearest side edge. Saves the position. */
    private fun endDrag() {
        val view = panelView ?: return
        val dm = resources.displayMetrics
        val maxX = max(0, dm.widthPixels - view.width); val maxY = max(0, dm.heightPixels - view.height)
        panelLp.x = if (mode == Mode.BUBBLE) (if (panelLp.x + view.width / 2 < dm.widthPixels / 2) 0 else maxX) else min(max(panelLp.x, 0), maxX)
        panelLp.y = min(max(panelLp.y, 0), maxY)
        wm.updateViewLayout(view, panelLp)
        prefs.edit().putInt(K_X, panelLp.x).putInt(K_Y, panelLp.y).apply()
    }

    /** Run endDrag once the new mode has been laid out. */
    private fun settle() {
        val view = panelView ?: return
        view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                if (r - l <= 0) return
                v.removeOnLayoutChangeListener(this)
                v.post { if (v === panelView) endDrag() }
            }
        })
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun PanelHost() {
        when (mode) {
            Mode.CARD -> PanelCard(
                panel, onReveal = pressed("Reveal", ::toggleReveal), onHint = pressed("Hint", ::hint), onCat = pressed("Cat", ::placeCat),
                onX = pressed("X", ::markX), onAnswer = pressed("Answer", ::answer), onMinimize = { if (!panel.autoRunning) { mode = Mode.BUBBLE; settle() } }, onClose = ::close,
                dragModifier = Modifier.pointerInteropFilter { dragTouch(it, null) }, // applied to the status text only
            )
            Mode.BUBBLE -> {
                // Quick action set: tap runs it, long-press opens the panel. Otherwise tap opens it.
                val quick = panel.quick
                val expand = { if (!panel.autoRunning) { mode = Mode.CARD; settle() } }
                val tap = if (quick == null) expand else { { runQuick(quick) } }
                val long = if (quick == null) null else expand
                Bubble(
                    onClick = tap, onLongClick = long, // touch is consumed by dragTouch; these serve TalkBack
                    quick = quick, busy = panel.busy, running = panel.autoRunning,
                    modifier = Modifier.pointerInteropFilter { dragTouch(it, tap, long) },
                )
            }
        }
    }

    // ---------- actions ----------

    /** Logs every button press with the state it hit, so a shared log shows what the user did. */
    private fun pressed(name: String, action: () -> Unit): () -> Unit = {
        DebugLog.log("Pressed $name (busy=${panel.busy}, running=${panel.autoRunning})")
        action()
    }

    private fun runQuick(b: PanelButton) = when (b.also { DebugLog.log("Bubble quick action: $it") }) {
        PanelButton.REVEAL -> toggleReveal()
        PanelButton.HINT -> hint()
        PanelButton.CAT -> placeCat()
        PanelButton.X -> markX()
        PanelButton.ANSWER -> answer()
    }

    private fun hint() = withBoard { b, sol ->
        sol ?: return@withBoard done(NO_SOLUTION)
        val cell = MovePlanner.nextCat(b, sol, settings.order, settings.reverse) ?: return@withBoard done("Solved")
        val token = ++hintToken
        marks = marks.copy(hint = cell)
        handler.postDelayed({ if (token == hintToken) marks = marks.copy(hint = null) }, HINT_MS)
        done("Next cat: row ${cell / b.n + 1}, col ${cell % b.n + 1}")
    }

    private fun placeCat() = withBoard { b, sol ->
        sol ?: return@withBoard done(NO_SOLUTION)
        // Hint still on screen → place that cat, so Hint then Cat agree.
        val cell = marks.hint?.takeIf { it in MovePlanner.remaining(b, sol) } ?: MovePlanner.nextCat(b, sol, settings.order, settings.reverse) ?: return@withBoard done("Solved")
        runTaps(MovePlanner.catTaps(b, cell)) {
            marks = marks.copy(rings = marks.rings - cell, hint = marks.hint.takeIf { it != cell })
            "Cat placed: row ${cell / b.n + 1}, col ${cell % b.n + 1}"
        }
    }

    private fun markX(): Unit = withBoard(needSolution = false) { b, _ ->
        val want = MovePlanner.xTargets(b).toSet()
        if (want.isEmpty()) return@withBoard done("Nothing to mark")
        panel = panel.copy(status = "Marking…")
        markAndVerify(MovePlanner.xTaps(b), want, keep = (0 until b.n * b.n).filter { b.state[it] == Cell.X }.toSet(), round = 0)
    }

    /** Drags, re-reads, then clears X's marked by mistake and re-marks missed cells until the board matches. */
    private fun markAndVerify(gestures: List<Tap>, want: Set<Int>, keep: Set<Int>, round: Int): Unit = autoTaps(gestures) {
        settleThen(needSolution = false) { b, _ -> verifyX(b, want, keep, round, confirmed = false) }
    }

    private fun verifyX(b: Board, want: Set<Int>, keep: Set<Int>, round: Int, confirmed: Boolean) {
        val (wrong, missing) = MovePlanner.xErrors(b, want, keep)
        when {
            wrong.isEmpty() && missing.isEmpty() -> done("Marked ${want.size} cells")
            // Every fix gesture toggles, so acting on a misread cell creates the very mistake it meant to fix.
            !confirmed -> confirmRead(b, needSolution = false) { b2, _ -> verifyX(b2, want, keep, round, confirmed = true) }
            round >= MAX_FIX_ROUNDS -> done("Marked cells — ${wrong.size + missing.size} still off, tap X again")
            else -> {
                panel = panel.copy(status = "Fixing ${wrong.size + missing.size} cells…")
                markAndVerify(MovePlanner.fix(b, wrong, missing).first, want, keep, round + 1)
            }
        }
    }

    /** Waits for the game to animate, then reads the board. */
    private fun settleThen(needSolution: Boolean, action: (Board, IntArray?) -> Unit) {
        val s = session
        handler.postDelayed({ if (s == session) withBoard(needSolution, internal = true, action) }, AUTO_SETTLE_MS)
    }

    /**
     * Re-reads until two reads in a row agree, then runs [action] on that board. One screenshot can catch a cell
     * mid-animation (an X not drawn yet, a cat still fading in); a tap planned from it would undo a correct cell.
     */
    private fun confirmRead(prev: Board, needSolution: Boolean, tries: Int = 3, action: (Board, IntArray?) -> Unit): Unit =
        settleThen(needSolution) { b, sol ->
            when {
                b.n == prev.n && b.state contentEquals prev.state -> action(b, sol)
                tries > 1 -> confirmRead(b, needSolution, tries - 1, action)
                else -> done(UNSTABLE)
            }
        }

    private fun toggleReveal() {
        if (panel.busy) return
        val on = !panel.revealOn
        prefs.edit().putBoolean(K_REVEAL, on).apply()
        panel = panel.copy(revealOn = on)
        if (!on) { marks = marks.copy(rings = emptyList()); return }
        withBoard { _, sol ->
            sol ?: return@withBoard done(NO_SOLUTION)
            done(if (marks.rings.isEmpty()) "Solved" else "Showing ${marks.rings.size} cats")
        }
    }

    // ---------- auto-Answer ----------

    private fun answer() {
        if (panel.autoRunning) { stopAnswer(); return }
        if (panel.busy || SystemClock.uptimeMillis() - stoppedAt < STOP_GUARD_MS) return
        panel = panel.copy(busy = true, autoRunning = true, status = "Placing cats…")
        answerRun()
    }

    private fun stopAnswer() {
        session++ // drops the in-flight capture, settle wait, and remaining taps
        setPanelTouchable(true)
        setOverlayVisible(true)
        done("Stopped")
    }

    /** One read, then every missing cat (X's after all but the last) in a single run. The game rejects a wrong cat, so no re-check. */
    private fun answerRun() = withBoard(internal = true) { b, sol ->
        sol ?: return@withBoard done(NO_SOLUTION)
        val todo = MovePlanner.remaining(b, sol)
        if (todo.isEmpty()) return@withBoard done("Solved")
        panel = panel.copy(status = "Placing ${todo.size} cats…")
        autoTaps(AutoSolver.plan(b, sol, settings.order, settings.reverse, settings.answerX)) {
            marks = marks.copy(rings = marks.rings - todo.toSet(), hint = marks.hint.takeIf { it !in todo })
            done("Placed ${todo.size} cats")
        }
    }

    /** Runs taps without ending the busy state; Stop stays tappable unless a tap would land on the panel. */
    private fun autoTaps(list: List<Tap>, next: () -> Unit) {
        val s = session
        DebugLog.log("Running ${list.size} gestures")
        setPanelTouchable(!tapsHitPanel(list))
        taps.run(list, prefs.getInt(K_DOUBLE_MS, MovePlanner.GAP_MS.toInt()).toLong(), cancelled = { s != session }) { ok ->
            if (s != session) return@run
            setPanelTouchable(true)
            if (!ok) { DebugLog.log("Gestures cancelled"); stoppedAt = SystemClock.uptimeMillis(); done("Stopped") }
            else try { next() } catch (e: Exception) { DebugLog.error("Step after gestures failed", e); done(READ_FAIL) }
        }
    }

    private fun tapsHitPanel(list: List<Tap>): Boolean {
        val v = panelView ?: return false
        val loc = IntArray(2).also { v.getLocationOnScreen(it) }
        val pad = (8 * resources.displayMetrics.density).toInt()
        val r = Rect(loc[0] - pad, loc[1] - pad, loc[0] + v.width + pad, loc[1] + v.height + pad)
        return list.any { r.contains(it.x, it.y) }
    }

    // ---------- plumbing ----------

    private fun done(msg: String) {
        DebugLog.log("Result: $msg")
        panel = panel.copy(status = msg, busy = false, autoRunning = false)
        // Bubble hides the status line, so a quick action's result would be silent.
        if (mode == Mode.BUBBLE && panelView != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun runTaps(list: List<Tap>, success: () -> String) {
        val s = session
        panel = panel.copy(status = "Working…")
        DebugLog.log("Running ${list.size} gestures")
        setPanelTouchable(false)
        taps.run(list, prefs.getInt(K_DOUBLE_MS, MovePlanner.GAP_MS.toInt()).toLong(), cancelled = { s != session }) { ok ->
            if (s != session) return@run
            setPanelTouchable(true)
            done(if (!ok) "Stopped" else try { success() } catch (e: Exception) { DebugLog.error("After gestures failed", e); READ_FAIL })
        }
    }

    /** Hides the overlay, screenshots, reads the board and refreshes answer rings before running [action]. */
    private fun withBoard(needSolution: Boolean = true, internal: Boolean = false, action: (Board, IntArray?) -> Unit) {
        if (panel.busy && !internal) return
        panel = panel.copy(busy = true)
        setOverlayVisible(false)
        val s = session
        handler.postDelayed({ if (s == session) capture(s, true, needSolution, action) }, CAPTURE_DELAY_MS)
    }

    private fun handleBoard(b: Board, needSolution: Boolean, action: (Board, IntArray?) -> Unit) {
        val sol = if (needSolution || panel.revealOn) Solver.solve(b) else null
        DebugLog.log("Board ${b.n}x${b.n}, ${b.cats.size} cats, solution=${if (sol == null) "none" else "found"}\n${DebugLog.describe(b)}")
        marks = marks.copy(board = b,
            rings = if (panel.revealOn) sol?.let { MovePlanner.remaining(b, it) } ?: emptyList() else marks.rings)
        action(b, sol)
    }

    private fun capture(s: Int, retry: Boolean, needSolution: Boolean, action: (Board, IntArray?) -> Unit) {
        try { takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(res: ScreenshotResult) {
                var px: IntArray? = null; var w = 0; var h = 0
                try {
                    val hw = Bitmap.wrapHardwareBuffer(res.hardwareBuffer, res.colorSpace)
                    val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    if (bmp != null) {
                        w = bmp.width; h = bmp.height
                        px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
                        bmp.recycle()
                    }
                } catch (e: Exception) { DebugLog.error("Screenshot copy failed", e)
                } catch (e: OutOfMemoryError) { DebugLog.error("Screenshot copy out of memory", e)
                } finally { res.hardwareBuffer.close() }
                if (s != session) return
                setOverlayVisible(true)
                if (px == null) return done(READ_FAIL)
                try {
                    handleBoard(BoardReader.read(px, w, h), needSolution, action)
                } catch (_: BoardNotFound) {
                    DebugLog.log("Board not found in ${w}x$h screenshot")
                    marks = Marks()
                    done(if (panel.autoRunning) "Stopped — board not found" else "Open a Meowdoku puzzle first")
                } catch (e: Exception) {
                    DebugLog.error("Board read failed", e); setPanelTouchable(true); done(READ_FAIL)
                } catch (e: OutOfMemoryError) {
                    DebugLog.error("Board read out of memory", e); setPanelTouchable(true); done(READ_FAIL)
                }
            }

            override fun onFailure(errorCode: Int) {
                if (s != session) return
                DebugLog.log("Screenshot failed, code $errorCode, retry=$retry")
                if (retry) {
                    val delay = if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) THROTTLE_RETRY_MS else RETRY_MS
                    handler.postDelayed({ if (s == session) capture(s, false, needSolution, action) }, delay)
                }
                else { setOverlayVisible(true); done(READ_FAIL) }
            }
        }) } catch (e: Exception) {
            DebugLog.error("takeScreenshot threw", e)
            if (s == session) { setOverlayVisible(true); setPanelTouchable(true); done(READ_FAIL) }
        }
    }
}
