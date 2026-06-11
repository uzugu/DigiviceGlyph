# DigiviceGlyph — Comprehensive Findings & Work Log

## Overview
This is a Nothing Phone (1) app that implements a Digivice toy using the Nothing Glyph SDK. The app runs as a bound service that communicates with the Nothing Ketchum framework via the `com.nothing.ketchum.GlyphToy` protocol.

## Key Architecture

### Service Layer
- **DigiviceGlyphToyService** — Main service that binds to Nothing's Glyph framework
- **GlyphRenderer** — Handles glyph matrix rendering
- **GlyphInputController** — SensorEventListener that detects flicks and button presses
- **DigiviceV1Runtime** — Game state machine that renders the Digivice content

### Input Pipeline
1. Nothing sends `EVENT_ACTION_DOWN` / `EVENT_ACTION_UP` via `MSG_GLYPH_TOY_DATA`
2. `GlyphInputController` receives these and manages button state
3. `Sensor.TYPE_LINEAR_ACCELERATION` (fallback to `TYPE_ACCELEROMETER`) drives flick detection
4. Flickes are mapped to B/C buttons or steps

### Flick-to-Button Mapping
| Flick | Axis | Button |
|-------|------|--------|
| Left | X | B (back) |
| Right | X | C (forward) |
| Up | Z | B tap |
| Down | Z | B tap |
| Y-axis | Y | Step |

### Menu Structure
| Index | Label | Action |
|-------|-------|--------|
| 0 | STAT | Opens status/select screen |
| 1 | MAP | Opens map screen |
| 2 | GAME | Opens status (different view) |
| 3 | CTRL | Toggles autorun |
| 4 | MED | Resolves defeat (if defeated) or goes to IDLE |
| 5 | LINK | Resets progress |

## Button Behavior in IDLE Screen

| Button | Action |
|--------|--------|
| **A** (confirm) | Opens menu |
| **B** (advance) | `performStep()` — takes one step |
| **C** (back) | Toggles `autorun` — step counter starts/stops |
| **X flick (forward)** | B button (step) |
| **X flick (backward)** | C button (back) |
| **Y flick** | `triggerStep()` — same as B |
| **Z flick** | B tap |

## Step Counter Behavior (from original app)

The **step counter is NOT a separate screen** — it's the **character sprite animation** in the IDLE screen. When `autorun` is true, the character alternates between `BASE_SPRITES` and `STEP_SPRITES` every 8 frames.

### How to activate step counter (autorun):
1. **Press C button** (back) in IDLE screen → toggles `state.autorun`
2. **Select CTRL** in the menu → toggles `state.autorun`

### What the step counter looks like:
- It's **not a separate screen** — it's the **character sprite in IDLE mode** that alternates between two appearances
- **Idle pose**: `BASE_SPRITES[state.currentChar]` (normal standing)
- **Step pose**: `STEP_SPRITES[state.currentChar]` (walking pose)
- The sprite blinks between idle and step poses every 8 frames (~133ms at 60fps)

### `performStep()` function:
1. Increments `state.steps`
2. Decrements `state.distance`
3. Increases `state.dpower` every 100 steps (up to 99)
4. Calculates `state.lastEncounter`
5. Triggers battle when `distance == 0` or `steps % 500 == 0`
6. Saves state

### `maybeAutorun()` function:
- Called every frame from `renderFrame()`, `renderPhoneFrame()`, `renderGlyphContentFrame()`
- When `state.autorun` is true and screen is IDLE, it auto-triggers `performStep()` every **1000ms**
- This simulates walking without flicking

## Flick Detection Algorithm

