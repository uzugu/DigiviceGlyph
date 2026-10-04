package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** V1 Step_0:43-47 arms Alarm_3=30 at APK GEN8 60FPS; Alarm_3 selects by sign.
 * Real input/scheduler/audio-manager cue admission. Unsafe skips Android startup;
 * no samples loaded, so SoundPool playback and audible output are not exercised. */
class DigiviceV1SlotResultSoundTest {
    private fun allocate(type: Class<*>): Any {
        val u = Class.forName("sun.misc.Unsafe")
        val f = u.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return u.getMethod("allocateInstance", Class::class.java).invoke(f.get(null), type)
    }
    private fun get(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun set(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
    private fun advance(runtime: DigiviceV1Runtime) {
        runtime.javaClass.getDeclaredMethod("maybeAdvanceSlotGame").apply { isAccessible = true }.invoke(runtime)
    }
    @Test fun `result cue waits thirty frames then fires once for each result sign`() {
        // Actual SLOT_POINTS: indices 0=+1, 2=-1; mixed signs retain zero result.
        for ((rolls, expected) in listOf((0 to 0) to DigiviceAudioManager.Cue.START,
            (2 to 2) to DigiviceAudioManager.Cue.SAD_SMALL, (0 to 2) to DigiviceAudioManager.Cue.SAD_HAPPY)) {
            val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
            val state = DigiviceV1State().apply { soundEnabled = false; startSequencePending = false }
            set(runtime, "state", state)
            val audio = allocate(DigiviceAudioManager::class.java) as DigiviceAudioManager
            val admitted = mutableMapOf<DigiviceAudioManager.Cue, Long>()
            set(audio, "lastPlayedAt", admitted)
            set(audio, "soundIds", mutableMapOf<DigiviceAudioManager.Cue, Int>())
            set(audio, "activeStreams", linkedSetOf<Int>())
            set(audio, "soundPool", allocate(Class.forName("android.media.SoundPool")))
            set(runtime, "audioManager", audio)
            val screen = Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
                .enumConstants!!.first { (it as Enum<*>).name == "MENU" }
            set(runtime, "screen", screen); set(runtime, "menuIndex", 2)
            runtime.onButtonDown(GlyphButton.A)
            runtime.onButtonDown(GlyphButton.A)
            val session = get(runtime, "slotSession")!!
            set(session, "roll1", rolls.first); set(session, "roll2", rolls.second)
            set(session, "counter1", 3); set(session, "counter2", 3)
            state.soundEnabled = true
            val before = System.currentTimeMillis(); advance(runtime); val after = System.currentTimeMillis()
            assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
            assertTrue("$expected must not play immediately on reel completion", admitted.isEmpty())
            val due = get(session, "nextResultSoundAtMs") as Long
            assertTrue("Alarm_3 must be armed for 500ms", due in (before + 500L)..(after + 500L))
            set(session, "nextPhaseAtMs", System.currentTimeMillis() + 60000L)
            set(session, "nextResultSoundAtMs", System.currentTimeMillis() + 60000L)
            advance(runtime)
            assertTrue("no early cue", admitted.isEmpty())
            set(session, "nextResultSoundAtMs", 1L)
            advance(runtime)
            assertEquals(setOf(expected), admitted.keys)
            assertEquals(0L, get(session, "nextResultSoundAtMs"))
            admitted.clear() // Detect replay without relying on the audio cooldown.
            advance(runtime); advance(runtime)
            assertTrue("one-shot Alarm_3 must stay disabled", admitted.isEmpty())
            assertFalse(get(session, "resultApplied") as Boolean)
            assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
            runtime.onButtonDown(GlyphButton.C)
            // KeyPress_40:1-6 contradicts the former cancellation characterization.
            assertSame(session, get(runtime, "slotSession"))
            assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
            assertEquals(0L, get(session, "nextResultSoundAtMs"))
            advance(runtime)
            assertTrue("ignored C neither emits CANCEL nor replays the result cue", admitted.isEmpty())
        }
    }
}
