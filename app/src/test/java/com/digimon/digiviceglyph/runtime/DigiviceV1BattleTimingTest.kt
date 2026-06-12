package com.digimon.digiviceglyph.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigiviceV1BattleTimingTest {
    @Test
    fun readyGoUsesTwoSixSecondPagesBeforeEvoAnimationStarts() {
        assertEquals(0, DigiviceV1Runtime.computeReadyGoState(0L).frame)
        assertEquals(1, DigiviceV1Runtime.computeReadyGoState(6_500L).frame)
        assertTrue(DigiviceV1Runtime.computeReadyGoState(12_000L).finished)
    }

    @Test
    fun pushWindowMatchesOriginalTenSecondAlarm() {
        val opening = DigiviceV1Runtime.computePushState(0L)
        val almostDone = DigiviceV1Runtime.computePushState(9_900L)
        val finished = DigiviceV1Runtime.computePushState(10_000L)

        assertEquals(300, opening.remainingTicks)
        assertFalse(opening.finished)
        assertTrue(almostDone.remainingTicks in 1..3)
        assertFalse(almostDone.finished)
        assertEquals(0, finished.remainingTicks)
        assertTrue(finished.finished)
    }

    @Test
    fun evoSequenceLiftsSuccessfulFormInTwoFourPixelSteps() {
        val preRise = DigiviceV1Runtime.computeEvoSequenceState(18_000L)
        val firstRise = DigiviceV1Runtime.computeEvoSequenceState(20_500L)
        val endRise = DigiviceV1Runtime.computeEvoSequenceState(24_500L)

        assertEquals(25, preRise.currentMenu)
        assertEquals(0, preRise.posY)
        assertEquals(26, firstRise.currentMenu)
        assertEquals(0, firstRise.posY)
        assertEquals(28, endRise.currentMenu)
        assertEquals(8, endRise.posY)
    }

    @Test
    fun evoSequenceEndsAfterSecondRiseAndFinalPause() {
        val finalRise = DigiviceV1Runtime.computeEvoSequenceState(25_900L)
        val finished = DigiviceV1Runtime.computeEvoSequenceState(27_900L)

        assertEquals(29, finalRise.currentMenu)
        assertEquals(8, finalRise.posY)
        assertTrue(finished.finished)
    }

    @Test
    fun mineAttackNonEvoUsesOriginalEarlyProjectileTimeline() {
        val state = DigiviceV1Runtime.computeAttackTimeline(
            elapsedMs = 1_600L,
            currentEvo = 0,
            boss = false,
            mineAttack = true
        )

        assertEquals(4, state.turn)
        assertEquals(-3, state.posX)
        assertTrue(state.hitTriggered)
        assertFalse(state.damageApplied)
    }

    @Test
    fun attackDamageAppliesAfterFasterOneSecondHpDropWait() {
        val beforeDamage = DigiviceV1Runtime.computeAttackTimeline(
            elapsedMs = 13_000L,
            currentEvo = 0,
            boss = false,
            mineAttack = true
        )
        val afterDamage = DigiviceV1Runtime.computeAttackTimeline(
            elapsedMs = 13_900L,
            currentEvo = 0,
            boss = false,
            mineAttack = true
        )

        assertFalse(beforeDamage.damageApplied)
        assertTrue(afterDamage.damageApplied)
        assertTrue(afterDamage.turn >= 48)
    }

    @Test
    fun finishDevolveFlashesCurrentFormBeforeSettlingOnBaseForm() {
        val opening = DigiviceV1Runtime.computeFinishDevolveState(elapsedMs = 0L, currentEvo = 2)
        val longFlash = DigiviceV1Runtime.computeFinishDevolveState(elapsedMs = 6_500L, currentEvo = 2)
        val baseFlash = DigiviceV1Runtime.computeFinishDevolveState(elapsedMs = 8_100L, currentEvo = 2)
        val finished = DigiviceV1Runtime.computeFinishDevolveState(elapsedMs = 13_000L, currentEvo = 2)

        assertEquals(2, opening.displayedEvo)
        assertFalse(opening.drawFilter)
        assertTrue(longFlash.drawFilter)
        assertEquals(0, baseFlash.displayedEvo)
        assertTrue(finished.finished)
    }

    @Test
    fun finishSlideMatchesOriginalTwoPointFourSecondReturnToBasePosition() {
        val opening = DigiviceV1Runtime.computeFinishSlideState(0L)
        val midpoint = DigiviceV1Runtime.computeFinishSlideState(1_200L)
        val finished = DigiviceV1Runtime.computeFinishSlideState(2_400L)

        assertEquals(32, opening.posX)
        assertEquals(20, midpoint.posX)
        assertEquals(8, finished.posX)
        assertTrue(finished.finished)
    }

    @Test
    fun resultBlinkStartsOnHappyOrDefeatSpriteAndEndsAfterOriginalDelay() {
        val winOpening = DigiviceV1Runtime.computeResultBlinkState(0L, playerWon = true)
        val winSecond = DigiviceV1Runtime.computeResultBlinkState(1_200L, playerWon = true)
        val winFinished = DigiviceV1Runtime.computeResultBlinkState(8_000L, playerWon = true)
        val loseFinished = DigiviceV1Runtime.computeResultBlinkState(9_000L, playerWon = false)

        assertFalse(winOpening.animation)
        assertTrue(winSecond.animation)
        assertTrue(winFinished.finished)
        assertTrue(loseFinished.finished)
    }
}
