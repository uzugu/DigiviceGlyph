package com.digimon.digiviceglyph.input

import kotlin.math.abs
import kotlin.math.max

internal class FlickGestureDetector {
    enum class Gesture {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    private enum class Axis {
        NONE,
        X,
        Y
    }

    companion object {
        private const val START_THRESHOLD = 4.6f
        private const val REBOUND_THRESHOLD = 2.5f
        private const val REBOUND_RATIO_OF_START = 0.5f
        private const val AXIS_DOMINANCE_RATIO = 1.2f
        private const val DIRECTION_EPSILON = 0.15f
        private const val MIN_REBOUND_DELAY_MS = 26L
        private const val FLICK_WINDOW_MS = 230L
        private const val COOLDOWN_MS = 260L
    }

    private var pendingAxis = Axis.NONE
    private var pendingDirection = 0
    private var pendingStartAbs = 0f
    private var pendingSinceMs = 0L
    private var lastGestureAtMs: Long? = null

    fun update(nowMs: Long, x: Float, y: Float, z: Float): Gesture? {
        if (pendingAxis == Axis.NONE) {
            beginGestureIfEligible(nowMs, x, y, z)
            return null
        }

        if (nowMs - pendingSinceMs > FLICK_WINDOW_MS) {
            clearPending()
            beginGestureIfEligible(nowMs, x, y, z)
            return null
        }

        val value = when (pendingAxis) {
            Axis.X -> x
            Axis.Y -> y
            Axis.NONE -> return null
        }
        val orthogonalAbs = when (pendingAxis) {
            Axis.X -> max(abs(y), abs(z))
            Axis.Y -> max(abs(x), abs(z))
            Axis.NONE -> return null
        }
        val direction = signedDirection(value)
        val reboundAbs = abs(value)
        val reboundThreshold = max(REBOUND_THRESHOLD, pendingStartAbs * REBOUND_RATIO_OF_START)
        val enoughDelay = nowMs - pendingSinceMs >= MIN_REBOUND_DELAY_MS
        val axisDominant = reboundAbs >= orthogonalAbs * AXIS_DOMINANCE_RATIO

        if (
            direction != 0 &&
            direction == -pendingDirection &&
            enoughDelay &&
            axisDominant &&
            reboundAbs >= reboundThreshold
        ) {
            val gesture = when (pendingAxis) {
                Axis.X -> if (pendingDirection > 0) Gesture.RIGHT else Gesture.LEFT
                Axis.Y -> if (pendingDirection > 0) Gesture.DOWN else Gesture.UP
                Axis.NONE -> return null
            }
            lastGestureAtMs = nowMs
            clearPending()
            return gesture
        }

        return null
    }

    fun reset() {
        clearPending()
        lastGestureAtMs = null
    }

    private fun beginGestureIfEligible(nowMs: Long, x: Float, y: Float, z: Float) {
        val lastGesture = lastGestureAtMs
        if (lastGesture != null && nowMs - lastGesture < COOLDOWN_MS) return

        val absX = abs(x)
        val absY = abs(y)
        val absZ = abs(z)
        val axis: Axis
        val value: Float
        when {
            absX >= absY && absX >= absZ &&
                absX >= max(absY, absZ) * AXIS_DOMINANCE_RATIO -> {
                axis = Axis.X
                value = x
            }
            absY >= absX && absY >= absZ &&
                absY >= max(absX, absZ) * AXIS_DOMINANCE_RATIO -> {
                axis = Axis.Y
                value = y
            }
            else -> return
        }
        if (abs(value) < START_THRESHOLD) return

        pendingAxis = axis
        pendingDirection = if (value >= 0f) 1 else -1
        pendingStartAbs = abs(value)
        pendingSinceMs = nowMs
    }

    private fun signedDirection(value: Float): Int = when {
        value > DIRECTION_EPSILON -> 1
        value < -DIRECTION_EPSILON -> -1
        else -> 0
    }

    private fun clearPending() {
        pendingAxis = Axis.NONE
        pendingDirection = 0
        pendingStartAbs = 0f
        pendingSinceMs = 0L
    }
}
