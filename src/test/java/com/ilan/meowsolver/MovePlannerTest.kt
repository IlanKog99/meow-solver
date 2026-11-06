package com.ilan.meowsolver

import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovePlannerTest {
    private val sol81 = intArrayOf(5, 2, 6, 4, 7, 1, 3, 0)

    @Test fun nextCatIsFirstRowWithoutCat() {
        val rows = LEVEL81.copyOf(); rows[0] = "A A B B B C@ C C"
        assertEquals(1 * 8 + 2, MovePlanner.nextCat(boardOf(*rows), sol81))
    }

    @Test fun nextCatNullWhenSolved() {
        val rows = LEVEL81.mapIndexed { r, line ->
            line.split(" ").mapIndexed { c, t -> if (c == sol81[r]) "$t@" else t }.joinToString(" ")
        }.toTypedArray()
        assertNull(MovePlanner.nextCat(boardOf(*rows), sol81))
    }

    @Test fun remainingSkipsPlacedCats() {
        val rows = LEVEL81.copyOf(); rows[0] = "A A B B B C@ C C"
        assertEquals(7, MovePlanner.remaining(boardOf(*rows), sol81).size)
    }

    @Test fun catOnEmptyIsOneDoubleTap() {
        val b = boardOf("A B", "C D")
        assertEquals(listOf(Tap(150, 50, true, MovePlanner.GAP_MS)), MovePlanner.catTaps(b, 1))
    }

    @Test fun catOnXClearsThenDoubleTaps() {
        val b = boardOf("A B*", "C D")
        assertEquals(
            listOf(Tap(150, 50, false, MovePlanner.CLEAR_PAUSE_MS), Tap(150, 50, true, MovePlanner.GAP_MS)),
            MovePlanner.catTaps(b, 1),
        )
    }

    @Test fun catOnCatDoesNothing() = assertEquals(emptyList<Tap>(), MovePlanner.catTaps(boardOf("A B@", "C D"), 1))

    @Test fun xTargetsCoverRowColNeighboursAndColour() {
        val b = boardOf(
            "A A B* B B",
            "A A B B B",
            "C C C@ D D",
            "C E E D D",
            "E E E D D",
        )
        // row 2, col 2, 8 neighbours, rest of colour C; (0,2) already X is skipped
        assertEquals(listOf(6, 7, 8, 10, 11, 13, 14, 15, 16, 17, 18, 22), MovePlanner.xTargets(b).sorted())
    }

    @Test fun xTargetsDedupeAcrossCats() {
        val b = boardOf("A@ A A A", "B B B B", "C C C C", "D D D@ D")
        val t = MovePlanner.xTargets(b)
        assertEquals(t.toSet().size, t.size)
    }

    @Test fun xTapsAreSingleTaps() {
        val b = boardOf("A@ A", "B B")
        assertEquals(listOf(false, false, false), MovePlanner.xTaps(b).map { it.double })
    }

    @Test fun strokeTimings() {
        assertEquals(listOf(listOf(0L, 40L)), MovePlanner.strokes(Tap(0, 0, false, 0), 90).map { it.toList() })
        assertEquals(listOf(listOf(0L, 40L), listOf(130L, 40L)), MovePlanner.strokes(Tap(0, 0, true, 0), 90).map { it.toList() })
    }
}
