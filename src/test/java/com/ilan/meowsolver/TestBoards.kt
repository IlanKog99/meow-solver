package com.ilan.meowsolver

import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.Cell
import com.ilan.meowsolver.core.Tap

/** "A B* C@" → region letter, `*` = X, `@` = cat. Cells are 100 px squares. */
fun boardOf(vararg rows: String): Board {
    val toks = rows.map { it.trim().split(Regex("\\s+")) }
    val n = toks.size
    require(toks.all { it.size == n }) { "board must be square" }
    val flat = toks.flatten()
    val lo = IntArray(n) { it * 100 + 5 }; val hi = IntArray(n) { it * 100 + 95 }
    return Board(
        n, lo, hi, lo.copyOf(), hi.copyOf(),
        IntArray(n * n) { flat[it][0] - 'A' },
        Array(n * n) { when (flat[it].getOrNull(1)) { '*' -> Cell.X; '@' -> Cell.CAT; else -> Cell.EMPTY } },
    )
}

/** Level 81 regions (8×8), unique solution [5,2,6,4,7,1,3,0]. */
val LEVEL81 = arrayOf(
    "A A B B B C C C",
    "A A B D B B B C",
    "A B B D B E F G",
    "A A A D E E E G",
    "A A D D D E E G",
    "A D D E E E G G",
    "A A A H H E E G",
    "A A A A H H E E",
)

/** Cell index a tap lands on. */
fun Board.cellOf(t: Tap): Int =
    idx((0 until n).first { t.y in rowT[it]..rowB[it] }, (0 until n).first { t.x in colL[it]..colR[it] })

/** Applies taps with the game's rules: single EMPTY→X, X→EMPTY, CAT→EMPTY; double on EMPTY → CAT (otherwise no change). */
fun Board.after(taps: List<Tap>): Board {
    val st = state.copyOf()
    for (t in taps) {
        val i = cellOf(t)
        st[i] = if (t.double) (if (st[i] == Cell.EMPTY) Cell.CAT else st[i])
        else when (st[i]) { Cell.EMPTY -> Cell.X; Cell.X -> Cell.EMPTY; Cell.CAT -> Cell.EMPTY }
    }
    return Board(n, colL, colR, rowT, rowB, region, st)
}
