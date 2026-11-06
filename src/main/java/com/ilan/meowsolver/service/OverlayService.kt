package com.ilan.meowsolver.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ComposeView
import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.BoardNotFound
import com.ilan.meowsolver.core.BoardReader
import com.ilan.meowsolver.core.MovePlanner
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
        const val K_ANSWER = "answerOn"
        const val K_DOUBLE_MS = "doubleTapMs"
        private const val K_X = "panelX"
        private const val K_Y = "panelY"
        private const val CAPTURE_DELAY_MS = 150L
        private const val RETRY_MS = 400L
        private const val THROTTLE_RETRY_MS = 1000L // takeScreenshot is rate-limited to ~1/s
        private const val FLASH_MS = 2000L
        private const val HINT_MS = 5000L
        private const val NO_SOLUTION = "No solution — a placed cat is wrong"
        private const val READ_FAIL = "Couldn't read the screen. Try again"
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
    private var panel by mutableStateOf(PanelState("Ready", busy = false, answerOn = false))
    private var marks by mutableStateOf(Marks())
    private var hintToken = 0
    private var flashToken = 0
    private var session = 0 // bumped by close()/unbind: in-flight work from an older session is dropped
    private var dragDownX = 0f; private var dragDownY = 0f
    private var dragWinX = 0; private var dragWinY = 0
    private var dragMoved = false

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        taps = TapRunner(this, handler)
        panel = panel.copy(answerOn = prefs.getBoolean(K_ANSWER, false))
        owner = OverlayOwner().also { it.start() }
        instance = this
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        session++
        removeViews()
        owner.stop()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        session++
        removeViews()
        owner.stop()
        return super.onUnbind(intent)
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
        panel = panel.copy(status = "Ready", busy = false)
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
    private fun dragTouch(e: MotionEvent, onClick: (() -> Unit)?): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragDownX = e.rawX; dragDownY = e.rawY; dragWinX = panelLp.x; dragWinY = panelLp.y; dragMoved = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - dragDownX; val dy = e.rawY - dragDownY
                if (!dragMoved && hypot(dx, dy) > ViewConfiguration.get(this).scaledTouchSlop) dragMoved = true
                val view = panelView
                if (dragMoved && view != null) {
                    panelLp.x = dragWinX + dx.toInt(); panelLp.y = dragWinY + dy.toInt()
                    wm.updateViewLayout(view, panelLp)
                }
            }
            MotionEvent.ACTION_UP -> if (dragMoved) endDrag() else onClick?.invoke()
            MotionEvent.ACTION_CANCEL -> if (dragMoved) endDrag()
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
                panel, onScan = ::scan, onHint = ::hint, onCat = ::placeCat, onX = ::markX,
                onAnswer = ::toggleAnswer, onMinimize = { mode = Mode.BUBBLE; settle() }, onClose = ::close,
                dragModifier = Modifier.pointerInteropFilter { dragTouch(it, null) }, // applied to the status text only
            )
            Mode.BUBBLE -> Bubble(
                onClick = { mode = Mode.CARD; settle() }, // touch taps are consumed by dragTouch; this serves TalkBack
                modifier = Modifier.pointerInteropFilter { dragTouch(it) { mode = Mode.CARD; settle() } },
            )
        }
    }

    // ---------- actions ----------

    private fun scan() = withBoard { b, sol ->
        val token = ++flashToken
        marks = marks.copy(flash = true)
        handler.postDelayed({ if (token == flashToken) marks = marks.copy(flash = false) }, FLASH_MS)
        done("${b.n}×${b.n} · ${b.cats.size} cats · " + if (sol != null) "solvable" else "no solution")
    }

    private fun hint() = withBoard { b, sol ->
        sol ?: return@withBoard done(NO_SOLUTION)
        val cell = MovePlanner.nextCat(b, sol) ?: return@withBoard done("Solved")
        val token = ++hintToken
        marks = marks.copy(hint = cell)
        handler.postDelayed({ if (token == hintToken) marks = marks.copy(hint = null) }, HINT_MS)
        done("Next cat: row ${cell / b.n + 1}, col ${cell % b.n + 1}")
    }

    private fun placeCat() = withBoard { b, sol ->
        sol ?: return@withBoard done(NO_SOLUTION)
        val cell = MovePlanner.nextCat(b, sol) ?: return@withBoard done("Solved")
        runTaps(MovePlanner.catTaps(b, cell)) {
            marks = marks.copy(rings = marks.rings - cell, hint = marks.hint.takeIf { it != cell })
            "Cat placed: row ${cell / b.n + 1}, col ${cell % b.n + 1}"
        }
    }

    private fun markX() = withBoard(needSolution = false) { b, _ ->
        val t = MovePlanner.xTaps(b)
        if (t.isEmpty()) return@withBoard done("Nothing to mark")
        runTaps(t) { "Marked ${t.size} cells" }
    }

    private fun toggleAnswer() {
        if (panel.busy) return
        val on = !panel.answerOn
        prefs.edit().putBoolean(K_ANSWER, on).apply()
        panel = panel.copy(answerOn = on)
        if (!on) { marks = marks.copy(rings = emptyList()); return }
        withBoard { _, sol ->
            sol ?: return@withBoard done(NO_SOLUTION)
            done(if (marks.rings.isEmpty()) "Solved" else "Showing ${marks.rings.size} cats")
        }
    }

    // ---------- plumbing ----------

    private fun done(msg: String) { panel = panel.copy(status = msg, busy = false) }

    private fun runTaps(list: List<Tap>, success: () -> String) {
        val s = session
        panel = panel.copy(status = "Working…")
        setPanelTouchable(false)
        taps.run(list, prefs.getInt(K_DOUBLE_MS, MovePlanner.GAP_MS.toInt()).toLong(), cancelled = { s != session }) { ok ->
            if (s != session) return@run
            setPanelTouchable(true)
            done(if (!ok) "Stopped" else try { success() } catch (_: Exception) { READ_FAIL })
        }
    }

    /** Hides the overlay, screenshots, reads the board and refreshes answer rings before running [action]. */
    private fun withBoard(needSolution: Boolean = true, action: (Board, IntArray?) -> Unit) {
        if (panel.busy) return
        panel = panel.copy(busy = true)
        setOverlayVisible(false)
        val s = session
        handler.postDelayed({ if (s == session) capture(s, true, needSolution, action) }, CAPTURE_DELAY_MS)
    }

    private fun handleBoard(b: Board, needSolution: Boolean, action: (Board, IntArray?) -> Unit) {
        val sol = if (needSolution || panel.answerOn) Solver.solve(b) else null
        marks = marks.copy(board = b,
            rings = if (panel.answerOn) sol?.let { MovePlanner.remaining(b, it) } ?: emptyList() else marks.rings)
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
                } catch (_: Exception) {
                } catch (_: OutOfMemoryError) {
                } finally { res.hardwareBuffer.close() }
                if (s != session) return
                setOverlayVisible(true)
                if (px == null) return done(READ_FAIL)
                try {
                    handleBoard(BoardReader.read(px, w, h), needSolution, action)
                } catch (_: BoardNotFound) {
                    marks = Marks()
                    done("Open a Meowdoku puzzle first")
                } catch (_: Exception) {
                    setPanelTouchable(true); done(READ_FAIL)
                } catch (_: OutOfMemoryError) {
                    setPanelTouchable(true); done(READ_FAIL)
                }
            }

            override fun onFailure(errorCode: Int) {
                if (s != session) return
                if (retry) {
                    val delay = if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) THROTTLE_RETRY_MS else RETRY_MS
                    handler.postDelayed({ if (s == session) capture(s, false, needSolution, action) }, delay)
                }
                else { setOverlayVisible(true); done(READ_FAIL) }
            }
        }) } catch (_: Exception) {
            if (s == session) { setOverlayVisible(true); setPanelTouchable(true); done(READ_FAIL) }
        }
    }
}
