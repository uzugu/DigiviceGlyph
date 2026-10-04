package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** Oracle: obj_game_digivice_v1 KeyPress_37/38:3-9 stops all audio before
 * SELECT on initial start only. Production runtime/audio manager are exercised;
 * empty streams/samples and Unsafe skip Android startup and actual playback. */
class DigiviceV1SlotStartAudioTest {
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

    @Test fun `initial A or B stops all before SELECT and stopping reels does neither`() {
        for (button in listOf(GlyphButton.A, GlyphButton.B)) {
            verifyStart(button, enabled = true)
        }
    }

    @Test fun `initial start still stops all when sound is disabled`() {
        for (button in listOf(GlyphButton.A, GlyphButton.B)) {
            verifyStart(button, enabled = false)
        }
    }

    private fun verifyStart(button: GlyphButton, enabled: Boolean) {
        val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
        val state = DigiviceV1State().apply { soundEnabled = false; startSequencePending = false }
        val audio = allocate(DigiviceAudioManager::class.java) as DigiviceAudioManager
        val events = mutableListOf<String>()
        // Observe the real stopAll collection-clear boundary, not a replacement audio manager.
        // This empty collection deliberately avoids android.jar SoundPool.stop's throwing stub.
        val streams = object : LinkedHashSet<Int>() {
            override fun clear() { events += "stopAll"; super.clear() }
        }
        val admitted = object : LinkedHashMap<DigiviceAudioManager.Cue, Long>() {
            override fun put(key: DigiviceAudioManager.Cue, value: Long): Long? {
                events += key.name
                return super.put(key, value)
            }
        }
        set(runtime, "state", state)
        set(audio, "lastPlayedAt", admitted)
        set(audio, "soundIds", mutableMapOf<DigiviceAudioManager.Cue, Int>())
        set(audio, "activeStreams", streams)
        set(audio, "soundPool", allocate(Class.forName("android.media.SoundPool")))
        set(runtime, "audioManager", audio)
        val screen = Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
            .enumConstants!!.first { (it as Enum<*>).name == "MENU" }
        set(runtime, "screen", screen); set(runtime, "menuIndex", 2)
        runtime.onButtonDown(GlyphButton.A)
        assertTrue("menu entry is outside this stop-all oracle", events.isEmpty())
        val session = get(runtime, "slotSession")!!
        assertEquals("READY", (get(session, "phase") as Enum<*>).name)
        state.soundEnabled = enabled
        val before = System.currentTimeMillis()
        runtime.onButtonDown(button)
        val after = System.currentTimeMillis()
        assertEquals("$button initial start must stop all before cue admission (enabled=$enabled)",
            if (enabled) listOf("stopAll", "SELECT") else listOf("stopAll"), events)
        assertEquals("SPINNING", (get(session, "phase") as Enum<*>).name)
        for (field in listOf("nextReel1AtMs", "nextReel2AtMs")) {
            assertTrue("$field remains the source-backed 6-frame start delay",
                (get(session, field) as Long) in (before + 100L)..(after + 100L))
        }
        events.clear(); admitted.clear() // Do not let SELECT cooldown hide an erroneous stop cue.
        runtime.onButtonDown(button)
        assertTrue(get(session, "stop1") as Boolean)
        assertFalse(get(session, "stop2") as Boolean)
        assertTrue("first stop must not stop audio or admit SELECT", events.isEmpty())
        runtime.onButtonDown(button)
        assertTrue(get(session, "stop2") as Boolean)
        assertTrue("second stop must not stop audio or admit SELECT", events.isEmpty())
        runtime.onButtonDown(button)
        assertTrue("already requested stops stay silent", events.isEmpty())
    }
}
