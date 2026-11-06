package com.ilan.meowsolver.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object MeowIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
        }.build()

    val Cat = icon("cat") {
        moveTo(4f, 11f); lineTo(4f, 4f); lineTo(8.5f, 7.5f); lineTo(15.5f, 7.5f); lineTo(20f, 4f); lineTo(20f, 11f)
        curveTo(20f, 16.5f, 16.5f, 20f, 12f, 20f); curveTo(7.5f, 20f, 4f, 16.5f, 4f, 11f); close()
        moveTo(9f, 12.5f); lineTo(9f, 13f)
        moveTo(15f, 12.5f); lineTo(15f, 13f)
        moveTo(11f, 15.5f); lineTo(12f, 16.5f); lineTo(13f, 15.5f)
    }
    val X = icon("x") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Scan = icon("scan") {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
        moveTo(7f, 12f); lineTo(17f, 12f)
    }
    val Bulb = icon("bulb") {
        moveTo(9f, 15f); curveTo(7f, 13.5f, 6f, 11.8f, 6f, 9.5f)
        arcTo(6f, 6f, 0f, false, true, 18f, 9.5f)
        curveTo(18f, 11.8f, 17f, 13.5f, 15f, 15f); close()
        moveTo(9f, 18f); lineTo(15f, 18f)
        moveTo(10f, 21f); lineTo(14f, 21f)
    }
    val Eye = icon("eye") {
        moveTo(2f, 12f); curveTo(5f, 6.5f, 19f, 6.5f, 22f, 12f); curveTo(19f, 17.5f, 5f, 17.5f, 2f, 12f); close()
        moveTo(12f, 9.5f); arcTo(2.5f, 2.5f, 0f, true, true, 12f, 14.5f); arcTo(2.5f, 2.5f, 0f, true, true, 12f, 9.5f)
    }
    val Minus = icon("minus") { moveTo(6f, 12f); lineTo(18f, 12f) }
    val Power = icon("power") {
        moveTo(12f, 3f); lineTo(12f, 11f)
        moveTo(7f, 6.5f); arcTo(7f, 7f, 0f, true, false, 17f, 6.5f)
    }
    val Check = icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
}
