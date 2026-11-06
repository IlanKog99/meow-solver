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
     * Planned on a virtual copy of the board so no cell is ever tapped twice (a second tap would clear an X).
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
            for (i in MovePlanner.xTargetsFor(b, state, cell)) {
                out += Tap(b.cx(i % b.n), b.cy(i / b.n), false, MovePlanner.GAP_MS)
                state[i] = Cell.X
            }
        }
        return out
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
