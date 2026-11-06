package com.ilan.meowsolver.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import com.ilan.meowsolver.core.Board

/** Everything drawn over the game. Board coordinates are screen pixels. */
data class Marks(
    val board: Board? = null,
    val rings: List<Int> = emptyList(),
    val hint: Int? = null,
)

@Composable
fun MarksLayer(marks: Marks) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val b = marks.board ?: return
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
            fun center(i: Int) = Offset(b.cx(i % b.n) - origin.x, b.cy(i / b.n) - origin.y)
            for (i in marks.rings) {
                val rad = b.markRadius(i)
                drawCircle(Color.White, rad, center(i), style = Stroke(rad * 0.32f))
                drawCircle(Meow.Clay, rad, center(i), style = Stroke(rad * 0.2f))
            }
        }
        marks.hint?.let { HintPulse(b, it, origin) }
    }
}

/** Separate composable so the infinite transition only runs while a hint is shown. */
@Composable
private fun HintPulse(b: Board, i: Int, origin: Offset) {
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(
        0.85f, 1.1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "pulse",
    )
    val cat = rememberVectorPainter(MeowIcons.Cat)
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(b.cx(i % b.n) - origin.x, b.cy(i / b.n) - origin.y)
        val rad = b.markRadius(i) * pulse
        drawCircle(Meow.Clay.copy(alpha = 0.92f), rad, c)
        val s = rad * 1.2f
        translate(c.x - s / 2, c.y - s / 2) {
            with(cat) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
}
