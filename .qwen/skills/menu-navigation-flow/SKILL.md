---
name: menu-navigation-flow
description: Digivice menu navigation flow, button roles, and how MED option exits to IDLE
source: auto-skill
extracted_at: '2026-06-11T11:53:52.316Z'
---

# Menu Navigation Flow

The Digivice menu navigation uses three buttons with specific roles. Understanding the flow is critical when debugging menu behavior.

## Button Roles

- **A (confirm)** → `handleConfirm()` → handles the current menu item
- **B (advance)** → `handleAdvance()` → cycles through menu items (only in MENU screen)
- **C (back)** → `handleBack()` → exits the menu entirely (only in MENU screen)

## Menu Flow

```
IDLE → MENU → [STAT, MAP, GAME, CTRL, MED, LINK]
                          ↑    ↑    ↑    ↑    ↑    ↑
                          A    B    A    A    A    A
                          ↓    ↓    ↓    ↓    ↓    ↓
                        STAT  MAP  GAME CTRL MED  LINK
```

## Menu Item Actions

| Index | Item | Action |
|-------|------|--------|
| 0 | STAT | `screen = Screen.STATUS_SELECT` |
| 1 | MAP | `mapPreviewArea = state.area; screen = Screen.MAP` |
| 2 | GAME | `statusPage = 0; screen = Screen.STATUS` |
| 3 | CTRL | `state.autorun = !state.autorun; saveState()` |
| 4 | MED | If defeat: clear defeat and save. Then `screen = Screen.IDLE` |
| 5 | LINK | `resetProgress()` |

## Key Behavior

**MED does NOT have a sub-screen.** Pressing A on MED always exits to IDLE. This is by design — MED revives the player if defeated, then returns to the idle screen.

**B in MENU only cycles through items:** `menuIndex = (menuIndex + 1) % MENU_ITEMS.size`. It does NOT enter a sub-screen.

**C in MENU exits the menu:** `screen = Screen.IDLE`.

## Debugging Tips

When the menu feels "stuck," check:

1. **Are button events reaching the service?** Check logcat: `adb -s 192.168.0.16:34287 logcat | grep DigiviceGlyphToy`
2. **Is the service the active handler?** The Nothing Phone has a Glyph app where you select which app gets the physical button. Both DigimonGlyph and DigiviceGlyph register with `com.nothing.glyph.TOY`, so only one receives events at a time.
3. **Is the input controller initialized?** Check: `adb -s 192.168.0.16:34287 logcat | grep "Button DOWN: inputController="`
4. **Is the correct screen active?** Check: `adb -s 192.168.0.16:34287 logcat | grep "DigiviceV1Runtime"`

## Common Issues

1. **Button events go to wrong app** — DigimonGlyph may be the active handler in the Glyph app. Select DigiviceGlyph in the Glyph app settings.
2. **MED exits to IDLE** — This is correct behavior, not a bug. MED revives if defeated, then always returns to IDLE.
3. **Input controller is null** — Button events are silently dropped until `startRuntime()` is called in the service.
