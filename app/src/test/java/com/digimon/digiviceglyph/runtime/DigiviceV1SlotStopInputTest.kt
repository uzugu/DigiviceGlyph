package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** V1 KeyPress_37/38:1-20: SELECT is confined to initial start, never reel stops.
 * Executes real button dispatch and audio cue admission; no Android startup/samples. */
class DigiviceV1SlotStopInputTest {
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
    @Test fun `start admits SELECT but both stop buttons and redundant presses are silent`() {
        for (button in listOf(GlyphButton.A, GlyphButton.B)) {
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
            val screenClass = Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
            set(runtime, "screen", screenClass.enumConstants!!.first { (it as Enum<*>).name == "MENU" })
            set(runtime, "menuIndex", 2)
            runtime.onButtonDown(GlyphButton.A)
            val session = get(runtime, "slotSession")!!
            state.soundEnabled = true
            runtime.onButtonDown(button)
            assertEquals("start must still admit SELECT", setOf(DigiviceAudioManager.Cue.SELECT), admitted.keys)
            assertEquals("SPINNING", (get(session, "phase") as Enum<*>).name)
            admitted.clear() // Avoid cooldown masking a spurious SELECT on immediate stop.
            runtime.onButtonDown(button)
            assertTrue(get(session, "stop1") as Boolean)
            assertFalse(get(session, "stop2") as Boolean)
            assertTrue("$button first reel stop must be silent", admitted.isEmpty())
            runtime.onButtonDown(button)
            assertTrue(get(session, "stop2") as Boolean)
            assertTrue("$button second reel stop must be silent", admitted.isEmpty())
            val firstDue = get(session, "nextReel1AtMs")
            val secondDue = get(session, "nextReel2AtMs")
            runtime.onButtonDown(button)
            assertTrue("both stops requested must stay silent", admitted.isEmpty())
            assertEquals(firstDue, get(session, "nextReel1AtMs"))
            assertEquals(secondDue, get(session, "nextReel2AtMs"))
            set(session, "counter1", 3); set(session, "counter2", 3)
            runtime.javaClass.getDeclaredMethod("maybeAdvanceSlotGame").apply { isAccessible = true }.invoke(runtime)
            assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
            val soundDue = get(session, "nextResultSoundAtMs")
            val phaseDue = get(session, "nextPhaseAtMs")
            runtime.onButtonDown(button)
            assertTrue("completed reels must ignore start or stop cue", admitted.isEmpty())
            assertEquals(soundDue, get(session, "nextResultSoundAtMs"))
            assertEquals(phaseDue, get(session, "nextPhaseAtMs"))
            assertSame(session, get(runtime, "slotSession"))
        }
    }
}
