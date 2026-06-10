## Agent Handoff

Date: 2026-06-10

Project root:

- `C:\Users\uzuik\AndroidStudioProjects\DigiviceGlyph`

Decomp / recovered-source workspace:

- `C:\Users\uzuik\Documents\VRmakes\Projects\decomp digivice1`

### Objective

Port the abandoned GameMaker `Digivice V1` Android app into:

1. a faithful in-app phone renderer with visuals as close to the original as possible
2. a separate adapted Nothing Glyph Matrix renderer for the `25x25` mono rear display

Important constraint:

- The phone view should target original visuals.
- The Glyph Matrix cannot be visually exact because of hardware limits.

### Current State

The project is no longer just a shell.

Implemented:

- standalone Android app scaffold
- Nothing Glyph Toy service integration
- shared runtime instance between service and activity
- native Digivice save/load with recovered XOR save key
- step progression, distance countdown, dpower gain, milestone encounters
- recovered starter roster, evolution tables, enemy tables, battle flow skeleton
- sprite export pipeline from `game.droid`
- sound export pipeline from `game.droid`
- bundled sprite assets in `app/src/main/assets/sprites`
- bundled original sound assets in `app/src/main/assets/audio`
- in-app preview view and on-screen A/B/C controls
- native `SoundPool` audio manager using original Digivice sounds

Still not done:

- the in-app phone preview is now driven by a dedicated exact renderer, but several screens are still partial
- the Glyph Matrix view is adapted but still visually noisy on text-heavy screens
- the runtime behavior is partially ported, but presentation fidelity is the main missing piece

### Build / Deploy Status

Build command:

- `.\gradlew.bat assembleDebug`

Current APK:

- `app/build/outputs/apk/debug/app-debug.apk`

Known working wireless ADB target during this session:

- `adb-00024156A001549-bGMtHr._adb-tls-connect._tcp`

Install command used successfully:

- `adb -s adb-00024156A001549-bGMtHr._adb-tls-connect._tcp install -r "C:\Users\uzuik\AndroidStudioProjects\DigiviceGlyph\app\build\outputs\apk\debug\app-debug.apk"`

### Important Files

Runtime and rendering:

- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigiviceV1Runtime.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/ExactPhoneRenderer.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigiviceAudioManager.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigiviceV1State.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigiviceV1SaveRepository.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/GlyphSpriteLibrary.kt`
- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigiviceRuntimeStore.kt`

Activity / preview:

- `app/src/main/java/com/digimon/digiviceglyph/MainActivity.kt`
- `app/src/main/java/com/digimon/digiviceglyph/GlyphPreviewView.kt`
- `app/src/main/res/layout/activity_main.xml`

Glyph hardware:

- `app/src/main/java/com/digimon/digiviceglyph/DigiviceGlyphToyService.kt`
- `app/src/main/java/com/digimon/digiviceglyph/GlyphRenderer.kt`

Planning docs:

- `notes/exact-visual-port-plan.md`
- `notes/agent-handoff-2026-06-10.md`

### Recovered Source Of Truth

Decompiled GameMaker code:

- `C:\Users\uzuik\Documents\VRmakes\Projects\decomp digivice1\recovered\android_cli\CodeEntries`

Named exported sprite folders:

- `C:\Users\uzuik\Documents\VRmakes\Projects\decomp digivice1\recovered\android_named_sprites\Sprites`

Named exported sound files:

- `C:\Users\uzuik\Documents\VRmakes\Projects\decomp digivice1\recovered\android_named_sounds\Sounds`

High-value recovered draw scripts:

- `gml_Object_obj_start_digivice_v1_Draw_0.gml`
- `gml_Object_obj_char_digivice_v1_Draw_0.gml`
- `gml_Object_obj_show_chars_digivice_v1_Draw_0.gml`
- `gml_Object_obj_menu_stats_digivice_v1_Draw_0.gml`
- `gml_Object_obj_menu_map_digivice_v1_Draw_0.gml`
- `gml_Object_obj_menu_change_map_digivice_v1_Draw_0.gml`
- `gml_Object_obj_sel_menu_digi_digivice_v1_Draw_0.gml`
- `gml_Object_obj_battle_digivice_v1_Draw_0.gml`
- `gml_Object_obj_battle_start_digivice_v1_Draw_0.gml`
- `gml_Object_obj_battle_swap_digivice_v1_Draw_0.gml`
- `gml_Object_obj_game_digivice_v1_Draw_0.gml`

