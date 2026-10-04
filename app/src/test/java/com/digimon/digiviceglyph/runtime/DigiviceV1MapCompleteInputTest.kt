package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** Source: obj_menu_map_digivice_v1 Create_0:10-14, KeyPress_37:1-17,
 * KeyPress_38:1-10. Tests input/audio/transition boundaries, not native playback,
 * map-change distance snapshots, persistence or object-entry completion caching. */
class DigiviceV1MapCompleteInputTest {
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
    private fun screen(name: String): Any =
        Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
            .enumConstants!!.first { (it as Enum<*>).name == name }

    private class Fixture(
        val runtime: DigiviceV1Runtime,
        val state: DigiviceV1State,
        val before: DigiviceV1State,
        val events: MutableList<String>
    )

    private fun fixture(enabled: Boolean, preview: Int): Fixture {
        val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
        val state = DigiviceV1State().apply {
            startSequencePending = false
            soundEnabled = enabled
            areas = IntArray(7) { 2 }
            area = 4; distance = 4321; steps = 246; dpower = 135
        }
        val audio = allocate(DigiviceAudioManager::class.java) as DigiviceAudioManager
        val events = mutableListOf<String>()
        // Observe actual stopAll and cue admission before either transition or preview mutation.
        fun boundary() {
            assertEquals(screen("MAP"), get(runtime, "screen"))
            assertEquals(preview, get(runtime, "mapPreviewArea"))
        }
        val streams = object : LinkedHashSet<Int>() {
            override fun clear() { boundary(); events += "stopAll"; super.clear() }
        }
        val admitted = object : LinkedHashMap<DigiviceAudioManager.Cue, Long>() {
            override fun put(key: DigiviceAudioManager.Cue, value: Long): Long? {
                boundary(); events += key.name
                return super.put(key, value)
            }
        }
        set(runtime, "state", state)
        set(runtime, "audioManager", audio)
        set(runtime, "screen", screen("MAP"))
        set(runtime, "mapPreviewArea", preview)
        set(audio, "lastPlayedAt", admitted)
        set(audio, "soundIds", mutableMapOf<DigiviceAudioManager.Cue, Int>())
        set(audio, "activeStreams", streams)
        set(audio, "soundPool", allocate(Class.forName("android.media.SoundPool")))
        return Fixture(runtime, state, state.copy(areas = state.areas.copyOf()), events)
    }

    private fun assertProgressionUnchanged(f: Fixture) {
        assertEquals(f.before.area, f.state.area)
        assertEquals(f.before.distance, f.state.distance)
        assertEquals(f.before.steps, f.state.steps)
        assertEquals(f.before.dpower, f.state.dpower)
        assertArrayEquals(f.before.areas, f.state.areas)
        assertNull(f.state.lastEncounter)
    }

    @Test fun `complete map B stops all then SELECT before advancing and wrapping preview`() {
        for (enabled in listOf(true, false)) for (preview in 0..6) {
            val f = fixture(enabled, preview)
            f.runtime.onButtonDown(GlyphButton.B)
            assertEquals("B enabled=$enabled preview=$preview",
                if (enabled) listOf("stopAll", "SELECT") else listOf("stopAll"), f.events)
            assertEquals(screen("MAP"), get(f.runtime, "screen"))
            assertEquals(if (preview == 6) 0 else preview + 1, get(f.runtime, "mapPreviewArea"))
            assertProgressionUnchanged(f)
        }
    }

    @Test fun `complete map A stops all then SELECT before entering confirmation`() {
        for (enabled in listOf(true, false)) for (preview in 0..6) {
            val f = fixture(enabled, preview)
            f.runtime.onButtonDown(GlyphButton.A)
            assertEquals("A enabled=$enabled preview=$preview",
                if (enabled) listOf("stopAll", "SELECT") else listOf("stopAll"), f.events)
            assertEquals(screen("MAP_CHANGE"), get(f.runtime, "screen"))
            assertEquals(preview, get(f.runtime, "mapPreviewArea"))
            assertProgressionUnchanged(f)
        }
    }
}
