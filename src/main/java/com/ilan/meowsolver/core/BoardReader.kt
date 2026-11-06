package com.ilan.meowsolver.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class BoardNotFound(msg: String) : Exception(msg)

object BoardReader {
    private fun r(p: Int) = (p shr 16) and 255
    private fun g(p: Int) = (p shr 8) and 255
    private fun b(p: Int) = p and 255
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun colored(p: Int): Boolean {
        val mx = max(r(p), max(g(p), b(p))); val mn = min(r(p), min(g(p), b(p)))
        return mx - mn > 28 && mx > 60
    }
    private fun dist(p: Int, q: Int): Int {
        val dr = r(p) - r(q); val dg = g(p) - g(q); val db = b(p) - b(q)
        return sqrt((dr * dr + dg * dg + db * db).toDouble()).toInt()
    }

    fun read(px: IntArray, w: Int, h: Int): Board {
        // 1. Rows: y-lines where >15% of the width is coloured (white gaps between rows are ~0%).
        val rowOn = BooleanArray(h) { y -> fraction(w) { x -> px[y * w + x] } > 0.15 }
        val runs = runs(rowOn, max(8, h / 120))
        // The board is the longest chain of runs with similar height and small gaps.
        var bestS = 0; var bestLen = 0
        for (s in runs.indices) {
            val hh = runs[s][1] - runs[s][0]
            var len = 1
            while (s + len < runs.size) {
                val a = runs[s + len - 1]; val c = runs[s + len]
                if (kotlin.math.abs((c[1] - c[0]) - hh) > hh * 0.25 || c[0] - a[1] > hh * 0.5) break
                len++
            }
            if (len > bestLen) { bestLen = len; bestS = s }
        }
        if (bestLen < 4 || bestLen > 12) throw BoardNotFound("Board not found") // >12: junk screen, would overflow Int masks
        val n = bestLen
        val rowT = IntArray(n) { runs[bestS + it][0] }
        val rowB = IntArray(n) { runs[bestS + it][1] }

        // 2. Columns inside the board band.
        val top = rowT[0]; val bot = rowB[n - 1]
        val colOn = BooleanArray(w) { x -> fraction(bot - top) { i -> px[(top + i) * w + x] } > 0.15 }
        val cr = runs(colOn, max(8, w / 120))
        val colL = IntArray(n); val colR = IntArray(n)
        if (cr.size == n) {
            for (i in 0 until n) { colL[i] = cr[i][0]; colR[i] = cr[i][1] }
        } else { // ponytail: fallback splits the coloured span evenly; upgrade if a board ever trips it
            val l = cr.firstOrNull()?.get(0) ?: 0; val rr = cr.lastOrNull()?.get(1) ?: w
            val step = (rr - l) / n.toDouble()
            for (i in 0 until n) { colL[i] = (l + i * step + step * 0.04).toInt(); colR[i] = (l + (i + 1) * step - step * 0.04).toInt() }
        }

        // 3. Cell colour + state.
        val cellRgb = IntArray(n * n)
        val state = Array(n * n) { Cell.EMPTY }
        for (row in 0 until n) for (c in 0 until n) {
            val x0 = colL[c]; val y0 = rowT[row]; val cw = colR[c] - x0; val ch = rowB[row] - y0
            val i = row * n + c
            cellRgb[i] = cellColor(px, w, x0, y0, cw, ch)
            state[i] = cellState(px, w, x0, y0, cw, ch, cellRgb[i])
        }

        // 4. Cluster colours into exactly n regions (greedy, then merge closest).
        val cl = mutableListOf<IntArray>() // sumR, sumG, sumB, count
        val asg = IntArray(n * n)
        for (i in 0 until n * n) {
            val p = cellRgb[i]
            var best = -1; var bd = Int.MAX_VALUE
            cl.forEachIndexed { k, s -> val d = dist(p, cent(s)); if (d < bd) { bd = d; best = k } }
            if (best < 0 || bd > 30) { cl += intArrayOf(r(p), g(p), b(p), 1); best = cl.size - 1 }
            else cl[best].let { it[0] += r(p); it[1] += g(p); it[2] += b(p); it[3]++ }
            asg[i] = best
        }
        while (cl.size > n) {
            var ba = 0; var bb = 1; var bdd = Int.MAX_VALUE
            for (a in cl.indices) for (bi in a + 1 until cl.size) {
                val d = dist(cent(cl[a]), cent(cl[bi])); if (d < bdd) { bdd = d; ba = a; bb = bi }
            }
            for (j in 0..3) cl[ba][j] += cl[bb][j]
            cl.removeAt(bb)
            for (i in asg.indices) { if (asg[i] == bb) asg[i] = ba else if (asg[i] > bb) asg[i]-- }
        }
        if (cl.size < n) throw BoardNotFound("Found ${cl.size} colours for ${n}x$n")
        return Board(n, colL, colR, rowT, rowB, asg, state)
    }

