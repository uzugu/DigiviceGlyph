package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** Oracle: obj_menu_map_digivice_v1 Create_0:10-14 and KeyPress_37:19-23,
 * KeyPress_38:1-10. An incomplete map rejects A with stop-all then CANCEL;
 * B is silent and cannot move the preview. Real runtime/audio manager, no playback. */
class DigiviceV1MapIncompleteInputTest {
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

    @Test fun `incomplete map A stops all before CANCEL and B stays silent`() {
        verifyIncomplete(enabled = true)
    }

    @Test fun `incomplete map A still stops all with sound disabled`() {
        verifyIncomplete(enabled = false)
    }

    private fun verifyIncomplete(enabled: Boolean) {
        // Every one of the seven area flags gates completion, including the final one.
        for (incompleteArea in 0..6) {
            val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
            val state = DigiviceV1State().apply {
                startSequencePending = false
                soundEnabled = enabled
                areas = IntArray(7) { 2 }.also { it[incompleteArea] = 1 }
                area = 4; distance = 4321; steps = 246; dpower = 135
            }
            val beforeState = state.copy(areas = state.areas.copyOf())
            val audio = allocate(DigiviceAudioManager::class.java) as DigiviceAudioManager
            val events = mutableListOf<String>()
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
            val map = Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
                .enumConstants!!.first { (it as Enum<*>).name == "MAP" }
            set(runtime, "screen", map)
            set(runtime, "mapPreviewArea", 6)

            runtime.onButtonDown(GlyphButton.B)
            assertTrue("B is a silent no-op for incomplete area $incompleteArea", events.isEmpty())
            assertEquals(6, get(runtime, "mapPreviewArea"))
            assertEquals(map, get(runtime, "screen"))

            runtime.onButtonDown(GlyphButton.A)
            assertEquals("A rejection for incomplete area $incompleteArea (enabled=$enabled)",
                if (enabled) listOf("stopAll", "CANCEL") else listOf("stopAll"), events)
            assertEquals(map, get(runtime, "screen"))
            assertEquals(6, get(runtime, "mapPreviewArea"))
            assertEquals(beforeState.area, state.area)
            assertEquals(beforeState.distance, state.distance)
            assertEquals(beforeState.steps, state.steps)
            assertEquals(beforeState.dpower, state.dpower)
            assertArrayEquals(beforeState.areas, state.areas)
            assertNull(state.lastEncounter)
        }
    }
}
