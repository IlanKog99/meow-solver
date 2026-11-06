# Meow Solver v2.3 — Overlay Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Setup-screen toggles for which overlay buttons show, the cat order (Random/Rows/Columns + Reverse) used by Hint/Cat/Answer, and whether Answer also places X's.

**Architecture:** Pure `core/CatOrder.kt` (order + label) and `core/OverlaySettings.kt` (settings value, encode/decode, SharedPreferences adapters). `MovePlanner.nextCat` and `AutoSolver.plan` take order/reverse/withX. Service reads settings from prefs and listens for changes; PanelCard hides disabled buttons; SetupScreen edits settings.

**Tech Stack:** Kotlin 2.0.21, Compose (material3), Paparazzi 1.3.5, JUnit 4.

## Global Constraints
- Spec: `docs/superpowers/specs/2026-11-07-meow-solver-v2.3-settings-design.md`.
- Prefs file `OverlayService.PREFS` ("meow"); keys `panelButtons`, `catOrder`, `orderReverse`, `answerX`. Defaults: all buttons, RANDOM, false, true.
- Order labels exactly: `Random`, `Top → bottom`, `Bottom → top`, `Left → right`, `Right → left`.
- Build: `export ANDROID_HOME=/root/android-sdk`; `gradle <tasks> --no-daemon -q` in `/home/claude/meowsolver`.
- Commit: `git -c user.name="Ilan Kogan" -c user.email="ilanamir100.100@gmail.com" commit`, message ends with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01WBz1ktuMVmmVWzGdpUm9m1`. `git add` explicit paths only; never `.superpowers/` or `.kotlin/`.

---

### Task 1: Core — order, settings, plan options (TDD)

**Files:**
- Create: `src/main/java/com/ilan/meowsolver/core/CatOrder.kt`, `src/main/java/com/ilan/meowsolver/core/OverlaySettings.kt`, `src/test/java/com/ilan/meowsolver/OrderTest.kt`, `src/test/java/com/ilan/meowsolver/OverlaySettingsTest.kt`
- Modify: `src/main/java/com/ilan/meowsolver/core/MovePlanner.kt` (`nextCat`), `src/main/java/com/ilan/meowsolver/core/AutoSolver.kt` (`plan`), `src/test/java/com/ilan/meowsolver/MovePlannerTest.kt`, `src/test/java/com/ilan/meowsolver/AutoSolverTest.kt`

**Interfaces:**
- Consumes: `Board` (`n`, `state`), `MovePlanner.remaining(b, sol)`, `MovePlanner.catTaps`, `MovePlanner.xTargetsFor`, `MovePlanner.CLEAR_PAUSE_MS`, `GAP_MS`; test helpers `boardOf`, `LEVEL81`, `Fixtures`, `Board.cellOf`, `Board.after`.
- Produces: `enum class CatOrder(label) { RANDOM, ROWS, COLUMNS }`; `object Order { fun cells(b, sol, order, reverse, rnd = Random): List<Int>; fun label(order, reverse): String }`; `enum class PanelButton(label) { REVEAL, HINT, CAT, X, ANSWER }`; `data class OverlaySettings(buttons, order, reverse, answerX)` with `withButton(btn, on)`, `orderLabel`, `encodeButtons()`, `companion decode(...)` and keys `K_BUTTONS/K_ORDER/K_REVERSE/K_ANSWER_X`; `fun SharedPreferences.overlaySettings(): OverlaySettings`; `fun SharedPreferences.Editor.putOverlaySettings(s): SharedPreferences.Editor`; `MovePlanner.nextCat(b, sol, order = RANDOM, reverse = false, rnd = Random)`; `AutoSolver.plan(b, sol, order = RANDOM, reverse = false, withX = true, rnd = Random)`.

- [ ] **Step 1: Write failing tests**

`src/test/java/com/ilan/meowsolver/OrderTest.kt`:
```kotlin
package com.ilan.meowsolver

