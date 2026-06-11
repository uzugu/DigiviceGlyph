## Exact Visual Port Plan

Date: 2026-06-10

Update 2026-06-11:

- This document is still the right source-driven visual roadmap.
- It does not cover the verified Glyph SDK input constraints; see root `AGENT_HANDOFF.md` for that.
- Keep exact visual work focused on the in-app phone renderer.
- The rear Glyph Matrix should stay an adapted secondary renderer even when the phone view becomes exact.

### Goal

Recreate the original GameMaker `Digivice V1` visuals as closely as possible in the in-app phone preview.

The Nothing Glyph Matrix remains an adapted secondary view because the hardware is only `25x25` monochrome.

### Source Of Truth

Recovered GameMaker draw logic:

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

Recovered sprite assets already copied into app assets:

- partner sprites:
  - `spr_agu*`, `spr_gabu*`, `spr_biyo*`, `spr_pal*`, `spr_tento*`, `spr_goma*`, `spr_pata*`, `spr_gato*`
- evolutions:
  - `spr_grey`, `spr_garuru`, `spr_birdra`, `spr_toge`, `spr_kabuteri`, `spr_ikaku`, `spr_ange`, `spr_angewo_d`, `spr_metalgrey`, `spr_weregaruru`, `spr_garuda`, `spr_lily`, `spr_megakabuteri`, `spr_zudo`, `spr_magnaange`, `spr_magnadra`
- enemies:
  - `spr_scumon` through `spr_piedmon`
- ui:
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
  - `spr_happy`
  - `spr_defeat`

### Original Screen Composition Notes

#### Start Sequence

From `obj_start_digivice_v1_Draw_0`:

- menu `0`: `spr_start`
- menu `1`: `spr_start_pop`
- menu `2`: blink selected partner normal sprite at `x + 8`
- menu `3`: `spr_status_digivice_v1` plus candidate partner sprite
- menu `4`: blink chosen partner
- menu `5`: chosen partner slides horizontally
- menu `6+`: chosen partner alternates with happy sprite plus `spr_happy`

#### Idle Character Screen

From `obj_char_digivice_v1_Draw_0`:

- normal: current partner normal sprite
- stepping: current partner step sprite
- defeat: current partner defeat sprite plus `spr_defeat`
- happy flash: current partner happy sprite plus `spr_happy`

#### Stats Screen

From `obj_menu_stats_digivice_v1_Draw_0`:

- base background is object sprite via `draw_self()`
- uses `draw_number_with_sprite(..., spr_numbers)`
- pages:
  - distance
  - steps
  - dpower
  - wins / battles / win percentage

#### Map Screen

From `obj_menu_map_digivice_v1_Draw_0` and `obj_menu_change_map_digivice_v1_Draw_0`:

- base background is map sprite via `draw_self()`
- uses `global.map_pos_digivice_v1`
- current area or completed areas overlay `spr_map_cover`
- distance changes are rendered with `draw_number_with_sprite(..., spr_numbers)`

#### Evolution / Attack-Defense Panels

From `obj_sel_menu_digi_digivice_v1_Draw_0`:

- uses `spr_hp_bar_digivice_v1`
- overlays partner or evolution sprite at `x + 8`
- panel differs depending on stat page and current evolution tier

#### Battle

From battle objects:

- alert splash uses `spr_battle_alert_digivice_v1`
- base battle menu uses `spr_battle_menu_digivice_v1`
- push/mash screen uses `spr_battle_push_digivice_v1`
- swap screen uses object sprite plus partner attack sprite
- enemy / partner attack phases use actual attack sprites, not text labels

### Implementation Order

1. Build a dedicated `ExactPhoneRenderer` layer separate from the Glyph renderer.
2. Add helpers for:
   - exact sprite blitting
   - exact frame-based animation
   - `spr_numbers` digit composition
   - object-positioned overlays
3. Replace phone preview screens in this order:
   - start sequence
   - idle character screen
   - stats screen
   - map screen
   - battle alert/menu/push/swap
4. Keep runtime state machine separate from visuals so recovered behavior and recovered visuals can evolve independently.

### Immediate Next Slice

Implement these first in the phone preview:

1. Exact start sequence using `spr_start`, `spr_start_pop`, `spr_status_digivice_v1`, partner sprites, `spr_happy`.
2. Exact idle screen using normal/step/happy/defeat partner variants and overlay icons.
3. Exact stats screen using `spr_menu_stats_digivice_v1` plus `spr_numbers`.
