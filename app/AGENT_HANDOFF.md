# DigiviceGlyph Handoff

Last updated: 2026-06-11

## Current state

- Project root: `C:\Users\uzuik\AndroidStudioProjects\DigiviceGlyph`
- App module root: `C:\Users\uzuik\AndroidStudioProjects\DigiviceGlyph\app`
- Device used for verification: Nothing Phone 3, model `A024`
- App builds successfully with `.\gradlew.bat assembleDebug`
- Current renderer is using a centered `25x25` output path with:
  - horizontal crop `32 -> 25` via `H_CROP_LEFT = 3`
  - vertical placement at the bottom of the matrix via `FIT_Y_OFFSET = 9`
  - inverted polarity already handled by mapping dark LCD pixels to lit Glyph pixels

## Verified findings

### 1. The main blocker was not the crop math

The renderer is producing non-empty centered frames. Current debug logs from `GlyphRenderer` show:

- lit pixels around `78-80`
- centered bounds around `x=5..20`
- payload bounds match source bounds

This means the app-side bitmap generation is not blank and the SDK payload is not empty.

### 2. The Nothing firmware was dropping frames because the master Glyph switch was off

The critical device setting was:

```text
adb shell settings get global led_effect_enable
```

It returned `0` during the failing runs.

After setting:

```text
adb shell settings put global led_effect_enable 1
```

the firmware started logging non-zero `finalColors` arrays with values like `2047`, which means frames were finally being written to hardware.

### 3. `session is null` in `GlyphService` was misleading

I pulled and decompiled the device firmware app `NtThirdParty.apk` and inspected `com.nothing.thirdparty.GlyphService`.

Important result:

- `setMatrixColors` and `setAppMatrixColors` both open a `LightsSession` automatically when missing
- the real gate for applying matrix frames is `led_effect_enable`
- so `session is null` alone is not the root cause

### 4. Firmware light ids

From the decompiled service:

- normal matrix output uses light `115` or `121` depending on firmware state
- app matrix path uses light `123`

In the successful run after enabling the master Glyph switch, firmware logs showed non-zero `finalColors` on light `115`.

## Files currently changed locally

These files are modified and not committed:

- `app/src/main/java/com/digimon/digiviceglyph/DigiviceGlyphToyService.kt`
- `app/src/main/java/com/digimon/digiviceglyph/GlyphAvailability.kt`
- `app/src/main/java/com/digimon/digiviceglyph/GlyphRenderer.kt`
- `app/src/main/java/com/digimon/digiviceglyph/MainActivity.kt`
- `app/src/main/res/values/strings.xml`

There is also unrelated untracked local noise:

- `.qwen/`

## What the current local code does

### `GlyphRenderer.kt`

- Uses double-buffered standalone `25x25` bitmaps
- Logs frame summaries every 60 frames
- Calls `GlyphMatrixManager.setMatrixFrame(...)`
- Does not contain the temporary reflection/session hack anymore

### `MainActivity.kt`

- Shows a clearer status message when the device is Nothing hardware but the master Glyph switch is disabled
- Warns with a toast before starting preview if `led_effect_enable == 0`

### `GlyphAvailability.kt`

- Adds `isMasterGlyphEnabled(context)` using:
  - `Settings.Global.getInt(..., "led_effect_enable", 1)`

## Recommended starting checklist for the next agent

Before debugging the rear Glyph again, verify these in this order:

1. Confirm the global Glyph switch is on:

```text
adb shell settings get global led_effect_enable
```

2. Start the app preview explicitly:

```text
adb shell am start-foreground-service -a com.digimon.digiviceglyph.START_PREVIEW -n com.digimon.digiviceglyph/.DigiviceGlyphToyService
```

3. Watch app logs:

```text
adb logcat -d -s DigiviceGlyphToy:D DigiviceGlyphRender:D AndroidRuntime:E *:S
```

4. Watch firmware logs:

```text
adb logcat -d -s GlyphService:D *:S
```

Success signal:

- `GlyphRenderer` reports non-zero lit pixels
- `GlyphService` logs `finalColors` with many `2047` values

Failure signal:

- `led_effect_enable=0`
- or `GlyphService` logs only all-zero `finalColors`

## Useful decompilation artifacts

The following temporary artifacts were used during debugging:

- Pulled firmware APK:
  - `%TEMP%\NtThirdParty.apk`
- Decompiled Java:
  - `%TEMP%\NtThirdParty-jadx\sources\com\nothing\thirdparty\GlyphService.java`

That decompiled `GlyphService.java` is worth re-checking if future debugging touches sessions, light ids, or firmware filtering.

## Input contract verified 2026-06-11

The authoritative control mapping is:

- Glyph Button hold or long press -> A
- flick right -> B
- flick left -> C
- vertical flick -> one walking step

Idle-screen behavior was corrected after a control regression:

- `B` from idle opens the main menu
- `C` from idle opens the stats pages directly
- `C` no longer toggles autorun
- menu item `CTRL` no longer toggles autorun
- Auto Walk remains available only through the app UI button

Nothing's Glyph Toy API does not deliver an ordinary short press to the selected
toy. A short press is reserved by Nothing OS for cycling the toy carousel.
Supported app events are:

- `EVENT_CHANGE` after enabling `com.nothing.glyph.toy.longpress=1`
- `EVENT_ACTION_DOWN` and `EVENT_ACTION_UP` while the Glyph Button is held

`DigiviceGlyphToyService` handles both forms. `GlyphInputController` combines
them into one deduplicated A-button state, so overlapping `change` and
down/up events cannot produce duplicate presses.

Glyph Button events arrive through the `Messenger` returned by the toy
service's `onBind()`. The standalone foreground service can keep gameplay and
motion sensors alive, but it cannot receive Glyph Button events while this toy
is not selected. This is an SDK/platform ownership rule, not an app bug.

Horizontal and vertical motion detection now lives in
`input/FlickGestureDetector.kt`. Do not restore the old A/B/C latch coupling or
advance flick cooldown timestamps on every sensor event. Unit coverage is in
`app/src/test/java/com/digimon/digiviceglyph/input/FlickGestureDetectorTest.kt`.

Primary SDK reference:

- https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit

## Suggested next work

- Verify visually on the phone whether the rear Glyph now matches the successful firmware log state.
- If the rear Glyph still looks wrong while firmware logs show non-zero `finalColors`, the next debugging target is no longer session/setup. It becomes:
  - physical mapping shape on NP3
  - light id selection differences between firmware states
  - possible mismatch between the 25x25 matrix logical grid and the visible hardware region
- If visual output is present but alignment is still off, adjust only the LCD-to-matrix mapping in `GlyphRenderer.kt`. Do not revisit SDK/session logic first.
