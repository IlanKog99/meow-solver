package com.ilan.meowsolver.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
    val flash: Boolean = false,
)

@Composable
fun MarksLayer(marks: Marks) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(
        0.85f, 1.1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "pulse",
    )
    val cat = rememberVectorPainter(MeowIcons.Cat)
    val b = marks.board ?: return
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        fun center(i: Int) = Offset(b.cx(i % b.n) - origin.x, b.cy(i / b.n) - origin.y)
        if (marks.flash) for (r in 0 until b.n) for (c in 0 until b.n) {
            val off = Offset(b.colL[c] - origin.x, b.rowT[r] - origin.y)
            val sz = Size((b.colR[c] - b.colL[c]).toFloat(), (b.rowB[r] - b.rowT[r]).toFloat())
            drawRect(Color(0xCC2A2420), off, sz, style = Stroke(7f))
            drawRect(Color.White, off, sz, style = Stroke(3f))
        }
        for (i in marks.rings) {
            val rad = b.markRadius(i)
            drawCircle(Color.White, rad, center(i), style = Stroke(rad * 0.32f))
            drawCircle(Meow.Clay, rad, center(i), style = Stroke(rad * 0.2f))
        }
        marks.hint?.let { i ->
            val rad = b.markRadius(i) * pulse
            drawCircle(Meow.Clay.copy(alpha = 0.92f), rad, center(i))
            val s = rad * 1.2f
            translate(center(i).x - s / 2, center(i).y - s / 2) {
                with(cat) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
            }
        }
    }
}