### Assets Already Copied Into The App

The app already contains:

- partner normal / attack / happy / defeat / step variants
- evolution sprites
- enemy sprites
- key UI sprites:
  - `spr_start`
  - `spr_start_pop`
  - `spr_status_digivice_v1`
  - `spr_menu_digivice_v1`
  - `spr_menu_stats_digivice_v1`
  - `spr_menu_stats_sel`
  - `spr_menu_sel`
  - `spr_map__digivice_v1`
  - `spr_map_change`
  - `spr_map_cover`
  - `spr_map_cover_2`
  - `spr_battle_alert_digivice_v1`
  - `spr_battle_menu_digivice_v1`
  - `spr_battle_push_digivice_v1`
  - `spr_battle_card`
  - `spr_battle_msg`
  - `spr_status_menu_digivice_v1`
  - `spr_status_attack_defense_v1`
  - `spr_numbers`
  - `spr_numbers_white`
  - `spr_hp_bar_digivice_v1`
  - `spr_happy`
  - `spr_defeat`

App asset location:

- `app/src/main/assets/sprites`
- `app/src/main/assets/audio`

### Audio Coverage

Recovered Digivice-specific sounds are now exported and wired into the runtime using original filenames from the GameMaker build.

Currently bundled:

- `sound_select`
- `sound_cancel`
- `sound_start`
- `sound_alert_old`
- `sound_encounter`
- `sound_ready_go`
- `sound_evo_digivice_small`
- `sound_evo_digivice`
- `sound_hit_digivice`
- `sound_change`
- `sound_happy`
- `sound_sad`
- `sound_sad_small`
- `sound_sad_happy`
- `sound_finish_game_old`
- `sound_connect`
- `sound_shake`

Currently wired:

- boot start / confirm / final happy phase
- main menu, status, map, and general select/cancel flows
- encounter trigger and battle alert intro
- battle menu select/cancel
- ready-go, evo charge, evo sequence, and hit timing
- rescue success/fail resolution
- battle outcome happy/sad feedback
- area swap
- finish-game sequence

### What Is Currently Wrong

1. The phone preview is only partially rendering original screen layouts.

`renderPhoneFrame()` now delegates to `ExactPhoneRenderer`, and these are source-driven already:

- timed multi-phase boot/start sequence
- exact menu frame selection using `spr_menu_digivice_v1` subimages
- exact status subflow plates:
  - `obj_show_chars_digivice_v1`
  - `obj_sel_menu_stats_digivice_v1`
  - `obj_sel_menu_digi_digivice_v1`

Still approximate:

- progress/stat pages outside the status-subflow
- some attack/evo/result timing still needs device-side tuning
- some rescue / finish-game timing still needs device-side tuning

2. The Glyph rear-display renderer is still too text-heavy.

Text-heavy states collapse poorly on a `25x25` mono matrix.

3. The runtime still mixes a lot of state logic and the Glyph-side composition in one file.

Phone rendering has already been split out, but the runtime still owns too much boot/status/menu plumbing.

### Exact Next Steps

Do these in order:

1. Refine `ExactPhoneRenderer` against the remaining source scripts instead of adding more synthetic screens.

Implemented file:

- `app/src/main/java/com/digimon/digiviceglyph/runtime/ExactPhoneRenderer.kt`

Current responsibilities:

- accept runtime state and battle state
- render `160x160` in-app preview
- use original sprites and positions

2. Finish tightening the original start sequence timings and transitions.

Source:

- `gml_Object_obj_start_digivice_v1_Draw_0.gml`

Implemented:

- multi-phase start state
- blink timing
- horizontal slide phase
- happy confirmation phase

Check next:

- exact timing cadence against device preview
- whether the final happy phase should linger longer before entering idle

3. Tighten the original idle character screen and mood overlays.

Source:

- `gml_Object_obj_char_digivice_v1_Draw_0.gml`

Required behavior:

- normal sprite
- step sprite
- defeat sprite + `spr_defeat`
- happy sprite + `spr_happy`

