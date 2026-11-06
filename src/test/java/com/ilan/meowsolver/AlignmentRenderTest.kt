package com.ilan.meowsolver

import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Solver
import com.ilan.meowsolver.ui.Marks
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.io.File
import javax.imageio.ImageIO

/** Draws what MarksLayer draws (same geometry) onto real screenshots; PNGs go to build/alignment for review. */
class AlignmentRenderTest {
    @Test fun marksLandOnCells() {
        val out = File("build/alignment").apply { mkdirs() }
        for (name in listOf("hi_211922.jpg", "hi_211447.jpg", "hi_211320.jpg", "lo_33560.jpg")) {
            val img = Fixtures.image(name)
            val b = Fixtures.board(name)
            val sol = Solver.solve(b)!!
            val marks = Marks(b, MovePlanner.remaining(b, sol), MovePlanner.nextCat(b, sol))
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            for (r in 0 until b.n) for (c in 0 until b.n) {
                val x = b.colL[c]; val y = b.rowT[r]; val w = b.colR[c] - b.colL[c]; val h = b.rowB[r] - b.rowT[r]
                g.color = Color(0x2A, 0x24, 0x20, 204); g.stroke = BasicStroke(7f); g.drawRect(x, y, w, h)
                g.color = Color.WHITE; g.stroke = BasicStroke(3f); g.drawRect(x, y, w, h)
            }
            for (i in marks.rings) {
                val rad = b.markRadius(i); val x = b.cx(i % b.n); val y = b.cy(i / b.n)
                assertTrue("$name ring $i radius", rad * 2 < b.colR[i % b.n] - b.colL[i % b.n])
                assertTrue("$name ring $i inside image", x - rad > 0 && x + rad < img.width && y - rad > 0 && y + rad < img.height)
                g.color = Color.WHITE; g.stroke = BasicStroke(rad * 0.32f)
                g.drawOval((x - rad).toInt(), (y - rad).toInt(), (2 * rad).toInt(), (2 * rad).toInt())
                g.color = Color(0xD9, 0x77, 0x57); g.stroke = BasicStroke(rad * 0.2f)
                g.drawOval((x - rad).toInt(), (y - rad).toInt(), (2 * rad).toInt(), (2 * rad).toInt())
            }
            marks.hint?.let { i ->
                val rad = b.markRadius(i); val x = b.cx(i % b.n); val y = b.cy(i / b.n)
                g.color = Color(0xD9, 0x77, 0x57, 235)
                g.fillOval((x - rad).toInt(), (y - rad).toInt(), (2 * rad).toInt(), (2 * rad).toInt())
            }
            g.dispose()
            ImageIO.write(img, "png", File(out, name.replace(".jpg", ".png")))
        }
    }
}