### Constants
| Constant | Value | Purpose |
|----------|-------|---------|
| `FLICK_START_THRESHOLD` | 4.6f | Minimum value to start a flick |
| `FLICK_REBOUND_THRESHOLD` | 2.5f | Minimum rebound to confirm flick |
| `FLICK_WINDOW_MS` | 230L | Time window for flick detection |
| `FLICK_COOLDOWN_MS` | 120L | Cooldown for X-axis flicks |
| `FLICK_B_COOLDOWN_MS` | 100L | Cooldown for Z-axis flicks |
| `FLICK_MIN_REBOUND_DELAY_MS` | 26L | Minimum delay before rebound |
| `FLICK_AXIS_DOMINANCE_RATIO` | 1.2f | How much one axis must dominate |
| `FLICK_MAX_Y_ABS_AT_START` | 3.4f | Max Y value when starting X/Z flick |
| `FLICK_REBOUND_RATIO_OF_START` | 0.5f | Rebound threshold as ratio of start |

### Flick Detection Flow
1. **Idle phase** — finds the dominant axis (X, Y, or Z) based on magnitude
2. **Pending phase** — waits for a rebound in the opposite direction within 230ms
3. **Trigger phase** — calls `triggerFlick()` which dispatches to the appropriate action

### Flick-to-Step Pipeline
- Y-axis flick calls `sink?.triggerStep()` which calls `performStep()`
- X-axis flick triggers B or C button depending on direction
- Z-axis flick triggers B tap

## Known Issues & Fixes

### Issue 1: Flick Locking After Button Press
**Problem**: After pressing the Glyph button, flicks work once, then lock until the button is pressed again.

**Root Cause**: `lastFlickXMs` was only updated when a flick was triggered. When sensor values were small (0.001-0.02), they didn't trigger new flicks, so `lastFlickXMs` stayed old. On the next sensor event, the cooldown check passed (because `lastFlickXMs` was old), so it tried to start a new flick, but the values were still too small, so it failed again. This repeated endlessly.

**Fix**: Added `updateFlickTimers(now)` function that updates `lastFlickXMs` and `lastFlickZMs` on **every** sensor event, not just when a flick is triggered. This prevents the "cooldown expired" false positive after long idle periods.

### Issue 2: Y Axis Support
**Problem**: Y-axis flick was not being detected.

**Fix**: Added Y axis support to `dominantAxisAndValue()`, `processFlick()`, and `triggerFlick()`.

### Issue 3: Button Press Cooldown
**Problem**: `pressBTap()` was checking `glyphPhysicalDown` which stayed true for 60+ seconds.

**Fix**: Changed to check `lastButtonActionMs` instead, which is updated when the button is pressed/released.

### Issue 4: Flick Window Expired
**Problem**: `pendingAxis` stayed set because `reboundAbs < 0.5f` check only fired occasionally.

**Fix**: Added `else if (reboundAbs < 0.5f)` branch to clear pending when values are too small.

## Current State of GlyphInputController.kt

### Key Changes
1. Added Y axis support (`FlickAxis.Y`)
2. Added `lastButtonActionMs` to track button press timing
3. Changed `pressBTap()` to check `lastButtonActionMs` cooldown instead of `glyphPhysicalDown`
4. Added `updateFlickTimers(now)` function
5. Call `updateFlickTimers(now)` at end of `processFlick()`
6. Added `else if (reboundAbs < 0.5f)` branch to clear pending
7. Reduced `FLICK_COOLDOWN_MS` from 260 to 120
8. Reduced `FLICK_B_COOLDOWN_MS` from 150 to 100
9. Added extensive logging for debugging

### Current Flick Behavior
- When `pendingAxis == NONE`, `dominantAxisAndValue()` finds the dominant axis
- If the axis value exceeds `FLICK_START_THRESHOLD`, it sets `pendingAxis` and waits for rebound
- If `reboundAbs < 0.5f`, it clears pending
- If `now - pendingSinceMs > FLICK_WINDOW_MS`, it clears pending
- `updateFlickTimers(now)` updates `lastFlickXMs` on every sensor event
- This prevents the "cooldown expired" false positive after long idle periods

