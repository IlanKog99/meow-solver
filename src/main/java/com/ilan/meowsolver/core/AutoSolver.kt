package com.ilan.meowsolver.core

import kotlin.random.Random

object AutoSolver {
    /**
     * Taps for every missing solution cat in the user's cat order (random by default), each followed by the cells it
     * rules out; the last cat gets no X's after it. Planned once on a virtual copy of the board: the game itself
     * rejects a wrong cat, so there is no re-read and fix round. X's go on as drags that start on an empty cell,
     * so they never clear an X.
     */
    fun plan(
        b: Board, sol: IntArray,
        order: CatOrder = CatOrder.RANDOM, reverse: Boolean = false, withX: Boolean = true, rnd: Random = Random,
    ): List<Tap> {
        val todo = Order.cells(b, sol, order, reverse, rnd)
        val state = b.state.copyOf()
        val out = mutableListOf<Tap>()
        for ((i, cell) in todo.withIndex()) {
            // Let the game's double-tap window close before and after each cat, so taps on different cells never merge.
            out.lastOrNull()?.let { out[out.lastIndex] = it.copy(pauseAfterMs = MovePlanner.CLEAR_PAUSE_MS) }
            val cat = MovePlanner.catTaps(b, cell, state[cell])
            out += cat.dropLast(1) + cat.last().copy(pauseAfterMs = MovePlanner.CLEAR_PAUSE_MS)
            state[cell] = Cell.CAT
            if (!withX || i == todo.lastIndex) continue
            val xs = MovePlanner.xTargetsFor(b, state, cell)
            out += MovePlanner.xDrags(b, state, xs)
            xs.forEach { state[it] = Cell.X }
        }
        return out
    }
}
