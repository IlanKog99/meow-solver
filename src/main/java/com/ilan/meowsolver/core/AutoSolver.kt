package com.ilan.meowsolver.core

import kotlin.random.Random

/** Result of re-reading the board during auto-Answer. */
sealed interface Check {
    data object Solved : Check
    data object NoSolution : Check
    data class LastOnly(val cell: Int) : Check
    data class Missing(val cells: List<Int>) : Check
}

object AutoSolver {
    /**
     * Taps for every missing solution cat except one, in the user's cat order (random by default); the last one is held back: the cat, then the cells it rules out.
     * Planned on a virtual copy of the board; X's go on as drags that start on an empty cell, so they never clear an X.
     */
    fun plan(
        b: Board, sol: IntArray,
        order: CatOrder = CatOrder.RANDOM, reverse: Boolean = false, withX: Boolean = true, rnd: Random = Random,
    ): List<Tap> {
        val todo = Order.cells(b, sol, order, reverse, rnd)
        if (todo.size <= 1) return emptyList()
        val state = b.state.copyOf()
        val out = mutableListOf<Tap>()
        for (cell in todo.dropLast(1)) {
            // Let the game's double-tap window close before and after each cat, so taps on different cells never merge.
            out.lastOrNull()?.let { out[out.lastIndex] = it.copy(pauseAfterMs = MovePlanner.CLEAR_PAUSE_MS) }
            val cat = MovePlanner.catTaps(b, cell, state[cell])
            out += cat.dropLast(1) + cat.last().copy(pauseAfterMs = MovePlanner.CLEAR_PAUSE_MS)
            state[cell] = Cell.CAT
            if (!withX) continue
            val xs = MovePlanner.xTargetsFor(b, state, cell)
            out += MovePlanner.xDrags(b, state, xs)
            xs.forEach { state[it] = Cell.X }
        }
        return out
    }

    /**
     * A later round: re-marks X's the last round missed, then plans the cats still missing. X's are never cleared here:
     * any X off the solution is harmless (the X's of a cat that didn't land included; clearing them only to re-mark them
     * after its cat lands is the "mistake, then fix" churn), and an X on a solution cell is cleared by its cat's own taps.
     */
    fun replan(
        b: Board, sol: IntArray, first: Boolean,
        order: CatOrder = CatOrder.RANDOM, reverse: Boolean = false, withX: Boolean = true, rnd: Random = Random,
    ): List<Tap> {
        val missing = if (first || !withX) emptyList() else MovePlanner.xTargets(b).filter { b.state[it] == Cell.EMPTY }
        val (fixTaps, st) = MovePlanner.fix(b, emptyList(), missing)
        val cats = plan(Board(b.n, b.colL, b.colR, b.rowT, b.rowB, b.region, st), sol, order, reverse, withX, rnd)
        // Close the double-tap window before the first cat, as between cats.
        val fix = fixTaps.lastOrNull()?.let { fixTaps.dropLast(1) + it.copy(pauseAfterMs = MovePlanner.CLEAR_PAUSE_MS) } ?: fixTaps
        return fix + cats
    }

    fun check(b: Board, sol: IntArray?): Check {
        sol ?: return Check.NoSolution
        val todo = MovePlanner.remaining(b, sol)
        return when (todo.size) {
            0 -> Check.Solved
            1 -> Check.LastOnly(todo[0])
            else -> Check.Missing(todo)
        }
    }

    /** The last cat gets no X's after it. */
    fun lastCatTaps(b: Board, cell: Int): List<Tap> = MovePlanner.catTaps(b, cell)
}
