# Digivice Glyph

Separate Nothing Glyph Matrix app for the abandoned GameMaker-based `Digivice V1 Emulator`.

## Current status

This project is intentionally separate from `DigimonGlyph`.

- `DigimonGlyph` remains the ROM-accurate V-pet emulator.
- `DigiviceGlyph` is the new host app for the old GameMaker Digivice experience.

The first scaffold in this repo includes:

- Nothing Glyph Toy service wiring
- the same motion plus glyph-button control model used by `DigimonGlyph`
- a placeholder Digivice runtime that renders live feedback to the Glyph Matrix

It does **not** yet contain the original GameMaker gameplay logic.

## Why a separate app

The abandoned APK and Windows executable are GameMaker exports with their own gameplay runtime:

- `data.win`
- `game.droid`

That logic is not compatible with the ROM emulator architecture used by `DigimonGlyph`, so forcing both into one app would create a brittle codebase.

## Next porting steps

1. Recover Digivice V1 game state, screens, and button behavior from the packaged GameMaker data.
2. Replace the placeholder runtime in this app with a real `DigiviceV1Runtime`.
3. Map the recovered UI/state flow onto the 25x25 Glyph Matrix.
4. Keep the current motion and glyph-button input contract.
