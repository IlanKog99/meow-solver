package com.ilan.meowsolver.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Tap

/** Plays taps one gesture at a time; stops on the first cancelled gesture. */
class TapRunner(private val svc: AccessibilityService, private val handler: Handler) {
    fun run(taps: List<Tap>, doubleGapMs: Long, cancelled: () -> Boolean = { false }, onDone: (completed: Boolean) -> Unit) =
        step(taps, 0, doubleGapMs, cancelled, onDone)

    private fun step(taps: List<Tap>, i: Int, gap: Long, cancelled: () -> Boolean, onDone: (Boolean) -> Unit) {
        if (cancelled()) { onDone(false); return }
        if (i == taps.size) { onDone(true); return }
        val t = taps[i]
        val path = Path().apply { moveTo(t.x.toFloat(), t.y.toFloat()) }
        val g = GestureDescription.Builder().apply {
            for (s in MovePlanner.strokes(t, gap)) addStroke(GestureDescription.StrokeDescription(path, s[0], s[1]))
        }.build()
        val ok = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription) { handler.postDelayed({ step(taps, i + 1, gap, cancelled, onDone) }, t.pauseAfterMs) }
            override fun onCancelled(d: GestureDescription) = onDone(false)
        }, null)
        if (!ok) onDone(false)
    }
}