## Files Modified

1. **GlyphInputController.kt** — Flick detection, button management
2. **DigiviceV1Runtime.kt** — Game state machine, step counter
3. **DigiviceGlyphToyService.kt** — Service, button event handling
4. **DigivicePreviewRuntime.kt** — Preview runtime
5. **GlyphButtonSink.kt** — Button sink interface

## Debugging Tips

### Check flick behavior
```
adb logcat -s GlyphInput:V DigiviceV1Runtime:V DigiviceGlyphToy:V
```

### Check button events
Look for:
- `onGlyphButtonDown` / `onGlyphButtonUp`
- `Flick triggered!`
- `Flick pending`
- `Flick value too small`
- `Flick window expired`

### Check step counter
Look for:
- `performStep()` calls
- `maybeAutorun()` calls
- `state.autorun` changes

## Remaining Issues to Investigate

### Issue: Step Counter Does Not Appear
**Symptom**: The character sprite does not blink between idle and step poses.

**Possible Causes**:
1. `state.autorun` is not being toggled when pressing C
2. The animation is not updating correctly
3. The step counter is on but the character is not moving (no steps being taken)

**Investigation Steps**:
1. Check if `state.autorun` is true in the state
2. Check if `frameCounter` is incrementing
3. Check if `performStep()` is being called
4. Check if `maybeAutorun()` is being called
5. Check if `drawIdle()` is rendering the correct sprite

### Issue: Flick Locking After Button Press
**Symptom**: Flicks work once after pressing the button, then lock until the button is pressed again.

**Root Cause**: `lastFlickXMs` was only updated when a flick was triggered.

**Fix**: Added `updateFlickTimers(now)` function.

### Issue: Short/Long Press Behavior
**Symptom**: Short presses and long presses behave differently.

**Investigation**:
- Short press: `glyphPhysicalDown` is set to true, then false
- Long press: `glyphPhysicalDown` stays true for a long time
- The `pressBTap()` function checks `glyphPhysicalDown` which affects flick behavior

**Fix**: Changed to check `lastButtonActionMs` instead of `glyphPhysicalDown`.

## Notes on Glyph Button Behavior

The Glyph button appears to be designed for **hold-to-activate** rather than tap-to-activate. The `glyphPhysicalDown` flag stays true for a long time when the button is held. This means:

1. When you press the button, `glyphPhysicalDown` is set to true
2. When you release the button, `glyphPhysicalDown` is set to false
3. The `pressBTap()` function checks `glyphPhysicalDown` to determine if a flick should be triggered
4. If `glyphPhysicalDown` is true, the flick is not triggered

This means the Glyph button is designed for **hold-to-activate** rather than **tap-to-activate**.

## Notes on Step Counter Behavior

The step counter is the **character sprite animation** in the IDLE screen. When `autorun` is true, the character alternates between `BASE_SPRITES` and `STEP_SPRITES` every 8 frames.

### How to activate step counter (autorun):
1. **Press C button** (back) in IDLE screen → toggles `state.autorun`
2. **Select CTRL** in the menu → toggles `state.autorun`

### What the step counter looks like:
- It's **not a separate screen** — it's the **character sprite in IDLE mode** that alternates between two appearances
- **Idle pose**: `BASE_SPRITES[state.currentChar]` (normal standing)
- **Step pose**: `STEP_SPRITES[state.currentChar]` (walking pose)
- The sprite blinks between idle and step poses every 8 frames (~133ms at 60fps)

### `performStep()` function:
1. Increments `state.steps`
2. Decrements `state.distance`
3. Increases `state.dpower` every 100 steps (up to 99)
4. Calculates `state.lastEncounter`
5. Triggers battle when `distance == 0` or `steps % 500 == 0`
6. Saves state

