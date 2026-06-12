package com.digimon.digiviceglyph.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class GlyphInputController(context: Context) : SensorEventListener {
    companion object {
        private const val TAG = "GlyphInput"
        private const val BUTTON_TAP_MS = 90L
        private const val MASH_TAP_MS = 25L
        private const val MASH_SAMPLE_LOG_MS = 180L
        private const val FLICK_VIBRATE_MS = 40L
        private const val FLICK_VIBRATE_AMPLITUDE = 180
        private const val ACCEL_SMOOTH_ALPHA = 0.35f
        private const val ACCEL_GRAVITY_ALPHA = 0.82f
        private const val WALK_TRIGGER_THRESHOLD = 0.55f
        private const val WALK_REARM_THRESHOLD = 0.16f
        private const val WALK_DEBOUNCE_MS = 180L
        private const val MASH_TRIGGER_THRESHOLD = 2.35f
        private const val MASH_REARM_THRESHOLD = 0.95f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometerFallback =
        if (linearAccelerationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val flickDetector = FlickGestureDetector()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var sink: GlyphButtonSink? = null
    private var started = false
    private var sensorsRegistered = false

    private var glyphPhysicalDown = false
    private var glyphChangePulseDown = false
    private var buttonAEmitted = false
    private var buttonBActive = false
    private var buttonCActive = false

    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var mashArmed = true
    private var lastMashSignature = 0
    private var lastMashSampleLogAtMs = 0L
    private var lastMotionMode = GlyphMotionMode.DEFAULT
    private var walkArmed = true
    private var lastWalkStepAtMs = 0L
    private var lastStepCounterValue: Int? = null

    private val releaseGlyphChangePulse = Runnable {
        glyphChangePulseDown = false
        syncButtonA()
    }

    private val releaseBTap = Runnable {
        if (!buttonBActive) return@Runnable
        buttonBActive = false
        sink?.onButtonUp(GlyphButton.B)
    }

    private val releaseCTap = Runnable {
        if (!buttonCActive) return@Runnable
        buttonCActive = false
        sink?.onButtonUp(GlyphButton.C)
    }

    fun attach(buttonSink: GlyphButtonSink) {
        sink = buttonSink
    }

    fun start() {
        if (started) return
        started = true
        resetMotionState()
        registerSensors()
    }

    fun stop() {
        started = false
        sensorManager.unregisterListener(this)
        sensorsRegistered = false
        releaseAll()
        resetMotionState()
    }

    /**
     * Nothing sends action_down/action_up only while this toy owns the Glyph button.
     * Keep that physical state independent from synthetic flick taps.
     */
    fun onGlyphButtonDown() {
        if (glyphPhysicalDown) return
        glyphPhysicalDown = true
        Log.d(TAG, "Glyph action_down -> A down")
        syncButtonA()
    }

    fun onGlyphButtonUp() {
        if (!glyphPhysicalDown) return
        glyphPhysicalDown = false
        Log.d(TAG, "Glyph action_up -> A up")
        syncButtonA()
    }

    /**
     * EVENT_CHANGE is Nothing's long-press action. Some firmware sends it instead
     * of a useful down/up pair, so expose it as a short A pulse.
     */
    fun onGlyphButtonChange() {
        if (glyphPhysicalDown || glyphChangePulseDown) return
        glyphChangePulseDown = true
        Log.d(TAG, "Glyph change -> A tap")
        syncButtonA()
        mainHandler.removeCallbacks(releaseGlyphChangePulse)
        mainHandler.postDelayed(releaseGlyphChangePulse, BUTTON_TAP_MS)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val nowMs = SystemClock.elapsedRealtime()
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            processStepCounterEvent(nowMs, event)
            return
        }
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            if (sink?.acceptsPassiveWalking() == true && canTriggerWalkingStep(nowMs)) {
                Log.d(TAG, "Step detector accepted values=${event.values.joinToString()}")
                lastWalkStepAtMs = nowMs
                sink?.triggerStep()
            }
            return
        }

        val x: Float
        val y: Float
        val z: Float
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                x = event.values[0]
                y = event.values[1]
                z = event.values[2]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravityX = ACCEL_GRAVITY_ALPHA * gravityX + (1f - ACCEL_GRAVITY_ALPHA) * event.values[0]
                gravityY = ACCEL_GRAVITY_ALPHA * gravityY + (1f - ACCEL_GRAVITY_ALPHA) * event.values[1]
                gravityZ = ACCEL_GRAVITY_ALPHA * gravityZ + (1f - ACCEL_GRAVITY_ALPHA) * event.values[2]
                x = event.values[0] - gravityX
                y = event.values[1] - gravityY
                z = event.values[2] - gravityZ
            }
            else -> return
        }

        filteredX += (x - filteredX) * ACCEL_SMOOTH_ALPHA
        filteredY += (y - filteredY) * ACCEL_SMOOTH_ALPHA
        filteredZ += (z - filteredZ) * ACCEL_SMOOTH_ALPHA

        val motionMode = sink?.motionInputMode() ?: GlyphMotionMode.DEFAULT
        if (motionMode != lastMotionMode) {
            Log.d(TAG, "Motion mode -> $motionMode")
            lastMotionMode = motionMode
        }

        if (motionMode == GlyphMotionMode.MASH_ALL_DIRECTIONS) {
            logMashSample(nowMs, filteredX, filteredY, filteredZ)
            if (processMashMotion(filteredX, filteredY, filteredZ)) {
                vibrateFlickConfirm()
            }
            return
        }

        if (sink?.acceptsPassiveWalking() == true) {
            processWalkingMotion(nowMs, filteredX, filteredY, filteredZ)
        }

        val gesture = flickDetector.update(
            nowMs = nowMs,
            x = filteredX,
            y = filteredY,
            z = filteredZ
        ) ?: return

        when (gesture) {
            FlickGestureDetector.Gesture.LEFT -> pressCTap()
            FlickGestureDetector.Gesture.RIGHT -> pressBTap()
            FlickGestureDetector.Gesture.VERTICAL_STEP -> sink?.triggerStep()
        }
        Log.d(TAG, "Motion gesture=$gesture")
        vibrateFlickConfirm()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerSensors() {
        if (sensorsRegistered) return
        var registeredAny = false
        var stepCounterRegistered = false
        var stepDetectorRegistered = false
        stepCounterSensor?.let {
            stepCounterRegistered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            registeredAny = stepCounterRegistered || registeredAny
        }
        stepDetectorSensor?.let {
            stepDetectorRegistered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            registeredAny = stepDetectorRegistered || registeredAny
        }
        val motionSensor = linearAccelerationSensor ?: accelerometerFallback
        var motionRegistered = false
        motionSensor?.let {
            motionRegistered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            registeredAny = motionRegistered || registeredAny
        }
        sensorsRegistered = registeredAny
        Log.d(
            TAG,
            "Sensor registration any=$sensorsRegistered motionRegistered=$motionRegistered motionType=${motionSensor?.stringType} stepCounterRegistered=$stepCounterRegistered stepCounterType=${stepCounterSensor?.stringType} stepDetectorRegistered=$stepDetectorRegistered stepDetectorType=${stepDetectorSensor?.stringType}"
        )
    }

    private fun syncButtonA() {
        val shouldBeDown = glyphPhysicalDown || glyphChangePulseDown
        if (shouldBeDown == buttonAEmitted) return
        buttonAEmitted = shouldBeDown
        if (shouldBeDown) {
            sink?.onButtonDown(GlyphButton.A)
        } else {
            sink?.onButtonUp(GlyphButton.A)
        }
    }

    private fun processMashMotion(x: Float, y: Float, z: Float): Boolean {
        val magnitudeSquared = x * x + y * y + z * z
        val rearmSquared = MASH_REARM_THRESHOLD * MASH_REARM_THRESHOLD
        val triggerSquared = MASH_TRIGGER_THRESHOLD * MASH_TRIGGER_THRESHOLD
        val signature = dominantMashSignature(x, y, z)

        if (magnitudeSquared <= rearmSquared) {
            if (!mashArmed) {
                Log.d(TAG, "Mash rearmed magnitude=${"%.2f".format(kotlin.math.sqrt(magnitudeSquared.toDouble()))}")
            }
            mashArmed = true
            lastMashSignature = 0
            return false
        }
        if (!mashArmed && signature != 0 && signature != lastMashSignature) {
            mashArmed = true
            Log.d(TAG, "Mash rearmed by direction change old=$lastMashSignature new=$signature")
        }
        if (!mashArmed || magnitudeSquared < triggerSquared) {
            return false
        }

        mashArmed = false
        lastMashSignature = signature
        val magnitude = kotlin.math.sqrt(magnitudeSquared.toDouble())
        val accepted = pressBTap(MASH_TAP_MS)
        Log.d(
            TAG,
            "Mash accept magnitude=${"%.2f".format(magnitude)} signature=$signature accepted=$accepted buttonBActive=$buttonBActive"
        )
        return accepted
    }

    private fun dominantMashSignature(x: Float, y: Float, z: Float): Int {
        val absX = kotlin.math.abs(x)
        val absY = kotlin.math.abs(y)
        val absZ = kotlin.math.abs(z)
        return when {
            absX >= absY && absX >= absZ -> if (x >= 0f) 1 else -1
            absY >= absX && absY >= absZ -> if (y >= 0f) 2 else -2
            absZ >= absX && absZ >= absY -> if (z >= 0f) 3 else -3
            else -> 0
        }
    }

    private fun logMashSample(nowMs: Long, x: Float, y: Float, z: Float) {
        if (nowMs - lastMashSampleLogAtMs < MASH_SAMPLE_LOG_MS) return
        lastMashSampleLogAtMs = nowMs
        val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble())
        Log.d(
            TAG,
            "Mash sample mag=${"%.2f".format(magnitude)} x=${"%.2f".format(x)} y=${"%.2f".format(y)} z=${"%.2f".format(z)} armed=$mashArmed buttonBActive=$buttonBActive"
        )
    }

    private fun pressBTap(tapMs: Long = BUTTON_TAP_MS): Boolean {
        if (buttonBActive) {
            Log.d(TAG, "pressBTap ignored because B is still active")
            return false
        }
        buttonBActive = true
        sink?.onButtonDown(GlyphButton.B)
        mainHandler.removeCallbacks(releaseBTap)
        mainHandler.postDelayed(releaseBTap, tapMs)
        return true
    }

    private fun pressCTap(tapMs: Long = BUTTON_TAP_MS): Boolean {
        if (buttonCActive) return false
        buttonCActive = true
        sink?.onButtonDown(GlyphButton.C)
        mainHandler.removeCallbacks(releaseCTap)
        mainHandler.postDelayed(releaseCTap, tapMs)
        return true
    }

    private fun releaseAll() {
        mainHandler.removeCallbacks(releaseGlyphChangePulse)
        mainHandler.removeCallbacks(releaseBTap)
        mainHandler.removeCallbacks(releaseCTap)

        glyphPhysicalDown = false
        glyphChangePulseDown = false
        if (buttonAEmitted) sink?.onButtonUp(GlyphButton.A)
        if (buttonBActive) sink?.onButtonUp(GlyphButton.B)
        if (buttonCActive) sink?.onButtonUp(GlyphButton.C)
        buttonAEmitted = false
        buttonBActive = false
        buttonCActive = false
    }

    private fun resetMotionState() {
        filteredX = 0f
        filteredY = 0f
        filteredZ = 0f
        gravityX = 0f
        gravityY = 0f
        gravityZ = 0f
        mashArmed = true
        lastMashSignature = 0
        lastMashSampleLogAtMs = 0L
        lastMotionMode = GlyphMotionMode.DEFAULT
        walkArmed = true
        lastWalkStepAtMs = 0L
        lastStepCounterValue = null
        flickDetector.reset()
    }

    private fun processStepCounterEvent(nowMs: Long, event: SensorEvent) {
        val currentValue = event.values.firstOrNull()?.toInt() ?: return
        val previousValue = lastStepCounterValue
        lastStepCounterValue = currentValue
        if (previousValue == null) {
            Log.d(TAG, "Step counter baseline=$currentValue")
            return
        }

        val delta = (currentValue - previousValue).coerceAtLeast(0)
        if (delta == 0) return
        if (sink?.acceptsPassiveWalking() != true) {
            Log.d(TAG, "Step counter delta ignored delta=$delta screenNotEligible=true")
            return
        }
        if (!canTriggerWalkingStep(nowMs)) {
            Log.d(TAG, "Step counter delta ignored delta=$delta debounce=${nowMs - lastWalkStepAtMs}ms")
            return
        }

        lastWalkStepAtMs = nowMs
        Log.d(TAG, "Step counter accepted current=$currentValue previous=$previousValue delta=$delta")
        repeat(delta) {
            sink?.triggerStep()
        }
    }

    private fun processWalkingMotion(nowMs: Long, x: Float, y: Float, z: Float) {
        val magnitudeSquared = x * x + y * y + z * z
        val rearmSquared = WALK_REARM_THRESHOLD * WALK_REARM_THRESHOLD
        val triggerSquared = WALK_TRIGGER_THRESHOLD * WALK_TRIGGER_THRESHOLD

        if (magnitudeSquared <= rearmSquared) {
            walkArmed = true
            return
        }
        if (!walkArmed || magnitudeSquared < triggerSquared || !canTriggerWalkingStep(nowMs)) {
            return
        }

        walkArmed = false
        lastWalkStepAtMs = nowMs
        Log.d(TAG, "Walking motion accepted magnitude=${"%.2f".format(kotlin.math.sqrt(magnitudeSquared.toDouble()))}")
        sink?.triggerStep()
    }

    private fun canTriggerWalkingStep(nowMs: Long): Boolean {
        return nowMs - lastWalkStepAtMs >= WALK_DEBOUNCE_MS
    }

    private fun vibrateFlickConfirm() {
        val currentVibrator = vibrator ?: return
        if (!currentVibrator.hasVibrator()) return
        currentVibrator.vibrate(
            VibrationEffect.createOneShot(FLICK_VIBRATE_MS, FLICK_VIBRATE_AMPLITUDE)
        )
    }
}
