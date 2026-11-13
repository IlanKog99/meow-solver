package com.ilan.meowsolver

import com.ilan.meowsolver.core.AutoSolver
import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.CatOrder
import com.ilan.meowsolver.core.Cell
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Solver
import com.ilan.meowsolver.core.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

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

    private fun solCells(b: Board, sol: IntArray) = sol.mapIndexed { r, c -> b.idx(r, c) }.toSet()

    private fun ruledOut(b: Board, cat: Int, i: Int): Boolean {
        val n = b.n
        return i != cat && (i / n == cat / n || i % n == cat % n || b.region[i] == b.region[cat] ||
            (abs(i / n - cat / n) <= 1 && abs(i % n - cat % n) <= 1))
    }

    private fun boardWith(catRows: Int): Board = boardOf(*LEVEL81.mapIndexed { r, line ->
        line.split(" ").mapIndexed { c, t -> if (c == sol81[r] && r < catRows) "$t@" else t }.joinToString(" ")
    }.toTypedArray())

    @Test fun planAloneSolvesEveryFixture() {
        for (withX in listOf(true, false)) {
            val failures = Fixtures.expected().mapNotNull { e ->
                val b = Fixtures.board(e.name)
                val sol = Solver.solve(b)!!
                val fin = b.after(AutoSolver.plan(b, sol, withX = withX, rnd = Random(3)))
                rulesProblem(fin)?.let { "${e.name} withX=$withX: $it" }
                    ?: if (MovePlanner.remaining(fin, sol).isNotEmpty()) "${e.name} withX=$withX: cats missing" else null
            }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        }
    }

    /** Steps through the plan on a simulated board, checking every gesture against the spec. */
    @Test fun planGesturesFollowTheRulesOnEveryFixture() {
        for (e in Fixtures.expected()) for (seed in 0 until 3) {
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)!!
            val plan = AutoSolver.plan(b, sol, rnd = Random(seed))
            val sc = solCells(b, sol)
            var cur = b
            plan.forEachIndexed { k, t ->
                val cell = cur.cellOf(t)
                when {
                    t.drag.isNotEmpty() -> {
                        assertTrue("${e.name}: drag $k starts on ${cur.state[cell]}/solution=${cell in sc}", cur.state[cell] == Cell.EMPTY && cell !in sc)
                        for ((x, y) in t.drag) {
                            val c = cur.cellOf(Tap(x, y, false, 0))
                            assertTrue("${e.name}: drag $k crosses solution cell $c", c !in sc || cur.state[c] == Cell.X)
                        }
                    }
                    t.double -> assertTrue("${e.name}: cat $k on $cell", cell in sc && cur.state[cell] == Cell.EMPTY)
                    // single tap: a one-cell X mark on an empty non-solution cell...
                    cur.state[cell] == Cell.EMPTY -> assertTrue("${e.name}: single X tap $k on solution cell $cell", cell !in sc)
                    else -> { // ...or clearing an X off a solution cell, right before its cat
                        assertTrue("${e.name}: single tap $k on $cell", cell in sc && cur.state[cell] == Cell.X)
                        assertTrue("${e.name}: clear $k not followed by cat", plan.getOrNull(k + 1)?.let { it.double && cur.cellOf(it) == cell } == true)
                    }
                }
                cur = cur.after(listOf(t))
            }
        }
    }

    @Test fun eachCatButTheLastIsFollowedByItsXsAndTheLastByNothing() {
        for (e in Fixtures.expected()) {
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)!!
            val plan = AutoSolver.plan(b, sol, rnd = Random(5))
            if (MovePlanner.remaining(b, sol).isEmpty()) continue
            assertTrue("${e.name}: last gesture is not the last cat", plan.last().double)
            val cats = plan.indices.filter { plan[it].double }
            for ((j, k) in cats.dropLast(1).withIndex()) {
                val cat = b.cellOf(plan[k])
                val st = b.after(plan.subList(0, cats[j + 1])) // everything up to the next cat
                val empty = (0 until b.n * b.n).filter { ruledOut(b, cat, it) && st.state[it] == Cell.EMPTY }
                assertEquals("${e.name}: cells ruled out by $cat still empty before next cat", emptyList<Int>(), empty)
            }
        }
    }

    @Test fun catsOnlyPlanTapsOnlySolutionCells() {
        for (e in Fixtures.expected()) {
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)!!
            val plan = AutoSolver.plan(b, sol, withX = false)
            assertTrue("${e.name}: drag in cats-only plan", plan.none { it.drag.isNotEmpty() })
            assertTrue("${e.name}: tapped a non-solution cell", plan.all { b.cellOf(it) in solCells(b, sol) })
            assertEquals("${e.name}", MovePlanner.remaining(b, sol).size, plan.count { it.double })
        }
    }

    @Test fun solvedBoardPlansNothing() {
        val b = boardWith(8)
        assertEquals(emptyList<Tap>(), AutoSolver.plan(b, Solver.solve(b)!!))
    }

    @Test fun oneRemainingPlansJustThatCat() {
        val b = boardWith(7)
        val plan = AutoSolver.plan(b, Solver.solve(b)!!)
        assertEquals(1, plan.size)
        assertTrue(plan[0].double)
        assertEquals(7 * 8 + 0, b.cellOf(plan[0]))
        assertEquals(null, rulesProblem(b.after(plan)))
    }

    @Test fun xOnSolutionCellIsClearedThenCatted() {
        val rows = LEVEL81.copyOf(); rows[0] = "A A B B B C* C C" // X sits on row 0's solution cell (col 5)
        val b = boardOf(*rows)
        val sol = Solver.solve(b)!!
        for (seed in 0 until 10) {
            val plan = AutoSolver.plan(b, sol, rnd = Random(seed))
            val on5 = plan.filter { it.drag.isEmpty() && b.cellOf(it) == 5 }
            assertEquals(listOf(false, true), on5.map { it.double })
            assertEquals(null, rulesProblem(b.after(plan)))
        }
    }

    @Test fun catDoubleTapIsIsolatedByPauses() {
        for (e in Fixtures.expected()) {
            val b = Fixtures.board(e.name)
            val plan = AutoSolver.plan(b, Solver.solve(b)!!)
            plan.forEachIndexed { i, t ->
                if (t.double) {
                    assertEquals("${e.name}: pause after cat $i", MovePlanner.CLEAR_PAUSE_MS, t.pauseAfterMs)
                    if (i > 0) assertEquals("${e.name}: pause before cat $i", MovePlanner.CLEAR_PAUSE_MS, plan[i - 1].pauseAfterMs)
                }
            }
        }
    }

    @Test fun planOrderIsShuffled() {
        val b = Fixtures.board("hi_211922.jpg")
        val sol = Solver.solve(b)!!
        val orders = (0 until 20).map { seed -> AutoSolver.plan(b, sol, rnd = Random(seed)).filter { it.double }.map { b.cellOf(it) } }
        assertTrue("plan order never varied", orders.toSet().size > 1)
        orders.forEach { assertEquals(MovePlanner.remaining(b, sol).toSet(), it.toSet()) }
    }

    @Test fun rowsPlanCoversAllRowsInOrder() {
        val b = boardOf(*LEVEL81)
        val sol = Solver.solve(b)!!
        for (withX in listOf(true, false)) {
            val down = AutoSolver.plan(b, sol, CatOrder.ROWS, reverse = false, withX = withX).filter { it.double }.map { b.cellOf(it) / 8 }
            assertEquals((0 until 8).toList(), down)
            val up = AutoSolver.plan(b, sol, CatOrder.ROWS, reverse = true, withX = withX).filter { it.double }.map { b.cellOf(it) / 8 }
            assertEquals((7 downTo 0).toList(), up)
        }
    }

    @Test fun columnsPlanCoversAllColumnsInOrder() {
        val b = boardOf(*LEVEL81)
        val sol = Solver.solve(b)!!
        val right = AutoSolver.plan(b, sol, CatOrder.COLUMNS).filter { it.double }.map { b.cellOf(it) % 8 }
        assertEquals((0 until 8).toList(), right)
        val left = AutoSolver.plan(b, sol, CatOrder.COLUMNS, reverse = true).filter { it.double }.map { b.cellOf(it) % 8 }
        assertEquals((7 downTo 0).toList(), left)
    }
}