import com.ilan.meowsolver.core.CatOrder
import com.ilan.meowsolver.core.Order
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class OrderTest {
    private val sol81 = intArrayOf(5, 2, 6, 4, 7, 1, 3, 0)
    private val b = boardOf(*LEVEL81)
    private fun cells(o: CatOrder, rev: Boolean) = Order.cells(b, sol81, o, rev, Random(1))

    @Test fun rowsTopToBottomAndReverse() {
        val rows = (0 until 8).map { it * 8 + sol81[it] }
        assertEquals(rows, cells(CatOrder.ROWS, false))
        assertEquals(rows.reversed(), cells(CatOrder.ROWS, true))
    }

    @Test fun columnsLeftToRightAndReverse() {
        val cols = (0 until 8).map { r -> r * 8 + sol81[r] }.sortedBy { it % 8 }
        assertEquals(cols, cells(CatOrder.COLUMNS, false))
        assertEquals(cols.reversed(), cells(CatOrder.COLUMNS, true))
    }

    @Test fun randomIsAPermutationAndIgnoresReverse() {
        val all = (0 until 8).map { it * 8 + sol81[it] }.toSet()
        assertEquals(all, cells(CatOrder.RANDOM, false).toSet())
        assertEquals(8, cells(CatOrder.RANDOM, true).size)
    }

    @Test fun skipsPlacedCats() {
        val rows = LEVEL81.copyOf(); rows[0] = "A A B B B C@ C C"
        assertEquals(1 * 8 + 2, Order.cells(boardOf(*rows), sol81, CatOrder.ROWS, false).first())
    }

    @Test fun labels() {
        assertEquals("Random", Order.label(CatOrder.RANDOM, false))
        assertEquals("Random", Order.label(CatOrder.RANDOM, true))
        assertEquals("Top → bottom", Order.label(CatOrder.ROWS, false))
        assertEquals("Bottom → top", Order.label(CatOrder.ROWS, true))
        assertEquals("Left → right", Order.label(CatOrder.COLUMNS, false))
        assertEquals("Right → left", Order.label(CatOrder.COLUMNS, true))
    }
}
```

`src/test/java/com/ilan/meowsolver/OverlaySettingsTest.kt`:
```kotlin
package com.ilan.meowsolver

import com.ilan.meowsolver.core.CatOrder
import com.ilan.meowsolver.core.OverlaySettings
import com.ilan.meowsolver.core.PanelButton
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlaySettingsTest {
    @Test fun decodeDefaults() {
        assertEquals(OverlaySettings(), OverlaySettings.decode(null, null, false, true))
        assertEquals(PanelButton.entries.toSet(), OverlaySettings.decode("", "nope", false, true).buttons)
        assertEquals(CatOrder.RANDOM, OverlaySettings.decode(null, "nope", false, true).order)
        assertEquals(PanelButton.entries.toSet(), OverlaySettings.decode("FOO,BAR", null, false, true).buttons)
    }

    @Test fun encodeDecodeRoundTrip() {
        val s = OverlaySettings(setOf(PanelButton.HINT, PanelButton.ANSWER), CatOrder.COLUMNS, reverse = true, answerX = false)
        assertEquals("HINT,ANSWER", s.encodeButtons())
        assertEquals(s, OverlaySettings.decode(s.encodeButtons(), s.order.name, s.reverse, s.answerX))
    }

    @Test fun lastButtonCannotBeRemoved() {
        val one = OverlaySettings(buttons = setOf(PanelButton.CAT))
        assertEquals(one, one.withButton(PanelButton.CAT, false))
        assertEquals(setOf(PanelButton.CAT, PanelButton.X), one.withButton(PanelButton.X, true).buttons)
        assertEquals(PanelButton.entries.toSet() - PanelButton.X, OverlaySettings().withButton(PanelButton.X, false).buttons)
    }

    @Test fun orderLabelFollowsSettings() {
        assertEquals("Bottom → top", OverlaySettings(order = CatOrder.ROWS, reverse = true).orderLabel)
    }
}
```

In `MovePlannerTest.kt` change `MovePlanner.nextCat(b, sol81, Random(it))` to `MovePlanner.nextCat(b, sol81, rnd = Random(it))` and add:
```kotlin
    @Test fun nextCatFollowsOrder() {
        val b = boardOf(*LEVEL81)
        assertEquals(0 * 8 + 5, MovePlanner.nextCat(b, sol81, CatOrder.ROWS, reverse = false))
        assertEquals(7 * 8 + 0, MovePlanner.nextCat(b, sol81, CatOrder.ROWS, reverse = true))
        assertEquals(7 * 8 + 0, MovePlanner.nextCat(b, sol81, CatOrder.COLUMNS, reverse = false)) // col 0 is row 7
    }
