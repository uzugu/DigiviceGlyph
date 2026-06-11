package com.digimon.digiviceglyph.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class DigiviceV1MiniGameLogicTest {
    @Test
    fun slotPositiveMatchReducesDistanceByTwoHundred() {
        assertEquals(200, DigiviceV1Runtime.computeSlotResultDistance(1, 1, sameRoll = true))
    }

    @Test
    fun slotNegativePairAddsDistance() {
        assertEquals(-100, DigiviceV1Runtime.computeSlotResultDistance(-1, -1, sameRoll = false))
    }

    @Test
    fun safeCardShouldNotBePressed() {
        assertEquals(1, DigiviceV1Runtime.scoreCardSelection(isEnemyCard = false, pressed = false))
        assertEquals(-1, DigiviceV1Runtime.scoreCardSelection(isEnemyCard = false, pressed = true))
    }

    @Test
    fun enemyCardShouldBePressed() {
        assertEquals(1, DigiviceV1Runtime.scoreCardSelection(isEnemyCard = true, pressed = true))
        assertEquals(-1, DigiviceV1Runtime.scoreCardSelection(isEnemyCard = true, pressed = false))
    }
}