4. Finish separating the two different original stat flows.

Source:

- `gml_Object_obj_menu_stats_digivice_v1_Draw_0.gml`

Already present:

- number rendering by `spr_numbers`

Still needed:

- determine where `obj_menu_stats_digivice_v1` belongs in the menu flow vs the partner-status flow
- align that runtime route more closely with the original object transitions

5. Implement exact map screen using:

- `spr_map__digivice_v1`
- `spr_map_cover`
- `spr_map_change`
- `spr_numbers`

Sources:

- `gml_Object_obj_menu_map_digivice_v1_Draw_0.gml`
- `gml_Object_obj_menu_change_map_digivice_v1_Draw_0.gml`

Implemented since the first handoff:

- `MAP` now behaves like the overview screen
- current area blink is source-driven
- completed areas use `state.areas == 2`
- `MAP_CHANGE` is now a separate screen showing `spr_map_change` plus number sprites

Still needed:

- persist per-area distances instead of reusing `MAP_DISTANCES[area]` for non-current areas
- verify final map-complete flow on device

6. Only after phone visuals improve, simplify the Glyph renderer more aggressively.

Glyph guidance:

- use icons and silhouettes
- avoid raw text where possible
- never mirror dense phone layouts 1:1

### Recommended Refactor Boundary

Keep:

- `DigiviceV1Runtime` as gameplay state + input + transitions

Move out:

- phone preview drawing
- sprite placement
- number-sprite composition
- screen-specific visual composition

Potential new files:

- `ExactPhoneRenderer.kt`
- `NumberSpriteRenderer.kt`
- `GlyphStateRenderer.kt`

### Known Good Behavior To Preserve

- shared runtime between activity and service
- save/load compatibility
- wireless install path
- explicit `GlyphRenderer.turnOff()` clear before each frame push
- A/B/C phone controls already mapped through the same runtime

### Short-Term Success Criteria

A good next iteration should achieve all of these:

1. The in-app preview no longer looks like a debug or approximate screen.
2. The start sequence resembles the original app.
3. The idle partner screen resembles the original app.
4. Stats and map use original UI plates and number sprites.
5. The Glyph rear screen remains secondary and readable.

### Latest Source Notes

Map object behavior:

- `obj_menu_map_digivice_v1`:
  - `A` only opens change-map when `game_complete`
  - `B` cycles areas only when `game_complete`
  - `display` blinks every `30` ticks
- `obj_menu_change_map_digivice_v1`:
  - draws only its own sprite plus `draw_number_with_sprite(new_distance, 25, 8, spr_numbers)`

Battle object behavior now mapped:

- `obj_battle_start_digivice_v1`:
  - alert splash is a blinking layer over the partner attack sprite
  - 24px enemy sprites use different intro positioning
- `obj_battle_menu_digivice_v1`:
  - action selection is by object `image_index` / sprite subimage, not a separate cursor
- `obj_battle_push_digivice_v1`:
  - no custom draw script; visual is just the object sprite
- `obj_battle_swap_digivice_v1`:
  - base card sprite plus attack sprite blink for the chosen partner
- `obj_mine_attack_digivice_v1`:
  - phased projectile/explosion/hp-bar sequence now approximated in the phone renderer
- `obj_enemy_attack_digivice_v1`:
  - mirrored projectile/explosion/hp-bar sequence now approximated in the phone renderer
- `obj_ready_go_digivice_v1`:
  - evo charge plate now has its own phase using the recovered sprite
- `obj_evo_digivice_v1`:
  - evo transition sequence now has a dedicated renderer phase using `spr_evo_digivice_v1` and `spr_evo_filter`
- `obj_finish_battle_digivice_v1`:
  - post-battle slide-in is now represented by a dedicated finish phase
- `obj_save_char_digivice_v1`:
  - rescue now follows a timed phase sequence with filter levels and a press window
- `obj_finish_digivice_v1`:
  - final-area completion now has a dedicated finish-game screen using the recovered end sprite

Next likely renderer slice:

1. Tune attack/evo/rescue/finish timing against real device preview
2. Add better happy/sad continuity after the finish slide into the idle object flow
3. Port `obj_swap_map_digivice_v1` more faithfully
