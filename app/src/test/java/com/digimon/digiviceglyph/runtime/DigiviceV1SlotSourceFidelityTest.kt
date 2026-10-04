package com.digimon.digiviceglyph.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class DigiviceV1SlotSourceFidelityTest {
    @Test
    fun `all original slot reel pairs have source faithful reward distances`() {
        // Original android_cli/CodeEntries/obj_game_digivice_v1 Create_0:1
        // and Step_0:3-39. Only +/-1 occur in the original 16-entry table.
        val originalPoints = intArrayOf(1, -1, -1, 1, 1, -1, 1, 1, -1, 1, -1, -1, -1, 1, -1, 1)
        val pointField = DigiviceV1Runtime::class.java.getDeclaredField("SLOT_POINTS")
        pointField.isAccessible = true
        org.junit.Assert.assertArrayEquals(originalPoints, pointField.get(null) as IntArray)
        for (left in originalPoints.indices) {
            for (right in originalPoints.indices) {
                val expected = when {
                    originalPoints[left] != originalPoints[right] -> 0
                    left == right -> originalPoints[left] * 200
                    else -> originalPoints[left] * 100
                }
                assertEquals("original reels $left/$right", expected,
                    DigiviceV1Runtime.computeSlotResultDistance(
                        originalPoints[left], originalPoints[right], left == right))
            }
        }
    }

    @Test
    fun `equal point values do not receive identical sprite bonus`() {
        // Create_0:2 maps index 0 to Agumon, index 3 to Biyomon (both +1),
        // and index 1 to Tyranomon, index 2 to PicoDevimon (both -1).
        assertEquals(100, DigiviceV1Runtime.computeSlotResultDistance(1, 1, false))
        assertEquals(-100, DigiviceV1Runtime.computeSlotResultDistance(-1, -1, false))
        assertEquals(200, DigiviceV1Runtime.computeSlotResultDistance(1, 1, true))
        assertEquals(-200, DigiviceV1Runtime.computeSlotResultDistance(-1, -1, true))
    }
}
