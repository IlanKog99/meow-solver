# Meow Solver v2.1 (Reveal + auto-Answer) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Scan with a Reveal toggle (old Answer behaviour) and turn Answer into a stoppable auto-solver (one read → all cats but the last with their X's → re-read → last cat, with up to 3 fix rounds).

**Architecture:** New pure-Kotlin `core/AutoSolver` (plan + check), tested on all 41 real screenshots through a tap simulator. `OverlayService` gets a small auto-Answer state machine reusing `withBoard`/`TapRunner`/session cancel. `PanelCard` gets the new button order and a Stop state.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.10.01, Paparazzi 1.3.5, JUnit 4.13.2 (unchanged).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-06-meow-solver-v2.1-design.md`.
- Build env: `export ANDROID_HOME=/root/android-sdk`; run `gradle <tasks> --no-daemon -q` from `/home/claude/meowsolver` (long timeouts, up to 10 min).
- Game input: single tap EMPTY↔X; fast double-tap on EMPTY = cat; X → cat = single tap, 350 ms, double-tap. A second single tap on an X clears it — never tap the same cell twice.
- Card order: **Reveal · Hint · Cat · X · Answer**; Cat stays the filled clay tile; while auto-Answer runs the Answer tile shows the `Stop` icon labelled `"Stop"` and stays enabled.
- Reveal pref key stays `"answerOn"`.
- Exact strings (new/changed): `"Placing cats… P/N"`, `"Couldn't place all cats — tap Answer again"`, `"Stopped"`, `"Solved"`, `"No solution — a placed cat is wrong"`, `"Showing N cats"`. Existing strings unchanged.
- Timings: `AUTO_SETTLE_MS = 400L` after each auto tap batch; `MAX_FIX_ROUNDS = 3`.
- No X after the last cat. Cats are never removed.
- Icons are original line drawings in `MeowIcons`; no game art.
- Commit with `git -c user.name="Ilan Kogan" -c user.email="ilanamir100.100@gmail.com" commit`, `git add` specific paths only (never `-A`/`-f`, nothing under `.superpowers/`), message ending:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01WBz1ktuMVmmVWzGdpUm9m1
  ```

## File map

| File | Change |
|---|---|
| `src/main/java/com/ilan/meowsolver/core/MovePlanner.kt` | `catTaps(b, cell, current)`, new `xTargetsFor(b, state, cat)`; `xTargets` reuses it |
| `src/main/java/com/ilan/meowsolver/core/AutoSolver.kt` | **new**: `Check`, `AutoSolver.plan/check/lastCatTaps` |
| `src/test/java/com/ilan/meowsolver/TestBoards.kt` | add `Board.after(taps)` tap simulator + `cellOf` |
| `src/test/java/com/ilan/meowsolver/AutoSolverTest.kt` | **new** |
| `src/test/java/com/ilan/meowsolver/MovePlannerTest.kt` | add `xTargetsFor` / `catTaps(current)` tests |
| `src/main/java/com/ilan/meowsolver/ui/PanelCard.kt`, `ui/MeowIcons.kt`, `ui/MarksLayer.kt`, `ui/SetupScreen.kt` | new order, Stop state, icons, drop flash, how-to text |
| `src/main/java/com/ilan/meowsolver/service/OverlayService.kt` | Reveal rename, remove scan/flash, auto-Answer runner |
| `src/test/java/com/ilan/meowsolver/UiSnapshotTest.kt`, `AlignmentRenderTest.kt`, `src/test/snapshots/images/*` | new snapshots, drop flash arg |

---

### Task 1: AutoSolver core + tap simulator (TDD)

**Files:**
- Modify: `src/main/java/com/ilan/meowsolver/core/MovePlanner.kt`
- Create: `src/main/java/com/ilan/meowsolver/core/AutoSolver.kt`
- Modify: `src/test/java/com/ilan/meowsolver/TestBoards.kt`, `src/test/java/com/ilan/meowsolver/MovePlannerTest.kt`
- Create: `src/test/java/com/ilan/meowsolver/AutoSolverTest.kt`

