---
name: flick-to-control-mapping
description: FlickSensorController maps X/Y/Z axes to B/C buttons and steps via dominantAxisAndValue, with cooldown and latch logic
source: auto-skill
extracted_at: '2026-06-11T11:08:12.343Z'
---

# Flick-to-Control Mapping

The Digivice uses three flick axes (X, Y, Z) to map to buttons (A, B, C) and steps. The control system has one physical button (A) and three flick axes that produce B, C, and step actions.

## Control Mapping

| Axis | Flick Direction | Action |
|------|-----------------|--------|
| X (horizontal) | Left | B button |
| X (horizontal) | Right | C button |
| Y (vertical) | Up/Down | Step (no button) |
| Z (depth) | Forward/Back | Step (no button) |
| Physical | Press/Release | A button |

## Key Classes

### GlyphButtonSink (interface)

```kotlin
interface GlyphButtonSink {
    fun onButtonDown(button: GlyphButton)
    fun onButtonUp(button: GlyphButton)
    fun triggerStep()  // Called by Y-axis flick
}
```

### GlyphInputController

The SensorEventListener that detects flicks from the linear acceleration sensor:

```kotlin
private enum class FlickAxis { NONE, X, Y, Z }
```

- `dominantAxisAndValue(filteredX, filteredZ)` → determines which axis has the strongest flick
- `processFlick(now)` → detects flick threshold, tracks cooldown, triggers action
- `triggerFlick(axis, direction, now)` → routes to correct action based on axis

### DigiviceV1Runtime

Implements GlyphButtonSink:

```kotlin
override fun triggerStep() {
    performStep()
}
```

## Flick Detection Flow

1. `processFlick()` checks if `pendingAxis == NONE`
2. Calls `dominantAxisAndValue(filteredX, filteredZ)` to find the strongest axis
3. Checks cooldown: Z axis has shorter cooldown (150ms) than X (260ms)
4. Verifies start threshold (4.6) and Y axis constraint (≤ 3.4)
5. Sets `pendingAxis`, `pendingDirection`, `pendingStartAbs`, `pendingSinceMs`
6. On rebound detection (direction reversal), calls `triggerFlick()`

## Common Pitfalls

1. **When expressions must be exhaustive**: Kotlin requires all enum branches. Missing Y branch causes compilation errors.
2. **triggerStep() must be `override`**: DigiviceV1Runtime implements GlyphButtonSink.
3. **sink.triggerStep() vs performStep()**: triggerFlick() calls `sink.triggerStep()` (interface method), not `performStep()` (private method).
4. **Y axis constraint**: `abs(filteredY) <= FLICK_MAX_Y_ABS_AT_START` (3.4) must hold for X/Z flicks to register.
5. **Cooldown tracking**: X and Z axes have separate cooldown timers (`lastFlickXMs` and `lastFlickZMs`).

## Verification Checklist

- [ ] `dominantAxisAndValue` checks all three axes (X, Y, Z)
- [ ] `triggerFlick` routes Y axis to `sink.triggerStep()`
- [ ] `triggerFlick` routes X axis to B/C buttons
- [ ] `triggerFlick` routes Z axis to B button
- [ ] All when expressions include Y branch
- [ ] `triggerStep()` has `override` modifier
- [ ] Cooldown values are correct (Z: 150ms, X: 260ms)
- [ ] Y axis constraint (3.4) prevents false flicks when tilting