```
(import `com.ilan.meowsolver.core.CatOrder`).

In `AutoSolverTest.kt` change every `AutoSolver.plan(b, sol, Random(x))` to `AutoSolver.plan(b, sol, rnd = Random(x))` and add (import `CatOrder`):
```kotlin
    @Test fun catsOnlyPlanHasNoXTapsAndSolvesEveryFixture() {
        val failures = Fixtures.expected().mapNotNull { e ->
            val b = Fixtures.board(e.name)
            val sol = Solver.solve(b)!!
            val plan = AutoSolver.plan(b, sol, withX = false)
            val solCells = sol.mapIndexed { r, c -> r * b.n + c }.toSet()
            if (plan.any { b.cellOf(it) !in solCells }) return@mapNotNull "${e.name}: tapped a non-solution cell"
            val mid = b.after(plan)
            val c = AutoSolver.check(mid, Solver.solve(mid))
            if (MovePlanner.remaining(b, sol).isEmpty()) return@mapNotNull null
            if (c !is Check.LastOnly) return@mapNotNull "${e.name}: after plan $c"
            val fin = mid.after(AutoSolver.lastCatTaps(mid, c.cell))
            rulesProblem(fin)?.let { "${e.name}: $it" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun rowsPlanGoesTopToBottomAndHoldsBackTheBottomCat() {
        val b = boardOf(*LEVEL81)
        val sol = Solver.solve(b)!!
        val cats = AutoSolver.plan(b, sol, CatOrder.ROWS, reverse = false).filter { it.double }.map { b.cellOf(it) / 8 }
        assertEquals((0 until 7).toList(), cats)
        val up = AutoSolver.plan(b, sol, CatOrder.ROWS, reverse = true).filter { it.double }.map { b.cellOf(it) / 8 }
        assertEquals((7 downTo 1).toList(), up)
    }
```

- [ ] **Step 2: Run — expect compile FAIL**

`gradle testDebugUnitTest --no-daemon -q --tests '*OrderTest*' --tests '*OverlaySettingsTest*' --tests '*AutoSolverTest*' --tests '*MovePlannerTest*'` → unresolved `CatOrder`/`Order`/`OverlaySettings`.

- [ ] **Step 3: `core/CatOrder.kt`**
```kotlin
package com.ilan.meowsolver.core

import kotlin.random.Random

/** Order Hint, Cat and Answer take the remaining cats in. */
enum class CatOrder(val label: String) { RANDOM("Random"), ROWS("Rows"), COLUMNS("Columns") }

object Order {
    /** Remaining solution cells in [order]; [reverse] flips rows/columns and is ignored for RANDOM. */
    fun cells(b: Board, sol: IntArray, order: CatOrder, reverse: Boolean, rnd: Random = Random): List<Int> {
        val left = MovePlanner.remaining(b, sol)
        val sorted = when (order) {
            CatOrder.RANDOM -> return left.shuffled(rnd)
            CatOrder.ROWS -> left.sortedBy { it / b.n }
            CatOrder.COLUMNS -> left.sortedBy { it % b.n }
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun label(order: CatOrder, reverse: Boolean) = when (order) {
        CatOrder.RANDOM -> "Random"
        CatOrder.ROWS -> if (reverse) "Bottom → top" else "Top → bottom"
        CatOrder.COLUMNS -> if (reverse) "Right → left" else "Left → right"
    }
}
```

- [ ] **Step 4: `core/OverlaySettings.kt`**
```kotlin
package com.ilan.meowsolver.core

import android.content.SharedPreferences

enum class PanelButton(val label: String) { REVEAL("Reveal"), HINT("Hint"), CAT("Cat"), X("X"), ANSWER("Answer") }

/** User choices from the setup screen that shape the overlay. */
data class OverlaySettings(
    val buttons: Set<PanelButton> = PanelButton.entries.toSet(),
    val order: CatOrder = CatOrder.RANDOM,
    val reverse: Boolean = false,
    val answerX: Boolean = true,
) {
    /** Turns a button on/off; the last visible button can't be turned off. */
    fun withButton(btn: PanelButton, on: Boolean): OverlaySettings {
        val next = if (on) buttons + btn else buttons - btn
        return if (next.isEmpty()) this else copy(buttons = next)
    }

    val orderLabel: String get() = Order.label(order, reverse)

    fun encodeButtons() = PanelButton.entries.filter { it in buttons }.joinToString(",") { it.name }

    companion object {
        const val K_BUTTONS = "panelButtons"
        const val K_ORDER = "catOrder"
        const val K_REVERSE = "orderReverse"
        const val K_ANSWER_X = "answerX"

        fun decode(buttons: String?, order: String?, reverse: Boolean, answerX: Boolean): OverlaySettings {
            val set = buttons.orEmpty().split(",").mapNotNull { n -> PanelButton.entries.firstOrNull { it.name == n } }.toSet()
            return OverlaySettings(
                buttons = set.ifEmpty { PanelButton.entries.toSet() },
                order = CatOrder.entries.firstOrNull { it.name == order } ?: CatOrder.RANDOM,
                reverse = reverse,
                answerX = answerX,
            )
        }
    }
}

fun SharedPreferences.overlaySettings() = OverlaySettings.decode(
    getString(OverlaySettings.K_BUTTONS, null), getString(OverlaySettings.K_ORDER, null),
    getBoolean(OverlaySettings.K_REVERSE, false), getBoolean(OverlaySettings.K_ANSWER_X, true),
)

fun SharedPreferences.Editor.putOverlaySettings(s: OverlaySettings): SharedPreferences.Editor =
    putString(OverlaySettings.K_BUTTONS, s.encodeButtons()).putString(OverlaySettings.K_ORDER, s.order.name)
        .putBoolean(OverlaySettings.K_REVERSE, s.reverse).putBoolean(OverlaySettings.K_ANSWER_X, s.answerX)
```

- [ ] **Step 5: `MovePlanner.nextCat`** — replace the current line with:
```kotlin
    fun nextCat(b: Board, sol: IntArray, order: CatOrder = CatOrder.RANDOM, reverse: Boolean = false, rnd: Random = Random): Int? =
        Order.cells(b, sol, order, reverse, rnd).firstOrNull()
```

- [ ] **Step 6: `AutoSolver.plan`** — new signature and order source; X loop skipped when `!withX`:
```kotlin
    fun plan(
        b: Board, sol: IntArray,
        order: CatOrder = CatOrder.RANDOM, reverse: Boolean = false, withX: Boolean = true, rnd: Random = Random,
    ): List<Tap> {
        val todo = Order.cells(b, sol, order, reverse, rnd)
        if (todo.size <= 1) return emptyList()
        val state = b.state.copyOf()
        val out = mutableListOf<Tap>()
        for (cell in todo.dropLast(1)) {
            // Let the game's double-tap window close before and after each cat, so taps on different cells never merge.
            out.lastOrNull()?.let { out[out.lastIndex] = it.copy(pauseAfterMs = MovePlanner.CLEAR_PAUSE_MS) }
            val cat = MovePlanner.catTaps(b, cell, state[cell])
            out += cat.dropLast(1) + cat.last().copy(pauseAfterMs = MovePlanner.CLEAR_PAUSE_MS)
            state[cell] = Cell.CAT
            if (!withX) continue
            for (i in MovePlanner.xTargetsFor(b, state, cell)) {
                out += Tap(b.cx(i % b.n), b.cy(i / b.n), false, MovePlanner.GAP_MS)
                state[i] = Cell.X
            }
        }
        return out
    }
```
Update the KDoc: "in the user's cat order (random by default); the last one is held back".

- [ ] **Step 7: Run** — `gradle testDebugUnitTest --no-daemon -q` → all pass (previous 42 + new). Fix any test call sites the compiler flags (`AlignmentRenderTest` uses `nextCat(b, sol)` — unchanged signature works).

- [ ] **Step 8: Commit** `feat(core): cat order, overlay settings, cats-only Answer plan`.

---

### Task 2: Settings UI + live overlay

**Files:**
- Modify: `src/main/java/com/ilan/meowsolver/ui/PanelCard.kt`, `src/main/java/com/ilan/meowsolver/ui/SetupScreen.kt`, `src/main/java/com/ilan/meowsolver/MainActivity.kt`, `src/main/java/com/ilan/meowsolver/service/OverlayService.kt`, `src/test/java/com/ilan/meowsolver/UiSnapshotTest.kt`; snapshots in `src/test/snapshots/images/`

**Interfaces:**
- Consumes (Task 1): `PanelButton`, `CatOrder`, `OverlaySettings` (+`withButton`, `orderLabel`), `SharedPreferences.overlaySettings()`, `Editor.putOverlaySettings(s)`, `MovePlanner.nextCat(b, sol, order, reverse)`, `AutoSolver.plan(b, sol, order, reverse, withX)`.
- Produces: `PanelState(..., buttons: Set<PanelButton> = PanelButton.entries.toSet())`; `SetupState(..., overlay: OverlaySettings = OverlaySettings())`; `SetupScreen(state, dark, onStart, onAppInfo, onAccessibility, onDoubleTapMs, onThemeMode, onOverlay: (OverlaySettings) -> Unit)`.

- [ ] **Step 1: Snapshot tests first** — in `UiSnapshotTest.kt`:
  - every `SetupScreen(...)` call gets one more trailing `{}`.
  - `setupOn` passes `overlay = OverlaySettings(order = CatOrder.ROWS, reverse = true, answerX = false, buttons = PanelButton.entries.toSet() - PanelButton.X)` inside its `SetupState`.
  - add `@Test fun panelSomeHidden() = paparazzi.snapshot { OnGame { Panel(PanelState("Ready", busy = false, revealOn = false, buttons = setOf(PanelButton.HINT, PanelButton.CAT, PanelButton.ANSWER))) } }`
  - raise `tall()` `screenHeight` so the whole setup screen fits (start with 4200; check the PNG bottom shows the Tuning card).
  Run compile → FAIL (unknown params).

- [ ] **Step 2: `PanelCard.kt`**
  - `data class PanelState(val status: String, val busy: Boolean, val revealOn: Boolean, val autoRunning: Boolean = false, val buttons: Set<PanelButton> = PanelButton.entries.toSet())` (import `com.ilan.meowsolver.core.PanelButton`).
  - Button row: `horizontalArrangement = Arrangement.SpaceEvenly`; wrap each `ActionButton` in `if (PanelButton.X in state.buttons)` etc. (REVEAL, HINT, CAT, X, ANSWER; the Answer/Stop if-else sits inside `if (PanelButton.ANSWER in state.buttons)`).

- [ ] **Step 3: `SetupScreen.kt`**
  - `data class SetupState(val serviceOn: Boolean, val doubleTapMs: Int, val themeMode: ThemeMode = ThemeMode.SYSTEM, val overlay: OverlaySettings = OverlaySettings())`.
  - `SetupScreen` gets a last parameter `onOverlay: (OverlaySettings) -> Unit`.
  - Add private helpers (imports: `androidx.compose.material3.Switch`, `androidx.compose.material3.SwitchDefaults`, `androidx.compose.ui.draw.alpha`, core `CatOrder`, `OverlaySettings`, `PanelButton`):
```kotlin
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalSetupColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Meow.Clay else Color.Transparent)
                    .border(1.dp, if (sel) Meow.Clay else c.ink.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = label) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (sel) Color.White else c.ink, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = LocalSetupColors.current
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp)
            if (detail != null) Text(detail, color = c.inkMuted, fontSize = 13.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Meow.Clay, checkedBorderColor = Meow.Clay,
                uncheckedThumbColor = c.inkMuted, uncheckedTrackColor = c.ink.copy(alpha = 0.12f), uncheckedBorderColor = c.inkMuted,
                disabledCheckedThumbColor = Color.White, disabledCheckedTrackColor = Meow.Clay, disabledCheckedBorderColor = Meow.Clay,
                disabledUncheckedThumbColor = c.inkMuted, disabledUncheckedTrackColor = c.ink.copy(alpha = 0.12f), disabledUncheckedBorderColor = c.inkMuted,
            ),
        )
    }
}
```
  - Replace the Appearance picker body with `Segmented(ThemeMode.entries.map { it.label }, state.themeMode.ordinal) { onThemeMode(ThemeMode.entries[it]) }`.
  - Insert after the "How it works" card, before "Appearance":
```kotlin
            val o = state.overlay
            SectionTitle("Overlay buttons")
            Card {
                for (btn in PanelButton.entries) {
                    val on = btn in o.buttons
                    SwitchRow(btn.label, null, on, enabled = !(on && o.buttons.size == 1)) { onOverlay(o.withButton(btn, it)) }
                }
            }

            SectionTitle("Cat order")
            Card {
                Segmented(CatOrder.entries.map { it.label }, o.order.ordinal) { onOverlay(o.copy(order = CatOrder.entries[it])) }
                SwitchRow(
                    "Reverse", if (o.order == CatOrder.RANDOM) "Not used with Random" else null, o.reverse,
                    enabled = o.order != CatOrder.RANDOM,
                ) { onOverlay(o.copy(reverse = it)) }
                Text("Order: ${o.orderLabel} — used by Hint, Cat and Answer", color = c.inkMuted, fontSize = 13.sp)
            }

            SectionTitle("Answer places")
            Card {
                Segmented(listOf("Cats + X's", "Cats only"), if (o.answerX) 0 else 1) { onOverlay(o.copy(answerX = it == 0)) }
            }
