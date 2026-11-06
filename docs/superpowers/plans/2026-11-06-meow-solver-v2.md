# Meow Solver v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild Meow Solver as a Kotlin + Compose accessibility-overlay app (Scan / Hint / Cat / X / Answer, minimize-to-bubble, close) with JVM tests, GUI snapshots, and alignment renders.

**Architecture:** Pure-Kotlin core (`BoardReader` → `Solver` → `MovePlanner`) tested on 41 real screenshots. One `AccessibilityService` hosts two Compose overlay windows (panel/bubble + full-screen marks), takes screenshots and dispatches gestures. Compose UI is stateless (state + callbacks) so Paparazzi can snapshot it.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Gradle 8.14.3 (`/opt/gradle/bin/gradle`), Compose BOM 2024.10.01, activity-compose 1.9.3, Paparazzi 1.3.5, JUnit 4.13.2.

## Global Constraints

- Package `com.ilan.meowsolver`; single Gradle module at repo root `/home/claude/meowsolver`.
- `compileSdk 35`, `minSdk 30`, `targetSdk 35`; Java + Kotlin JVM target 17.
- Repos (in this order): `google()`, `maven("https://maven-central.storage-download.googleapis.com/maven2/")`, `mavenCentral()`, `gradlePluginPortal()` (Maven Central rate-limits this sandbox).
- Build env: `export ANDROID_HOME=/root/android-sdk` before every gradle command; always `--no-daemon -q`.
- Game input: empty + single tap = X; X + single tap = empty; empty + fast double-tap = cat; X → cat = single tap, 350 ms pause, double-tap.
- Double-tap = two 40 ms strokes, gap configurable 60–200 ms, default 90 ms.
- Colors: cream `#FBF5EF`, clay `#D97757`, card `#2A2420` at 94% alpha (`0xF02A2420`), ink `#2A2420`, ink-muted `#7A6A60`, on-card white / 70% white.
- Icons are original vector paths drawn in this repo. No game art.
- Exact user-facing strings: `"Ready"`, `"Working…"`, `"{n}×{n} · {cats} cats · solvable"`, `"{n}×{n} · {cats} cats · no solution"`, `"Next cat: row R, col C"`, `"Cat placed: row R, col C"`, `"Marked N cells"`, `"Nothing to mark"`, `"Showing N cats"`, `"Solved"`, `"Stopped"`, `"Open a Meowdoku puzzle first"`, `"Couldn't read the screen. Try again"`, `"No solution — a placed cat is wrong"`.
- Timings: capture delay 150 ms, screenshot retry 400 ms (once), grid flash 2000 ms, hint 5000 ms.
- Every commit message ends with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01WBz1ktuMVmmVWzGdpUm9m1
  ```
  Commit with `git -c user.name="Ilan Kogan" -c user.email="ilanamir100.100@gmail.com" commit`.

## File map

| File | Responsibility |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` | Build |
| `src/main/AndroidManifest.xml`, `res/xml/accessibility.xml`, `res/values/strings.xml`, `res/drawable/ic_launcher_fg.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml` | App wiring + icon |
| `core/Board.kt` | Board model (`Cell`, bounds, regions, state) |
| `core/BoardReader.kt` | Pixels → `Board` |
| `core/Solver.kt` | `Board` → solution |
| `core/MovePlanner.kt` | Next cat, X targets, `Tap` plans, stroke timings |
| `ui/Theme.kt`, `ui/MeowIcons.kt` | Colors + original vector icons |
| `ui/PanelCard.kt`, `ui/Bubble.kt`, `ui/MarksLayer.kt`, `ui/SetupScreen.kt` | Stateless Compose UI |
| `service/OverlayOwner.kt`, `service/TapRunner.kt`, `service/OverlayService.kt` | Android glue |
| `MainActivity.kt` | Setup screen host |
| `src/test/.../Fixtures.kt`, `TestBoards.kt` | Test helpers |
| `src/test/.../BoardReaderTest.kt`, `SolverTest.kt`, `MovePlannerTest.kt`, `AlignmentRenderTest.kt`, `UiSnapshotTest.kt` | Tests |

All Kotlin sources live under `src/main/java/com/ilan/meowsolver/` and tests under `src/test/java/com/ilan/meowsolver/`. Fixtures already committed: `src/test/resources/screens/*.jpg` (41 files) and `src/test/resources/expected.csv` (`name,n,cats,solution` where solution is space-separated column per row).

---

### Task 1: Kotlin + Compose + Paparazzi scaffold

**Files:**
- Delete: `build.gradle`, `settings.gradle`, `src/main/java/com/ilan/meowsolver/*.java`, `src/main/res/values/strings.xml`, `src/main/res/xml/accessibility.xml`
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `src/main/AndroidManifest.xml` (replace), `src/main/java/com/ilan/meowsolver/MainActivity.kt` (placeholder)

**Interfaces:** Produces a buildable module that later tasks add files to.

- [ ] **Step 1: Remove v1 sources**

```bash
cd /home/claude/meowsolver && git rm -q build.gradle settings.gradle src/main/java/com/ilan/meowsolver/*.java src/main/res/values/strings.xml src/main/res/xml/accessibility.xml
```

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "MeowSolver"
```

- [ ] **Step 3: Write `build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("app.cash.paparazzi") version "1.3.5"
}

