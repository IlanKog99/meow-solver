# Task 7 report
Status: DONE. Brief code used verbatim; compiled first try, zero deviations.

## Build/test
`export ANDROID_HOME=/root/android-sdk; gradle assembleDebug testDebugUnitTest --no-daemon -q` -> exit 0 (only SDK-XML-version warning). APK: build/outputs/apk/debug/MeowSolver-debug.apk (22.7 MB).
Tests (summed from build/test-results XML): 25 tests, 0 failures, 0 errors.

## aapt
    A: android:canPerformGestures(0x0101050d)=(type 0x12)0xffffffff
    A: android:canTakeScreenshot(0x0101060f)=(type 0x12)0xffffffff

## Deviations
None. (Brief's colors.xml block was written to res/values/colors.xml as instructed; strings.xml as given.)
Step 9 said `git add -A`; used specific paths per task instructions.

## Self-review (lifecycle/threading)
1. OverlayOwner.stop() sets DESTROYED, which is terminal. If the same service instance gets onServiceConnected again after onUnbind (rare; system normally creates a new instance), owner.start() would throw IllegalStateException and performRestore would run twice. Fix if seen: create a fresh OverlayOwner in onServiceConnected.
2. All callbacks (handler, takeScreenshot via mainExecutor, gesture callback with null handler = main thread) run on main; no cross-thread state.
3. Close during a pending capture/tap: views null so setOverlayVisible/setPanelTouchable no-op; action still updates marks/panel state (marks could reappear invisible until next showOverlay, then be shown). Minor.
4. If the service is unbound mid-tap-sequence, TapRunner keeps posting on the handler; dispatchGesture would fail -> onDone(false) -> setPanelTouchable no-op. Harmless.
5. Panel set NOT_TOUCHABLE during taps and restored after both success and cancel paths; dispatchGesture false path also restores via onDone(false).
6. Unverified without device: screenshot excluding overlay timing (150 ms), TYPE_ACCESSIBILITY_OVERLAY insets with LAYOUT_NO_LIMITS alignment of MarksLayer vs screenshot coords.

## Files
Created: service/OverlayOwner.kt, TapRunner.kt, OverlayService.kt; res/xml/accessibility.xml, res/values/strings.xml, res/values/colors.xml, res/drawable/ic_launcher_fg.xml, res/mipmap-anydpi-v26/ic_launcher.xml.
Modified: MainActivity.kt, AndroidManifest.xml.

## Fix (review findings)
1. Crash safety: OverlayService.capture/handleBoard — decode in try/catch(Exception, OOM); BoardNotFound branch first; other failures -> "Couldn't read the screen. Try again", panel touchable restored, busy cleared, overlay re-shown.
2. Cancel on close: `session` counter bumped in close()/onUnbind()/onDestroy(); withBoard delay, retry, screenshot callback and runTaps result drop when stale; TapRunner.run(cancelled) checked before every step -> onDone(false).
3. Rings: handleBoard (single place) sets marks.board and, when answerOn, rings = remaining(b, sol) or empty; X solves only when answerOn.
4. Drag: pointerInteropFilter + rawX/rawY (dragTouch); touch-slop click for bubble.
5. settle() after minimize/expand snaps bubble / clamps; saved position clamped in showOverlay and endDrag.
6. BoardReader: n > 12 -> BoardNotFound; test tooManyRowsThrows (14 stripes).
7. hw bitmap recycled after copy.
8. Fresh OverlayOwner in onServiceConnected; instance nulled in onDestroy.
9. accessibility.xml left at typeWindowStateChanged (no "none" enum value; omitting attribute untested on device).
Verify: gradle assembleDebug testDebugUnitTest -> 26 tests, 0 failures; aapt shows canPerformGestures and canTakeScreenshot 0xffffffff.
