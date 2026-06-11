---
name: audio-soundpool-cooldown
description: SoundPool cooldown bug with Long.MIN_VALUE overflow and sound toggle fix
source: auto-skill
extracted_at: '2026-06-11T16:16:21.441Z'
---

# Audio SoundPool Cooldown Bug Fix

The Digivice audio system uses Android's `SoundPool` to play 17 WAV files from `app/src/main/assets/audio/`. A subtle `Long.MIN_VALUE` overflow bug caused all sounds to be silently skipped.

## The Bug

In `DigiviceAudioManager.kt`, the cooldown check used `Long.MIN_VALUE` as the sentinel for "never played":

```kotlin
val previous = lastPlayedAt[cue] ?: Long.MIN_VALUE
if (now - previous < cue.minGapMs) {
    // Skip due to cooldown
    return
}
```

When `lastPlayedAt[cue]` is `null`, `Long.MIN_VALUE` is used. But `now` is a normal `SystemClock.elapsedRealtime()` timestamp (e.g., ~1000000000). The subtraction `now - Long.MIN_VALUE` overflows to a large **negative** number, and the condition `now - previous < cue.minGapMs` evaluates to `true` because a large negative number is always less than 50ms. This caused every sound to be skipped silently.

## The Fix

Use `0L` as the sentinel instead, and check `previous > 0`:

```kotlin
val previous = lastPlayedAt[cue] ?: 0L
if (previous > 0 && now - previous < cue.minGapMs) {
    return
}
```

When `previous` is `0L`, the `previous > 0` guard short-circuits and the sound plays immediately.

## Debugging Tips

1. **Check if sounds are loading**:
   ```
   adb logcat -d -s DigiviceAudio:D
   ```
   You should see `Loading sound_xxx.wav...` followed by `All sounds queued. Loaded count=17/17`.

2. **Check if sounds are being played**:
   ```
   adb logcat -d -s DigiviceAudio:D | findstr /i "Play Skip"
   ```
   Look for "Play" messages. If you see "Skip, cooldown" with a large negative value, the bug is present.

3. **Check if sound is enabled**:
   The `soundEnabled` flag is saved in the encrypted save file (`digivice_v1_eu.dat`). If `sound_digivice_v1` is `false` (encrypted as `34-80-11-66-50`), all sounds are skipped. Fix by changing to `true` (encrypted as `34-80-11-66-54`):
   ```
   adb shell "run-as com.digimon.digiviceglyph sed -i 's/\"sound_digivice_v1\":\"34-80-11-66-50\"/\"sound_digivice_v1\":\"34-80-11-66-54\"/' /data/data/com.digimon.digiviceglyph/files/digivice_v1_eu.dat"
   ```

4. **Check if sound is disabled vs cooldown skip**:
   - `Sound disabled, skipping X` → the sound toggle is off
   - `Skip X, cooldown (negative ms)` → Long.MIN_VALUE overflow bug
   - `Play X sampleId=Y streamId=Z` → sound played successfully

## Key Classes

### DigiviceAudioManager
- `app/src/main/java/com/digimon/digiviceglyph/runtime/DigiviceAudioManager.kt`
- Uses `SoundPool` with `setOnLoadCompleteListener` to track which samples are loaded
- `soundIds` map stores `Cookie` values from `SoundPool.load()`
- `loadedIds` set tracks which `sampleId` values are ready to play
- `lastPlayedAt` map tracks the last time each cue was played for cooldown

### DigiviceV1Runtime
- Calls `playSound(cue)` for various game events
- `state.soundEnabled` gates playback

### SoundToggle UI
- `btnSound` button in `activity_main.xml`
- `toggleSound()` and `isSoundEnabled()` on `DigiviceV1Runtime`
- `updateSoundButton()` in `MainActivity`

## Verification Checklist

- [ ] `lastPlayedAt` uses `0L` sentinel, not `Long.MIN_VALUE`
- [ ] `previous > 0` guard prevents the overflow case
- [ ] All 17 WAV files load successfully
- [ ] Sound toggle button works and reflects current state
- [ ] `sound_digivice_v1` in save file is `true` (or defaults to `true`)
