---
name: battle-system-mechanics
description: GameMaker Digivice battle system with digivolution phases, MAP_ENCOUNTERS area-based enemy spawning, and 500-step encounter mechanic
source: auto-skill
extracted_at: '2026-06-11T10:46:12.321Z'
---

# Battle System Mechanics

When implementing the Digivice battle system (port from GameMaker), the key mechanics are: digivolution phase transitions, MAP_ENCOUNTERS area-based enemy spawning, and the 500-step encounter loop.

## Battle Phase Flow

```
ALERT → MENU → PUSH → (EVO_SEQUENCE | MINE_ATTACK | ENEMY_ATTACK) → FINISH → RESULT
```

The digivolution flow specifically:

```
MENU → EVO → READY_GO → EVO_SEQUENCE → FINISH
```

## Key Rules

- **A** = confirm, **B** = advance, **C** = back
- **Push phase**: increment presses on C, check > 2
- **500-step encounter**: `state.steps % 500 == 0` triggers battle
- **MAP_ENCOUNTERS**: area-based enemy spawning using `MAP_ENCOUNTERS[state.area]`
- **calculateMilestone**: determines next encounter based on distance, steps, and dpower
- **Autorun**: 1 step/sec (1000ms interval) in IDLE screen
- **Distance counter**: decreases by 1 per step; when 0, triggers boss battle

## Enemy Spawning (MAP_ENCOUNTERS)

The MAP_ENCOUNTERS array maps each of the 7 areas to a pool of 3 enemy indices:

```kotlin
private val MAP_ENCOUNTERS = arrayOf(
    intArrayOf(0, 1, 2),   // Area 0: Scumon, Numemon, Shellmon (early game)
    intArrayOf(3, 0, 1),   // Area 1: Bakemon, Scumon, Numemon
    intArrayOf(4, 5, 3),   // Area 2: PicoDevimon, Gazimon, Bakemon
    intArrayOf(6, 7, 5),   // Area 3: Hangyomon, Anomalocarimon, Gazimon
    intArrayOf(8, 6, 4),   // Area 4: Tyranomon, Hangyomon, PicoDevimon
    intArrayOf(9, 10, 8),  // Area 5: Phantomon, Megadramon, Tyranomon
    intArrayOf(11, 9, 10)  // Area 6: WaruMonzaemon, Phantomon, Megadramon
)
```

Enemies are progressively stronger as you move through areas. Boss encounters use BOSS_ENEMY_IDS:

```kotlin
private val BOSS_ENEMY_IDS = intArrayOf(12, 13, 14, 15, 16, 17, 18)
// Devimon, Etemon, Myotismon, MetalSeadramon, Puppetmon, Mugendramon, Piedmon
```

## calculateMilestone

Determines the next encounter type (boss vs regular) and updates state:

```kotlin
private fun calculateMilestone(distance: Int, steps: Int, dpower: Int): DigiviceEncounter {
    val currentStepMod = steps % 500
    val stepsToNext = if (currentStepMod == 0) 500 else 500 - currentStepMod
    var nextSteps = steps + stepsToNext
    var nextDistance = (distance - stepsToNext).coerceAtLeast(0)
    var nextEnergy = (dpower + (stepsToNext / 100)).coerceAtMost(99)
    var type = "battle"
    if (distance <= stepsToNext) {
        val stepsToZero = distance.coerceAtLeast(0)
        nextDistance = 0
        nextSteps = steps + stepsToZero
        nextEnergy = (dpower + (stepsToZero / 100)).coerceAtMost(99)
        type = "boss"
        if (distance == 0) nextSteps = steps
    }
    return DigiviceEncounter(type, nextDistance, nextSteps, nextEnergy)
}
```

## ensureBattleSessionIfNeeded

Called when entering BATTLE screen with battlePending=true:

