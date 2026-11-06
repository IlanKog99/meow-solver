package com.ilan.meowsolver

import com.ilan.meowsolver.core.BoardNotFound
import com.ilan.meowsolver.core.BoardReader
import com.ilan.meowsolver.core.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardReaderTest {
    @Test fun readsAndSolvesAll41Screenshots() {
        val all = Fixtures.expected()
        assertEquals(41, all.size)
        val failures = all.mapNotNull { e ->
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)
            when {
                b.n != e.n -> "${e.name}: n=${b.n} want ${e.n}"
                b.cats.size != e.cats -> "${e.name}: cats=${b.cats.size} want ${e.cats}"
                sol == null -> "${e.name}: no solution"
                !sol.contentEquals(e.solution) -> "${e.name}: ${sol.toList()} want ${e.solution.toList()}"
                else -> null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun handSolvedLevelsMatch() {
        // Solved by hand in chat: level 83, 81, 32
        assertEquals(listOf(0, 5, 2, 6, 3, 9, 7, 4, 8, 1), Solver.solve(Fixtures.board("hi_211922.jpg"))!!.toList())
        assertEquals(listOf(5, 2, 6, 4, 7, 1, 3, 0), Solver.solve(Fixtures.board("hi_211447.jpg"))!!.toList())
        assertEquals(listOf(1, 7, 4, 0, 9, 3, 6, 8, 2, 5), Solver.solve(Fixtures.board("lo_33560.jpg"))!!.toList())
    }

    @Test(expected = BoardNotFound::class) fun blankScreenThrows() {
        BoardReader.read(IntArray(400 * 800) { 0xFFF7F1EE.toInt() }, 400, 800)
    }

    @Test(expected = BoardNotFound::class) fun tooManyRowsThrows() {
        // 14x14 grid of square cells, white gaps, distinct saturated colours: only the n > 12 guard can reject it
        val n = 14; val cell = 40; val gap = 6; val pad = 20
        val w = pad * 2 + n * cell + (n - 1) * gap; val h = w
        val px = IntArray(w * h) { 0xFFF7F1EE.toInt() }
        val colors = IntArray(n) { java.awt.Color.HSBtoRGB(it / n.toFloat(), 1f, 1f) } // 14 hues, > 30 apart in RGB
        for (row in 0 until n) for (col in 0 until n) {
            val c = colors[(row * 5 + col * 3) % n]
            val x0 = pad + col * (cell + gap); val y0 = pad + row * (cell + gap)
            for (y in y0 until y0 + cell) for (x in x0 until x0 + cell) px[y * w + x] = c
        }
        BoardReader.read(px, w, h)
    }
}