**Interfaces:**
- Consumes: `Board`, `Cell`, `Tap`, `MovePlanner.remaining/GAP_MS`, `Solver.solve`, test helpers `boardOf`, `LEVEL81`, `Fixtures.board/expected`.
- Produces: `MovePlanner.catTaps(b: Board, cell: Int, current: Cell = b.state[cell]): List<Tap>`; `MovePlanner.xTargetsFor(b: Board, state: Array<Cell>, cat: Int): List<Int>`; `sealed interface Check { Solved; NoSolution; LastOnly(cell: Int); Missing(cells: List<Int>) }`; `AutoSolver.plan(b: Board, sol: IntArray): List<Tap>`, `AutoSolver.check(b: Board, sol: IntArray?): Check`, `AutoSolver.lastCatTaps(b: Board, cell: Int): List<Tap>`.

- [ ] **Step 1: Add the tap simulator to `TestBoards.kt`** (append; add imports `com.ilan.meowsolver.core.Tap`)

```kotlin
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
```

- [ ] **Step 2: Write failing tests**

Append to `MovePlannerTest.kt` (inside the class; add import `com.ilan.meowsolver.core.Cell`):
```kotlin
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
```

Create `AutoSolverTest.kt`:
```kotlin
package com.ilan.meowsolver

import com.ilan.meowsolver.core.AutoSolver
import com.ilan.meowsolver.core.Board
import com.ilan.meowsolver.core.Check
import com.ilan.meowsolver.core.MovePlanner
import com.ilan.meowsolver.core.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AutoSolverTest {
    private val sol81 = intArrayOf(5, 2, 6, 4, 7, 1, 3, 0)

    /** null when the cats form a valid complete solution. */
    private fun rulesProblem(b: Board): String? {
        val cats = b.cats
        if (cats.size != b.n) return "cats=${cats.size}"
        if (cats.map { it / b.n }.toSet().size != b.n) return "row clash"
        if (cats.map { it % b.n }.toSet().size != b.n) return "column clash"
        if (cats.map { b.region[it] }.toSet().size != b.n) return "colour clash"
        for (a in cats) for (c in cats) if (a < c && abs(a / b.n - c / b.n) <= 1 && abs(a % b.n - c % b.n) <= 1) return "touching $a $c"
        return null
    }

    @Test fun planThenLastSolvesEveryFixture() {
        val failures = Fixtures.expected().mapNotNull { e ->
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)!!
            val mid = b.after(AutoSolver.plan(b, sol))
            val c = AutoSolver.check(mid, Solver.solve(mid))
            if (MovePlanner.remaining(b, sol).isEmpty()) return@mapNotNull if (c == Check.Solved) null else "${e.name}: $c"
            if (c !is Check.LastOnly) return@mapNotNull "${e.name}: after plan $c"
            val fin = mid.after(AutoSolver.lastCatTaps(mid, c.cell))
            rulesProblem(fin)?.let { "${e.name}: $it" }
                ?: if (AutoSolver.check(fin, Solver.solve(fin)) != Check.Solved) "${e.name}: not Solved" else null
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun planNeverTapsACellTwiceOrTouchesTheLastCat() {
        for (e in Fixtures.expected()) {
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)!!
            val plan = AutoSolver.plan(b, sol)
            val byCell = plan.groupBy { b.cellOf(it) }
            for ((cell, taps) in byCell) {
                val ok = taps.size == 1 ||
                    (taps.size == 2 && !taps[0].double && taps[1].double && b.state[cell] == com.ilan.meowsolver.core.Cell.X)
                assertTrue("${e.name}: cell $cell tapped $taps", ok)
            }
            MovePlanner.remaining(b, sol).lastOrNull()?.let { last ->
                assertTrue("${e.name}: last cat $last tapped", last !in byCell)
            }
        }
    }

    @Test fun missedCatTapIsFixedByReplan() {
        val b = Fixtures.board("hi_211922.jpg")
        val sol = Solver.solve(b)!!
        val plan = AutoSolver.plan(b, sol)
        val missed = plan.first { it.double }
        val mid = b.after(plan - missed)
        val c = AutoSolver.check(mid, Solver.solve(mid))
        assertTrue("expected Missing, got $c", c is Check.Missing && b.cellOf(missed) in c.cells)
        val fixed = mid.after(AutoSolver.plan(mid, Solver.solve(mid)!!))
        assertTrue(AutoSolver.check(fixed, Solver.solve(fixed)) is Check.LastOnly)
    }

    @Test fun wrongCatGivesNoSolution() {
        val rows = LEVEL81.copyOf(); rows[0] = "A@ A B B B C C C"
        val b = boardOf(*rows)
        assertEquals(Check.NoSolution, AutoSolver.check(b, Solver.solve(b)))
    }

    @Test fun solvedBoardIsSolved() {
        val rows = LEVEL81.mapIndexed { r, line ->
            line.split(" ").mapIndexed { c, t -> if (c == sol81[r]) "$t@" else t }.joinToString(" ")
        }.toTypedArray()
        val b = boardOf(*rows)
        assertEquals(Check.Solved, AutoSolver.check(b, Solver.solve(b)))
    }

    @Test fun oneRemainingPlansNothingAndIsLastOnly() {
        val rows = LEVEL81.mapIndexed { r, line ->
            line.split(" ").mapIndexed { c, t -> if (c == sol81[r] && r < 7) "$t@" else t }.joinToString(" ")
        }.toTypedArray()
        val b = boardOf(*rows)
        val sol = Solver.solve(b)!!
        assertEquals(emptyList<Any>(), AutoSolver.plan(b, sol))
        assertEquals(Check.LastOnly(7 * 8 + 0), AutoSolver.check(b, sol))
    }
}
```

