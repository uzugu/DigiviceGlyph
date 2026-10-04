package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** JVM fixture bypasses Android startup only; input, slot scheduler and reward/save are real.
 * Oracle: original android_cli Step_0:45 (240), distance Create_0:4 (120).
 * Original APK game.droid GEN8 GMS2FPS=60; no game_set_speed/room_speed override.
 */
class DigiviceV1SlotTimingTest {
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
    private fun enumValue(name: String, value: String): Any =
        Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$$name")
            .enumConstants!!.first { (it as Enum<*>).name == value }
    private fun advance(runtime: DigiviceV1Runtime) {
        // Same production scheduler called by renderFrame/renderPhoneFrame/renderGlyphContentFrame.
        runtime.javaClass.getDeclaredMethod("maybeAdvanceSlotGame").apply { isAccessible = true }.invoke(runtime)
    }
    private fun withStoppedSlot(block: (DigiviceV1Runtime, Any, DigiviceV1State) -> Unit) {
        val dir = Files.createTempDirectory("v1-slot-timing-").toFile()
        try {
            val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
            val state = DigiviceV1State().apply { soundEnabled = false; startSequencePending = false; distance = 1000 }
            val repo = allocate(DigiviceV1SaveRepository::class.java)
            set(repo, "filesDir", dir)
            set(runtime, "saveRepository", repo)
            set(runtime, "state", state)
            val audio = allocate(DigiviceAudioManager::class.java)
            // Unsafe bypasses field initialization; initial slot start now calls real stopAll.
            set(audio, "activeStreams", linkedSetOf<Int>())
            set(audio, "soundPool", allocate(Class.forName("android.media.SoundPool")))
            set(runtime, "audioManager", audio)
            set(runtime, "screen", enumValue("Screen", "MENU"))
            set(runtime, "menuIndex", 2)
            runtime.onButtonDown(GlyphButton.A) // Actual menu -> startSlotGame -> READY.
            val session = get(runtime, "slotSession")!!
            runtime.onButtonDown(GlyphButton.A) // READY -> SPINNING.
            runtime.onButtonDown(GlyphButton.A) // Stop left.
            runtime.onButtonDown(GlyphButton.A) // Stop right.
            // Deterministic stopped reel outcome; no reel-speed claim in this slice.
            set(session, "roll1", 0); set(session, "roll2", 0)
            set(session, "counter1", 3); set(session, "counter2", 3)
            set(session, "nextReel1AtMs", 0L); set(session, "nextReel2AtMs", 0L)
            block(runtime, session, state)
        } finally { dir.deleteRecursively() }
    }
    @Test fun `slot result applies reward once after 120 steps at original 60 FPS`() = withStoppedSlot { runtime, session, state ->
        advance(runtime)
        set(session, "nextPhaseAtMs", 0L)
        val before = System.currentTimeMillis()
        advance(runtime)
        val after = System.currentTimeMillis()
        assertEquals("RESULT_SCREEN", (get(session, "phase") as Enum<*>).name)
        assertEquals(1000, state.distance)
        assertEquals(false, get(session, "resultApplied"))
        val deadline = get(session, "nextPhaseAtMs") as Long
        assertTrue("120/60 seconds deadline: actual delay ${deadline - before}ms", deadline in (before + 2000L)..(after + 2000L))
        set(session, "nextPhaseAtMs", System.currentTimeMillis() + 60000L)
        advance(runtime)
        assertEquals(1000, state.distance)
        runtime.onButtonDown(GlyphButton.A) // Cannot replay until original close=true/application.
        assertSame(session, get(runtime, "slotSession"))
        set(session, "nextPhaseAtMs", 0L)
        advance(runtime)
        assertEquals(800, state.distance)
        assertEquals(800, state.perAreaDistances[state.area])
        assertEquals(true, get(session, "resultApplied"))
        assertNotNull(state.lastEncounter)
        val repo = get(runtime, "saveRepository")!!
        val dir = get(repo, "filesDir") as java.io.File
        assertTrue("Actual production save executes", java.io.File(dir, "digivice_v1_eu.dat").isFile)
        assertEquals("Encrypted production save reloads reward", 800, (repo as DigiviceV1SaveRepository).load()!!.distance)
        advance(runtime)
        assertEquals("Reward must not apply twice", 800, state.distance)
        runtime.onButtonDown(GlyphButton.A)
        assertNotSame(session, get(runtime, "slotSession"))
        assertEquals("SLOT", (get(runtime, "screen") as Enum<*>).name)
    }
    @Test fun `runtime reward application clamps wins and increases distance for losses`() {
        for ((roll, distance, expected) in listOf(Triple(0, 100, 0), Triple(1, 1000, 1200))) {
            withStoppedSlot { runtime, session, state ->
                state.distance = distance
                set(session, "roll1", roll); set(session, "roll2", roll)
                advance(runtime)
                set(session, "nextPhaseAtMs", 0L); advance(runtime)
                assertEquals(distance, state.distance)
                set(session, "nextPhaseAtMs", 0L); advance(runtime)
                assertEquals(expected, state.distance)
                assertEquals(expected, state.perAreaDistances[state.area])
                advance(runtime)
                assertEquals(expected, state.distance)
            }
        }
    }
    @Test fun `stopped reels wait 240 steps at original 60 FPS before result screen`() = withStoppedSlot { runtime, session, state ->
        val before = System.currentTimeMillis()
        advance(runtime)
        val after = System.currentTimeMillis()
        assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
        assertEquals(200, get(session, "resultDistance"))
        assertEquals(1000, state.distance)
        val deadline = get(session, "nextPhaseAtMs") as Long
        assertTrue("240/60 seconds deadline: actual delay ${deadline - before}ms", deadline in (before + 4000L)..(after + 4000L))
        set(session, "nextPhaseAtMs", System.currentTimeMillis() + 60000L)
        advance(runtime)
        assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
        assertEquals(1000, state.distance)
        set(session, "nextPhaseAtMs", 0L)
        advance(runtime)
        assertEquals("SLOT_RESULT", (get(runtime, "screen") as Enum<*>).name)
        assertEquals(1000, state.distance)
    }
}
