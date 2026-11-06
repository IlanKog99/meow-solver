# Meow Solver v2.3 — overlay settings

Date: 2026-11-07 · Owner: Ilan · Status: approved · Builds on v2.1/v2.2 specs

## Setup-screen sections (after "How it works")
1. **Overlay buttons** — switch per button: Reveal, Hint, Cat, X, Answer. Default all on. The last switch that's on is disabled (can't turn it off). Panel shows only enabled buttons (evenly spaced); Minimize + Close always shown.
2. **Cat order** — one setting shared by Hint, Cat, Answer. Segmented `Random · Rows · Columns` (default Random) + **Reverse** switch (greyed, "Not used with Random", when Random). Line: `Order: <label> — used by Hint, Cat and Answer`, label ∈ `Random`, `Top → bottom`, `Bottom → top`, `Left → right`, `Right → left`.
3. **Answer places** — segmented `Cats + X's` (default) · `Cats only`. Cats only = Answer taps cats only, no X taps.

## Behaviour
- Hint: first cell of the order. Cat: hinted cell if hint still showing and still needed, else first of the order. Answer: plan in that order, last one held back for the final check; Random reshuffles each round.
- Prefs: `panelButtons` (comma list of `REVEAL,HINT,CAT,X,ANSWER`; empty/invalid → all), `catOrder` (`RANDOM|ROWS|COLUMNS`, unknown → RANDOM), `orderReverse` (false), `answerX` (true).
- Overlay listens to pref changes and updates live. Hiding Reveal while on turns Reveal off (rings cleared). Hiding Answer during a run stops it.

## Tests
`OrderTest` (5 labels, rows/cols ± reverse, random is a permutation), `OverlaySettingsTest` (decode defaults/garbage, encode round-trip, last button can't be removed), `AutoSolverTest` (cats-only plan has no X taps and solves all fixtures; ROWS plan is top→bottom), Paparazzi `panelSomeHidden`, updated setup snapshots. Task reviews + final review; release APK.
