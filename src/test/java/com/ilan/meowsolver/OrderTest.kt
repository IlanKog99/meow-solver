package com.ilan.meowsolver

import com.ilan.meowsolver.core.CatOrder
import com.ilan.meowsolver.core.Order
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class OrderTest {
    private val sol81 = intArrayOf(5, 2, 6, 4, 7, 1, 3, 0)
    private val b = boardOf(*LEVEL81)
    private fun cells(o: CatOrder, rev: Boolean) = Order.cells(b, sol81, o, rev, Random(1))

    @Test fun rowsTopToBottomAndReverse() {
        val rows = (0 until 8).map { it * 8 + sol81[it] }
        assertEquals(rows, cells(CatOrder.ROWS, false))
        assertEquals(rows.reversed(), cells(CatOrder.ROWS, true))
    }

    @Test fun columnsLeftToRightAndReverse() {
        val cols = (0 until 8).map { r -> r * 8 + sol81[r] }.sortedBy { it % 8 }
        assertEquals(cols, cells(CatOrder.COLUMNS, false))
        assertEquals(cols.reversed(), cells(CatOrder.COLUMNS, true))
    }

    @Test fun randomIsAPermutationAndIgnoresReverse() {
        val all = (0 until 8).map { it * 8 + sol81[it] }.toSet()
        assertEquals(all, cells(CatOrder.RANDOM, false).toSet())
        assertEquals(8, cells(CatOrder.RANDOM, true).size)
    }

    @Test fun skipsPlacedCats() {
        val rows = LEVEL81.copyOf(); rows[0] = "A A B B B C@ C C"
        assertEquals(1 * 8 + 2, Order.cells(boardOf(*rows), sol81, CatOrder.ROWS, false).first())
    }

    @Test fun labels() {
        assertEquals("Random", Order.label(CatOrder.RANDOM, false))
        assertEquals("Random", Order.label(CatOrder.RANDOM, true))
        assertEquals("Top → bottom", Order.label(CatOrder.ROWS, false))
        assertEquals("Bottom → top", Order.label(CatOrder.ROWS, true))
        assertEquals("Left → right", Order.label(CatOrder.COLUMNS, false))
        assertEquals("Right → left", Order.label(CatOrder.COLUMNS, true))
    }
}
