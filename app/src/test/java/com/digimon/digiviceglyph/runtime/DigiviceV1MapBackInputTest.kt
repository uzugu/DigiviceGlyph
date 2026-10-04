package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** Oracle obj_menu_map_digivice_v1 KeyPress_40:1-5 stops audio and plays
 * sound_select BEFORE creating MENU with image_index=1. BACK aliases C in the port.
 * Tests actual production cue admission, not native playback or object-entry drawing. */
class DigiviceV1MapBackInputTest {
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

    @Test fun `map C and BACK stopAll then SELECT before returning to MAP menu item without progression`() {
        for (button in listOf(GlyphButton.C, GlyphButton.BACK))
            for (enabled in listOf(true, false)) for (complete in listOf(true, false))
                for (preview in 0..6) {
                    val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
                    val state = DigiviceV1State().apply {
                        startSequencePending = false
                        soundEnabled = enabled
                        areas = if (complete) IntArray(7) { 2 } else intArrayOf(2, 2, 1, 0, 0, 0, 0)
                        area = 4; distance = 4321; steps = 246; dpower = 135
                        lastEncounter = DigiviceEncounter("battle", 4321, 246, 135)
                    }
                    val before = state.copy(areas = state.areas.copyOf(),
                        perAreaDistances = state.perAreaDistances.copyOf(),
                        unlockedChars = state.unlockedChars.copyOf())
                    val audio = allocate(DigiviceAudioManager::class.java) as DigiviceAudioManager
                    val events = mutableListOf<String>()
                    fun boundary() {
                        // Unlike MAP_CHANGE back, MAP back performs audio before destination creation.
                        assertEquals(screen("MAP"), get(runtime, "screen"))
                        assertEquals(5, get(runtime, "menuIndex"))
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
                    // Source explicitly sets new menu image_index=1; do not rely on stale menu state.
                    set(runtime, "menuIndex", 5)
                    set(runtime, "mapPreviewArea", preview)
                    set(audio, "lastPlayedAt", admitted)
                    set(audio, "soundIds", mutableMapOf<DigiviceAudioManager.Cue, Int>())
                    set(audio, "activeStreams", streams)
                    set(audio, "soundPool", allocate(Class.forName("android.media.SoundPool")))

                    runtime.onButtonDown(button)

                    assertEquals("$button enabled=$enabled complete=$complete preview=$preview",
                        if (enabled) listOf("stopAll", "SELECT") else listOf("stopAll"), events)
                    assertEquals(screen("MENU"), get(runtime, "screen"))
                    assertEquals(1, get(runtime, "menuIndex"))
                    assertEquals(preview, get(runtime, "mapPreviewArea"))
                    assertArrayEquals(before.areas, state.areas)
                    assertArrayEquals(before.perAreaDistances, state.perAreaDistances)
                    assertArrayEquals(before.unlockedChars, state.unlockedChars)
                    assertEquals(before.copy(areas = state.areas, perAreaDistances = state.perAreaDistances,
                        unlockedChars = state.unlockedChars), state)
                }
    }
}