- [ ] **Step 3: Run, expect compile FAIL** (`AutoSolver`, `Check`, `xTargetsFor`, 3-arg `catTaps` unresolved)

Run: `gradle testDebugUnitTest --no-daemon -q --tests '*AutoSolverTest*' --tests '*MovePlannerTest*'`

- [ ] **Step 4: Update `MovePlanner.kt`** — replace `catTaps` and `xTargets` with:

```kotlin
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
```

- [ ] **Step 5: Create `core/AutoSolver.kt`**

```kotlin
package com.ilan.meowsolver.core

/** Result of re-reading the board during auto-Answer. */
sealed interface Check {
    data object Solved : Check
    data object NoSolution : Check
    data class LastOnly(val cell: Int) : Check
    data class Missing(val cells: List<Int>) : Check
}

object AutoSolver {
    /**
     * Taps for every missing solution cat except the last (top → bottom): the cat, then the cells it rules out.
     * Planned on a virtual copy of the board so no cell is ever tapped twice (a second tap would clear an X).
     */
    fun plan(b: Board, sol: IntArray): List<Tap> {
        val todo = MovePlanner.remaining(b, sol)
        if (todo.size <= 1) return emptyList()
        val state = b.state.copyOf()
        val out = mutableListOf<Tap>()
        for (cell in todo.dropLast(1)) {
            out += MovePlanner.catTaps(b, cell, state[cell])
            state[cell] = Cell.CAT
            for (i in MovePlanner.xTargetsFor(b, state, cell)) {
                out += Tap(b.cx(i % b.n), b.cy(i / b.n), false, MovePlanner.GAP_MS)
                state[i] = Cell.X
            }
        }
        return out
    }

    fun check(b: Board, sol: IntArray?): Check {
        sol ?: return Check.NoSolution
        val todo = MovePlanner.remaining(b, sol)
        return when (todo.size) {
            0 -> Check.Solved
            1 -> Check.LastOnly(todo[0])
            else -> Check.Missing(todo)
        }
    }

    /** The last cat gets no X's after it. */
    fun lastCatTaps(b: Board, cell: Int): List<Tap> = MovePlanner.catTaps(b, cell)
}
```

- [ ] **Step 6: Run full suite, expect PASS** — `gradle testDebugUnitTest --no-daemon -q` (26 existing + 2 MovePlanner + 6 AutoSolver = 34 tests).

- [ ] **Step 7: Commit** — `git add` the five files; message `feat(core): AutoSolver plan/check with tap simulator tests` (+ trailer).

---

### Task 2: Reveal + auto-Answer UI and service