android {
    namespace = "com.ilan.meowsolver"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ilan.meowsolver"
        minSdk = 30 // takeScreenshot() needs Android 11+
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx3g
```

- [ ] **Step 5: Write placeholder `AndroidManifest.xml` and `MainActivity.kt`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Meow Solver"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

```kotlin
package com.ilan.meowsolver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Meow Solver") }
    }
}
```

- [ ] **Step 6: Build**

Run: `cd /home/claude/meowsolver && export ANDROID_HOME=/root/android-sdk && gradle assembleDebug testDebugUnitTest --no-daemon -q`
Expected: exit 0, `build/outputs/apk/debug/MeowSolver-debug.apk` exists.
Known risk: if Paparazzi rejects compileSdk 35, install `platforms;android-34` with sdkmanager and set `compileSdk = 34`.

- [ ] **Step 7: Commit** — `git add -A && git commit -m "build: Kotlin + Compose + Paparazzi scaffold, drop v1 Java"` (+ trailer)

---

### Task 2: Board model + BoardReader (TDD on real screenshots)

**Files:**
- Create: `src/main/java/com/ilan/meowsolver/core/Board.kt`, `core/BoardReader.kt`, `core/Solver.kt` (stub only so tests compile — real in Task 3)
- Test: `src/test/java/com/ilan/meowsolver/Fixtures.kt`, `BoardReaderTest.kt`

**Interfaces:**
- Produces: `enum class Cell { EMPTY, X, CAT }`; `class Board(n: Int, colL/colR/rowT/rowB: IntArray, region: IntArray, state: Array<Cell>)` with `idx(r,c)`, `cx(c)`, `cy(r)`, `markRadius(cell): Float`, `cats: List<Int>`; `object BoardReader { fun read(px: IntArray, w: Int, h: Int): Board }` throwing `BoardNotFound`.
- Test helper: `object Fixtures { fun image(name): BufferedImage; fun board(name): Board; fun expected(): List<Expected> }`, `data class Expected(name, n, cats, solution: IntArray)`.

- [ ] **Step 1: Write test helpers + failing test**

`src/test/java/com/ilan/meowsolver/Fixtures.kt`:
```kotlin
package com.ilan.meowsolver

import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.BoardReader
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

data class Expected(val name: String, val n: Int, val cats: Int, val solution: IntArray)

object Fixtures {
    private val cl = Fixtures::class.java.classLoader!!
    fun image(name: String): BufferedImage = ImageIO.read(cl.getResource("screens/$name")!!)
    fun board(name: String): Board {
        val im = image(name)
        val w = im.width; val h = im.height
        return BoardReader.read(im.getRGB(0, 0, w, h, null, 0, w), w, h)
    }
    fun expected(): List<Expected> = cl.getResource("expected.csv")!!.readText().lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val p = line.split(',')
            Expected(p[0], p[1].toInt(), p[2].toInt(), p[3].split(' ').map(String::toInt).toIntArray())
        }
}
```

`src/test/java/com/ilan/meowsolver/BoardReaderTest.kt`:
```kotlin
package com.ilan.meowsolver

