package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** Source: V1 Step_0:47 arms alarm[4]=30; Alarm_4:1-2 toggles/rearms 30.
 * Original APK GEN8 GMS2FPS=60 (/tmp/digivice-v1-slot-speed-evidence.json).
 * Bypasses Android construction only; actual button handlers and slot scheduler execute.
 */
class DigiviceV1SlotBlinkTest {
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
    @Test fun `stopped reel animation first toggles and rearms after 30 original frames`() {
        val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
        set(runtime, "state", DigiviceV1State().apply { soundEnabled = false; startSequencePending = false })
        val audio = allocate(DigiviceAudioManager::class.java)
        // Unsafe bypasses field initialization; initial slot start now calls real stopAll.
        set(audio, "activeStreams", linkedSetOf<Int>())
        set(audio, "soundPool", allocate(Class.forName("android.media.SoundPool")))
        set(runtime, "audioManager", audio)
        val screen = Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
            .enumConstants!!.first { (it as Enum<*>).name == "MENU" }
        set(runtime, "screen", screen)
        set(runtime, "menuIndex", 2)
        runtime.onButtonDown(GlyphButton.A) // Menu -> READY.
        val session = get(runtime, "slotSession")!!
        runtime.onButtonDown(GlyphButton.A) // READY -> SPINNING.
        runtime.onButtonDown(GlyphButton.A) // Stop left.
        runtime.onButtonDown(GlyphButton.A) // Stop right.
        // Seed completed counters only: reel cadence is outside this blink-only slice.
        set(session, "counter1", 3); set(session, "counter2", 3)
        set(session, "nextReel1AtMs", 0L); set(session, "nextReel2AtMs", 0L)
        val before = System.currentTimeMillis()
        advance(runtime)
        val after = System.currentTimeMillis()
        assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
        assertEquals(false, get(session, "animation"))
        val initial = get(session, "nextAnimationAtMs") as Long
        assertTrue("First blink 30/60 seconds: actual delay ${initial - before}ms", initial in (before + 500L)..(after + 500L))
        // Avoid real-time sleeps/jitter. Test future/past deadlines on the real scheduler.
        set(session, "nextPhaseAtMs", System.currentTimeMillis() + 60000L)
        set(session, "nextAnimationAtMs", System.currentTimeMillis() + 60000L)
        advance(runtime)
        assertEquals("No early toggle", false, get(session, "animation"))
        for (expected in listOf(true, false)) {
            set(session, "nextAnimationAtMs", 1L)
            val toggleBefore = System.currentTimeMillis()
            advance(runtime)
            val toggleAfter = System.currentTimeMillis()
            assertEquals(expected, get(session, "animation"))
            val rearmed = get(session, "nextAnimationAtMs") as Long
            assertTrue("Recurring blink 30/60 seconds: actual delay ${rearmed - toggleBefore}ms", rearmed in (toggleBefore + 500L)..(toggleAfter + 500L))
            assertEquals("RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
        }
    }
}
