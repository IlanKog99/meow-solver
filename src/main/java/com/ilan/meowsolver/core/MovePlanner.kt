package com.ilan.meowsolver.core

import kotlin.random.Random

import kotlin.math.abs

/**
 * One gesture on a cell. double = fast double-tap (places a cat); single toggles empty ↔ X.
 * Non-empty [drag] = a drag from (x, y) through those points: starting on EMPTY it marks X on every cell it crosses
 * (existing X's stay); starting on X it clears every X it crosses.
 */
data class Tap(val x: Int, val y: Int, val double: Boolean, val pauseAfterMs: Long, val drag: List<Pair<Int, Int>> = emptyList())

object MovePlanner {
    const val GAP_MS = 90L
    const val CLEAR_PAUSE_MS = 350L
    const val STROKE_MS = 40L
    const val DRAG_STEP_MS = 70L // ponytail: fixed per-cell drag speed; make it a setting if the game drops cells

    /** Next solution cell without a cat, in the user's order (random by default). */
    fun nextCat(b: Board, sol: IntArray, order: CatOrder = CatOrder.RANDOM, reverse: Boolean = false, rnd: Random = Random): Int? =
        Order.cells(b, sol, order, reverse, rnd).firstOrNull()

    fun remaining(b: Board, sol: IntArray): List<Int> =
        (0 until b.n).map { b.idx(it, sol[it]) }.filter { b.state[it] != Cell.CAT }

    fun catTaps(b: Board, cell: Int, current: Cell = b.state[cell]): List<Tap> {
        val x = b.cx(cell % b.n); val y = b.cy(cell / b.n)
        return when (current) {
            Cell.CAT -> emptyList()
            Cell.EMPTY -> listOf(Tap(x, y, true, GAP_MS))
            Cell.X -> listOf(Tap(x, y, false, CLEAR_PAUSE_MS), Tap(x, y, true, GAP_MS))
        }
    }

    /** EMPTY cells (in [state]) ruled out by a cat at [cat]: same row, column, colour, or touching. */
    fun xTargetsFor(b: Board, state: Array<Cell>, cat: Int): List<Int> {
        val n = b.n; val r = cat / n; val c = cat % n; val reg = b.region[cat]
        return (0 until n * n).filter { i ->
            state[i] == Cell.EMPTY &&
                (i / n == r || i % n == c || b.region[i] == reg || (abs(i / n - r) <= 1 && abs(i % n - c) <= 1))
        }
    }

    /** Empty cells ruled out by any cat on the board. */
    fun xTargets(b: Board): List<Int> {
        val out = LinkedHashSet<Int>()
        for (cat in b.cats) out += xTargetsFor(b, b.state, cat)
        return out.toList()
    }

    /** Drags that mark every ruled-out empty cell. */
    fun xTaps(b: Board): List<Tap> = xDrags(b, b.state, xTargets(b))

    /** Drags marking [targets] (EMPTY in [state]); they start on a target and only cross targets or existing X's. */
    fun xDrags(b: Board, state: Array<Cell>, targets: Collection<Int>): List<Tap> {
        val pass = targets.toHashSet().apply { addAll((0 until b.n * b.n).filter { state[it] == Cell.X }) }
        return walks(b.n, targets.toSet(), pass).map { drag(b, it) }
    }

    /** Drags clearing the X's in [cells]; they never cross any other cell. */
    fun clearDrags(b: Board, cells: Collection<Int>): List<Tap> = walks(b.n, cells.toSet(), cells.toSet()).map { drag(b, it) }

    /** X's sitting on cells where the solution puts a cat. */
    fun wrongXs(b: Board, sol: IntArray): List<Int> = remaining(b, sol).filter { b.state[it] == Cell.X }

    /**
     * The X button, from one read: mark every cell the placed cats rule out (colour, row, column, 3x3 ring), and when
     * [clearWrong] also clear the X's on [wrong] (solution) cells first. No re-read or fix rounds: those stalled when the
     * board changed under them (cats placed meanwhile).
     */
    fun xButtonTaps(b: Board, wrong: List<Int>, clearWrong: Boolean): List<Tap> =
        if (clearWrong && wrong.isNotEmpty()) fix(b, wrong, xTargets(b)).first else xTaps(b)

    /** Clears [wrong] X's, then marks [missing] cells. Returns the gestures and the state they leave. */
    fun fix(b: Board, wrong: List<Int>, missing: List<Int>): Pair<List<Tap>, Array<Cell>> {
        val st = b.state.copyOf().also { s -> wrong.forEach { s[it] = Cell.EMPTY } }
        val out = clearDrags(b, wrong) + xDrags(b, st, missing)
        missing.forEach { st[it] = Cell.X }
        return out to st
    }

    /**
     * Cell paths covering every cell of [cover], each step to a side neighbour inside [pass] (so a straight line between
     * centres never clips another cell). One path per connected group: a row, column or hollow ring is a single drag.
     */
    fun walks(n: Int, cover: Set<Int>, pass: Set<Int>): List<List<Int>> {
        fun nb(i: Int) = listOfNotNull(
            (i - n).takeIf { it >= 0 }, (i + n).takeIf { it < n * n },
            (i - 1).takeIf { i % n > 0 }, (i + 1).takeIf { i % n < n - 1 },
        ).filter { it in pass }
        val left = cover.sorted().toCollection(LinkedHashSet())
        val out = mutableListOf<List<Int>>()
        while (left.isNotEmpty()) {
            // Start at an end of a line when there is one, so rows and columns become one straight drag.
            val start = left.minBy { c -> nb(c).count { it in left } }
            val walk = mutableListOf(start); left -= start
            while (true) {
                val cur = walk.last()
                val dir = if (walk.size > 1) cur - walk[walk.size - 2] else 0
                // BFS to the nearest uncovered cell, trying "keep going straight" first.
                val prev = hashMapOf(cur to cur); val q = ArrayDeque(listOf(cur)); var hit: Int? = null
                while (q.isNotEmpty() && hit == null) {
                    val c = q.removeFirst()
                    for (m in nb(c).sortedBy { if (c == cur && it - c == dir) 0 else 1 }) if (m !in prev) {
                        prev[m] = c
                        if (m in left) { hit = m; break }
                        q += m
                    }
                }
                hit ?: break
                val path = generateSequence(hit) { prev[it]!!.takeIf { p -> p != it } }.takeWhile { it != cur }.toList().asReversed()
                walk += path; left -= path.toSet()
            }
            out += walk
        }
        return out
    }

    /** A drag through the centres of [cells]; a single cell is a plain tap. */
    fun drag(b: Board, cells: List<Int>, pauseAfterMs: Long = GAP_MS): Tap {
        val pts = cells.map { b.cx(it % b.n) to b.cy(it / b.n) }
        return Tap(pts[0].first, pts[0].second, false, pauseAfterMs, pts.drop(1))
    }

    /** [startMs, durationMs] for each stroke of one tap gesture. */
    fun strokes(tap: Tap, doubleGapMs: Long): List<LongArray> = when {
        tap.drag.isNotEmpty() -> listOf(longArrayOf(0, STROKE_MS + DRAG_STEP_MS * tap.drag.size))
        !tap.double -> listOf(longArrayOf(0, STROKE_MS))
        else -> listOf(longArrayOf(0, STROKE_MS), longArrayOf(STROKE_MS + doubleGapMs, STROKE_MS))
    }
}