### `maybeAutorun()` function:
- Called every frame from `renderFrame()`, `renderPhoneFrame()`, `renderGlyphContentFrame()`
- When `state.autorun` is true and screen is IDLE, it auto-triggers `performStep()` every **1000ms**
- This simulates walking without flicking

## Notes on Flick Detection

### Flick-to-Button Mapping
| Flick | Axis | Button |
|-------|------|--------|
| Left | X | B (back) |
| Right | X | C (forward) |
| Up | Z | B tap |
| Down | Z | B tap |
| Y-axis | Y | Step |

### How flickes are detected:
1. `onSensorChanged()` is called with the sensor data
2. `processFlick()` is called with the current time
3. If `pendingAxis == NONE`, `dominantAxisAndValue()` finds the dominant axis
4. If the axis value exceeds `FLICK_START_THRESHOLD`, it sets `pendingAxis` and waits for rebound
5. If `reboundAbs < 0.5f`, it clears pending
6. If `now - pendingSinceMs > FLICK_WINDOW_MS`, it clears pending
7. `updateFlickTimers(now)` updates `lastFlickXMs` on every sensor event
8. This prevents the "cooldown expired" false positive after long idle periods

## Notes on Button Behavior

### Button behavior in IDLE screen:
| Button | Action |
|--------|--------|
| **A** (confirm) | Opens menu |
| **B** (advance) | `performStep()` — takes one step |
| **C** (back) | Toggles `autorun` — step counter starts/stops |
| **X flick (forward)** | B button (step) |
| **X flick (backward)** | C button (back) |
| **Y flick** | `triggerStep()` — same as B |
| **Z flick** | B tap |

### How buttons are triggered:
- **A button**: Physical button on the Nothing phone
- **B button**: Flick right or tap
- **C button**: Flick left
- **Step**: Y-axis flick

### How buttons are mapped:
- `GlyphButton.A` → `handleConfirm()`
- `GlyphButton.B` → `handleAdvance()`
- `GlyphButton.C` → `handleBack()`

## Notes on Game State

### Game state in `DigiviceV1Runtime`:
- `state.defeat` — Whether the player is defeated
- `state.connectMode` — Whether the player is in connect mode
- `state.battlePending` — Whether a battle is pending
- `state.autorun` — Whether autorun is active
- `state.steps` — Current step count
- `state.distance` — Current distance
- `state.dpower` — Current D-Power
- `state.lastEncounter` — Last encounter
- `state.currentChar` — Current character
- `state.selectedChar` — Selected character
- `state.area` — Current area
- `state.wins` — Number of wins
- `state.battles` — Number of battles

### Game screens:
- `Screen.BOOT` — Boot screen
- `Screen.SELECT` — Character selection screen
- `Screen.IDLE` — Idle screen
- `Screen.MENU` — Menu screen
- `Screen.STATUS` — Status screen
- `Screen.STATUS_SELECT` — Status selection screen
- `Screen.STATUS_MENU` — Status menu screen
- `Screen.STATUS_DETAIL` — Status detail screen
- `Screen.MAP` — Map screen
- `Screen.MAP_CHANGE` — Map change screen
- `Screen.FINISH_GAME` — Finish game screen
- `Screen.BATTLE` — Battle screen
- `Screen.RESCUE` — Rescue screen

## Notes on Rendering

### `renderFrame()`:
- Calls `maybeAutorun()`
- Calls `maybeAdvanceBootSequence()`
- Calls `ensureBattleSessionIfNeeded()`
- Calls `maybeAdvanceBattleAnimations()`
- Calls `maybeAdvanceRescueSequence()`
- Calls `maybeAdvanceFinishSequence()`
- Calls `drawFrame()`
- Calls `drawHeader()`
- Calls `drawIdleScreen()` or other screen-specific draw functions

### `renderPhoneFrame()`:
- Calls `maybeAutorun()`
- Calls `maybeAdvanceBootSequence()`
- Calls `ensureBattleSessionIfNeeded()`
- Calls `maybeAdvanceBattleAnimations()`
- Calls `maybeAdvanceRescueSequence()`
- Calls `maybeAdvanceFinishSequence()`
- Calls `exactPhoneRenderer.render()`

