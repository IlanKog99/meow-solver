package com.ilan.meowsolver.core

import kotlin.math.abs

/** One gesture on a cell. double = fast double-tap (places a cat); single toggles empty ↔ X. */
data class Tap(val x: Int, val y: Int, val double: Boolean, val pauseAfterMs: Long)

object MovePlanner {
    const val GAP_MS = 90L
    const val CLEAR_PAUSE_MS = 350L
    const val STROKE_MS = 40L

    /** First row (top → bottom) whose solution cell has no cat yet. */
    fun nextCat(b: Board, sol: IntArray): Int? = remaining(b, sol).firstOrNull()

    fun remaining(b: Board, sol: IntArray): List<Int> =
        (0 until b.n).map { b.idx(it, sol[it]) }.filter { b.state[it] != Cell.CAT }

    fun catTaps(b: Board, cell: Int): List<Tap> {
        val x = b.cx(cell % b.n); val y = b.cy(cell / b.n)
        return when (b.state[cell]) {
            Cell.CAT -> emptyList()
            Cell.EMPTY -> listOf(Tap(x, y, true, GAP_MS))
            Cell.X -> listOf(Tap(x, y, false, CLEAR_PAUSE_MS), Tap(x, y, true, GAP_MS))
        }
    }

    /** Empty cells ruled out by any cat: same row, column, colour, or touching. */
    fun xTargets(b: Board): List<Int> {
        val n = b.n
        val out = LinkedHashSet<Int>()
        for (cat in b.cats) {
            val r = cat / n; val c = cat % n; val reg = b.region[cat]
            for (i in 0 until n * n) {
                if (b.state[i] != Cell.EMPTY) continue
                val rr = i / n; val cc = i % n
                if (rr == r || cc == c || b.region[i] == reg || (abs(rr - r) <= 1 && abs(cc - c) <= 1)) out += i
            }
        }
        return out.toList()
    }

    fun xTaps(b: Board): List<Tap> = xTargets(b).map { Tap(b.cx(it % b.n), b.cy(it / b.n), false, GAP_MS) }

    /** [startMs, durationMs] for each stroke of one tap gesture. */
    fun strokes(tap: Tap, doubleGapMs: Long): List<LongArray> =
        if (!tap.double) listOf(longArrayOf(0, STROKE_MS))
        else listOf(longArrayOf(0, STROKE_MS), longArrayOf(STROKE_MS + doubleGapMs, STROKE_MS))
}
