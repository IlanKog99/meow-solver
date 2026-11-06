# Meow Solver v2.1 — Reveal + auto-Answer

Date: 2026-11-06 · Owner: Ilan · Status: awaiting approval · Builds on: `2026-11-06-meow-solver-v2-design.md`

## Why
Every button already re-reads the board, so **Scan** adds nothing. Ilan wants the old Answer behaviour under a clearer name, and a button that actually solves the level.

## Changes
1. **Scan removed.** Its grid-flash code goes too (`Marks.flash`, `FLASH_MS`, flash drawing) — nothing else uses it.
2. **Reveal** = today's Answer toggle, unchanged behaviour: eye icon, on/off, rings on every remaining solution cell, refreshed after each action, persisted. Pref key stays `"answerOn"` so the saved setting carries over. Status when turned on: `"Showing N cats"` / `"Solved"`.
3. **Answer** = auto-solve (new original fast-forward icon, label `"Answer"`).

Card order: **Reveal · Hint · Cat · X · Answer**. Cat stays the filled clay tile.

## Answer flow
1. Read board (existing capture path), solve. No solution → stop `"No solution — a placed cat is wrong"`. Already solved → `"Solved"`.
2. **Bulk plan**: every solution cell without a cat, top to bottom, **except the last one**. For each: cat taps (empty → double-tap; X → single tap, 350 ms, double-tap), then single taps on the cells this cat rules out (same row, column, 8 neighbours, same colour) that are EMPTY **on a virtual copy of the board** updated as the plan is built — so no cell is ever tapped twice (a second tap would clear an X).
3. Run the taps. Wait 400 ms (`AUTO_SETTLE_MS`) for the game to animate.
4. **Re-read** and check:
   - Solved → `"Solved"`.
   - Only the last solution cell missing → place it (cat taps only, **no X after it**) → `"Solved"`.
   - Earlier cells missing (a tap didn't register) → **fix round**: plan again from this board (same rules, still excluding the last cell), run, wait, re-read. Max **3** fix rounds, then stop `"Couldn't place all cats — tap Answer again"`.
   - No solution (a cat sits in a wrong cell) → stop `"No solution — a placed cat is wrong"`. Cats are never removed.
5. Status while running: `"Placing cats… P/N"` (P = cats on board at last read, N = board size).

**Stop**: while running, Answer shows a stop-square icon labelled `"Stop"` and stays enabled; tapping it cancels at once (no further taps) → `"Stopped"`. Other buttons are dimmed and ignored. Close also cancels. Cancel uses the existing session counter.

Reveal rings, if on, are refreshed from every read during the run.

## Units
| Unit | Job |
|---|---|
| `MovePlanner.xTargetsFor(b, state, cat)` | EMPTY cells (in `state`) ruled out by one cat. `xTargets(b)` reuses it. |
| `core/AutoSolver` (new, pure) | `plan(b, sol): List<Tap>` — bulk plan per step 2. `check(b, sol?): Check` → `Solved`, `LastOnly(cell)`, `Missing(cells)`, `NoSolution`. `lastCatTaps(b, cell)`. |
| `OverlayService` | Answer runner state machine (read → plan → taps → settle → read → check → last / fix / stop), Stop, Reveal toggle. |
| `ui/PanelCard` | New order, `PanelState(status, busy, revealOn, autoRunning)`; Answer tile shows Stop when `autoRunning`. |
| `ui/MeowIcons` | `FastForward`, `Stop` (original line icons). |

## Tests
1. `TapSim` (test helper): applies taps to a board's cell states with game rules (single: EMPTY↔X, double on EMPTY → CAT).
2. `AutoSolverTest`:
   - On all 41 fixtures with a solution: `plan` → simulate → `check` = `LastOnly`; place last → every row, column, colour has one cat, no two touch, `check` = `Solved`.
   - No cell appears in two single taps of one plan; the last solution cell is never tapped.
   - Missed tap: drop one double-tap → `check` = `Missing` → re-plan → simulate → `LastOnly`.
   - Wrong cat on board → `NoSolution`; solved board → `Solved`.
3. `MovePlannerTest`: `xTargetsFor` and unchanged `xTargets` behaviour.
4. Paparazzi: panel idle (new order), Reveal on, running (Stop tile), bubble, setup screens.
5. Per-task reviews + final whole-branch review; release APK.

## Out of scope
Removing wrong cats; X after the last cat; speed tuning beyond the existing double-tap slider.

## Addendum: app dark mode (approved 2026-11-07)
- Setup screen gets an **Appearance** section: 3-way picker **System · Light · Dark**, default System, saved in prefs key `"themeMode"` (values `SYSTEM`/`LIGHT`/`DARK`; unknown → System).
- Dark palette: background `#1C1714`, cards `#2A2420`, text `#F3ECE6`, secondary text `#B3A59B`; clay `#D97757` unchanged. Light palette = today's.
- Status-bar / nav-bar icons follow the resolved theme (light icons on dark).
- Overlay card unchanged (already dark).
- Tests: `ThemeMode` mapping unit test; Paparazzi `setupDark` snapshot alongside the light ones.

## Addendum: random order (approved 2026-11-07)
- **Hint** shows a random remaining solution cell (not top-to-bottom).
- **Cat** places the hinted cell while its 5 s hint is still showing and that cell still needs a cat; otherwise a random remaining cell.
- **Answer** plans cats in shuffled order; the cat left for the final check is whichever comes last in the shuffle. Each fix round reshuffles.
- `MovePlanner.nextCat(b, sol, rnd: Random = Random)` and `AutoSolver.plan(b, sol, rnd: Random = Random)`; tests pass seeded `Random`.
- Tests: nextCat returns a remaining cell and varies across seeds; plan leaves exactly one remaining cell untouched; plan order varies across seeds.