**Files:**
- Modify: `ui/MeowIcons.kt`, `ui/PanelCard.kt`, `ui/MarksLayer.kt`, `ui/SetupScreen.kt`, `service/OverlayService.kt`
- Modify tests: `UiSnapshotTest.kt`, `AlignmentRenderTest.kt`; snapshots under `src/test/snapshots/images/`

**Interfaces:**
- Consumes: `AutoSolver`, `Check` (Task 1); existing `withBoard`, `handleBoard`, `TapRunner.run(list, gapMs, cancelled, onDone)`, `done`, `session`, `setPanelTouchable`, `setOverlayVisible`.
- Produces: `PanelState(status: String, busy: Boolean, revealOn: Boolean, autoRunning: Boolean = false)`; `PanelCard(state, onReveal, onHint, onCat, onX, onAnswer, onMinimize, onClose, dragModifier)`; `MeowIcons.FastForward`, `MeowIcons.Stop`; `Marks(board, rings, hint)` (no `flash`).

- [ ] **Step 1: Update snapshot tests first** (they won't compile until the UI changes)

In `UiSnapshotTest.kt` replace the three panel tests with:
```kotlin
    @Test fun panelIdle() = paparazzi.snapshot { OnGame { Panel(PanelState("Ready", busy = false, revealOn = false)) } }
    @Test fun panelBusy() = paparazzi.snapshot { OnGame { Panel(PanelState("Working…", busy = true, revealOn = false)) } }
    @Test fun panelRevealOn() = paparazzi.snapshot { OnGame { Panel(PanelState("Showing 7 cats", busy = false, revealOn = true)) } }
    @Test fun panelRunning() = paparazzi.snapshot { OnGame { Panel(PanelState("Placing cats… 3/10", busy = true, revealOn = false, autoRunning = true)) } }
```
In `AlignmentRenderTest.kt` change `Marks(b, MovePlanner.remaining(b, sol), MovePlanner.nextCat(b, sol), flash = true)` to `Marks(b, MovePlanner.remaining(b, sol), MovePlanner.nextCat(b, sol))` (the test keeps drawing its own grid outline).

Run `gradle testDebugUnitTest --no-daemon -q --tests '*UiSnapshotTest*'` → expect compile FAIL (`revealOn`, `autoRunning` unknown).

- [ ] **Step 2: Icons** — in `MeowIcons.kt` delete `Scan` and add:
```kotlin
    val FastForward = icon("fast-forward") {
        moveTo(4f, 6f); lineTo(11f, 12f); lineTo(4f, 18f); close()
        moveTo(12f, 6f); lineTo(19f, 12f); lineTo(12f, 18f); close()
    }
    val Stop = icon("stop") { moveTo(7f, 7f); lineTo(17f, 7f); lineTo(17f, 17f); lineTo(7f, 17f); close() }
```

- [ ] **Step 3: `PanelCard.kt`** — replace the data class, signature, and action row:
```kotlin
data class PanelState(val status: String, val busy: Boolean, val revealOn: Boolean, val autoRunning: Boolean = false)

@Composable
fun PanelCard(
    state: PanelState,
    onReveal: () -> Unit, onHint: () -> Unit, onCat: () -> Unit, onX: () -> Unit, onAnswer: () -> Unit,
    onMinimize: () -> Unit, onClose: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
```
and the action row body:
```kotlin
            val on = !state.busy
            ActionButton(MeowIcons.Eye, "Reveal", on, toggled = state.revealOn, onClick = onReveal)
            ActionButton(MeowIcons.Bulb, "Hint", on, onClick = onHint)
            ActionButton(MeowIcons.Cat, "Cat", on, filled = true, onClick = onCat)
            ActionButton(MeowIcons.X, "X", on, onClick = onX)
            if (state.autoRunning) ActionButton(MeowIcons.Stop, "Stop", true, toggled = true, onClick = onAnswer)
            else ActionButton(MeowIcons.FastForward, "Answer", on, onClick = onAnswer)
```

- [ ] **Step 4: `MarksLayer.kt`** — remove `val flash: Boolean = false` from `Marks` and delete the `if (marks.flash) … { … }` block (rings and hint unchanged).

- [ ] **Step 5: `SetupScreen.kt`** — change the subtitle to `"Hint, place, or solve Meowdoku from a floating panel."` and replace the five `HowRow` lines with:
```kotlin
            HowRow(MeowIcons.Eye, "Reveal", "Rings every remaining cat. Tap again to hide.")
            HowRow(MeowIcons.Bulb, "Hint", "Shows the next cat for 5 seconds")
            HowRow(MeowIcons.Cat, "Cat", "Places the next cat for you")
            HowRow(MeowIcons.X, "X", "Marks every cell a cat rules out")
            HowRow(MeowIcons.FastForward, "Answer", "Solves the level. Tap Stop to halt.")
```

- [ ] **Step 6: `OverlayService.kt`** — apply these edits:

a) Constants: rename `K_ANSWER` → `K_REVEAL` (value stays `"answerOn"`); delete `FLASH_MS`; add
```kotlin
        private const val AUTO_SETTLE_MS = 400L // let the game animate before re-reading
        private const val MAX_FIX_ROUNDS = 3
        private const val FIX_FAIL = "Couldn't place all cats — tap Answer again"
```
Delete the `flashToken` field. Imports: add `com.ilan.meowsolver.core.AutoSolver`, `com.ilan.meowsolver.core.Check`, `android.graphics.Rect`.

