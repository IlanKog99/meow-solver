package com.ilan.meowsolver.core

/** Result of re-reading the board during auto-Answer. */
sealed interface Check {
    data object Solved : Check
    data object NoSolution : Check
    data class LastOnly(val cell: Int) : Check
    data class Missing(val cells: List<Int>) : Check
}

object AutoSolver {
    /**
     * Taps for every missing solution cat except the last (top → bottom): the cat, then the cells it rules out.
     * Planned on a virtual copy of the board so no cell is ever tapped twice (a second tap would clear an X).
     */
    fun plan(b: Board, sol: IntArray): List<Tap> {
        val todo = MovePlanner.remaining(b, sol)
        if (todo.size <= 1) return emptyList()
        val state = b.state.copyOf()
        val out = mutableListOf<Tap>()
        for (cell in todo.dropLast(1)) {
            out += MovePlanner.catTaps(b, cell, state[cell])
            state[cell] = Cell.CAT
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