import com.ilan.meowsolver.core.BoardNotFound
import com.ilan.meowsolver.core.BoardReader
import com.ilan.meowsolver.core.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardReaderTest {
    @Test fun readsAndSolvesAll41Screenshots() {
        val all = Fixtures.expected()
        assertEquals(41, all.size)
        val failures = all.mapNotNull { e ->
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)
            when {
                b.n != e.n -> "${e.name}: n=${b.n} want ${e.n}"
                b.cats.size != e.cats -> "${e.name}: cats=${b.cats.size} want ${e.cats}"
                sol == null -> "${e.name}: no solution"
                !sol.contentEquals(e.solution) -> "${e.name}: ${sol.toList()} want ${e.solution.toList()}"
                else -> null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun handSolvedLevelsMatch() {
        // Solved by hand in chat: level 83, 81, 32
        assertEquals(listOf(0, 5, 2, 6, 3, 9, 7, 4, 8, 1), Solver.solve(Fixtures.board("hi_211922.jpg"))!!.toList())
        assertEquals(listOf(5, 2, 6, 4, 7, 1, 3, 0), Solver.solve(Fixtures.board("hi_211447.jpg"))!!.toList())
        assertEquals(listOf(1, 7, 4, 0, 9, 3, 6, 8, 2, 5), Solver.solve(Fixtures.board("lo_33560.jpg"))!!.toList())
    }

    @Test(expected = BoardNotFound::class) fun blankScreenThrows() {
        BoardReader.read(IntArray(400 * 800) { 0xFFF7F1EE.toInt() }, 400, 800)
    }
}
```

`core/Solver.kt` stub so it compiles:
```kotlin
package com.ilan.meowsolver.core

object Solver {
    fun solve(b: Board): IntArray? = TODO("Task 3")
}
```

- [ ] **Step 2: Run, expect compile failure** (`Board`/`BoardReader` missing)

Run: `gradle testDebugUnitTest --no-daemon -q --tests '*BoardReaderTest*'`
Expected: FAIL, unresolved reference `BoardReader`.

- [ ] **Step 3: Write `core/Board.kt`**

```kotlin
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
```

- [ ] **Step 4: Write `core/BoardReader.kt`** (port of the v1 algorithm verified on all 41 screenshots)

```kotlin
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
        if (bestLen < 4) throw BoardNotFound("Board not found")
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
```

- [ ] **Step 5: Run, expect `handSolved`/`readsAndSolves` to fail on `TODO` (Solver stub) and `blankScreenThrows` to pass**

Run: `gradle testDebugUnitTest --no-daemon -q --tests '*BoardReaderTest*'`
Expected: `blankScreenThrows` PASS; other two FAIL with `NotImplementedError: Task 3`.

- [ ] **Step 6: Commit** — `git add -A && git commit -m "feat(core): Board model and BoardReader ported from v1"` (+ trailer)

---

### Task 3: Solver

**Files:**
- Modify: `src/main/java/com/ilan/meowsolver/core/Solver.kt`
- Test: `src/test/java/com/ilan/meowsolver/TestBoards.kt`, `SolverTest.kt`

**Interfaces:**
- Consumes: `Board`, `Cell`.
- Produces: `Solver.solve(b: Board): IntArray?` (column per row; null = impossible). Test helper `boardOf(vararg rows: String): Board` (tokens like `A`, `B*`, `C@`; cells are 100 px, bounds `c*100+5 .. c*100+95`).

- [ ] **Step 1: Write helper + failing tests**

`TestBoards.kt`:
```kotlin
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
```

`SolverTest.kt`:
```kotlin
package com.ilan.meowsolver

import com.ilan.meowsolver.core.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SolverTest {
    private val want = listOf(5, 2, 6, 4, 7, 1, 3, 0)

    @Test fun solvesFreshBoard() = assertEquals(want, Solver.solve(boardOf(*LEVEL81))!!.toList())

    @Test fun honoursCorrectPlacedCat() {
        val rows = LEVEL81.copyOf(); rows[2] = "A B B D B E F@ G"
        assertEquals(want, Solver.solve(boardOf(*rows))!!.toList())
    }

    @Test fun wrongPlacedCatGivesNull() {
        val rows = LEVEL81.copyOf(); rows[0] = "A@ A B B B C C C"
        assertNull(Solver.solve(boardOf(*rows)))
    }

    @Test fun twoCatsInOneRowGivesNull() {
        val rows = LEVEL81.copyOf(); rows[0] = "A A B B B C@ C C@"
        assertNull(Solver.solve(boardOf(*rows)))
    }

    @Test fun solutionObeysAllRules() {
        val b = boardOf(*LEVEL81); val s = Solver.solve(b)!!
        assertEquals(8, s.toSet().size)                                        // one per column
        assertEquals(8, s.indices.map { b.region[b.idx(it, s[it])] }.toSet().size) // one per colour
        for (r in 1 until 8) assertTrue("touching at row $r", abs(s[r] - s[r - 1]) > 1)
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (`NotImplementedError`)

Run: `gradle testDebugUnitTest --no-daemon -q --tests '*SolverTest*'`

- [ ] **Step 3: Implement `core/Solver.kt`**

```kotlin
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
```

- [ ] **Step 4: Run all core tests, expect PASS**

Run: `gradle testDebugUnitTest --no-daemon -q --tests '*SolverTest*' --tests '*BoardReaderTest*'`
Expected: all PASS (41 screenshots + 3 hand-solved levels + 5 solver tests).

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(core): backtracking solver honouring placed cats"` (+ trailer)

---

### Task 4: MovePlanner

**Files:**
- Create: `src/main/java/com/ilan/meowsolver/core/MovePlanner.kt`
- Test: `src/test/java/com/ilan/meowsolver/MovePlannerTest.kt`

**Interfaces:**
- Consumes: `Board`, `Cell`, `TestBoards.boardOf`, `LEVEL81`.
- Produces: `data class Tap(x: Int, y: Int, double: Boolean, pauseAfterMs: Long)`; `object MovePlanner` with `GAP_MS = 90L`, `CLEAR_PAUSE_MS = 350L`, `STROKE_MS = 40L`, `nextCat(b, sol): Int?`, `remaining(b, sol): List<Int>`, `catTaps(b, cell): List<Tap>`, `xTargets(b): List<Int>`, `xTaps(b): List<Tap>`, `strokes(tap, doubleGapMs): List<LongArray>` (each `[startMs, durationMs]`).

- [ ] **Step 1: Write failing tests**

```kotlin
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
```

- [ ] **Step 2: Run, expect FAIL** (unresolved `MovePlanner`)

Run: `gradle testDebugUnitTest --no-daemon -q --tests '*MovePlannerTest*'`

- [ ] **Step 3: Implement `core/MovePlanner.kt`**

```kotlin
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
```

- [ ] **Step 4: Run all tests, expect PASS**

Run: `gradle testDebugUnitTest --no-daemon -q`

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(core): move planner for cats, X marks and tap timings"` (+ trailer)

---

### Task 5: Theme, original icons, PanelCard, Bubble, SetupScreen + Paparazzi snapshots

**Files:**
- Create: `ui/Theme.kt`, `ui/MeowIcons.kt`, `ui/PanelCard.kt`, `ui/Bubble.kt`, `ui/SetupScreen.kt`
- Test: `src/test/java/com/ilan/meowsolver/UiSnapshotTest.kt`

**Interfaces:**
- Produces: `object Meow { Cream, Clay, Card, Ink, InkMuted, OnCard, OnCardMuted }`; `object MeowIcons { Cat, X, Scan, Bulb, Eye, Minus, Power, Check }` (all `ImageVector`, 24×24, stroke-only);
  `data class PanelState(status: String, busy: Boolean, answerOn: Boolean)`;
  `@Composable fun PanelCard(state, onScan, onHint, onCat, onX, onAnswer, onMinimize, onClose, dragModifier: Modifier = Modifier)`;
  `@Composable fun Bubble(onClick: () -> Unit, modifier: Modifier = Modifier)`;
  `data class SetupState(serviceOn: Boolean, doubleTapMs: Int)`;
  `@Composable fun SetupScreen(state, onStart, onAppInfo, onAccessibility, onDoubleTapMs: (Int) -> Unit)`.

- [ ] **Step 1: Write the snapshot test (fails to compile)**

```kotlin
package com.ilan.meowsolver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.ilan.meowsolver.ui.Bubble
import com.ilan.meowsolver.ui.PanelCard
import com.ilan.meowsolver.ui.PanelState
import com.ilan.meowsolver.ui.SetupScreen
import com.ilan.meowsolver.ui.SetupState
import org.junit.Rule
import org.junit.Test

class UiSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Composable private fun OnGame(content: @Composable () -> Unit) =
        Box(Modifier.fillMaxSize().background(Color(0xFFF7F1EE)).padding(24.dp)) { content() }

    @Composable private fun Panel(s: PanelState) = PanelCard(s, {}, {}, {}, {}, {}, {}, {})

    @Test fun panelIdle() = paparazzi.snapshot { OnGame { Panel(PanelState("10×10 · 3 cats · solvable", busy = false, answerOn = false)) } }
    @Test fun panelBusy() = paparazzi.snapshot { OnGame { Panel(PanelState("Working…", busy = true, answerOn = false)) } }
    @Test fun panelAnswerOn() = paparazzi.snapshot { OnGame { Panel(PanelState("Showing 7 cats", busy = false, answerOn = true)) } }
    @Test fun bubble() = paparazzi.snapshot { OnGame { Bubble(onClick = {}) } }
    @Test fun setupOff() = paparazzi.snapshot { SetupScreen(SetupState(serviceOn = false, doubleTapMs = 90), {}, {}, {}, {}) }
    @Test fun setupOn() = paparazzi.snapshot { SetupScreen(SetupState(serviceOn = true, doubleTapMs = 120), {}, {}, {}, {}) }
}
```

- [ ] **Step 2: Run, expect compile FAIL** — `gradle testDebugUnitTest --no-daemon -q --tests '*UiSnapshotTest*'`

- [ ] **Step 3: Write `ui/Theme.kt`**

```kotlin
package com.ilan.meowsolver.ui

import androidx.compose.ui.graphics.Color

object Meow {
    val Cream = Color(0xFFFBF5EF)
    val Clay = Color(0xFFD97757)
    val Card = Color(0xF02A2420)
    val Ink = Color(0xFF2A2420)
    val InkMuted = Color(0xFF7A6A60)
    val OnCard = Color.White
    val OnCardMuted = Color(0xB3FFFFFF)
}
```

- [ ] **Step 4: Write `ui/MeowIcons.kt`** (original stroke icons, 24 viewport)

```kotlin
package com.ilan.meowsolver.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object MeowIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
            )
        }.build()

    val Cat = icon("cat") {
        moveTo(4f, 11f); lineTo(4f, 4f); lineTo(8.5f, 7.5f); lineTo(15.5f, 7.5f); lineTo(20f, 4f); lineTo(20f, 11f)
        curveTo(20f, 16.5f, 16.5f, 20f, 12f, 20f); curveTo(7.5f, 20f, 4f, 16.5f, 4f, 11f); close()
        moveTo(9f, 12.5f); lineTo(9f, 13f)
        moveTo(15f, 12.5f); lineTo(15f, 13f)
        moveTo(11f, 15.5f); lineTo(12f, 16.5f); lineTo(13f, 15.5f)
    }
    val X = icon("x") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Scan = icon("scan") {
        moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
        moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
        moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
        moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
        moveTo(7f, 12f); lineTo(17f, 12f)
    }
    val Bulb = icon("bulb") {
        moveTo(9f, 15f); curveTo(7f, 13.5f, 6f, 11.8f, 6f, 9.5f)
        arcTo(6f, 6f, 0f, false, true, 18f, 9.5f)
        curveTo(18f, 11.8f, 17f, 13.5f, 15f, 15f); close()
        moveTo(9f, 18f); lineTo(15f, 18f)
        moveTo(10f, 21f); lineTo(14f, 21f)
    }
    val Eye = icon("eye") {
        moveTo(2f, 12f); curveTo(5f, 6.5f, 19f, 6.5f, 22f, 12f); curveTo(19f, 17.5f, 5f, 17.5f, 2f, 12f); close()
        moveTo(12f, 9.5f); arcTo(2.5f, 2.5f, 0f, true, true, 12f, 14.5f); arcTo(2.5f, 2.5f, 0f, true, true, 12f, 9.5f)
    }
    val Minus = icon("minus") { moveTo(6f, 12f); lineTo(18f, 12f) }
    val Power = icon("power") {
        moveTo(12f, 3f); lineTo(12f, 11f)
        moveTo(7f, 6.5f); arcTo(7f, 7f, 0f, true, false, 17f, 6.5f)
    }
    val Check = icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
}
```

- [ ] **Step 5: Write `ui/PanelCard.kt`**

```kotlin
package com.ilan.meowsolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PanelState(val status: String, val busy: Boolean, val answerOn: Boolean)

@Composable
fun PanelCard(
    state: PanelState,
    onScan: () -> Unit, onHint: () -> Unit, onCat: () -> Unit, onX: () -> Unit, onAnswer: () -> Unit,
    onMinimize: () -> Unit, onClose: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    Column(
        Modifier.width(300.dp).background(Meow.Card, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(dragModifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.status, color = Meow.OnCardMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            HeaderIcon(MeowIcons.Minus, "Minimize", onMinimize)
            HeaderIcon(MeowIcons.Power, "Close", onClose)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val on = !state.busy
            ActionButton(MeowIcons.Scan, "Scan", on, onClick = onScan)
            ActionButton(MeowIcons.Bulb, "Hint", on, onClick = onHint)
            ActionButton(MeowIcons.Cat, "Cat", on, filled = true, onClick = onCat)
            ActionButton(MeowIcons.X, "X", on, onClick = onX)
            ActionButton(MeowIcons.Eye, "Answer", on, toggled = state.answerOn, onClick = onAnswer)
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector, label: String, enabled: Boolean,
    filled: Boolean = false, toggled: Boolean = false, onClick: () -> Unit,
) {
    val bg = when {
        filled -> Meow.Clay
        toggled -> Meow.Clay.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    Column(
        Modifier.width(54.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Meow.OnCard, modifier = Modifier.size(22.dp))
        Text(label, color = Meow.OnCard, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = Meow.OnCardMuted, modifier = Modifier.size(18.dp))
    }
}
```

- [ ] **Step 6: Write `ui/Bubble.kt`**

```kotlin
package com.ilan.meowsolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun Bubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(52.dp).clip(CircleShape).background(Meow.Clay).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(MeowIcons.Cat, contentDescription = "Open Meow Solver", tint = Meow.OnCard, modifier = Modifier.size(28.dp))
    }
}
```

- [ ] **Step 7: Write `ui/SetupScreen.kt`**

```kotlin
package com.ilan.meowsolver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class SetupState(val serviceOn: Boolean, val doubleTapMs: Int)

@Composable
fun SetupScreen(
    state: SetupState,
    onStart: () -> Unit, onAppInfo: () -> Unit, onAccessibility: () -> Unit, onDoubleTapMs: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Meow.Cream).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Meow.Clay), contentAlignment = Alignment.Center) {
            Icon(MeowIcons.Cat, contentDescription = null, tint = Meow.OnCard, modifier = Modifier.size(36.dp))
        }
        Text("Meow Solver", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = Meow.Ink, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Scan, hint, and place cats in Meowdoku from a floating panel.",
            fontSize = 15.sp, color = Meow.InkMuted, modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onStart, shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Meow.Clay, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp),
        ) { Text(if (state.serviceOn) "Start overlay" else "Turn on overlay", fontSize = 17.sp) }

        SectionTitle("Setup")
        Card {
            Step(1, "Allow restricted settings", "App info → ⋮ → Allow restricted settings", state.serviceOn, "App info", onAppInfo)
            HorizontalDivider(color = Meow.Ink.copy(alpha = 0.08f))
            Step(2, "Turn on accessibility", "Installed apps → Meow Solver overlay → On", state.serviceOn, "Open", onAccessibility)
        }

        SectionTitle("How it works")
        Card {
            HowRow(MeowIcons.Scan, "Scan", "Reads the board and checks it's solvable")
            HowRow(MeowIcons.Bulb, "Hint", "Shows the next cat for 5 seconds")
            HowRow(MeowIcons.Cat, "Cat", "Places the next cat for you")
            HowRow(MeowIcons.X, "X", "Marks every cell a cat rules out")
            HowRow(MeowIcons.Eye, "Answer", "Toggles rings on all remaining cats")
        }

        SectionTitle("Tuning")
        Card {
            Text("Double-tap speed · ${state.doubleTapMs} ms", color = Meow.Ink, fontSize = 15.sp)
            Slider(
                value = state.doubleTapMs.toFloat(), onValueChange = { onDoubleTapMs((it / 10).roundToInt() * 10) },
                valueRange = 60f..200f, steps = 13,
                colors = SliderDefaults.colors(thumbColor = Meow.Clay, activeTrackColor = Meow.Clay),
            )
            Text("Raise it if Cat places an X instead.", color = Meow.InkMuted, fontSize = 13.sp)
        }
    }
}

@Composable private fun SectionTitle(t: String) =
    Text(t, color = Meow.InkMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))

@Composable private fun Card(content: @Composable () -> Unit) =
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }

@Composable
private fun Step(num: Int, title: String, detail: String, done: Boolean, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (done) Meow.Clay else Meow.Ink.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(MeowIcons.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
            else Text("$num", color = Meow.Ink, fontSize = 13.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Meow.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Meow.InkMuted, fontSize = 13.sp)
        }
        OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(action, color = Meow.Ink, fontSize = 13.sp) }
    }
}

@Composable
private fun HowRow(icon: ImageVector, name: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Meow.Clay, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Column {
            Text(name, color = Meow.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text, color = Meow.InkMuted, fontSize = 13.sp)
        }
    }
}
```

- [ ] **Step 8: Record snapshots, expect PASS**

Run: `gradle recordPaparazziDebug --no-daemon -q && ls src/test/snapshots/images/`
Expected: 6 PNGs (`*_panelIdle.png`, `*_panelBusy.png`, `*_panelAnswerOn.png`, `*_bubble.png`, `*_setupOff.png`, `*_setupOn.png`).

- [ ] **Step 9: Visually review every PNG** with the Read tool. Check: icons recognisable (power arc opens at top, eye and bulb closed), text not clipped, Cat button filled clay, Answer tinted when on, busy row dimmed. Fix and re-record until all six look right.

- [ ] **Step 10: Commit** — `git add -A && git commit -m "feat(ui): theme, original icons, panel card, bubble, setup screen + snapshots"` (+ trailer)

---

### Task 6: MarksLayer + alignment render on real screenshots

**Files:**
- Create: `ui/MarksLayer.kt`
- Test: `src/test/java/com/ilan/meowsolver/AlignmentRenderTest.kt`

**Interfaces:**
- Consumes: `Board.markRadius`, `Board.cx/cy`, `MovePlanner.remaining/nextCat`, `Solver.solve`, `MeowIcons.Cat`, `Meow.Clay`.
- Produces: `data class Marks(board: Board? = null, rings: List<Int> = emptyList(), hint: Int? = null, flash: Boolean = false)`; `@Composable fun MarksLayer(marks: Marks)`.

- [ ] **Step 1: Write failing alignment test**

```kotlin
package com.ilan.meowsolver

import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Solver
import com.ilan.meowsolver.ui.Marks
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.io.File
import javax.imageio.ImageIO

/** Draws what MarksLayer draws (same geometry) onto real screenshots; PNGs go to build/alignment for review. */
class AlignmentRenderTest {
    @Test fun marksLandOnCells() {
        val out = File("build/alignment").apply { mkdirs() }
        for (name in listOf("hi_211922.jpg", "hi_211447.jpg", "hi_211320.jpg", "lo_33560.jpg")) {
            val img = Fixtures.image(name)
            val b = Fixtures.board(name)
            val sol = Solver.solve(b)!!
            val marks = Marks(b, MovePlanner.remaining(b, sol), MovePlanner.nextCat(b, sol), flash = true)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE; g.stroke = BasicStroke(4f)
            for (r in 0 until b.n) for (c in 0 until b.n) g.drawRect(b.colL[c], b.rowT[r], b.colR[c] - b.colL[c], b.rowB[r] - b.rowT[r])
            for (i in marks.rings) {
                val rad = b.markRadius(i); val x = b.cx(i % b.n); val y = b.cy(i / b.n)
                assertTrue("$name ring $i radius", rad * 2 < b.colR[i % b.n] - b.colL[i % b.n])
                assertTrue("$name ring $i inside image", x - rad > 0 && x + rad < img.width && y - rad > 0 && y + rad < img.height)
                g.color = Color(0xD9, 0x77, 0x57); g.stroke = BasicStroke(rad * 0.2f)
                g.drawOval((x - rad).toInt(), (y - rad).toInt(), (2 * rad).toInt(), (2 * rad).toInt())
            }
            marks.hint?.let { i ->
                val rad = b.markRadius(i); val x = b.cx(i % b.n); val y = b.cy(i / b.n)
                g.color = Color(0xD9, 0x77, 0x57, 235)
                g.fillOval((x - rad).toInt(), (y - rad).toInt(), (2 * rad).toInt(), (2 * rad).toInt())
            }
            g.dispose()
            ImageIO.write(img, "png", File(out, name.replace(".jpg", ".png")))
        }
    }
}
```

- [ ] **Step 2: Run, expect compile FAIL** (`Marks` missing) — `gradle testDebugUnitTest --no-daemon -q --tests '*AlignmentRenderTest*'`

- [ ] **Step 3: Write `ui/MarksLayer.kt`**

```kotlin
package com.ilan.meowsolver.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import com.ilan.meowsolver.core.Board

/** Everything drawn over the game. Board coordinates are screen pixels. */
data class Marks(
    val board: Board? = null,
    val rings: List<Int> = emptyList(),
    val hint: Int? = null,
    val flash: Boolean = false,
)

@Composable
fun MarksLayer(marks: Marks) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(
        0.85f, 1.1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "pulse",
    )
    val cat = rememberVectorPainter(MeowIcons.Cat)
    val b = marks.board ?: return
    Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        fun center(i: Int) = Offset(b.cx(i % b.n) - origin.x, b.cy(i / b.n) - origin.y)
        if (marks.flash) for (r in 0 until b.n) for (c in 0 until b.n) {
            drawRect(
                Color.White, Offset(b.colL[c] - origin.x, b.rowT[r] - origin.y),
                Size((b.colR[c] - b.colL[c]).toFloat(), (b.rowB[r] - b.rowT[r]).toFloat()), style = Stroke(4f),
            )
        }
        for (i in marks.rings) {
            val rad = b.markRadius(i)
            drawCircle(Color.White, rad, center(i), style = Stroke(rad * 0.32f))
            drawCircle(Meow.Clay, rad, center(i), style = Stroke(rad * 0.2f))
        }
        marks.hint?.let { i ->
            val rad = b.markRadius(i) * pulse
            drawCircle(Meow.Clay.copy(alpha = 0.92f), rad, center(i))
            val s = rad * 1.2f
            translate(center(i).x - s / 2, center(i).y - s / 2) {
                with(cat) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
            }
        }
    }
}
```

- [ ] **Step 4: Run, expect PASS; then view the 4 PNGs in `build/alignment/` with Read** — rings must sit centred on the hand-solved cells (level 83: row 2 col 6, etc.), grid outlines on cell edges.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(ui): marks layer with alignment render test on real screenshots"` (+ trailer)

---

### Task 7: Accessibility service, tap runner, setup activity, launcher icon

**Files:**
- Create: `service/OverlayOwner.kt`, `service/TapRunner.kt`, `service/OverlayService.kt`, `res/xml/accessibility.xml`, `res/values/strings.xml`, `res/drawable/ic_launcher_fg.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `MainActivity.kt`, `AndroidManifest.xml`

**Interfaces:**
- Consumes: everything above.
- Produces: `OverlayService.instance: OverlayService?`, `OverlayService.showOverlay()`, prefs `"meow"` keys `answerOn`, `doubleTapMs`, `panelX`, `panelY`.

- [ ] **Step 1: `service/OverlayOwner.kt`** (Compose in a service needs a lifecycle + saved-state owner)

```kotlin
package com.ilan.meowsolver.service

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun start() { saved.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
    fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
    fun attach(v: View) { v.setViewTreeLifecycleOwner(this); v.setViewTreeSavedStateRegistryOwner(this) }
}
```

- [ ] **Step 2: `service/TapRunner.kt`**

```kotlin
package com.ilan.meowsolver.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Tap

/** Plays taps one gesture at a time; stops on the first cancelled gesture. */
class TapRunner(private val svc: AccessibilityService, private val handler: Handler) {
    fun run(taps: List<Tap>, doubleGapMs: Long, onDone: (completed: Boolean) -> Unit) = step(taps, 0, doubleGapMs, onDone)

    private fun step(taps: List<Tap>, i: Int, gap: Long, onDone: (Boolean) -> Unit) {
        if (i == taps.size) { onDone(true); return }
        val t = taps[i]
        val path = Path().apply { moveTo(t.x.toFloat(), t.y.toFloat()) }
        val g = GestureDescription.Builder().apply {
            for (s in MovePlanner.strokes(t, gap)) addStroke(GestureDescription.StrokeDescription(path, s[0], s[1]))
        }.build()
        val ok = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription) { handler.postDelayed({ step(taps, i + 1, gap, onDone) }, t.pauseAfterMs) }
            override fun onCancelled(d: GestureDescription) = onDone(false)
        }, null)
        if (!ok) onDone(false)
    }
}
```

- [ ] **Step 3: `service/OverlayService.kt`**

```kotlin
package com.ilan.meowsolver.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.BoardNotFound
import com.ilan.meowsolver.core.BoardReader
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Solver
import com.ilan.meowsolver.core.Tap
import com.ilan.meowsolver.ui.Bubble
import com.ilan.meowsolver.ui.Marks
import com.ilan.meowsolver.ui.MarksLayer
import com.ilan.meowsolver.ui.PanelCard
import com.ilan.meowsolver.ui.PanelState
import kotlin.math.roundToInt

class OverlayService : AccessibilityService() {
    companion object {
        var instance: OverlayService? = null
            private set
        const val PREFS = "meow"
        const val K_ANSWER = "answerOn"
        const val K_DOUBLE_MS = "doubleTapMs"
        private const val K_X = "panelX"
        private const val K_Y = "panelY"
        private const val CAPTURE_DELAY_MS = 150L
        private const val RETRY_MS = 400L
        private const val FLASH_MS = 2000L
        private const val HINT_MS = 5000L
        private const val NO_SOLUTION = "No solution — a placed cat is wrong"
    }

    private enum class Mode { CARD, BUBBLE }

    private val handler = Handler(Looper.getMainLooper())
    private val owner = OverlayOwner()
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var taps: TapRunner
    private lateinit var panelLp: LP
    private var panelView: ComposeView? = null
    private var marksView: ComposeView? = null

    private var mode by mutableStateOf(Mode.CARD)
    private var panel by mutableStateOf(PanelState("Ready", busy = false, answerOn = false))
    private var marks by mutableStateOf(Marks())
    private var hintToken = 0
    private var flashToken = 0

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        taps = TapRunner(this, handler)
        panel = panel.copy(answerOn = prefs.getBoolean(K_ANSWER, false))
        owner.start()
        instance = this
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        removeViews()
        owner.stop()
        return super.onUnbind(intent)
    }

    // ---------- windows ----------

    fun showOverlay() {
        mode = Mode.CARD
        if (panelView != null) return
        marksView = composeView { MarksLayer(marks) }.also {
            wm.addView(it, LP(LP.MATCH_PARENT, LP.MATCH_PARENT, LP.TYPE_ACCESSIBILITY_OVERLAY,
                LP.FLAG_NOT_TOUCHABLE or LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT))
        }
        val d = resources.displayMetrics.density
        panelLp = LP(LP.WRAP_CONTENT, LP.WRAP_CONTENT, LP.TYPE_ACCESSIBILITY_OVERLAY, LP.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(K_X, (12 * d).toInt())
            y = prefs.getInt(K_Y, (80 * d).toInt())
        }
        panelView = composeView { PanelHost() }.also { wm.addView(it, panelLp) }
    }

    private fun close() {
        removeViews()
        marks = Marks()
        panel = panel.copy(status = "Ready", busy = false)
    }

    private fun removeViews() {
        panelView?.let { wm.removeView(it) }
        marksView?.let { wm.removeView(it) }
        panelView = null; marksView = null
    }

    private fun composeView(content: @Composable () -> Unit) =
        ComposeView(this).apply { owner.attach(this); setContent(content) }

    private fun setOverlayVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.INVISIBLE
        panelView?.visibility = vis; marksView?.visibility = vis
    }

    /** Injected taps must not land on our own panel. */
    private fun setPanelTouchable(t: Boolean) {
        val view = panelView ?: return
        panelLp.flags = if (t) panelLp.flags and LP.FLAG_NOT_TOUCHABLE.inv() else panelLp.flags or LP.FLAG_NOT_TOUCHABLE
        wm.updateViewLayout(view, panelLp)
    }

    private fun movePanel(dx: Float, dy: Float) {
        panelLp.x += dx.roundToInt(); panelLp.y += dy.roundToInt()
        panelView?.let { wm.updateViewLayout(it, panelLp) }
    }

    private fun endDrag() {
        val view = panelView ?: return
        if (mode == Mode.BUBBLE) { // snap to the nearest side edge
            val sw = resources.displayMetrics.widthPixels
            panelLp.x = if (panelLp.x + view.width / 2 < sw / 2) 0 else sw - view.width
            wm.updateViewLayout(view, panelLp)
        }
        prefs.edit().putInt(K_X, panelLp.x).putInt(K_Y, panelLp.y).apply()
    }

    @Composable
    private fun PanelHost() {
        val drag = Modifier.pointerInput(Unit) {
            detectDragGestures(onDragEnd = ::endDrag) { change, d -> change.consume(); movePanel(d.x, d.y) }
        }
        when (mode) {
            Mode.CARD -> PanelCard(
                panel, onScan = ::scan, onHint = ::hint, onCat = ::placeCat, onX = ::markX,
                onAnswer = ::toggleAnswer, onMinimize = { mode = Mode.BUBBLE }, onClose = ::close, dragModifier = drag,
            )
            Mode.BUBBLE -> Bubble(onClick = { mode = Mode.CARD }, modifier = drag)
        }
    }

    // ---------- actions ----------

    private fun scan() = withBoard { b ->
        val sol = Solver.solve(b)
        val token = ++flashToken
        marks = marks.copy(board = b, flash = true,
            rings = if (sol != null && panel.answerOn) MovePlanner.remaining(b, sol) else marks.rings)
        handler.postDelayed({ if (token == flashToken) marks = marks.copy(flash = false) }, FLASH_MS)
        done("${b.n}×${b.n} · ${b.cats.size} cats · " + if (sol != null) "solvable" else "no solution")
    }

    private fun hint() = withBoard { b ->
        val sol = Solver.solve(b) ?: return@withBoard done(NO_SOLUTION)
        val cell = MovePlanner.nextCat(b, sol) ?: return@withBoard done("Solved")
        val token = ++hintToken
        marks = marks.copy(board = b, hint = cell)
        handler.postDelayed({ if (token == hintToken) marks = marks.copy(hint = null) }, HINT_MS)
        done("Next cat: row ${cell / b.n + 1}, col ${cell % b.n + 1}")
    }

    private fun placeCat() = withBoard { b ->
        val sol = Solver.solve(b) ?: return@withBoard done(NO_SOLUTION)
        val cell = MovePlanner.nextCat(b, sol) ?: return@withBoard done("Solved")
        runTaps(MovePlanner.catTaps(b, cell)) {
            marks = marks.copy(rings = marks.rings - cell, hint = marks.hint.takeIf { it != cell })
            "Cat placed: row ${cell / b.n + 1}, col ${cell % b.n + 1}"
        }
    }

    private fun markX() = withBoard { b ->
        val t = MovePlanner.xTaps(b)
        if (t.isEmpty()) return@withBoard done("Nothing to mark")
        runTaps(t) { "Marked ${t.size} cells" }
    }

    private fun toggleAnswer() {
        if (panel.busy) return
        val on = !panel.answerOn
        prefs.edit().putBoolean(K_ANSWER, on).apply()
        panel = panel.copy(answerOn = on)
        if (!on) { marks = marks.copy(rings = emptyList()); return }
        withBoard { b ->
            val sol = Solver.solve(b) ?: return@withBoard done(NO_SOLUTION)
            marks = marks.copy(board = b, rings = MovePlanner.remaining(b, sol))
            done(if (marks.rings.isEmpty()) "Solved" else "Showing ${marks.rings.size} cats")
        }
    }

    // ---------- plumbing ----------

    private fun done(msg: String) { panel = panel.copy(status = msg, busy = false) }

    private fun runTaps(list: List<Tap>, success: () -> String) {
        panel = panel.copy(status = "Working…")
        setPanelTouchable(false)
        taps.run(list, prefs.getInt(K_DOUBLE_MS, MovePlanner.GAP_MS.toInt()).toLong()) { ok ->
            setPanelTouchable(true)
            done(if (ok) success() else "Stopped")
        }
    }

    private fun withBoard(action: (Board) -> Unit) {
        if (panel.busy) return
        panel = panel.copy(busy = true)
        setOverlayVisible(false)
        handler.postDelayed({ capture(retry = true, action) }, CAPTURE_DELAY_MS)
    }

    private fun capture(retry: Boolean, action: (Board) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(res: ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(res.hardwareBuffer, res.colorSpace)
                val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                res.hardwareBuffer.close()
                setOverlayVisible(true)
                if (bmp == null) return done("Couldn't read the screen. Try again")
                val w = bmp.width; val h = bmp.height
                val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
                bmp.recycle()
                val board = try { BoardReader.read(px, w, h) } catch (e: BoardNotFound) { return done("Open a Meowdoku puzzle first") }
                action(board)
            }

            override fun onFailure(errorCode: Int) {
                if (retry) handler.postDelayed({ capture(false, action) }, RETRY_MS)
                else { setOverlayVisible(true); done("Couldn't read the screen. Try again") }
            }
        })
    }
}
```

- [ ] **Step 4: Resources**

`res/xml/accessibility.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:canRetrieveWindowContent="false"
    android:notificationTimeout="100"
    android:description="@string/a11y_desc" />
