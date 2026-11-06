package com.ilan.meowsolver

import com.ilan.meowsolver.core.AutoSolver
import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.Cell
import com.ilan.meowsolver.core.Check
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AutoSolverTest {
    private val sol81 = intArrayOf(5, 2, 6, 4, 7, 1, 3, 0)

    /** null when the cats form a valid complete solution. */
    private fun rulesProblem(b: Board): String? {
        val cats = b.cats
        if (cats.size != b.n) return "cats=${cats.size}"
        if (cats.map { it / b.n }.toSet().size != b.n) return "row clash"
        if (cats.map { it % b.n }.toSet().size != b.n) return "column clash"
        if (cats.map { b.region[it] }.toSet().size != b.n) return "colour clash"
        for (a in cats) for (c in cats) if (a < c && abs(a / b.n - c / b.n) <= 1 && abs(a % b.n - c % b.n) <= 1) return "touching $a $c"
        return null
    }

    @Test fun planThenLastSolvesEveryFixture() {
        val failures = Fixtures.expected().mapNotNull { e ->
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)!!
            val mid = b.after(AutoSolver.plan(b, sol))
            val c = AutoSolver.check(mid, Solver.solve(mid))
            if (MovePlanner.remaining(b, sol).isEmpty()) return@mapNotNull if (c == Check.Solved) null else "${e.name}: $c"
            if (c !is Check.LastOnly) return@mapNotNull "${e.name}: after plan $c"
            val fin = mid.after(AutoSolver.lastCatTaps(mid, c.cell))
            rulesProblem(fin)?.let { "${e.name}: $it" }
                ?: if (AutoSolver.check(fin, Solver.solve(fin)) != Check.Solved) "${e.name}: not Solved" else null
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun planNeverTapsACellTwiceOrTouchesTheLastCat() {
        for (e in Fixtures.expected()) {
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)!!
            val plan = AutoSolver.plan(b, sol)
            val byCell = plan.groupBy { b.cellOf(it) }
            for ((cell, taps) in byCell) {
                val ok = taps.size == 1 ||
                    (taps.size == 2 && !taps[0].double && taps[1].double && b.state[cell] == Cell.X)
                assertTrue("${e.name}: cell $cell tapped $taps", ok)
            }
            MovePlanner.remaining(b, sol).lastOrNull()?.let { last ->
                assertTrue("${e.name}: last cat $last tapped", last !in byCell)
            }
        }
    }

    @Test fun missedCatTapIsFixedByReplan() {
        val b = Fixtures.board("hi_211922.jpg")
        val sol = Solver.solve(b)!!
        val plan = AutoSolver.plan(b, sol)
        val missed = plan.first { it.double }
        val mid = b.after(plan - missed)
        val c = AutoSolver.check(mid, Solver.solve(mid))
        assertTrue("expected Missing, got $c", c is Check.Missing && b.cellOf(missed) in c.cells)
        val fixed = mid.after(AutoSolver.plan(mid, Solver.solve(mid)!!))
        assertTrue(AutoSolver.check(fixed, Solver.solve(fixed)) is Check.LastOnly)
    }

    @Test fun wrongCatGivesNoSolution() {
        val rows = LEVEL81.copyOf(); rows[0] = "A@ A B B B C C C"
        val b = boardOf(*rows)
        assertEquals(Check.NoSolution, AutoSolver.check(b, Solver.solve(b)))
    }

    @Test fun solvedBoardIsSolved() {
        val rows = LEVEL81.mapIndexed { r, line ->
            line.split(" ").mapIndexed { c, t -> if (c == sol81[r]) "$t@" else t }.joinToString(" ")
        }.toTypedArray()
        val b = boardOf(*rows)
        assertEquals(Check.Solved, AutoSolver.check(b, Solver.solve(b)))
    }

    @Test fun oneRemainingPlansNothingAndIsLastOnly() {
        val rows = LEVEL81.mapIndexed { r, line ->
            line.split(" ").mapIndexed { c, t -> if (c == sol81[r] && r < 7) "$t@" else t }.joinToString(" ")
        }.toTypedArray()
        val b = boardOf(*rows)
        val sol = Solver.solve(b)!!
        assertEquals(emptyList<Any>(), AutoSolver.plan(b, sol))
        assertEquals(Check.LastOnly(7 * 8 + 0), AutoSolver.check(b, sol))
    }
}
