package com.digimon.digiviceglyph.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlickGestureDetectorTest {
    @Test
    fun positiveHorizontalImpulseMapsToRight() {
        val detector = FlickGestureDetector()

        assertNull(detector.update(1_000L, 5.2f, 0.2f, 0.1f))
        assertEquals(
            FlickGestureDetector.Gesture.RIGHT,
            detector.update(1_040L, -3.0f, 0.1f, 0.2f)
        )
    }

    @Test
    fun negativeHorizontalImpulseMapsToLeft() {
        val detector = FlickGestureDetector()

        assertNull(detector.update(1_000L, -5.2f, 0.2f, 0.1f))
        assertEquals(
            FlickGestureDetector.Gesture.LEFT,
            detector.update(1_040L, 3.0f, 0.1f, 0.2f)
        )
    }

    @Test
    fun verticalImpulseCountsOneStep() {
        val detector = FlickGestureDetector()

        assertNull(detector.update(1_000L, 0.2f, 5.2f, 0.1f))
        assertEquals(
            FlickGestureDetector.Gesture.VERTICAL_STEP,
            detector.update(1_040L, 0.1f, -3.0f, 0.2f)
        )
    }

    @Test
    fun zeroCrossingDoesNotCancelPendingFlick() {
        val detector = FlickGestureDetector()

        assertNull(detector.update(1_000L, 5.2f, 0.2f, 0.1f))
        assertNull(detector.update(1_030L, 0.05f, 0.1f, 0.1f))
        assertEquals(
            FlickGestureDetector.Gesture.RIGHT,
            detector.update(1_050L, -3.0f, 0.1f, 0.2f)
        )
    }

    @Test
    fun depthImpulseDoesNotPressAnyButton() {
        val detector = FlickGestureDetector()

        assertNull(detector.update(1_000L, 0.1f, 0.2f, 6.0f))
        assertNull(detector.update(1_040L, 0.2f, 0.1f, -4.0f))
    }

    @Test
    fun cooldownPreventsDuplicateGesture() {
        val detector = FlickGestureDetector()

        detector.update(1_000L, 5.2f, 0.2f, 0.1f)
        assertEquals(
            FlickGestureDetector.Gesture.RIGHT,
            detector.update(1_040L, -3.0f, 0.1f, 0.2f)
        )
        assertNull(detector.update(1_100L, 5.2f, 0.1f, 0.1f))
        assertNull(detector.update(1_140L, -3.0f, 0.1f, 0.1f))
    }
}
