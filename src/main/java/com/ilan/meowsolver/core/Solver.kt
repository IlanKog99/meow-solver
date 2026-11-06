package com.ilan.meowsolver.core

import kotlin.math.abs

object Solver {
    /** Column per row, honouring cats already on the board; null if impossible. */
    fun solve(b: Board): IntArray? {
        val n = b.n
        val fixed = IntArray(n) { -1 }
        for (i in b.cats) {
            if (fixed[i / n] >= 0) return null
            fixed[i / n] = i % n
        }
        val sol = IntArray(n)
        return if (place(b, 0, 0, 0L, -9, fixed, sol)) sol else null
    }

    // Row by row; only the previous row can touch, so checking prevCol covers the no-touch rule.
    private fun place(b: Board, row: Int, cols: Int, regs: Long, prev: Int, fixed: IntArray, sol: IntArray): Boolean {
        if (row == b.n) return true
        for (c in 0 until b.n) {
            if (fixed[row] >= 0 && c != fixed[row]) continue
            val reg = b.region[b.idx(row, c)]
            if (cols shr c and 1 != 0 || regs shr reg and 1L != 0L || abs(c - prev) <= 1) continue
            sol[row] = c
            if (place(b, row + 1, cols or (1 shl c), regs or (1L shl reg), c, fixed, sol)) return true
        }
        return false
    }
}
