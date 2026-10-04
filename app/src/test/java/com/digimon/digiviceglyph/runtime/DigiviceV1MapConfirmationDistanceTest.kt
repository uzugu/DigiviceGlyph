package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** Read-only oracle: obj_menu_map_digivice_v1 KeyPress_37:5-16 snapshots
 * current-area live distance or other-area map distance at confirmation entry.
 * obj_menu_change_map_digivice_v1 Draw_0:2 displays that snapshot.
 * Tests actual input -> runtime -> phone snapshot, not decoded pixels or persistence. */
class DigiviceV1MapConfirmationDistanceTest {
    private fun allocate(type: Class<*>): Any {
        val u = Class.forName("sun.misc.Unsafe")
        val f = u.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return u.getMethod("allocateInstance", Class::class.java).invoke(f.get(null), type)
    }
    private fun set(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
    private fun screen(name: String): Any =
        Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
            .enumConstants!!.first { (it as Enum<*>).name == name }
    private fun targetDistance(runtime: DigiviceV1Runtime): Int {
        val snapshot = runtime.javaClass.getDeclaredMethod("buildPhoneSnapshot")
            .apply { isAccessible = true }.invoke(runtime)
        return snapshot.javaClass.getDeclaredMethod("getMapTargetDistance").invoke(snapshot) as Int
    }

    @Test fun `confirmation retains entry distance until canceled and reentered`() {
        for (preview in 0..6) {
            val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
            val state = DigiviceV1State().apply {
                startSequencePending = false; soundEnabled = false
                areas = IntArray(7) { 2 }
                area = 4; distance = 4321; steps = 246; dpower = 35
                perAreaDistances = IntArray(7) { 11000 + it * 123 }
            }
            val audio = allocate(DigiviceAudioManager::class.java) as DigiviceAudioManager
            set(audio, "activeStreams", linkedSetOf<Int>())
            set(runtime, "state", state)
            set(runtime, "audioManager", audio)
            set(runtime, "screen", screen("MAP"))
            set(runtime, "mapPreviewArea", preview)
            val expected = if (preview == state.area) state.distance else state.perAreaDistances[preview]
            assertEquals(expected, targetDistance(runtime))
            runtime.onButtonDown(GlyphButton.A)
            assertEquals(expected, targetDistance(runtime))
            assertEquals(4, state.area)
            assertEquals(4321, state.distance)
            // Distinct values expose recomputation; these are controlled fixture changes,
            // not a claim that normal confirmation ticks mutate gameplay distance.
            state.distance = 987
            state.perAreaDistances[preview] = 7654
            assertEquals("entry snapshot preview=$preview", expected, targetDistance(runtime))
            runtime.onButtonDown(GlyphButton.C)
            val refreshed = if (preview == state.area) 987 else 7654
            assertEquals(refreshed, targetDistance(runtime))
            runtime.onButtonDown(GlyphButton.A)
            assertEquals(refreshed, targetDistance(runtime))
        }
    }
}
