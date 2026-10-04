package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** V1 KeyPress_40:1-6 gates cancellation on counters, not phase or stop flags.
 * Real input/scheduler/audio cue admission; Android constructors and samples skipped. */
class DigiviceV1SlotBackInputTest {
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
    private fun fixture(): Pair<DigiviceV1Runtime, MutableMap<DigiviceAudioManager.Cue, Long>> {
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
        val screens = Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
        set(runtime, "screen", screens.enumConstants!!.first { (it as Enum<*>).name == "MENU" })
        set(runtime, "menuIndex", 2)
        runtime.onButtonDown(GlyphButton.A)
        state.soundEnabled = true
        return runtime to admitted
    }
    @Test fun `completed counters suppress C and BACK before and after result wait entry`() {
        for (button in listOf(GlyphButton.C, GlyphButton.BACK)) {
            val (runtime, admitted) = fixture()
            runtime.onButtonDown(GlyphButton.A)
            admitted.clear() // No cooldown can conceal a wrong CANCEL or replay.
            val session = get(runtime, "slotSession")!!
            set(session, "counter1", 3); set(session, "counter2", 3)
            runtime.onButtonDown(button) // Completed counters while still SPINNING.
            assertSame("$button must ignore completed counters before Step", session, get(runtime, "slotSession"))
            assertEquals("SLOT", (get(runtime, "screen") as Enum<*>).name)
            assertTrue("completed-counter back must be silent", admitted.isEmpty())
            advance(runtime)
            assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
            val due = get(session, "nextResultSoundAtMs")
            val phaseDue = get(session, "nextPhaseAtMs")
            val blinkDue = get(session, "nextAnimationAtMs")
            repeat(2) { runtime.onButtonDown(button) }
            assertSame(session, get(runtime, "slotSession"))
            assertEquals("SLOT", (get(runtime, "screen") as Enum<*>).name)
            assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
            assertEquals(due, get(session, "nextResultSoundAtMs"))
            assertEquals(phaseDue, get(session, "nextPhaseAtMs"))
            assertEquals(blinkDue, get(session, "nextAnimationAtMs"))
            assertTrue("ignored back cannot emit CANCEL", admitted.isEmpty())
            // Drive the preserved one-shot deadline without wall-clock sleeps.
            set(session, "nextResultSoundAtMs", 1L)
            advance(runtime)
            val expected = when ((get(session, "resultDistance") as Int).compareTo(0)) {
                1 -> DigiviceAudioManager.Cue.START
                -1 -> DigiviceAudioManager.Cue.SAD_SMALL
                else -> DigiviceAudioManager.Cue.SAD_HAPPY
            }
            assertEquals(setOf(expected), admitted.keys)
            assertEquals(0L, get(session, "nextResultSoundAtMs"))
            admitted.clear()
            runtime.onButtonDown(button)
            advance(runtime)
            assertSame(session, get(runtime, "slotSession"))
            assertTrue("ignored back must not replay result cue", admitted.isEmpty())
        }
    }
    @Test fun `incomplete counters still allow C and BACK cancellation`() {
        for (button in listOf(GlyphButton.C, GlyphButton.BACK)) {
            // READY, SPINNING, either reel finished, both stop flags but incomplete counters.
            for (counters in listOf(null, 0 to 0, 3 to 2, 2 to 3, 2 to 2)) {
                val (runtime, admitted) = fixture()
                val session = get(runtime, "slotSession")!!
                if (counters != null) {
                    runtime.onButtonDown(GlyphButton.A)
                    runtime.onButtonDown(GlyphButton.A)
                    runtime.onButtonDown(GlyphButton.B)
                    set(session, "counter1", counters.first); set(session, "counter2", counters.second)
                }
                admitted.clear()
                runtime.onButtonDown(button)
                assertNull("$button must allow incomplete cancellation: $counters", get(runtime, "slotSession"))
                assertEquals("MENU", (get(runtime, "screen") as Enum<*>).name)
                assertEquals(2, get(runtime, "menuIndex"))
                assertEquals(setOf(DigiviceAudioManager.Cue.CANCEL), admitted.keys)
            }
        }
    }
}