b) Every `answerOn` → `revealOn` (field init `PanelState("Ready", busy = false, revealOn = false)`, `onServiceConnected` pref read with `K_REVEAL`, `handleBoard`).

c) `close()` keeps its body; its panel line becomes `panel = panel.copy(status = "Ready", busy = false, autoRunning = false)`.

d) `done` clears the auto flag too:
```kotlin
    private fun done(msg: String) { panel = panel.copy(status = msg, busy = false, autoRunning = false) }
```

e) `PanelHost` card call:
```kotlin
            Mode.CARD -> PanelCard(
                panel, onReveal = ::toggleReveal, onHint = ::hint, onCat = ::placeCat, onX = ::markX,
                onAnswer = ::answer, onMinimize = { mode = Mode.BUBBLE; settle() }, onClose = ::close,
                dragModifier = Modifier.pointerInteropFilter { dragTouch(it, null) }, // applied to the status text only
            )
```

f) Delete `scan()`. Rename `toggleAnswer` → `toggleReveal` and inside it use `revealOn` / `K_REVEAL` (logic unchanged).

g) `withBoard` gains an `internal` flag so auto-Answer rounds can run while busy:
```kotlin
    private fun withBoard(needSolution: Boolean = true, internal: Boolean = false, action: (Board, IntArray?) -> Unit) {
        if (panel.busy && !internal) return
```
(rest unchanged).

h) Add the auto-Answer runner (after `toggleReveal`):
```kotlin
    // ---------- auto-Answer ----------

    private fun answer() {
        if (panel.autoRunning) { stopAnswer(); return }
        if (panel.busy) return
        panel = panel.copy(busy = true, autoRunning = true)
        answerStep(fixes = 0, first = true)
    }

    private fun stopAnswer() {
        session++ // drops the in-flight capture, settle wait, and remaining taps
        setPanelTouchable(true)
        setOverlayVisible(true)
        done("Stopped")
    }

    /** One read → check → act round. First round plans everything but the last cat; later rounds fix or finish. */
    private fun answerStep(fixes: Int, first: Boolean) = withBoard(internal = true) { b, sol ->
        panel = panel.copy(status = "Placing cats… ${b.cats.size}/${b.n}")
        when (val c = AutoSolver.check(b, sol)) {
            Check.Solved -> done("Solved")
            Check.NoSolution -> done(NO_SOLUTION)
            is Check.LastOnly -> autoTaps(AutoSolver.lastCatTaps(b, c.cell)) { done("Solved") }
            is Check.Missing ->
                if (!first && fixes >= MAX_FIX_ROUNDS) done(FIX_FAIL)
                else autoTaps(AutoSolver.plan(b, sol!!)) {
                    val s = session
                    handler.postDelayed({
                        if (s == session) answerStep(if (first) 0 else fixes + 1, first = false)
                    }, AUTO_SETTLE_MS)
                }
        }
    }

    /** Runs taps without ending the busy state; Stop stays tappable unless a tap would land on the panel. */
    private fun autoTaps(list: List<Tap>, next: () -> Unit) {
        val s = session
        setPanelTouchable(!tapsHitPanel(list))
        taps.run(list, prefs.getInt(K_DOUBLE_MS, MovePlanner.GAP_MS.toInt()).toLong(), cancelled = { s != session }) { ok ->
            if (s != session) return@run
            setPanelTouchable(true)
            if (!ok) done("Stopped") else try { next() } catch (_: Exception) { done(READ_FAIL) }
        }
    }

    private fun tapsHitPanel(list: List<Tap>): Boolean {
        val v = panelView ?: return false
        val loc = IntArray(2).also { v.getLocationOnScreen(it) }
        val r = Rect(loc[0] - 8, loc[1] - 8, loc[0] + v.width + 8, loc[1] + v.height + 8)
        return list.any { r.contains(it.x, it.y) }
    }
```