```

`res/values/strings.xml`:
```xml
<resources>
    <string name="a11y_desc">Shows a floating panel over Meowdoku. It reads the board from the screen and taps cells only when you press its buttons.</string>
</resources>
```

`res/drawable/ic_launcher_fg.xml` (original cat mark, white on clay):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="30" android:translateY="30" android:scaleX="2" android:scaleY="2">
        <path android:strokeColor="#FFFFFF" android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round"
            android:pathData="M4,11 L4,4 L8.5,7.5 L15.5,7.5 L20,4 L20,11 C20,16.5 16.5,20 12,20 C7.5,20 4,16.5 4,11 Z M9,12.5 L9,13 M15,12.5 L15,13 M11,15.5 L12,16.5 L13,15.5" />
    </group>
</vector>
```

`res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/clay" />
    <foreground android:drawable="@drawable/ic_launcher_fg" />
</adaptive-icon>
```
Add to `strings.xml` file's sibling `res/values/colors.xml`:
```xml
<resources><color name="clay">#D97757</color></resources>
```

- [ ] **Step 5: Manifest (replace)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Meow Solver" android:icon="@mipmap/ic_launcher"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service android:name=".service.OverlayService" android:exported="true"
            android:label="Meow Solver overlay"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility" />
        </service>
    </application>