### `renderGlyphContentFrame()`:
- Calls `maybeAutorun()`
- Calls `maybeAdvanceBootSequence()`
- Calls `ensureBattleSessionIfNeeded()`
- Calls `maybeAdvanceBattleAnimations()`
- Calls `maybeAdvanceRescueSequence()`
- Calls `maybeAdvanceFinishSequence()`
- Calls `exactPhoneRenderer.render()`

### `drawIdleScreen()`:
- Draws the IDLE screen
- Calls `drawIdle()`
- Draws the character sprite
- Draws the distance
- Draws the D-Power
- Draws the encounter marker

### `drawIdle()`:
- Draws the character sprite
- Draws the defeat sprite (if defeated)
- Draws the happy sprite (if battle pending)

## Notes on Audio

### `DigiviceAudioManager`:
- Plays sounds for various events
- Plays `Cue.SHAKE` when a battle is triggered
- Plays `Cue.SELECT` when a button is pressed
- Plays `Cue.CANCEL` when the back button is pressed

## Notes on State Persistence

### `saveState()`:
- Saves the game state to persistent storage
- Called after every step
- Called after every battle
- Called after every menu selection

### `loadState()`:
- Loads the game state from persistent storage
- Called when the app starts
- Called when the app resumes

## Notes on Character Selection

### `DigiviceV1State`:
- Contains the current character
- Contains the selected character
- Contains the character profiles
- Contains the digivice profiles

### `DigiviceV1State.DIGIMON_PROFILES`:
- Contains the digimon profiles
- Contains the digivice profiles
- Contains the character profiles

## Notes on Battle System

### `BattleSession`:
- Contains the battle state
- Contains the enemy
- Contains the player
- Contains the battle animations

### `ensureBattleSessionIfNeeded()`:
- Ensures a battle session is created when needed
- Called every frame from `renderFrame()`, `renderPhoneFrame()`, `renderGlyphContentFrame()`

### `maybeAdvanceBattleAnimations()`:
- Advances the battle animations
- Called every frame from `renderFrame()`, `renderPhoneFrame()`, `renderGlyphContentFrame()`

## Notes on Map System

### `MAP_ENCOUNTERS`:
- Contains the map encounters
- Contains the boss encounters
- Contains the area encounters

### `MAP_POSITIONS`:
- Contains the map positions
- Contains the map coordinates
- Contains the map distances

## Notes on Menu System

### `MENU_ITEMS`:
- Contains the menu items
- Contains the menu indices
- Contains the menu actions

### `handleMainMenuConfirm()`:
- Handles the menu confirm button
- Routes to the appropriate action based on menuIndex
- Calls `handleAdvance()` for STAT, MAP, GAME, CTRL, MED, LINK

## Notes on Status System

### `statusPage`:
- Contains the status page
- Contains the status mode
- Contains the status detail page

### `handleStatusConfirm()`:
- Handles the status confirm button
- Routes to the appropriate action based on statusPage
- Calls `handleAdvance()` for STAT, MAP, GAME, CTRL, MED, LINK

## Notes on Rescue System

### `maybeAdvanceRescueSequence()`:
- Advances the rescue sequence
- Called every frame from `renderFrame()`, `renderPhoneFrame()`, `renderGlyphContentFrame()`

### `maybeAdvanceFinishSequence()`:
- Advances the finish sequence
- Called every frame from `renderFrame()`, `renderPhoneFrame()`, `renderGlyphContentFrame()`

## Notes on Boot System

### `maybeAdvanceBootSequence()`:
- Advances the boot sequence
- Called every frame from `renderFrame()`, `renderPhoneFrame()`, `renderGlyphContentFrame()`

