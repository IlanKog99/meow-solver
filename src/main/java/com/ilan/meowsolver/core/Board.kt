package com.ilan.meowsolver.core

enum class Cell { EMPTY, X, CAT }

/** A read board. Bounds are screen pixels; region/state are indexed by r * n + c. */
class Board(
    val n: Int,
    val colL: IntArray, val colR: IntArray,
    val rowT: IntArray, val rowB: IntArray,
    val region: IntArray,
    val state: Array<Cell>,
) {
    fun idx(r: Int, c: Int) = r * n + c
    fun cx(c: Int) = (colL[c] + colR[c]) / 2
    fun cy(r: Int) = (rowT[r] + rowB[r]) / 2
    fun markRadius(cell: Int) = (colR[cell % n] - colL[cell % n]) * 0.36f
    val cats: List<Int> get() = (0 until n * n).filter { state[it] == Cell.CAT }
}