</manifest>
```

- [ ] **Step 6: `MainActivity.kt` (replace)**

```kotlin
package com.ilan.meowsolver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.service.OverlayService
import com.ilan.meowsolver.ui.SetupScreen
import com.ilan.meowsolver.ui.SetupState

class MainActivity : ComponentActivity() {
    private var serviceOn by mutableStateOf(false)
    private var doubleTapMs by mutableIntStateOf(MovePlanner.GAP_MS.toInt())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
        doubleTapMs = prefs.getInt(OverlayService.K_DOUBLE_MS, MovePlanner.GAP_MS.toInt())
        setContent {
            SetupScreen(
                SetupState(serviceOn, doubleTapMs),
                onStart = {
                    val svc = OverlayService.instance
                    if (svc == null) openAccessibility() else { svc.showOverlay(); moveTaskToBack(true) }
                },
                onAppInfo = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                },
                onAccessibility = ::openAccessibility,
                onDoubleTapMs = { doubleTapMs = it; prefs.edit().putInt(OverlayService.K_DOUBLE_MS, it).apply() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        serviceOn = OverlayService.instance != null
    }

    private fun openAccessibility() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
```

- [ ] **Step 7: Build APK + full test suite**

Run: `gradle assembleDebug testDebugUnitTest --no-daemon -q && ls -la build/outputs/apk/debug/`
Expected: exit 0, APK present; all tests PASS.

- [ ] **Step 8: Verify APK contents** — `/root/android-sdk/build-tools/35.0.0/aapt dump xmltree build/outputs/apk/debug/MeowSolver-debug.apk res/xml/accessibility.xml | grep -E "Gesture|Screenshot"` shows both `0xffffffff`.

- [ ] **Step 9: Commit** — `git add -A && git commit -m "feat: overlay service, tap runner, setup activity, launcher icon"` (+ trailer)

---

### Task 8: Review, verify, deliver

- [ ] **Step 1:** Dispatch independent code-review agent (superpowers:requesting-code-review) over `git diff 7c219a4..HEAD`; fix every Critical/Important finding with a test where testable; commit.
- [ ] **Step 2:** superpowers:verification-before-completion — rerun `gradle testDebugUnitTest recordPaparazziDebug assembleDebug --no-daemon -q`, read every snapshot + alignment PNG, confirm test counts.
- [ ] **Step 3:** Send Ilan: APK, 6 GUI snapshots, 4 alignment renders, test summary, and on-device checklist (install → restricted settings → accessibility → Scan/Hint/Cat/X/Answer/minimize/close on a live puzzle).