- [ ] **Step 7: Build, record, test**

Run: `gradle assembleDebug recordPaparazziDebug testDebugUnitTest --no-daemon -q`
Expected: exit 0; 34 + 1 (panelRunning) = 35 tests pass.
Delete the stale `src/test/snapshots/images/com.ilan.meowsolver_UiSnapshotTest_panelAnswerOn.png` (`git rm`).

- [ ] **Step 8: Visual review** — Read `panelIdle`, `panelRevealOn`, `panelRunning`, `setupOff` PNGs: order Reveal · Hint · Cat · X · Answer; Reveal tinted when on; running shows dimmed tiles with a tinted Stop tile; fast-forward and stop icons recognisable; how-to rows updated. Fix and re-record if not.

- [ ] **Step 9: Commit** — `git add` the changed sources/tests/snapshots (+ the `git rm`); message `feat: Reveal toggle and stoppable auto-Answer` (+ trailer).

---

### Task 3: App dark mode (System · Light · Dark)

**Files:**
- Create: `src/main/java/com/ilan/meowsolver/ui/ThemeMode.kt`, `src/test/java/com/ilan/meowsolver/ThemeModeTest.kt`
- Modify: `src/main/java/com/ilan/meowsolver/ui/SetupScreen.kt`, `src/main/java/com/ilan/meowsolver/MainActivity.kt`, `src/test/java/com/ilan/meowsolver/UiSnapshotTest.kt`; snapshots under `src/test/snapshots/images/`

**Interfaces:**
- Consumes: `Meow.Clay`, `Meow.OnCard`, `MeowIcons.*`, `OverlayService.PREFS`.
- Produces: `enum class ThemeMode { SYSTEM, LIGHT, DARK }` with `label`, `isDark(systemDark)`, `ThemeMode.PREF_KEY = "themeMode"`, `ThemeMode.fromPref(String?)`; `data class SetupColors(bg, card, ink, inkMuted)` with `SetupColors.Light` / `SetupColors.Dark`; `data class SetupState(serviceOn: Boolean, doubleTapMs: Int, themeMode: ThemeMode = ThemeMode.SYSTEM)`; `SetupScreen(state, dark: Boolean, onStart, onAppInfo, onAccessibility, onDoubleTapMs, onThemeMode: (ThemeMode) -> Unit)`.

- [ ] **Step 1: Failing tests**

`src/test/java/com/ilan/meowsolver/ThemeModeTest.kt`:
```kotlin
package com.ilan.meowsolver

import com.ilan.meowsolver.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test fun systemFollowsPhone() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
    }
    @Test fun explicitModesIgnorePhone() {
        assertEquals(false, ThemeMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, ThemeMode.DARK.isDark(systemDark = false))
    }
    @Test fun prefParsingDefaultsToSystem() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromPref("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPref("purple"))
    }
}
```

