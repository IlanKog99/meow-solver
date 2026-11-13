package com.ilan.meowsolver

import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.Cell
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Solver
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Spec-driven tests for the X button (A: colour, B: row/col, C: 3x3, D: wrong X's on solution cells). */
class XButtonTest {
    private val n = 8
    private val sol = intArrayOf(5, 2, 6, 4, 7, 1, 3, 0)

    /** LEVEL81 with cats and X's at the given cell indices. */
    private fun level(cats: Set<Int> = emptySet(), xs: Set<Int> = emptySet()): Board = boardOf(*Array(n) { r ->
        LEVEL81[r].split(' ').mapIndexed { c, t -> val i = r * n + c; t + (if (i in cats) "@" else if (i in xs) "*" else "") }.joinToString(" ")
    })

    private fun cell(r: Int, c: Int) = r * n + c
    private fun solCell(r: Int) = cell(r, sol[r])

    /** Independently computed A ∪ B ∪ C, excluding cat cells. */
    private fun targets(b: Board): Set<Int> {
        val cats = (0 until n * n).filter { b.state[it] == Cell.CAT }
        return (0 until n * n).filter { i ->
            b.state[i] != Cell.CAT && cats.any { k ->
                val (r, c) = i / n to i % n; val (kr, kc) = k / n to k % n
                b.region[i] == b.region[k] || r == kr || c == kc || (abs(r - kr) <= 1 && abs(c - kc) <= 1)
            }
        }.toSet()
    }

    private fun press(b: Board, clear: Boolean): Board {
        val s = Solver.solve(b)!!
        return b.after(MovePlanner.xButtonTaps(b, MovePlanner.wrongXs(b, s), clear))
    }

    @Test fun solverMatchesFixture() = assertArrayEquals(sol, Solver.solve(level())!!)

    @Test fun marksColourRowColumnAndNeighbours() {
        for (clear in listOf(false, true)) {
            val b = level(cats = setOf(solCell(0), solCell(3)))
            val out = press(b, clear)
            for (t in targets(b)) assertEquals("cell $t clear=$clear", Cell.X, out.state[t])
            assertEquals(Cell.CAT, out.state[solCell(0)]); assertEquals(Cell.CAT, out.state[solCell(3)])
            for (r in 0 until n) if (r != 0 && r != 3) assertEquals("sol row $r", Cell.EMPTY, out.state[solCell(r)])
            // nothing outside A∪B∪C gets an X
            for (i in 0 until n * n) if (i !in targets(b) && b.state[i] == Cell.EMPTY) assertEquals("stray X at $i", Cell.EMPTY, out.state[i])
        }
    }

    @Test fun singleCatEachCorner() {
        for (r in listOf(0, 7)) {
            val b = level(cats = setOf(solCell(r)))
            val out = press(b, false)
            for (t in targets(b)) assertEquals(Cell.X, out.state[t])
            assertEquals(Cell.CAT, out.state[solCell(r)])
        }
    }

    @Test fun wrongXsAreXsOnSolutionCellsOnly() {
        val user = cell(7, 7) // not a solution cell, not a target of the cats below
        val b = level(cats = setOf(solCell(0)), xs = setOf(solCell(5), solCell(7), user))
        assertEquals(setOf(solCell(5), solCell(7)), MovePlanner.wrongXs(b, sol).toSet())
        assertEquals(emptyList<Int>(), MovePlanner.wrongXs(level(cats = setOf(solCell(0))), sol))
    }

    @Test fun clearWrongOnRemovesWrongXsAndKeepsUserXs() {
        val user = cell(7, 7)
        val wrong = setOf(solCell(4), solCell(5), solCell(7))
        val b = level(cats = setOf(solCell(0), solCell(2)), xs = wrong + user)
        assertTrue(user !in targets(b))
        val out = press(b, true)
        for (w in wrong) assertEquals("wrong X $w must be cleared and not re-marked", Cell.EMPTY, out.state[w])
        assertEquals(Cell.X, out.state[user])
        for (t in targets(b)) assertEquals(Cell.X, out.state[t])
        assertEquals(Cell.CAT, out.state[solCell(0)]); assertEquals(Cell.CAT, out.state[solCell(2)])
    }

    @Test fun clearWrongOffLeavesWrongXs() {
        val user = cell(7, 7)
        val wrong = setOf(solCell(4), solCell(7))
        val b = level(cats = setOf(solCell(0)), xs = wrong + user)
        val out = press(b, false)
        for (w in wrong) assertEquals(Cell.X, out.state[w])
        assertEquals(Cell.X, out.state[user])
        for (t in targets(b)) assertEquals(Cell.X, out.state[t])
        for (r in 1 until n) if (solCell(r) !in wrong) assertEquals(Cell.EMPTY, out.state[solCell(r)])
    }

    @Test fun existingXsOnTargetsStayX() {
        val pre = cell(0, 0) // same row as cat (0,5)
        val b = level(cats = setOf(solCell(0)), xs = setOf(pre))
        val out = press(b, true)
        assertEquals(Cell.X, out.state[pre])
        for (t in targets(b)) assertEquals(Cell.X, out.state[t])
    }

    @Test fun secondPressChangesNothing() {
        val b = press(level(cats = setOf(solCell(1), solCell(6)), xs = setOf(solCell(3))), true)
        assertArrayEquals(b.state, press(b, true).state)
    }

    @Test fun noCatsNoTaps() {
        assertEquals(0, MovePlanner.xButtonTaps(level(), emptyList(), true).size)
        assertEquals(0, MovePlanner.xButtonTaps(level(xs = setOf(cell(7, 7))), emptyList(), false).size)
    }

    @Test fun noCatsButWrongXClearOnlyTouchesWrongX() {
        val b = level(xs = setOf(solCell(3), cell(7, 7)))
        val out = b.after(MovePlanner.xButtonTaps(b, MovePlanner.wrongXs(b, sol), true))
        assertEquals(Cell.EMPTY, out.state[solCell(3)])
        assertEquals(Cell.X, out.state[cell(7, 7)])
        assertEquals(1, out.state.count { it == Cell.X })
    }
}
