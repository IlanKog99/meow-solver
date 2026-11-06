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
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.roundToInt

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
        private const val FLASH_MS = 2000L
        private const val HINT_MS = 5000L
        private const val NO_SOLUTION = "No solution — a placed cat is wrong"
    }

    private enum class Mode { CARD, BUBBLE }

    private val handler = Handler(Looper.getMainLooper())
    private val owner = OverlayOwner()
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

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        taps = TapRunner(this, handler)
        panel = panel.copy(answerOn = prefs.getBoolean(K_ANSWER, false))
        owner.start()
        instance = this
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
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
            x = prefs.getInt(K_X, (12 * d).toInt())
            y = prefs.getInt(K_Y, (80 * d).toInt())
        }
        panelView = composeView { PanelHost() }.also { wm.addView(it, panelLp) }
    }

    private fun close() {
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

    private fun movePanel(dx: Float, dy: Float) {
        panelLp.x += dx.roundToInt(); panelLp.y += dy.roundToInt()
        panelView?.let { wm.updateViewLayout(it, panelLp) }
    }

    private fun endDrag() {
        val view = panelView ?: return
        if (mode == Mode.BUBBLE) { // snap to the nearest side edge
            val sw = resources.displayMetrics.widthPixels
            panelLp.x = if (panelLp.x + view.width / 2 < sw / 2) 0 else sw - view.width
            wm.updateViewLayout(view, panelLp)
        }
        prefs.edit().putInt(K_X, panelLp.x).putInt(K_Y, panelLp.y).apply()
    }

    @Composable
    private fun PanelHost() {
        val drag = Modifier.pointerInput(Unit) {
            detectDragGestures(onDragEnd = ::endDrag) { change, d -> change.consume(); movePanel(d.x, d.y) }
        }
        when (mode) {
            Mode.CARD -> PanelCard(
                panel, onScan = ::scan, onHint = ::hint, onCat = ::placeCat, onX = ::markX,
                onAnswer = ::toggleAnswer, onMinimize = { mode = Mode.BUBBLE }, onClose = ::close, dragModifier = drag,
            )
            Mode.BUBBLE -> Bubble(onClick = { mode = Mode.CARD }, modifier = drag)
        }
    }

    // ---------- actions ----------

    private fun scan() = withBoard { b ->
        val sol = Solver.solve(b)
        val token = ++flashToken
        marks = marks.copy(board = b, flash = true,
            rings = if (sol != null && panel.answerOn) MovePlanner.remaining(b, sol) else marks.rings)
        handler.postDelayed({ if (token == flashToken) marks = marks.copy(flash = false) }, FLASH_MS)
        done("${b.n}×${b.n} · ${b.cats.size} cats · " + if (sol != null) "solvable" else "no solution")
    }

    private fun hint() = withBoard { b ->
        val sol = Solver.solve(b) ?: return@withBoard done(NO_SOLUTION)
        val cell = MovePlanner.nextCat(b, sol) ?: return@withBoard done("Solved")
        val token = ++hintToken
        marks = marks.copy(board = b, hint = cell)
        handler.postDelayed({ if (token == hintToken) marks = marks.copy(hint = null) }, HINT_MS)
        done("Next cat: row ${cell / b.n + 1}, col ${cell % b.n + 1}")
    }

    private fun placeCat() = withBoard { b ->
        val sol = Solver.solve(b) ?: return@withBoard done(NO_SOLUTION)
        val cell = MovePlanner.nextCat(b, sol) ?: return@withBoard done("Solved")
        runTaps(MovePlanner.catTaps(b, cell)) {
            marks = marks.copy(rings = marks.rings - cell, hint = marks.hint.takeIf { it != cell })
            "Cat placed: row ${cell / b.n + 1}, col ${cell % b.n + 1}"
        }
    }

    private fun markX() = withBoard { b ->
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
        withBoard { b ->
            val sol = Solver.solve(b) ?: return@withBoard done(NO_SOLUTION)
            marks = marks.copy(board = b, rings = MovePlanner.remaining(b, sol))
            done(if (marks.rings.isEmpty()) "Solved" else "Showing ${marks.rings.size} cats")
        }
    }

    // ---------- plumbing ----------

    private fun done(msg: String) { panel = panel.copy(status = msg, busy = false) }

    private fun runTaps(list: List<Tap>, success: () -> String) {
        panel = panel.copy(status = "Working…")
        setPanelTouchable(false)
        taps.run(list, prefs.getInt(K_DOUBLE_MS, MovePlanner.GAP_MS.toInt()).toLong()) { ok ->
            setPanelTouchable(true)
            done(if (ok) success() else "Stopped")
        }
    }

    private fun withBoard(action: (Board) -> Unit) {
        if (panel.busy) return
        panel = panel.copy(busy = true)
        setOverlayVisible(false)
        handler.postDelayed({ capture(retry = true, action) }, CAPTURE_DELAY_MS)
    }

    private fun capture(retry: Boolean, action: (Board) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(res: ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(res.hardwareBuffer, res.colorSpace)
                val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                res.hardwareBuffer.close()
                setOverlayVisible(true)
                if (bmp == null) return done("Couldn't read the screen. Try again")
                val w = bmp.width; val h = bmp.height
                val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
                bmp.recycle()
                val board = try { BoardReader.read(px, w, h) } catch (e: BoardNotFound) { return done("Open a Meowdoku puzzle first") }
                action(board)
            }

            override fun onFailure(errorCode: Int) {
                if (retry) handler.postDelayed({ capture(false, action) }, RETRY_MS)
                else { setOverlayVisible(true); done("Couldn't read the screen. Try again") }
            }
        })
    }
}