```kotlin
private fun ensureBattleSessionIfNeeded() {
    if (screen != Screen.BATTLE || !state.battlePending || battleSession != null) return
    val encounter = state.lastEncounter ?: calculateMilestone(state.distance, state.steps, state.dpower)
    val boss = state.distance == 0 || encounter.type == "boss"
    val enemyId = if (boss) {
        BOSS_ENEMY_IDS[state.area.coerceIn(BOSS_ENEMY_IDS.indices)]
    } else {
        val pool = MAP_ENCOUNTERS[state.area.coerceIn(MAP_ENCOUNTERS.indices)]
        pool[Random.nextInt(pool.size)]
    }
    val enemy = ENEMIES[enemyId]
    battleSession = BattleSession(enemyId, enemy.name, boss, mineHp, enemyHp, ...)
}
```

## Battle Phase Button Behavior

| Phase    | A (confirm)          | B (advance)       | C (back)                  |
|----------|----------------------|-------------------|---------------------------|
| ALERT    | advance to MENU      | advance to MENU   | double-press to flee (600ms window) |
| MENU     | confirm RUN          | advance to PUSH   | no-op (no longer abandons) |
| PUSH     | no-op (shake phone)  | no-op (shake)     | no-op (shake)             |
| MINE_ATK | no-op (watch anim)   | no-op (watch)     | no-op (watch)             |
| ENEMY_ATK| no-op (watch anim)   | no-op (watch)     | no-op (watch)             |
| FINISH   | advance to RESULT    | advance to RESULT | advance to RESULT         |

### ALERT double-press flee

First C press advances ALERT→MENU. Second C press within 600ms triggers flee. Track with `lastBackPressAtMs`:

```kotlin
private var lastBackPressAtMs: Long = 0L
private val ALERT_FLEE_WINDOW_MS = 600L
```

## PUSH Phase Timing

- **Original GML**: `alarm[0] = 300` at 30 fps → 10 seconds
- **Kotlin**: `pushAlarm = 150` at ~15 fps → ~10 seconds
- **Flick behavior**: `pushPress += 2` (no max cap, original GML has no coerceAtMost)
- **Win condition**: `pushPress * 3 > random(0, 101)`

## Attack Animation Timing (MINE_ATTACK / ENEMY_ATTACK)

- **stepMs = 40ms** (not 50ms)
- **3 phases** (mirroring original GML `obj_battle_fight_d3_v1`):
  - Charge: 0–30 ticks (~1.2s) — blink animation
  - Move: 30–60 ticks (~1.2s) — sprite crosses screen
  - Hit: 60–90 ticks (~1.2s) — hit sound at tick 60, complete at tick 90
- **Sprite movement**: `spriteX = 2 + moveProgress * 9`, enemy moves from 11→9

## Common Pitfalls

1. **READY_GO phase**: Must be set in `beginEvolutionSequence()` before EVO_SEQUENCE. Without it, digivolution skips the READY_GO phase and the charge bar doesn't render.

2. **EVO phase increment**: The EVO confirm button must increment charge (not start sequence immediately). The EVO advance button must increment charge by 1 (not random jump).

3. **Enemy positioning**: MENU (14→11), PUSH (14→11), ATK (13→11). The enemy sprite is displaced when switching between MINE_ATTACK and ENEMY_ATTACK phases.

4. **Manual step trigger**: The `triggerStep()` public method should be removed if not needed — the 500-step mechanic is the primary trigger.

5. **lastEncounter caching**: `calculateMilestone` is called in both `performStep` and `ensureBattleSessionIfNeeded`. The `?:` operator ensures it's only computed once when `lastEncounter` is already set.

6. **Autorun interval**: Must be 1000ms (1 step/sec), not 650ms.

7. **Float-to-Int in draw calls**: When using `moveProgress * N` in `drawAssetSprite`, cast to Int (e.g., `val spriteY = (11 - moveProgress * 2).toInt()`).

## Verification Checklist

- [ ] `state.steps % 500 == 0` triggers encounter in `performStep`
- [ ] `MAP_ENCOUNTERS[state.area]` returns correct pool for current area
- [ ] `BOSS_ENEMY_IDS` has 7 entries (one per area)
- [ ] `calculateMilestone` returns correct type (boss vs battle)
- [ ] READY_GO phase is set during digivolution
- [ ] EVO phase correctly increments charge
- [ ] Enemy sprite positioning is consistent across phases
- [ ] Autorun interval is 1000ms
- [ ] Distance counter decreases by 1 per step
- [ ] dpower increases by 1 every 100 steps
