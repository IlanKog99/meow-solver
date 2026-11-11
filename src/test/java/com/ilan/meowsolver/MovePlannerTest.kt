package com.ilan.meowsolver

import com.ilan.meowsolver.core.CatOrder
import com.ilan.meowsolver.core.Cell
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MovePlannerTest {
    private val sol81 = intArrayOf(5, 2, 6, 4, 7, 1, 3, 0)

    @Test fun nextCatIsARandomRemainingCell() {
        val rows = LEVEL81.copyOf(); rows[0] = "A A B B B C@ C C"
        val b = boardOf(*rows)
        val left = MovePlanner.remaining(b, sol81).toSet()
        val picks = (0 until 50).map { MovePlanner.nextCat(b, sol81, rnd = Random(it))!! }
        assertTrue("picks outside remaining: $picks", picks.all { it in left })
        assertTrue("never varied: $picks", picks.toSet().size > 1)
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

    @Test fun xTapsAreOneDragNotDoubleTaps() {
        val b = boardOf("A@ A", "B B")
        val t = MovePlanner.xTaps(b)
        assertEquals(1, t.size)
        assertTrue(t.none { it.double })
        assertEquals(listOf(Cell.CAT, Cell.X, Cell.X, Cell.X), b.after(t).state.toList())
    }

    @Test fun fullRowIsOneStraightDrag() {
        val b = boardOf("A A A A", "B B B B", "C C C C", "D D D D")
        val t = MovePlanner.xDrags(b, b.state, listOf(4, 5, 6, 7))
        assertEquals(1, t.size)
        assertEquals(listOf(50 to 150, 150 to 150, 250 to 150, 350 to 150), listOf(t[0].x to t[0].y) + t[0].drag)
    }

    @Test fun hollowRingIsOneDrag() {
        val b = boardOf("A A A A A", "A A A A A", "A A B@ A A", "A A A A A", "A A A A A")
        val ring = listOf(6, 7, 8, 11, 13, 16, 17, 18)
        val t = MovePlanner.xDrags(b, b.state, ring)
        assertEquals(1, t.size)
        val after = b.after(t)
        assertEquals(ring, (0 until 25).filter { after.state[it] == Cell.X })
    }

    @Test fun dragsCrossOldXsButNeverOtherEmptyCells() {
        val b = boardOf("A A* A A", "B B B B", "C C C C", "D D D D")
        val t = MovePlanner.xDrags(b, b.state, listOf(0, 2, 3)) // gap at 1 is already X
        assertEquals(1, t.size)
        assertEquals(listOf(0, 1, 2, 3), (0 until 16).filter { b.after(t).state[it] == Cell.X })
        val e = boardOf("A A A A", "B B B B", "C C C C", "D D D D")
        val split = MovePlanner.xDrags(e, e.state, listOf(0, 2))
        assertEquals("1 is empty and not a target: two separate taps", 2, split.size)
    }

    @Test fun verifyFindsAndFixesWrongAndMissedXs() {
        val b = boardOf("A B@ C D", "E F G H", "I J K L", "M N O P")
        val want = MovePlanner.xTargets(b).toSet()
        val keep = setOf(15)
        // Bad drag: missed cell 0, wrongly marked 10 and 11; user's own X at 15 must stay.
        val bad = boardOf("A B@ C* D*", "E* F* G* H", "I J* K* L*", "M N* O P*")
        val (wrong, missing) = MovePlanner.xErrors(bad, want, keep)
        assertEquals(listOf(10, 11), wrong)
        assertEquals(listOf(0), missing)
        val (taps, st) = MovePlanner.fix(bad, wrong, missing)
        val fixed = bad.after(taps)
        assertEquals(st.toList(), fixed.state.toList())
        assertEquals(Pair(emptyList<Int>(), emptyList<Int>()), MovePlanner.xErrors(fixed, want, keep))
        assertEquals(Cell.X, fixed.state[15])
    }

    @Test fun xTargetsForUsesGivenState() {
        val b = boardOf(
            "A A B B B",
            "A A B B B",
            "C C C@ D D",
            "C E E D D",
            "E E E D D",
        )
        val st = b.state.copyOf().also { it[6] = Cell.X; it[22] = Cell.X }
        assertEquals(listOf(2, 7, 8, 10, 11, 13, 14, 15, 16, 17, 18), MovePlanner.xTargetsFor(b, st, 12))
    }

    @Test fun catTapsUseGivenCurrentState() {
        val b = boardOf("A B", "C D")
        assertEquals(
            listOf(Tap(150, 50, false, MovePlanner.CLEAR_PAUSE_MS), Tap(150, 50, true, MovePlanner.GAP_MS)),
            MovePlanner.catTaps(b, 1, Cell.X),
        )
    }

    @Test fun strokeTimings() {
        assertEquals(listOf(listOf(0L, 40L)), MovePlanner.strokes(Tap(0, 0, false, 0), 90).map { it.toList() })
        assertEquals(listOf(listOf(0L, 40L), listOf(130L, 40L)), MovePlanner.strokes(Tap(0, 0, true, 0), 90).map { it.toList() })
    }

    @Test fun nextCatFollowsOrder() {
        val b = boardOf(*LEVEL81)
        assertEquals(0 * 8 + 5, MovePlanner.nextCat(b, sol81, CatOrder.ROWS, reverse = false))
        assertEquals(7 * 8 + 0, MovePlanner.nextCat(b, sol81, CatOrder.ROWS, reverse = true))
        assertEquals(7 * 8 + 0, MovePlanner.nextCat(b, sol81, CatOrder.COLUMNS, reverse = false)) // col 0 is row 7
    }
}
