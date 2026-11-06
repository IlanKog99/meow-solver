package com.ilan.meowsolver

import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.Cell

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