```

- [ ] **Step 4: `MainActivity.kt`**
  - field `private var overlay by mutableStateOf(OverlaySettings())`; in `onCreate` after `themeMode = …`: `overlay = prefs.overlaySettings()`.
  - `SetupState(serviceOn, doubleTapMs, themeMode, overlay)`; new arg `onOverlay = { overlay = it; prefs.edit().putOverlaySettings(it).apply() }`.

- [ ] **Step 5: `OverlayService.kt`**
  - Fields:
```kotlin
    private var settings = OverlaySettings()
    // Held as a field: SharedPreferences keeps listeners weakly.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applySettings() }
```
  - In `onServiceConnected`, after `prefs = …` and the revealOn line: `prefs.registerOnSharedPreferenceChangeListener(prefListener); applySettings()`.
  - In `onDestroy` and `onUnbind` (before super): `if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)`.
  - New:
```kotlin
    private fun applySettings() {
        settings = prefs.overlaySettings()
        panel = panel.copy(buttons = settings.buttons)
        if (PanelButton.REVEAL !in settings.buttons && panel.revealOn) {
            panel = panel.copy(revealOn = false)
            marks = marks.copy(rings = emptyList())
            prefs.edit().putBoolean(K_REVEAL, false).apply()
        }
        if (PanelButton.ANSWER !in settings.buttons && panel.autoRunning) stopAnswer()
    }
