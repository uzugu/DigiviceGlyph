---
name: menu-map-system
description: GameMaker Digivice menu navigation, map screen with per-area distances, and area change logic
source: auto-skill
extracted_at: '2026-06-11T10:53:54.919Z'
---

# Menu & Map System

When implementing the Digivice menu and map screens (port from GameMaker), the key mechanics are: menu navigation flow, per-area distance tracking, and the map change screen.

## Menu Navigation Flow

```
IDLE → MENU → [STAT, MAP, GAME, CTRL, MED, LINK]
```

**Accessing the menu:**
- In IDLE screen (idle with your partner), press **A** → opens the MENU
- Press **B** to cycle through menu items
- Press **A** on each item to activate it

**Menu items:**
- **STAT** → opens status select (choose character)
- **MAP** → opens map screen
- **GAME** → opens status page
- **CTRL** → toggles autorun
- **MED** → revives if defeated
- **LINK** → resets progress

## Map Screen (`obj_menu_map_digivice_v1`)

The map screen shows the current area and a row of boxes for all 7 areas.

**Behavior:**
- Shows the current area number (1-7)
- Shows the distance for the **selected area** (not just the current area)
- Press **B** to cycle through areas (0-6)
- Press **A** to open the change-map screen (only when `game_complete`)
- The current area blinks every 30 ticks (via `alarm[0]`)

**Game complete check:**
```gml
game_complete = (areas[0] == 2 && areas[1] == 2 && ... && areas[6] == 2)
```

## Map Change Screen (`obj_menu_change_map_digivice_v1`)

When you press A on the map screen (in game_complete mode):
1. Creates `obj_menu_change_map_digivice_v1` instance
2. Sets `new_area = current_menu` (the selected area)
3. Sets `new_distance` based on the selected area:
   - If `current_menu == global.area_digivice_v1`: uses `global.distance_digivice_v1`
   - Otherwise: uses `global.map_distance_digivice_v1[current_menu]`
4. Draws `spr_map_change` plus the distance number using `spr_numbers`

## Per-Area Distance System

The original GameMaker code uses `global.map_distance_digivice_v1` — a per-area distance array. Each area has its own distance value that persists independently.

**Key insight:** The map screen doesn't just show the current area's distance — it shows the distance for the **selected** area, allowing you to see how far each zone is.

```kotlin
// In state
var perAreaDistances: IntArray = intArrayOf(10000, 12000, 14000, 16000, 18000, 20000, 22000)

// When changing map
state.area = mapPreviewArea
state.distance = if (mapPreviewArea == state.area) 
    state.distance 
    else 
    state.perAreaDistances[mapPreviewArea]
```

**When boss is defeated:**
```kotlin
state.areas[state.area] = 2
state.perAreaDistances[state.area] = state.distance  // Save distance for this area
if (state.area == MAP_DISTANCES.lastIndex) {
    startFinishGameSequence()
} else {
    state.area = (state.area + 1).coerceAtMost(MAP_DISTANCES.lastIndex)
    state.distance = MAP_DISTANCES[state.area]
}
```

## Map Screen Drawing

**Glyph (25x25):**
```kotlin
private fun drawMapScreen() {
    drawText("AREA", 3, 6)
    drawText("${mapPreviewArea + 1}", 10, 13)
    drawText("DIST:${state.perAreaDistances[mapPreviewArea]}", 3, 19)
    for (index in MAP_DISTANCES.indices) {
        val y = 20
        val x = 3 + index * 3
        if (index <= mapPreviewArea) {
            setPixel(x, y)
            setPixel(x + 1, y)
        } else {
            setPixel(x, y)
        }
    }
}
```

**Phone preview:**
```kotlin
private fun drawPhoneMap(screenRect: RectF) {
    phoneCanvas.drawText("MAP", ...)
    phoneCanvas.drawText("Area ${mapPreviewArea + 1}", ...)
    phoneCanvas.drawText("Distance ${state.perAreaDistances[mapPreviewArea]}", ...)
    // Draw circles for each area (filled = active, stroke = inactive)
}
```

## Save/Load

The per-area distances are saved under `map_distance_digivice_v1` in the save file:

```kotlin
// Save
putEncrypted("map_distance_digivice_v1", JSONArray(state.perAreaDistances.map { it }.toList()))

// Load
perAreaDistances = loadIntArray(root, "map_distance_digivice_v1", 
    intArrayOf(10000, 12000, 14000, 16000, 18000, 20000, 22000), 7)
```

## Common Pitfalls

1. **Static vs per-area distances**: Using `MAP_DISTANCES[area]` (static table) instead of `state.perAreaDistances[area]` (per-area) causes the map screen to show incorrect distances when you've changed areas.

2. **Distance not saved on boss defeat**: When a boss is defeated and the area advances, the distance must be saved to `perAreaDistances[state.area]` so it persists when navigating back to the map.

3. **MAP_CHANGE screen distance**: The change-map screen must show the distance of the **selected** area, not the current area. Use `currentMapTargetDistance()` which returns `perAreaDistances[mapPreviewArea]` for non-current areas.

4. **Game complete check**: The map A button only works when all areas are complete (`areas.all { it == 2 }`). The original checks all 7 areas individually.

5. **Map blink**: The current area overlay blinks every 30 ticks via `alarm[0]`. This is used to highlight the current area on the map sprite.

## Verification Checklist

- [ ] Menu A button opens correct sub-screen for each menu item
- [ ] Menu B button cycles through items correctly
- [ ] Map screen shows distance for selected area (not just current area)
- [ ] Map B button cycles areas 0-6
- [ ] Map A button opens change-map when game_complete
- [ ] Map change screen shows correct distance for selected area
- [ ] perAreaDistances is saved/loaded correctly
- [ ] Boss defeat saves distance to perAreaDistances
- [ ] Map blink animation works (every 30 ticks)
- [ ] Game complete check works (all 7 areas == 2)
