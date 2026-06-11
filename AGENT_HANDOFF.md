# DigiviceGlyph Root Handoff

Last updated: 2026-06-11

## Project state

- Project root: `C:\Users\uzuik\AndroidStudioProjects\DigiviceGlyph`
- Decompiled source workspace: `C:\Users\uzuik\Documents\VRmakes\Projects\decomp digivice1`
- Device used for verification: Nothing Phone 3, model `A024`
- Build status: `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug` both pass
- Latest verified APK:
  - `app\build\outputs\apk\debug\app-debug.apk`

This app is already beyond the original scaffold stage. It now contains:

- a real `DigiviceV1Runtime`
- bundled recovered GameMaker sprites and audio
- shared runtime between the activity and the Glyph toy service
- in-app Digivice phone rendering
- rear Glyph Matrix rendering
- source-driven map, battle, and progression work in progress
- working `SLOT` and `CARD` minigame runtime slices wired into the real menu flow

## Current control contract

This is the control model that should be preserved:

- Glyph Button hold or long press -> A
- flick right -> B
- flick left -> C
- vertical flick -> one walking step

Idle behavior is now aligned back toward the original GameMaker flow:

- from idle, `B` opens the main menu
- from idle, `C` opens the stats pages directly
- walking is only triggered by vertical motion or the app-only Auto Walk toggle
- Auto Walk is no longer reachable through Digivice gameplay input

Current implementation points:

- `app/src/main/java/com/digimon/digiviceglyph/DigiviceGlyphToyService.kt`
- `app/src/main/java/com/digimon/digiviceglyph/input/GlyphInputController.kt`
- `app/src/main/java/com/digimon/digiviceglyph/input/FlickGestureDetector.kt`
- `app/src/test/java/com/digimon/digiviceglyph/input/FlickGestureDetectorTest.kt`

## Minigame status

The `SLOT` and `CARD` entries now do real gameplay instead of stubs.

### Menu flow

- idle `B` -> six-item main menu
- main menu `SLOT` -> slot minigame
- main menu `CARD` -> four-round card minigame
- idle `C` remains the separate `DISTANCE / STEPS / D-POWER / win-rate` pager

### Slot game

- `A` and `B` both follow the original GameMaker behavior:
  - first press starts both reels
  - second press stops reel 1
  - third press stops reel 2
- `C` exits back to the main menu on `SLOT`
- after the reel result wait, the game opens a distance-result screen
- positive result reduces current-area distance
- negative result increases current-area distance

### Card game

- four rounds are played before the final payout
- during the choice window:
  - `A` marks the left card
  - `B` marks the right card
  - `C` exits back to the main menu on `CARD`
- safe Digimon should be left alone
- enemy cards should be pressed
- total reward is applied to current-area distance after the fourth round result screen

### Benefit wiring

Both minigames affect the same current area distance meter used by walking and encounters:

- slot reward path: `distance = distance - resultDistance`
- card reward path: `distance = distance - (totalPointUnits * 25)`

The implementation updates:

- `state.distance`
- `state.perAreaDistances[state.area]`
- `state.lastEncounter`

So the games now genuinely help or hurt map progression, which matches the ripped GameMaker logic.

### Files for this pass

- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigiviceV1Runtime.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/ExactPhoneRenderer.kt`
- `app/src/main/assets/sprites/spr_card_digivice_v1/`
- `app/src/test/java/com/digimon/digiviceglyph/runtime/DigiviceV1MiniGameLogicTest.kt`

## Verified Glyph SDK findings

These points are confirmed from:

- the Nothing SDK bundled in `app/libs/glyph-matrix-sdk-1.0.aar`
- app logs on device
- decompiled `NtThirdParty.apk` firmware behavior

### Input ownership

- `GlyphToy` button events arrive through the toy service `Messenger`.
- They are not standard Android `KeyEvent`s.
- The always-on service can keep the runtime active, but it cannot receive Glyph Button events while this toy is not the selected toy.

### Short press limitation

- A normal short press on the Glyph Button is reserved by Nothing OS for cycling the toy carousel.
- App-usable input is limited to:
  - `EVENT_CHANGE` when `com.nothing.glyph.toy.longpress=1`
  - `EVENT_ACTION_DOWN` / `EVENT_ACTION_UP` while the button is held

That is why the manifest must keep:

- `com.nothing.glyph.toy.longpress=1`

### Rear matrix gating

- The global device switch below can silently block all rear output:
  - `adb shell settings get global led_effect_enable`
- If it returns `0`, the firmware drops matrix writes even when the app and SDK look healthy.

### SDK render path

- `GlyphMatrixManager.setMatrixFrame(...)` is the correct path currently used for rear output.
- Decompiled firmware showed that `LightsSession` is auto-created when needed.
- The real blocker during earlier failures was `led_effect_enable`, not a null session.

### Firmware notes

- Firmware logs showed matrix light ids `115` / `121` in normal paths and `123` for the app-matrix path.
- Successful runs showed non-zero `finalColors` values such as `2047`.

## Current local repo state

Committed history already includes major runtime, map, battle, and Glyph renderer work.

There are also current local changes on top of git history in:

- `app/AGENT_HANDOFF.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/digimon/digiviceglyph/DigiviceGlyphToyService.kt`
- `app/src/main/java/com/digimon/digiviceglyph/input/GlyphButtonSink.kt`
- `app/src/main/java/com/digimon/digiviceglyph/input/GlyphInputController.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigivicePreviewRuntime.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigiviceV1Runtime.kt`

Current untracked additions include:

- `app/src/main/java/com/digimon/digiviceglyph/input/FlickGestureDetector.kt`
- `app/src/test/java/com/digimon/digiviceglyph/input/FlickGestureDetectorTest.kt`
- `.qwen/skills/...`
- `app/src/main/java/com/digimon/digiviceglyph/input/findings.md`

Treat `.qwen/` and `input/findings.md` as scratch context, not as authoritative docs.

## Recommended verification flow

1. Confirm the master Glyph switch:

```text
adb shell settings get global led_effect_enable
```

2. Build:

```text
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

3. Install:

```text
adb -s 192.168.0.16:34287 install -r app\build\outputs\apk\debug\app-debug.apk
```

4. Start the standalone preview path:

```text
adb -s 192.168.0.16:34287 shell am start-foreground-service -a com.digimon.digiviceglyph.START_PREVIEW -n com.digimon.digiviceglyph/.DigiviceGlyphToyService
```

5. Inspect logs:

```text
adb -s 192.168.0.16:34287 logcat -d -s DigiviceGlyphToy:D GlyphInput:D DigiviceV1Runtime:D AndroidRuntime:E
adb -s 192.168.0.16:34287 logcat -d -s GlyphService:D
```

Success signals:

- `Glyph registration target=A024 authorized=true`
- `Motion sensor registered=true`
- non-zero rear-matrix payloads and non-zero firmware `finalColors`

## High-value docs

- Historical broader handoff:
  - `notes/agent-handoff-2026-06-10.md`
- Visual-fidelity work plan:
  - `notes/exact-visual-port-plan.md`
- App-module Glyph debugging handoff:
  - `app/AGENT_HANDOFF.md`

## Guardrails for the next agent

- Do not assume the always-on service and selected-toy mode have identical input privileges. They do not.
- Do not revert to the older coupled button latch logic that mixed physical A state with synthetic B/C flick taps.
- Do not treat normal short Glyph-button press as an available app control.
- Do not reintroduce gameplay-side autorun toggles on idle `C` or menu item `CTRL`; autorun is app-only.
- Do not debug rear rendering before checking `led_effect_enable`.
- Keep the phone renderer and rear Glyph renderer conceptually separate. Exact phone fidelity and readable rear output are different goals.
- If a screen looks logically correct but visually wrong, check sprite-to-state binding before rewriting input or state logic. The broken idle `C` pager was caused by using `spr_menu_stats_digivice_v1` instead of `spr_menu_stats`.
