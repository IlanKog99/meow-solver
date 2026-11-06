package com.ilan.meowsolver

import com.ilan.meowsolver.core.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SolverTest {
    private val want = listOf(5, 2, 6, 4, 7, 1, 3, 0)

    @Test fun solvesFreshBoard() = assertEquals(want, Solver.solve(boardOf(*LEVEL81))!!.toList())

    @Test fun honoursCorrectPlacedCat() {
        val rows = LEVEL81.copyOf(); rows[2] = "A B B D B E F@ G"
        assertEquals(want, Solver.solve(boardOf(*rows))!!.toList())
    }

    @Test fun wrongPlacedCatGivesNull() {
        val rows = LEVEL81.copyOf(); rows[0] = "A@ A B B B C C C"
        assertNull(Solver.solve(boardOf(*rows)))
    }

    @Test fun twoCatsInOneRowGivesNull() {
        val rows = LEVEL81.copyOf(); rows[0] = "A A B B B C@ C C@"
        assertNull(Solver.solve(boardOf(*rows)))
    }

    @Test fun solutionObeysAllRules() {
        val b = boardOf(*LEVEL81); val s = Solver.solve(b)!!
        assertEquals(8, s.toSet().size)                                        // one per column
        assertEquals(8, s.indices.map { b.region[b.idx(it, s[it])] }.toSet().size) // one per colour
        for (r in 1 until 8) assertTrue("touching at row $r", abs(s[r] - s[r - 1]) > 1)
    }
}
