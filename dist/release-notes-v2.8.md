Fix: no more "mistake, then fix" taps.

- The check after marking X's (X button and Answer) used to act on a single screenshot. If it caught an X or cat still animating in, it tapped that cell to "fix" it, which actually undid it, then tapped it again next round. Now it only fixes a cell when two reads in a row agree.
- Answer no longer clears X's just to re-mark them when a cat tap was missed. X's off the solution are left alone; an X on a solution cell is cleared by that cat's own taps.
- Added a pause before the first cat after a fix, so the fix tap and the cat's double-tap can't merge.

Install: download **MeowSolver-v2.8.apk** below. Same signing key as v2.7, so it installs as an update.