```
  - `hint()`: `MovePlanner.nextCat(b, sol, settings.order, settings.reverse)`.
  - `placeCat()`: `val cell = marks.hint?.takeIf { it in MovePlanner.remaining(b, sol) } ?: MovePlanner.nextCat(b, sol, settings.order, settings.reverse) ?: return@withBoard done("Solved")` (replaces the `left`/`randomOrNull` lines; keep the comment).
  - `answerStep` Missing branch: `AutoSolver.plan(b, sol!!, settings.order, settings.reverse, settings.answerX)`.
  - Imports: `android.content.SharedPreferences` (if not present), core `OverlaySettings`, `PanelButton`, `overlaySettings`.

- [ ] **Step 6: Build, record, test** — `gradle assembleDebug recordPaparazziDebug testDebugUnitTest --no-daemon -q` → exit 0.

- [ ] **Step 7: Visual check** — Read `panelSomeHidden`, `panelIdle`, `setupOn`, `setupDark` PNGs: 3 buttons evenly spaced; 5-button panel unchanged in look; setup shows the three new sections, disabled Reverse looks greyed in `setupDark` (Random), Rows + Reverse on + "Order: Bottom → top …" + "Cats only" selected + X switch off in `setupOn`; whole screen fits including Tuning.

- [ ] **Step 8: Commit** `feat(app): overlay button, cat order and Answer X settings` with the changed files + snapshots.

---

### Task 3: Final review, verify, deliver
Whole-branch review of Tasks 1–2; fix Critical/Important; `gradle clean assembleRelease testDebugUnitTest`; `apksigner verify`; send APK + setup/panel snapshots.
