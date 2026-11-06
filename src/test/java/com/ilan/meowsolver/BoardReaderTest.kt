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
        val w = 400; val h = 1200
        val px = IntArray(w * h) { 0xFFF7F1EE.toInt() }
        for (i in 0 until 14) for (y in i * 80 + 10 until i * 80 + 70) for (x in 0 until w) px[y * w + x] = 0xFFC86432.toInt()
        BoardReader.read(px, w, h)
    }
}
