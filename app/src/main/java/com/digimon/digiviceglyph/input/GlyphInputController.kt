package com.digimon.digiviceglyph.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.abs

class GlyphInputController(context: Context) : SensorEventListener {
    private enum class FlickAxis { NONE, X, Z }

    companion object {
        private const val FLICK_START_THRESHOLD = 4.6f
        private const val FLICK_REBOUND_THRESHOLD = 2.5f
        private const val FLICK_WINDOW_MS = 230L
        private const val FLICK_COOLDOWN_MS = 260L
        private const val FLICK_B_COOLDOWN_MS = 150L
        private const val FLICK_MIN_REBOUND_DELAY_MS = 26L
        private const val FLICK_AXIS_DOMINANCE_RATIO = 1.2f
        private const val FLICK_MAX_Y_ABS_AT_START = 3.4f
        private const val FLICK_REBOUND_RATIO_OF_START = 0.5f
        private const val FLICK_PRESS_MS = 85L
        private const val B_FLICK_PRESS_MS = 75L
        private const val ACCEL_SMOOTH_ALPHA = 0.35f
        private const val ACCEL_GRAVITY_ALPHA = 0.82f
        private const val AXIS_X_POSITIVE_IS_BACK = true
        private const val FLICK_VIBRATE_MS = 160L
        private const val FLICK_VIBRATE_AMPLITUDE = 255
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometerFallback =
        if (linearAccelerationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var sink: GlyphButtonSink? = null
    private var started = false
    private var sensorsRegistered = false

    private var buttonAActive = false
    private var buttonBActive = false
    private var buttonCActive = false
    private var buttonALatchedByB = false
    private var buttonCLatchedByB = false
    private var glyphPhysicalDown = false

    private var linearX = 0f
    private var linearY = 0f
    private var linearZ = 0f
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    private var pendingAxis = FlickAxis.NONE
    private var pendingDirection = 0
    private var pendingStartAbs = 0f
    private var pendingSinceMs = 0L
    private var lastFlickXMs = 0L
    private var lastFlickZMs = 0L

    private val releaseATap = Runnable {
        if (buttonAActive) releaseA()
    }

    private val releaseBTap = Runnable {
        if (buttonBActive && !glyphPhysicalDown) {
            buttonBActive = false
            sink?.onButtonUp(GlyphButton.B)
        }
    }

    private val releaseCTap = Runnable {
        if (buttonCActive) releaseC()
    }

    fun attach(buttonSink: GlyphButtonSink) {
        sink = buttonSink
    }

    fun start() {
        if (started) return
        started = true
        registerSensors()
    }

    fun stop() {
        started = false
        sensorManager.unregisterListener(this)
        sensorsRegistered = false
        releaseAll()
    }

    fun onGlyphButtonDown() {
        glyphPhysicalDown = true
        if (!buttonAActive) {
            buttonAActive = true
            sink?.onButtonDown(GlyphButton.A)
        }
    }

    fun onGlyphButtonUp() {
        glyphPhysicalDown = false
        if (buttonAActive) {
            buttonAActive = false
            sink?.onButtonUp(GlyphButton.A)
        }
        if (buttonCLatchedByB) releaseC()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                linearX = event.values[0]
                linearY = event.values[1]
                linearZ = event.values[2]
                updateFilteredLinear()
                processFlick(now)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravityX = ACCEL_GRAVITY_ALPHA * gravityX + (1f - ACCEL_GRAVITY_ALPHA) * event.values[0]
                gravityY = ACCEL_GRAVITY_ALPHA * gravityY + (1f - ACCEL_GRAVITY_ALPHA) * event.values[1]
                gravityZ = ACCEL_GRAVITY_ALPHA * gravityZ + (1f - ACCEL_GRAVITY_ALPHA) * event.values[2]
                linearX = event.values[0] - gravityX
                linearY = event.values[1] - gravityY
                linearZ = event.values[2] - gravityZ
                updateFilteredLinear()
                processFlick(now)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun registerSensors() {
        if (sensorsRegistered) return
        val delay = SensorManager.SENSOR_DELAY_GAME
        rotationSensor?.let { sensorManager.registerListener(this, it, delay) }
        linearAccelerationSensor?.let { sensorManager.registerListener(this, it, delay) }
        accelerometerFallback?.let { sensorManager.registerListener(this, it, delay) }
        sensorsRegistered = true
    }

    private fun updateFilteredLinear() {
        filteredX += (linearX - filteredX) * ACCEL_SMOOTH_ALPHA
        filteredY += (linearY - filteredY) * ACCEL_SMOOTH_ALPHA
        filteredZ += (linearZ - filteredZ) * ACCEL_SMOOTH_ALPHA
    }

    private fun processFlick(now: Long) {
        if (pendingAxis == FlickAxis.NONE) {
            val (axis, value) = dominantAxisAndValue(filteredX, filteredZ)
            if (axis == FlickAxis.NONE) return
            val cooldown = if (axis == FlickAxis.Z) FLICK_B_COOLDOWN_MS else FLICK_COOLDOWN_MS
            val lastFlick = if (axis == FlickAxis.Z) lastFlickZMs else lastFlickXMs
            if (now - lastFlick < cooldown) return
            val startAbs = abs(value)
            if (startAbs >= FLICK_START_THRESHOLD && abs(filteredY) <= FLICK_MAX_Y_ABS_AT_START) {
                pendingAxis = axis
                pendingDirection = if (value >= 0f) 1 else -1
                pendingStartAbs = startAbs
                pendingSinceMs = now
            }
            return
        }

        if (now - pendingSinceMs > FLICK_WINDOW_MS) {
            clearPending()
            return
        }

        val value = when (pendingAxis) {
            FlickAxis.X -> filteredX
            FlickAxis.Z -> filteredZ
            FlickAxis.NONE -> 0f
        }
        val direction = signedDirection(value)
        val reboundAbs = abs(value)
        val orthogonalAbs = when (pendingAxis) {
            FlickAxis.X -> abs(filteredZ)
            FlickAxis.Z -> abs(filteredX)
            FlickAxis.NONE -> 0f
        }
        val enoughDelay = now - pendingSinceMs >= FLICK_MIN_REBOUND_DELAY_MS
        val reboundThreshold = maxOf(FLICK_REBOUND_THRESHOLD, pendingStartAbs * FLICK_REBOUND_RATIO_OF_START)
        val axisDominant = reboundAbs >= orthogonalAbs * FLICK_AXIS_DOMINANCE_RATIO
        if (direction == -pendingDirection && enoughDelay && axisDominant && reboundAbs >= reboundThreshold) {
            triggerFlick(pendingAxis, pendingDirection, now)
            clearPending()
        }
    }

    private fun dominantAxisAndValue(x: Float, z: Float): Pair<FlickAxis, Float> {
        val ax = abs(x)
        val az = abs(z)
        return if (ax >= az) {
            if (ax >= az * FLICK_AXIS_DOMINANCE_RATIO) FlickAxis.X to x else FlickAxis.NONE to 0f
        } else {
            if (az >= ax * FLICK_AXIS_DOMINANCE_RATIO) FlickAxis.Z to z else FlickAxis.NONE to 0f
        }
    }

    private fun signedDirection(value: Float): Int {
        return when {
            value > 0.15f -> 1
            value < -0.15f -> -1
            else -> 0
        }
    }

    private fun triggerFlick(axis: FlickAxis, direction: Int, now: Long) {
        when (axis) {
            FlickAxis.Z -> pressBTap()
            FlickAxis.X -> {
                val triggerBack = (direction > 0) == AXIS_X_POSITIVE_IS_BACK
                if (triggerBack) pressC() else pressBTap()
            }
            FlickAxis.NONE -> return
        }
        when (axis) {
            FlickAxis.X -> lastFlickXMs = now
            FlickAxis.Z -> lastFlickZMs = now
            FlickAxis.NONE -> {}
        }
        vibrateFlickConfirm()
    }

    private fun pressA() {
        if (buttonCActive) releaseC()
        if (!buttonAActive) {
            buttonAActive = true
            sink?.onButtonDown(GlyphButton.A)
        }
        mainHandler.removeCallbacks(releaseATap)
        buttonALatchedByB = glyphPhysicalDown
        if (!buttonALatchedByB) {
            mainHandler.postDelayed(releaseATap, FLICK_PRESS_MS)
        }
    }

    private fun pressBTap() {
        if (glyphPhysicalDown || buttonBActive) return
        buttonBActive = true
        sink?.onButtonDown(GlyphButton.B)
        mainHandler.removeCallbacks(releaseBTap)
        mainHandler.postDelayed(releaseBTap, B_FLICK_PRESS_MS)
    }

    private fun pressC() {
        if (buttonAActive) releaseA()
        if (!buttonCActive) {
            buttonCActive = true
            sink?.onButtonDown(GlyphButton.C)
        }
        mainHandler.removeCallbacks(releaseCTap)
        buttonCLatchedByB = glyphPhysicalDown
        if (!buttonCLatchedByB) {
            mainHandler.postDelayed(releaseCTap, FLICK_PRESS_MS)
        }
    }

    private fun clearPending() {
        pendingAxis = FlickAxis.NONE
        pendingDirection = 0
        pendingStartAbs = 0f
        pendingSinceMs = 0L
    }

    private fun releaseA() {
        if (!buttonAActive) return
        mainHandler.removeCallbacks(releaseATap)
        buttonAActive = false
        buttonALatchedByB = false
        sink?.onButtonUp(GlyphButton.A)
    }

    private fun releaseC() {
        if (!buttonCActive) return
        mainHandler.removeCallbacks(releaseCTap)
        buttonCActive = false
        buttonCLatchedByB = false
        sink?.onButtonUp(GlyphButton.C)
    }

    private fun releaseAll() {
        mainHandler.removeCallbacks(releaseATap)
        mainHandler.removeCallbacks(releaseBTap)
        mainHandler.removeCallbacks(releaseCTap)
        if (buttonAActive) sink?.onButtonUp(GlyphButton.A)
        if (buttonBActive) sink?.onButtonUp(GlyphButton.B)
        if (buttonCActive) sink?.onButtonUp(GlyphButton.C)
        buttonAActive = false
        buttonBActive = false
        buttonCActive = false
        buttonALatchedByB = false
        buttonCLatchedByB = false
        glyphPhysicalDown = false
        clearPending()
    }

    private fun vibrateFlickConfirm() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(FLICK_VIBRATE_MS, FLICK_VIBRATE_AMPLITUDE))
    }
}
