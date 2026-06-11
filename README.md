# Digivice Glyph

Separate Nothing Glyph Matrix app for the abandoned GameMaker-based `Digivice V1 Emulator`.

## Current status

This project is intentionally separate from `DigimonGlyph`.

- `DigimonGlyph` remains the ROM-accurate V-pet emulator.
- `DigiviceGlyph` is the new host app for the old GameMaker Digivice experience.

This repo is no longer just a scaffold. It currently includes:

- Nothing Glyph Toy service wiring
- a real `DigiviceV1Runtime` with save/load, step progression, map progression, battle flow work, and original assets
- a dedicated in-app phone renderer path for source-driven Digivice visuals
- a dedicated `25x25` Glyph Matrix renderer path for the rear display
- bundled original sprite and audio assets recovered from the abandoned GameMaker build
- on-screen A/B/C controls plus Glyph/motion input

It still does **not** fully match the original GameMaker app visually or behaviorally on every screen, but it is already running real gameplay logic rather than a placeholder preview.

## Why a separate app

The abandoned APK and Windows executable are GameMaker exports with their own gameplay runtime:

- `data.win`
- `game.droid`

That logic is not compatible with the ROM emulator architecture used by `DigimonGlyph`, so forcing both into one app would create a brittle codebase.

## Verified Glyph SDK behavior

These findings are now confirmed from the app, the bundled Nothing SDK, and decompiled device firmware:

- The rear Glyph Matrix only works when the phone-wide master switch is enabled:
  - `adb shell settings get global led_effect_enable`
- `GlyphToy` button events are delivered through the toy service `Messenger`, not as normal Android key events.
- A normal short Glyph-button press is reserved by Nothing OS for cycling toys and is not a reliable app input.
- Usable app input comes from:
  - `EVENT_CHANGE` when `com.nothing.glyph.toy.longpress=1`
  - `EVENT_ACTION_DOWN` / `EVENT_ACTION_UP` while the Glyph Button is held
- The always-on foreground service can keep gameplay and motion sensing alive, but it cannot receive Glyph-button events while this toy is not selected.
- `GlyphMatrixManager.setMatrixFrame(...)` is the correct rear-matrix path for this app.
- Decompiled `GlyphService` showed that missing `LightsSession` was not the real blocker; `led_effect_enable` was.

Current authoritative control mapping:

- Glyph Button hold or long press -> A
- flick right -> B
- flick left -> C
- vertical flick -> one step

Idle-screen behavior currently follows the original app more closely:

- `B` opens the main menu
- `C` opens the stats pages
- walking comes from vertical motion
- Auto Walk is an app-only helper, not a Digivice in-game toggle

## Next porting steps

1. Keep tightening the in-app phone renderer against recovered GameMaker draw scripts.
2. Continue validating battle, map, and finish-game timing against original behavior.
3. Keep the Glyph Matrix renderer readable as an adapted secondary display rather than forcing full phone-layout parity.
4. Preserve the verified input contract and do not reintroduce latch-based A/B/C coupling in the Glyph input layer.

## Key docs

- Root handoff: [AGENT_HANDOFF.md](/C:/Users/uzuik/AndroidStudioProjects/DigiviceGlyph/AGENT_HANDOFF.md)
- Historical project handoff: [notes/agent-handoff-2026-06-10.md](/C:/Users/uzuik/AndroidStudioProjects/DigiviceGlyph/notes/agent-handoff-2026-06-10.md)
- Visual fidelity plan: [notes/exact-visual-port-plan.md](/C:/Users/uzuik/AndroidStudioProjects/DigiviceGlyph/notes/exact-visual-port-plan.md)