In `UiSnapshotTest.kt` replace the two setup tests with:
```kotlin
    @Test fun setupOff() { tall(); paparazzi.snapshot { SetupScreen(SetupState(serviceOn = false, doubleTapMs = 90), dark = false, {}, {}, {}, {}, {}) } }
    @Test fun setupOn() { tall(); paparazzi.snapshot { SetupScreen(SetupState(serviceOn = true, doubleTapMs = 120, themeMode = ThemeMode.LIGHT), dark = false, {}, {}, {}, {}, {}) } }
    @Test fun setupDark() { tall(); paparazzi.snapshot { SetupScreen(SetupState(serviceOn = true, doubleTapMs = 90, themeMode = ThemeMode.DARK), dark = true, {}, {}, {}, {}, {}) } }
```
(add import `com.ilan.meowsolver.ui.ThemeMode`).

Run `gradle testDebugUnitTest --no-daemon -q --tests '*ThemeModeTest*' --tests '*UiSnapshotTest*'` → expect compile FAIL.

- [ ] **Step 2: `ui/ThemeMode.kt`**
```kotlin
package com.ilan.meowsolver.ui

/** App (setup screen) appearance. The overlay card is always dark. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    fun isDark(systemDark: Boolean) = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        const val PREF_KEY = "themeMode"
        fun fromPref(v: String?) = entries.firstOrNull { it.name == v } ?: SYSTEM
    }
}
```

- [ ] **Step 3: `ui/SetupScreen.kt`** — add palette + local, thread colours through, add Appearance section.

Add near the top (after imports; add imports `androidx.compose.foundation.border`, `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.runtime.staticCompositionLocalOf`):
```kotlin
data class SetupColors(val bg: Color, val card: Color, val ink: Color, val inkMuted: Color) {
    companion object {
        val Light = SetupColors(Meow.Cream, Color.White, Meow.Ink, Meow.InkMuted)
        val Dark = SetupColors(Color(0xFF1C1714), Color(0xFF2A2420), Color(0xFFF3ECE6), Color(0xFFB3A59B))
    }
}

private val LocalSetupColors = staticCompositionLocalOf { SetupColors.Light }

data class SetupState(val serviceOn: Boolean, val doubleTapMs: Int, val themeMode: ThemeMode = ThemeMode.SYSTEM)
```
(delete the old `SetupState` line.)

Replace the `SetupScreen` function with:
```kotlin
@Composable
fun SetupScreen(
    state: SetupState,
    dark: Boolean,
    onStart: () -> Unit, onAppInfo: () -> Unit, onAccessibility: () -> Unit, onDoubleTapMs: (Int) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val c = if (dark) SetupColors.Dark else SetupColors.Light
    CompositionLocalProvider(LocalSetupColors provides c) {
        Column(
            Modifier.fillMaxSize().background(c.bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 32.dp),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Meow.Clay), contentAlignment = Alignment.Center) {
                Icon(MeowIcons.Cat, contentDescription = null, tint = Meow.OnCard, modifier = Modifier.size(36.dp))
            }
            Text("Meow Solver", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = c.ink, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Hint, place, or solve Meowdoku from a floating panel.",
                fontSize = 15.sp, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onStart, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Meow.Clay, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp),
            ) { Text(if (state.serviceOn) "Start overlay" else "Turn on overlay", fontSize = 17.sp) }

            SectionTitle("Setup")
            Card {
                Step(1, "Allow restricted settings", "App info → ⋮ → Allow restricted settings", state.serviceOn, "App info", onAppInfo)
                HorizontalDivider(color = c.ink.copy(alpha = 0.08f))
                Step(2, "Turn on accessibility", "Installed apps → Meow Solver overlay → On", state.serviceOn, "Open", onAccessibility)
            }

            SectionTitle("How it works")
            Card {
                HowRow(MeowIcons.Eye, "Reveal", "Rings every remaining cat. Tap again to hide.")
                HowRow(MeowIcons.Bulb, "Hint", "Shows the next cat for 5 seconds")
                HowRow(MeowIcons.Cat, "Cat", "Places the next cat for you")
                HowRow(MeowIcons.X, "X", "Marks every cell a cat rules out")
                HowRow(MeowIcons.FastForward, "Answer", "Solves the level. Tap Stop to halt.")
            }

            SectionTitle("Appearance")
            Card {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in ThemeMode.entries) {
                        val selected = m == state.themeMode
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Meow.Clay else Color.Transparent)
                                .border(1.dp, if (selected) Meow.Clay else c.ink.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable(onClickLabel = m.label) { onThemeMode(m) },
                            contentAlignment = Alignment.Center,
                        ) { Text(m.label, color = if (selected) Color.White else c.ink, fontSize = 14.sp) }
                    }
                }
            }

            SectionTitle("Tuning")
            Card {
                Text("Double-tap speed · ${state.doubleTapMs} ms", color = c.ink, fontSize = 15.sp)
                Slider(
                    value = state.doubleTapMs.toFloat(), onValueChange = { onDoubleTapMs((it / 10).roundToInt() * 10) },
                    valueRange = 60f..200f, steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = Meow.Clay, activeTrackColor = Meow.Clay, inactiveTrackColor = c.ink.copy(alpha = 0.12f),
                        activeTickColor = Color.White.copy(alpha = 0.6f), inactiveTickColor = c.inkMuted.copy(alpha = 0.5f),
                    ),
                )
                Text("Raise it if Cat places an X instead.", color = c.inkMuted, fontSize = 13.sp)
            }
        }
    }
}
```
(add import `androidx.compose.foundation.clickable`.)

