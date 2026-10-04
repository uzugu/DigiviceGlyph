package com.digimon.digiviceglyph.runtime

import com.digimon.digiviceglyph.input.GlyphButton
import org.junit.Assert.*
import org.junit.Test

/** Original V1 KeyPress_38 and Alarm_0/1; APK GEN8 GMS2FPS=60.
 * Actual button dispatch and scheduler; Android construction bypassed, deadlines forced without sleeps. */
class DigiviceV1SlotRearmTest {
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
    private fun ready(): DigiviceV1Runtime {
        val runtime = allocate(DigiviceV1Runtime::class.java) as DigiviceV1Runtime
        set(runtime, "state", DigiviceV1State().apply { soundEnabled = false; startSequencePending = false })
        val audio = allocate(DigiviceAudioManager::class.java)
        // Unsafe bypasses field initialization; initial slot start now calls real stopAll.
        set(audio, "activeStreams", linkedSetOf<Int>())
        set(audio, "soundPool", allocate(Class.forName("android.media.SoundPool")))
        set(runtime, "audioManager", audio)
        val screen = Class.forName("com.digimon.digiviceglyph.runtime.DigiviceV1Runtime\$Screen")
            .enumConstants!!.first { (it as Enum<*>).name == "MENU" }
        set(runtime, "screen", screen); set(runtime, "menuIndex", 2)
        runtime.onButtonDown(GlyphButton.A)
        return runtime
    }
    private fun deadline(session: Any, field: String, delay: Long, action: () -> Unit) {
        val before = System.currentTimeMillis(); action(); val after = System.currentTimeMillis()
        val actual = get(session, field) as Long
        assertTrue("$field expected ${delay}ms, actual ${actual - before}ms", actual in (before + delay)..(after + delay))
    }
    @Test fun `stopped reels advance three times at twenty four frame cadence then disable`() {
        val runtime = ready(); val session = get(runtime, "slotSession")!!
        runtime.onButtonDown(GlyphButton.A)
        runtime.onButtonDown(GlyphButton.A); runtime.onButtonDown(GlyphButton.A)
        set(session, "roll1", 15); set(session, "roll2", 14)
        set(session, "nextReel1AtMs", System.currentTimeMillis() + 60000L)
        set(session, "nextReel2AtMs", System.currentTimeMillis() + 60000L)
        for (reel in 1..2) {
            val initial = get(session, "roll$reel") as Int
            for (count in 1..3) {
                set(session, "nextReel${reel}AtMs", 1L)
                if (count < 3) deadline(session, "nextReel${reel}AtMs", 400L) { advance(runtime) }
                else advance(runtime)
                assertEquals(count, get(session, "counter$reel"))
                assertEquals((initial + count) % 16, get(session, "roll$reel"))
                if (count < 3) {
                    set(session, "nextReel${reel}AtMs", System.currentTimeMillis() + 60000L)
                    advance(runtime)
                    assertEquals(count, get(session, "counter$reel"))
                }
            }
            assertEquals(0L, get(session, "nextReel${reel}AtMs"))
            val stoppedRoll = get(session, "roll$reel")
            advance(runtime)
            assertEquals(stoppedRoll, get(session, "roll$reel"))
            assertEquals(3, get(session, "counter$reel"))
            assertEquals(if (reel == 1) "SPINNING" else "RESULT_WAIT", (get(session, "phase") as Enum<*>).name)
        }
    }
    @Test fun `each stop replaces a pending alarm with twelve original frames`() {
        val runtime = ready(); val session = get(runtime, "slotSession")!!
        runtime.onButtonDown(GlyphButton.A)
        for (reel in 1..2) {
            val other = 3 - reel
            // An already due alarm must be replaced, not merely retained or slowed after firing.
            set(session, "nextReel${reel}AtMs", 1L)
            val otherDeadline = System.currentTimeMillis() + 60000L
            set(session, "nextReel${other}AtMs", otherDeadline)
            val roll = get(session, "roll$reel")
            deadline(session, "nextReel${reel}AtMs", 200L) { runtime.onButtonDown(GlyphButton.A) }
            assertEquals(true, get(session, "stop$reel"))
            assertEquals(otherDeadline, get(session, "nextReel${other}AtMs"))
            assertEquals(0, get(session, "counter$reel"))
            // Move both deadlines into the future before invoking, avoiding wall-clock sleeps.
            set(session, "nextReel${reel}AtMs", System.currentTimeMillis() + 60000L)
            advance(runtime)
            assertEquals(roll, get(session, "roll$reel"))
            assertEquals(0, get(session, "counter$reel"))
        }
        val left = get(session, "nextReel1AtMs"); val right = get(session, "nextReel2AtMs")
        runtime.onButtonDown(GlyphButton.A) // Both stopped: no third rearm.
        assertEquals(left, get(session, "nextReel1AtMs")); assertEquals(right, get(session, "nextReel2AtMs"))
    }
    @Test fun `both reels start and free spin every six original frames`() {
        val runtime = ready(); val session = get(runtime, "slotSession")!!
        set(session, "roll1", 15); set(session, "roll2", 15)
        advance(runtime)
        assertEquals(0L, get(session, "nextReel1AtMs")); assertEquals(0L, get(session, "nextReel2AtMs"))
        deadline(session, "nextReel1AtMs", 100L) { runtime.onButtonDown(GlyphButton.A) }
        assertEquals(get(session, "nextReel1AtMs"), get(session, "nextReel2AtMs"))
        for (reel in 1..2) {
            set(session, "nextReel${reel}AtMs", System.currentTimeMillis() + 60000L)
        }
        advance(runtime)
        assertEquals(15, get(session, "roll1")); assertEquals(15, get(session, "roll2"))
        for (reel in 1..2) {
            set(session, "nextReel${reel}AtMs", 1L)
            deadline(session, "nextReel${reel}AtMs", 100L) { advance(runtime) }
            assertEquals(0, get(session, "roll$reel"))
            assertEquals(0, get(session, "counter$reel"))
        }
    }
}
