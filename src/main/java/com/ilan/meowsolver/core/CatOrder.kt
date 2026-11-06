package com.ilan.meowsolver.core

import kotlin.random.Random

/** Order Hint, Cat and Answer take the remaining cats in. */
enum class CatOrder(val label: String) { RANDOM("Random"), ROWS("Rows"), COLUMNS("Columns") }

object Order {
    /** Remaining solution cells in [order]; [reverse] flips rows/columns and is ignored for RANDOM. */
    fun cells(b: Board, sol: IntArray, order: CatOrder, reverse: Boolean, rnd: Random = Random): List<Int> {
        val left = MovePlanner.remaining(b, sol)
        val sorted = when (order) {
            CatOrder.RANDOM -> return left.shuffled(rnd)
            CatOrder.ROWS -> left.sortedBy { it / b.n }
            CatOrder.COLUMNS -> left.sortedBy { it % b.n }
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun label(order: CatOrder, reverse: Boolean) = when (order) {
        CatOrder.RANDOM -> "Random"
        CatOrder.ROWS -> if (reverse) "Bottom → top" else "Top → bottom"
        CatOrder.COLUMNS -> if (reverse) "Right → left" else "Left → right"
    }
}