In the private composables, replace colour references: `SectionTitle` → `LocalSetupColors.current.inkMuted`; `Card` background `Color.White` → `LocalSetupColors.current.card`; `Step`: `Meow.Ink.copy(alpha = 0.08f)` → `ink.copy(alpha = 0.12f)`, every `Meow.Ink` → `ink`, `Meow.InkMuted` → `inkMuted` (with `val ink = LocalSetupColors.current.ink; val inkMuted = LocalSetupColors.current.inkMuted` at the top of `Step` and `HowRow`); `HowRow` likewise. Keep `Meow.Clay` and `Color.White` on clay surfaces.

- [ ] **Step 4: `MainActivity.kt`** — persist and apply the mode, flip system-bar icons:
```kotlin
// fields
private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

// in onCreate, after doubleTapMs is read:
themeMode = ThemeMode.fromPref(prefs.getString(ThemeMode.PREF_KEY, null))
```
Replace `enableEdgeToEdge()` + `setContent { … }` with:
```kotlin
        setContent {
            val dark = themeMode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                          else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            SetupScreen(
                SetupState(serviceOn, doubleTapMs, themeMode),
                dark = dark,
                onStart = {
                    val svc = OverlayService.instance
                    if (svc == null) openAccessibility() else { svc.showOverlay(); moveTaskToBack(true) }
                },
                onAppInfo = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                },
                onAccessibility = ::openAccessibility,
                onDoubleTapMs = { doubleTapMs = it; prefs.edit().putInt(OverlayService.K_DOUBLE_MS, it).apply() },
                onThemeMode = { themeMode = it; prefs.edit().putString(ThemeMode.PREF_KEY, it.name).apply() },
            )
        }
```
Imports: `android.graphics.Color as AndroidColor`, `androidx.activity.SystemBarStyle`, `androidx.compose.foundation.isSystemInDarkTheme`, `androidx.compose.runtime.LaunchedEffect`, `com.ilan.meowsolver.ui.ThemeMode`.

- [ ] **Step 5: Build, record, test** — `gradle assembleDebug recordPaparazziDebug testDebugUnitTest --no-daemon -q` → exit 0; 35 + 3 (ThemeModeTest) + 1 (setupDark) = 39 tests.

- [ ] **Step 6: Visual review** — Read `setupDark`, `setupOn`, `setupOff` PNGs: dark background/cards/text readable, picker shows the selected mode in clay, slider/track visible in dark, light screens unchanged except the new Appearance section.

- [ ] **Step 7: Commit** — `git add` the changed/new files + snapshots; message `feat(app): System/Light/Dark appearance for the setup screen` (+ trailer).

---

### Task 4: Final review, verify, deliver

- [ ] Final whole-branch review (superpowers:requesting-code-review) over `3cb066d..HEAD` (Tasks 1–2 already final-reviewed; review Task 3's range); fix Critical/Important.
- [ ] `gradle clean assembleRelease testDebugUnitTest --no-daemon -q`; confirm counts, `apksigner verify`, release APK size.
- [ ] Send Ilan: release APK, new panel snapshots, on-device checklist for Reveal / Answer / Stop.