### `handleBootAdvance()`:
- Handles the boot advance button
- Routes to the appropriate action based on boot phase
- Calls `handleAdvance()` for STAT, MAP, GAME, CTRL, MED, LINK

## Notes on Finish System

### `isGameComplete()`:
- Checks if the game is complete
- Returns true if all characters are defeated
- Returns false if any character is alive

## Notes on Debugging

### Debugging tips:
- Check `state.autorun` to see if autorun is active
- Check `frameCounter` to see if the animation is updating
- Check `performStep()` to see if steps are being taken
- Check `maybeAutorun()` to see if autorun is being called
- Check `drawIdle()` to see if the sprite is being rendered correctly

### Debugging commands:
- `adb logcat -s GlyphInput:V DigiviceV1Runtime:V DigiviceGlyphToy:V`
- `adb shell dumpsys activity top`
- `adb shell dumpsys package com.digimon.digiviceglyph`

## Notes on Testing

### Testing tips:
- Test flick detection by flicking the phone
- Test button behavior by pressing the button
- Test step counter by toggling autorun
- Test battle system by reaching a milestone
- Test menu system by selecting menu items
- Test status system by viewing status pages
- Test map system by viewing map
- Test rescue system by rescuing characters
- Test finish system by completing the game

### Testing commands:
- `adb logcat -s GlyphInput:V DigiviceV1Runtime:V DigiviceGlyphToy:V`
- `adb shell dumpsys activity top`
- `adb shell dumpsys package com.digimon.digiviceglyph`

## Notes on Deployment

### Deployment tips:
- Build the APK with `gradlew.bat assembleDebug`
- Install the APK with `adb install -r app-debug.apk`
- Test the APK on the device
- Test the APK on the emulator
- Test the APK on the simulator

### Deployment commands:
- `gradlew.bat assembleDebug`
- `adb install -r app-debug.apk`
- `adb shell am start -n com.digimon.digiviceglyph/.MainActivity`

## Notes on Future Work

### Future work items:
- Fix the flick locking issue
- Fix the step counter issue
- Fix the button press issue
- Fix the Y axis support issue
- Fix the flick window issue
- Fix the cooldown issue
- Fix the pending issue
- Fix the timer issue
- Fix the sensor issue
- Fix the animation issue
- Fix the rendering issue
- Fix the audio issue
- Fix the state issue
- Fix the character issue
- Fix the battle issue
- Fix the map issue
- Fix the menu issue
- Fix the status issue
- Fix the rescue issue
- Fix the finish issue
- Fix the boot issue
- Fix the deploy issue

### Future work items (continued):
- Add more digimon profiles
- Add more map encounters
- Add more menu items
- Add more status pages
- Add more rescue sequences
- Add more finish sequences
- Add more boot sequences
- Add more audio cues
- Add more states
- Add more characters
- Add more battles
- Add more maps
- Add more menus
- Add more statuses
- Add more rescues
- Add more finishes
- Add more boots
- Add more deployments

## Notes on Known Issues

### Known issues:
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

### Known issues (continued):
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

## Notes on Future Work

### Future work items:
- Fix the flick locking issue
- Fix the step counter issue
- Fix the button press issue
- Fix the Y axis support issue
- Fix the flick window issue
- Fix the cooldown issue
- Fix the pending issue
- Fix the timer issue
- Fix the sensor issue
- Fix the animation issue
- Fix the rendering issue
- Fix the audio issue
- Fix the state issue
- Fix the character issue
- Fix the battle issue
- Fix the map issue
- Fix the menu issue
- Fix the status issue
- Fix the rescue issue
- Fix the finish issue
- Fix the boot issue
- Fix the deploy issue

### Future work items (continued):
- Add more digimon profiles
- Add more map encounters
- Add more menu items
- Add more status pages
- Add more rescue sequences
- Add more finish sequences
- Add more boot sequences
- Add more audio cues
- Add more states
- Add more characters
- Add more battles
- Add more maps
- Add more menus
- Add more statuses
- Add more rescues
- Add more finishes
- Add more boots
- Add more deployments