    private inline fun fraction(len: Int, pixel: (Int) -> Int): Double {
        var cnt = 0; var tot = 0; var i = 0
        while (i < len) { tot++; if (colored(pixel(i))) cnt++; i += 2 }
        return if (tot == 0) 0.0 else cnt.toDouble() / tot
    }

    private fun cent(k: IntArray) = rgb(k[0] / k[3], k[1] / k[3], k[2] / k[3])

    private fun runs(on: BooleanArray, minLen: Int): List<IntArray> {
        val out = mutableListOf<IntArray>(); var s = -1
        for (i in 0..on.size) {
            val v = i < on.size && on[i]
            if (v && s < 0) s = i
            if (!v && s >= 0) { if (i - s >= minLen) out += intArrayOf(s, i); s = -1 }
        }
        return out
    }

    /** Region colour = consensus of edge samples (avoids the X and the cat in the middle). */
    private fun cellColor(px: IntArray, w: Int, x0: Int, y0: Int, cw: Int, ch: Int): Int {
        val pts = arrayOf(.5 to .09, .09 to .5, .91 to .5, .5 to .91, .14 to .14, .86 to .14, .14 to .86, .86 to .86, .3 to .09, .7 to .91)
        val s = pts.map { (fx, fy) -> avg(px, w, x0 + (fx * cw).toInt(), y0 + (fy * ch).toInt()) }.filter(::colored)
        if (s.isEmpty()) return avg(px, w, x0 + cw / 2, y0 + ch / 10)
        val best = s.maxBy { p -> s.count { dist(p, it) < 30 } }
        val near = s.filter { dist(best, it) < 30 }
        return rgb(near.sumOf { r(it) } / near.size, near.sumOf { g(it) } / near.size, near.sumOf { b(it) } / near.size)
    }

    private fun avg(px: IntArray, w: Int, x: Int, y: Int): Int {
        var rr = 0; var gg = 0; var bb = 0
        for (dy in -1..1) for (dx in -1..1) { val p = px[(y + dy) * w + x + dx]; rr += r(p); gg += g(p); bb += b(p) }
        return rgb(rr / 9, gg / 9, bb / 9)
    }

    private fun cellState(px: IntArray, w: Int, x0: Int, y0: Int, cw: Int, ch: Int, base: Int): Cell {
        var dark = 0; var off = 0; var tot = 0
        for (i in 0 until 11) for (j in 0 until 11) {
            val p = px[(y0 + ((0.2 + 0.06 * j) * ch).toInt()) * w + x0 + ((0.2 + 0.06 * i) * cw).toInt()]
            tot++
            if (max(r(p), max(g(p), b(p))) < 70) dark++ else if (dist(p, base) > 70) off++
        }
        return when {
            dark > tot * 0.04 -> Cell.CAT
            off > tot * 0.12 -> Cell.X // white (or red) X strokes
            else -> Cell.EMPTY
        }
    }
}
