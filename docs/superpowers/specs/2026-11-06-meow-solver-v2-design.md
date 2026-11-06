# Meow Solver v2 — Design Spec

Date: 2026-11-06 · Owner: Ilan · Status: awaiting approval

## Goal
Android overlay that sits on top of Meowdoku, reads the board from the screen, and helps step by step: scan, hint, place one cat, mark X's. v1 worked but had no close/hide, mis-tapped cats, ugly UI, auto rings, and no tests.

## Game facts (from Ilan)
- Rules: one cat per row, column, and color; cats can't touch (8 neighbours).
- Single tap on an empty cell = X. Single tap on an X = clears it.
- Fast double-tap on an empty cell = cat.
- X → cat = single tap (clear), pause, then double-tap.
- Board sizes seen: 8×8, 9×9, 10×10.

## Scope
In:
1. Overlay card with 5 actions: Scan, Hint, Cat, X, Answer (toggle).
2. Minimize to a bubble; close (hide everything).
3. App screen: Start overlay, setup checklist, how-to, double-tap timing knob.
4. Warm cozy theme, original vector icons.
5. Automated tests + GUI screenshots + independent code review.

Out (YAGNI):
- Solve-all button.
- Pointing at a wrong cat (just say "No solution").
- Quick-settings tile.
- Android 10 and older (needs Android 11+ for screenshots).

## Architecture
Kotlin, Jetpack Compose, minSdk 30, single module, one accessibility service (no other permissions).

| Unit | Job | Depends on |
|---|---|---|
| `BoardReader` | Pixels (IntArray ARGB, w, h) → `Board` (n, cell bounds, region per cell, state per cell). Port of verified v1 algorithm. | nothing |
| `Solver` | `Board` → `IntArray?` (column per row), honouring cats already on the board. | `Board` |
| `MovePlanner` | `Board` + solution → next cat cell, hint cell, X target cells, and a `List<Tap>` plan. | `Board` |
| `TapRunner` | Plays a `List<Tap>` through `dispatchGesture`, one at a time; stops on cancel. | Android |
| `OverlayService` | AccessibilityService: hosts overlay windows, takes screenshots, wires buttons to flows. | all above |
| `OverlayUi` | Compose: `PanelCard`, `Bubble`, `MarksLayer` (rings, hint pulse, grid flash). Pure UI, takes state + callbacks. | Compose |
| `MainActivity` | Compose setup screen. | Compose |

`Tap` = `{x, y, kind: SINGLE | DOUBLE, pauseAfterMs}`. DOUBLE = two 40 ms strokes 90 ms apart (configurable 60–200 ms).

## Flows
Every action: hide overlay → wait 150 ms → `takeScreenshot` (retry once after 400 ms on failure) → show overlay → `BoardReader.read` → `Solver.solve` → act.

- **Scan:** status `"{n}×{n} · {cats} cats · solvable"` or `"… · no solution"`. MarksLayer flashes an outline of every detected cell for 2 s.
- **Hint:** status `"Next cat: row R, col C"`. MarksLayer shows a pulsing cat icon on that cell for 5 s. No taps.
- **Cat:** taps the next solution cell (first row, top to bottom, whose solution cell has no cat). Empty → DOUBLE. X → SINGLE, 350 ms pause, DOUBLE. Status `"Cat placed: row R, col C"`.
- **X:** for every cat on the board, targets = same row ∪ same column ∪ 8 neighbours ∪ same region, only cells that are currently EMPTY, deduplicated. Each gets SINGLE with 90 ms pause. Status `"Marked N cells"` or `"Nothing to mark"`.
- **Answer (toggle):** when on, MarksLayer draws rings on every solution cell without a cat, refreshed after each action. Default off, persisted.
- **Minimize:** card → 52 dp bubble (clay circle, cat icon), draggable, snaps to nearest side edge. Tap → card.
- **Close:** removes card, bubble, and marks. Service stays enabled. App "Start overlay" re-shows the card (broadcast to the running service). If the service isn't enabled, Start opens Accessibility settings.
- Busy rule: while a screenshot or tap sequence runs, buttons show a busy state and presses are ignored.

## UI
- Theme: cream `#FBF5EF` background, clay accent `#D97757`, card dark brown `#2A2420` at 94% opacity, text white / 70% white.
- Card: header row = status text (drag handle) + minimize + close icons. Body = 5 columns, each icon (22 dp) over a label (11 sp). Answer button shows on/off state. Corner radius 16 dp.
- Icons: hand-drawn original vector paths (cat face outline, X, scan frame, bulb, eye). No game art.
- App screen: title, big clay "Start overlay" button, checklist with live ticks (restricted settings allowed, accessibility on), 4-line how-to, "Double-tap speed" slider (60–200 ms, default 90).

## Errors
| Case | Message |
|---|---|
| Board not found / unreadable | "Open a Meowdoku puzzle first" |
| Screenshot fails twice | "Couldn't read the screen. Try again" |
| No solution | "No solution — a placed cat is wrong" |
| Gesture cancelled | "Stopped" (remaining taps dropped) |
| All cats placed | "Solved" |

## Testing
1. `BoardReaderTest`: all 41 user screenshots as fixtures; assert grid size, cat count, and that a solution exists; spot-check exact solutions for levels 32, 81, 83 against hand-solved answers.
2. `SolverTest`: solvable, unsolvable, pre-placed cats honoured, adjacency rule.
3. `MovePlannerTest`: tap plan per cell state (empty→DOUBLE, X→SINGLE+pause+DOUBLE, cat→none), X targets incl. dedupe and skipping X/cat cells, next-cat order.
4. Alignment render: draw rings / hint / grid flash onto real screenshots (JVM), send images to Ilan.
5. Paparazzi screenshots: PanelCard (idle, busy, answer on), Bubble, MainActivity screen.
6. Independent code review agent; fix findings.
7. Manual on-device test by Ilan (only untestable part: real taps).