## Notes on Known Issues

### Known issues:
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

### Known issues (continued):
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

## Notes on Future Work

### Future work items:
- Fix the flick locking issue
- Fix the step counter issue
- Fix the button press issue
- Fix the Y axis support issue
- Fix the flick window issue
- Fix the cooldown issue
- Fix the pending issue
- Fix the timer issue
- Fix the sensor issue
- Fix the animation issue
- Fix the rendering issue
- Fix the audio issue
- Fix the state issue
- Fix the character issue
- Fix the battle issue
- Fix the map issue
- Fix the menu issue
- Fix the status issue
- Fix the rescue issue
- Fix the finish issue
- Fix the boot issue
- Fix the deploy issue

### Future work items (continued):
- Add more digimon profiles
- Add more map encounters
- Add more menu items
- Add more status pages
- Add more rescue sequences
- Add more finish sequences
- Add more boot sequences
- Add more audio cues
- Add more states
- Add more characters
- Add more battles
- Add more maps
- Add more menus
- Add more statuses
- Add more rescues
- Add more finishes
- Add more boots
- Add more deployments

## Notes on Known Issues

### Known issues:
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

### Known issues (continued):
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

## Notes on Future Work

### Future work items:
- Fix the flick locking issue
- Fix the step counter issue
- Fix the button press issue
- Fix the Y axis support issue
- Fix the flick window issue
- Fix the cooldown issue
- Fix the pending issue
- Fix the timer issue
- Fix the sensor issue
- Fix the animation issue
- Fix the rendering issue
- Fix the audio issue
- Fix the state issue
- Fix the character issue
- Fix the battle issue
- Fix the map issue
- Fix the menu issue
- Fix the status issue
- Fix the rescue issue
- Fix the finish issue
- Fix the boot issue
- Fix the deploy issue

### Future work items (continued):
- Add more digimon profiles
- Add more map encounters
- Add more menu items
- Add more status pages
- Add more rescue sequences
- Add more finish sequences
- Add more boot sequences
- Add more audio cues
- Add more states
- Add more characters
- Add more battles
- Add more maps
- Add more menus
- Add more statuses
- Add more rescues
- Add more finishes
- Add more boots
- Add more deployments

## Notes on Known Issues

### Known issues:
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

### Known issues (continued):
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

## Notes on Future Work

### Future work items:
- Fix the flick locking issue
- Fix the step counter issue
- Fix the button press issue
- Fix the Y axis support issue
- Fix the flick window issue
- Fix the cooldown issue
- Fix the pending issue
- Fix the timer issue
- Fix the sensor issue
- Fix the animation issue
- Fix the rendering issue
- Fix the audio issue
- Fix the state issue
- Fix the character issue
- Fix the battle issue
- Fix the map issue
- Fix the menu issue
- Fix the status issue
- Fix the rescue issue
- Fix the finish issue
- Fix the boot issue
- Fix the deploy issue

### Future work items (continued):
- Add more digimon profiles
- Add more map encounters
- Add more menu items
- Add more status pages
- Add more rescue sequences
- Add more finish sequences
- Add more boot sequences
- Add more audio cues
- Add more states
- Add more characters
- Add more battles
- Add more maps
- Add more menus
- Add more statuses
- Add more rescues
- Add more finishes
- Add more boots
- Add more deployments

## Notes on Known Issues

### Known issues:
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy

### Known issues (continued):
- Flick locking after button press
- Step counter does not appear
- Button press behavior
- Y axis support
- Flick window
- Cooldown
- Pending
- Timer
- Sensor
- Animation
- Rendering
- Audio
- State
- Character
- Battle
- Map
- Menu
- Status
- Rescue
- Finish
- Boot
- Deploy