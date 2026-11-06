package com.ilan.meowsolver;

import java.util.ArrayList;
import java.util.List;

/** Reads a Meowdoku board from raw ARGB screen pixels and solves it. Pure Java (no Android deps). */
public final class Board {
    public static final int EMPTY = 0, X = 1, CAT = 2;

    public int n;
    public int[] colL, colR, rowT, rowB; // cell bounds in screen pixels
    public int[] region;                 // n*n region id
    public int[] state;                  // n*n EMPTY / X / CAT
    public int[] regionRgb;              // centroid colour per region

    public int cx(int c) { return (colL[c] + colR[c]) / 2; }
    public int cy(int r) { return (rowT[r] + rowB[r]) / 2; }

    // ---------- reading ----------

    static int r(int p) { return (p >> 16) & 255; }
    static int g(int p) { return (p >> 8) & 255; }
    static int b(int p) { return p & 255; }
    static int sat(int p) { int r = r(p), g = g(p), b = b(p); return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)); }
    static boolean colored(int p) { return sat(p) > 28 && Math.max(r(p), Math.max(g(p), b(p))) > 60; }
    static int dist(int p, int q) { int dr = r(p) - r(q), dg = g(p) - g(q), db = b(p) - b(q); return (int) Math.sqrt(dr * dr + dg * dg + db * db); }

    public static Board parse(int[] px, int w, int h) {
        // 1. rows: y-lines where >15% of the width is coloured
        boolean[] rowOn = new boolean[h];
        for (int y = 0; y < h; y++) {
            int cnt = 0, tot = 0;
            for (int x = 0; x < w; x += 2) { tot++; if (colored(px[y * w + x])) cnt++; }
            rowOn[y] = cnt > tot * 0.15; // gaps between cell rows are ~0
        }
        List<int[]> runs = runs(rowOn, Math.max(8, h / 120));
        // board = longest chain of consecutive runs with similar height and small gaps
        int bestS = 0, bestLen = 0;
        for (int s = 0; s < runs.size(); s++) {
            int len = 1;
            int hh = runs.get(s)[1] - runs.get(s)[0];
            while (s + len < runs.size()) {
                int[] a = runs.get(s + len - 1), c = runs.get(s + len);
                int ch = c[1] - c[0];
                if (Math.abs(ch - hh) > hh * 0.25 || c[0] - a[1] > hh * 0.5) break;
                len++;
            }
            if (len > bestLen) { bestLen = len; bestS = s; }
        }
        if (bestLen < 4) throw new IllegalStateException("Board not found");
        Board bd = new Board();
        int n = bestLen;
        bd.n = n;
        bd.rowT = new int[n]; bd.rowB = new int[n];
        for (int i = 0; i < n; i++) { bd.rowT[i] = runs.get(bestS + i)[0]; bd.rowB[i] = runs.get(bestS + i)[1]; }

        // 2. columns: x-lines inside the board band where >15% coloured
        int top = bd.rowT[0], bot = bd.rowB[n - 1];
        boolean[] colOn = new boolean[w];
        for (int x = 0; x < w; x++) {
            int cnt = 0, tot = 0;
            for (int y = top; y < bot; y += 2) { tot++; if (colored(px[y * w + x])) cnt++; }
            colOn[x] = cnt > tot * 0.15;
        }
        List<int[]> cr = runs(colOn, Math.max(8, w / 120));
        bd.colL = new int[n]; bd.colR = new int[n];
        if (cr.size() == n) {
            for (int i = 0; i < n; i++) { bd.colL[i] = cr.get(i)[0]; bd.colR[i] = cr.get(i)[1]; }
        } else { // ponytail: fallback = evenly split the coloured span
            int l = cr.isEmpty() ? 0 : cr.get(0)[0], rr = cr.isEmpty() ? w : cr.get(cr.size() - 1)[1];
            double step = (rr - l) / (double) n;
            for (int i = 0; i < n; i++) { bd.colL[i] = (int) (l + i * step + step * 0.04); bd.colR[i] = (int) (l + (i + 1) * step - step * 0.04); }
        }

        // 3. cell colour + state
        int[] cellRgb = new int[n * n];
        bd.state = new int[n * n];
        for (int rr = 0; rr < n; rr++) for (int c = 0; c < n; c++) {
            int x0 = bd.colL[c], y0 = bd.rowT[rr], cw = bd.colR[c] - x0, ch = bd.rowB[rr] - y0;
            cellRgb[rr * n + c] = cellColor(px, w, x0, y0, cw, ch);
            bd.state[rr * n + c] = cellState(px, w, x0, y0, cw, ch, cellRgb[rr * n + c]);
        }

        // 4. cluster colours into exactly n regions
        List<int[]> cl = new ArrayList<>(); // {sumR,sumG,sumB,count}
        int[] asg = new int[n * n];
        for (int i = 0; i < n * n; i++) {
            int p = cellRgb[i], best = -1, bd2 = 9999;
            for (int k = 0; k < cl.size(); k++) { int d = dist(p, cent(cl.get(k))); if (d < bd2) { bd2 = d; best = k; } }
            if (best < 0 || bd2 > 30) { cl.add(new int[]{r(p), g(p), b(p), 1}); best = cl.size() - 1; }
            else { int[] k = cl.get(best); k[0] += r(p); k[1] += g(p); k[2] += b(p); k[3]++; }
            asg[i] = best;
        }
        while (cl.size() > n) { // merge two closest clusters
            int ba = 0, bb = 1, bdd = 99999;
            for (int a = 0; a < cl.size(); a++) for (int b2 = a + 1; b2 < cl.size(); b2++) {
                int d = dist(cent(cl.get(a)), cent(cl.get(b2)));
                if (d < bdd) { bdd = d; ba = a; bb = b2; }
            }
            int[] A = cl.get(ba), B = cl.get(bb);
            for (int j = 0; j < 4; j++) A[j] += B[j];
            cl.remove(bb);
            for (int i = 0; i < n * n; i++) { if (asg[i] == bb) asg[i] = ba; else if (asg[i] > bb) asg[i]--; }
        }
        if (cl.size() < n) throw new IllegalStateException("Found " + cl.size() + " colours for " + n + "x" + n);
        bd.region = asg;
        bd.regionRgb = new int[n];
        for (int k = 0; k < n; k++) bd.regionRgb[k] = cent(cl.get(k));
        return bd;
    }

    static int cent(int[] k) { return 0xFF000000 | ((k[0] / k[3]) << 16) | ((k[1] / k[3]) << 8) | (k[2] / k[3]); }

    static List<int[]> runs(boolean[] on, int minLen) {
        List<int[]> out = new ArrayList<>();
        int s = -1;
        for (int i = 0; i <= on.length; i++) {
            boolean v = i < on.length && on[i];
            if (v && s < 0) s = i;
            if (!v && s >= 0) { if (i - s >= minLen) out.add(new int[]{s, i}); s = -1; }
        }
        return out;
    }

    /** Region colour = consensus of samples along the cell's edges (avoids the X and the cat in the middle). */
    static int cellColor(int[] px, int w, int x0, int y0, int cw, int ch) {
        double[][] pts = {{.5, .09}, {.09, .5}, {.91, .5}, {.5, .91}, {.14, .14}, {.86, .14}, {.14, .86}, {.86, .86}, {.3, .09}, {.7, .91}};
        List<Integer> s = new ArrayList<>();
        for (double[] q : pts) {
            int p = avg(px, w, x0 + (int) (q[0] * cw), y0 + (int) (q[1] * ch));
            if (colored(p)) s.add(p);
        }
        if (s.isEmpty()) return avg(px, w, x0 + cw / 2, y0 + ch / 10);
        int best = s.get(0), bestCnt = -1;
        for (int p : s) { int c = 0; for (int q : s) if (dist(p, q) < 30) c++; if (c > bestCnt) { bestCnt = c; best = p; } }
        int R = 0, G = 0, B = 0, k = 0;
        for (int q : s) if (dist(best, q) < 30) { R += r(q); G += g(q); B += b(q); k++; }
        return 0xFF000000 | ((R / k) << 16) | ((G / k) << 8) | (B / k);
    }

    static int avg(int[] px, int w, int x, int y) {
        int R = 0, G = 0, B = 0;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) { int p = px[(y + dy) * w + x + dx]; R += r(p); G += g(p); B += b(p); }
        return 0xFF000000 | ((R / 9) << 16) | ((G / 9) << 8) | (B / 9);
    }

    static int cellState(int[] px, int w, int x0, int y0, int cw, int ch, int base) {
        int dark = 0, off = 0, tot = 0;
        for (int i = 0; i < 11; i++) for (int j = 0; j < 11; j++) {
            int p = px[(y0 + (int) ((0.2 + 0.06 * j) * ch)) * w + x0 + (int) ((0.2 + 0.06 * i) * cw)];
            tot++;
            if (Math.max(r(p), Math.max(g(p), b(p))) < 70) dark++;
            else if (dist(p, base) > 70) off++; // white (or red) X strokes
        }
        if (dark > tot * 0.04) return CAT;
        if (off > tot * 0.12) return X;
        return EMPTY;
    }

    // ---------- solving ----------

    /** Returns col index per row, honouring cats already on the board; null if impossible. */
    public int[] solve() {
        int[] fixedCol = new int[n];
        java.util.Arrays.fill(fixedCol, -1);
        for (int i = 0; i < n * n; i++) if (state[i] == CAT) fixedCol[i / n] = i % n;
        int[] sol = new int[n];
        return place(0, 0, 0, -9, fixedCol, sol) ? sol : null;
    }

    private boolean place(int row, int usedCols, long usedRegs, int prevCol, int[] fixedCol, int[] sol) {
        if (row == n) return true;
        for (int c = 0; c < n; c++) {
            if (fixedCol[row] >= 0 && c != fixedCol[row]) continue;
            int reg = region[row * n + c];
            if ((usedCols >> c & 1) != 0 || (usedRegs >> reg & 1) != 0 || Math.abs(c - prevCol) <= 1) continue;
            sol[row] = c;
            if (place(row + 1, usedCols | 1 << c, usedRegs | 1L << reg, c, fixedCol, sol)) return true;
        }
        return false;
    }

    /** Cells that should be X once a cat sits at (r,c): row, column, 8 neighbours, rest of its colour. */
    public List<Integer> xTargets(int r, int c) {
        List<Integer> out = new ArrayList<>();
        int reg = region[r * n + c];
        for (int i = 0; i < n * n; i++) {
            int rr = i / n, cc = i % n;
            if (i == r * n + c || state[i] != EMPTY) continue;
            if (rr == r || cc == c || region[i] == reg || (Math.abs(rr - r) <= 1 && Math.abs(cc - c) <= 1)) out.add(i);
        }
        return out;
    }

    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int i = r * n + c;
                sb.append((char) ('A' + region[i])).append(state[i] == CAT ? '@' : state[i] == X ? '*' : ' ').append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
