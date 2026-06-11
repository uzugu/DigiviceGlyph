---
name: glyph-physical-button-control
description: Nothing phone Glyph SDK physical button → A button flow via DigiviceGlyphToyService messenger
source: auto-skill
extracted_at: '2026-06-11T11:42:50.867Z'
---

# Glyph Physical Button Control

The Nothing phone has a physical Glyph button that maps to the A button in DigiviceGlyph. The button event flows through the Glyph SDK messenger to the input controller.

## Control Mapping

| Input | Action |
|-------|--------|
| Physical Glyph button (press/hold/release) | A button |
| Left flick | B button |
| Right flick | C button |
| Up/down flick | Step (no button) |
| Forward/back flick | Step (no button) |

## Event Flow

1. **Nothing firmware** sends `GlyphToy` messages via `Messenger` on the `com.nothing.glyph.TOY` intent.
2. **`DigiviceGlyphToyService`** receives messages in `handleToyMessage()`:
   - `EVENT_ACTION_DOWN` → `inputController?.onGlyphButtonDown()`
   - `EVENT_ACTION_UP` → `inputController?.onGlyphButtonUp()`
   - `EVENT_AOD` → `pushStaticFrame()`
3. **`GlyphInputController`** processes the button event:
   - `onGlyphButtonDown()` → sets `glyphPhysicalDown = true`, sets `buttonAActive = true`, calls `sink?.onButtonDown(GlyphButton.A)`
   - `onGlyphButtonUp()` → sets `glyphPhysicalDown = false`, releases A, latches C if needed
4. **`DigiviceV1Runtime`** (implements `GlyphButtonSink`) receives the event:
   - `onButtonDown(GlyphButton.A)` → `handleConfirm()` → dispatches to current screen
   - `onButtonUp(GlyphButton.A)` → no-op (just releases the button)

## Key Classes

### DigiviceGlyphToyService
- Service that binds to the Glyph SDK and handles the physical button.
- `handleToyMessage(Message)` parses the event string and dispatches to `inputController`.
- `startRuntime()` creates `GlyphInputController` and attaches it to `DigiviceV1Runtime`.
- `onBind()` creates the messenger lazily.
- `onBind()` calls `initGlyphManager()` which registers with the Glyph framework.

### GlyphInputController
- `onGlyphButtonDown()` and `onGlyphButtonUp()` handle the physical button.
- `glyphPhysicalDown` flag affects flick behavior (suppresses B flicks, latches C).
- `buttonALatchedByB` prevents auto-release of A while physical button is held.

### GlyphButtonSink (interface)
```kotlin
interface GlyphButtonSink {
    fun onButtonDown(button: GlyphButton)
    fun onButtonUp(button: GlyphButton)
    fun triggerStep()
}
```

## Common Pitfalls

1. **`inputController` may be null**: If button events arrive before `startRuntime()` is called, `inputController?.onGlyphButtonDown()` silently drops the event.
2. **Messenger is lazy**: The messenger is created lazily in `onBind()`. If `onBind()` is called before `startRuntime()`, the messenger's handler is ready but `inputController` may not be.
3. **`triggerStep()` must be `override`**: `DigiviceV1Runtime` implements `GlyphButtonSink`.
4. **Y axis constraint**: `abs(filteredY) <= 3.4` must hold for X/Z flicks to register.
5. **Cooldown tracking**: X and Z axes have separate cooldown timers.
6. **Button latching**: `buttonCLatchedByB` prevents C from releasing while physical button is held.
7. **`onGlyphButtonUp()` does not clear `buttonALatchedByB`**: This flag is only cleared in `releaseA()`, not in `onGlyphButtonUp()`.
8. **Flick locking after button press**: When `pendingAxis` is set and sensor values stay small (0.001-0.02), `reboundAbs` never drops below 0.5 fast enough to trigger `clearPending()`. Meanwhile `lastFlickXMs` is only updated when a flick is triggered, so subsequent sensor events see `elapsed > cooldown` and try to start new flicks that fail, creating an endless loop of failed flicks that appear as "locked" flicks. **Fix**: call `updateFlickTimers(now)` at the end of every `processFlick()` call to keep `lastFlickXMs` current even when no flick is triggered.

## Verification Checklist

- [ ] Service is registered in AndroidManifest.xml with `android:exported="true"`
- [ ] Intent filter for `com.nothing.glyph.TOY` is present
- [ ] `startRuntime()` creates `inputController` and attaches to runtime
- [ ] `handleToyMessage()` checks `inputController` is not null
- [ ] `onGlyphButtonDown()` sets `glyphPhysicalDown` and calls `sink.onButtonDown(A)`
- [ ] `onGlyphButtonUp()` clears `glyphPhysicalDown` and releases A
- [ ] `triggerStep()` has `override` modifier
- [ ] All when expressions include Y branch

## Debugging Tips

- Check if service is running: `adb shell dumpsys activity services | grep -A 10 DigiviceGlyphToy`
- Check Glyph master switch: `adb shell settings get global led_effect_enable`
- Check button events in logcat: `adb logcat | grep -E "GlyphInput|DigiviceGlyphToy"`
- If button doesn't work, the event may be arriving before `inputController` is initialized.
