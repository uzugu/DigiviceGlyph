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
        private const val FLICK_VIBRATE_MS = 40L
        private const val FLICK_VIBRATE_AMPLITUDE = 180
        private const val ACCEL_SMOOTH_ALPHA = 0.35f
        private const val ACCEL_GRAVITY_ALPHA = 0.82f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
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

        val gesture = flickDetector.update(
            nowMs = SystemClock.elapsedRealtime(),
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
        val sensor = linearAccelerationSensor ?: accelerometerFallback
        sensorsRegistered = sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } == true
        Log.d(TAG, "Motion sensor registered=$sensorsRegistered type=${sensor?.stringType}")
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

    private fun pressBTap() {
        if (buttonBActive) return
        buttonBActive = true
        sink?.onButtonDown(GlyphButton.B)
        mainHandler.removeCallbacks(releaseBTap)
        mainHandler.postDelayed(releaseBTap, BUTTON_TAP_MS)
    }

    private fun pressCTap() {
        if (buttonCActive) return
        buttonCActive = true
        sink?.onButtonDown(GlyphButton.C)
        mainHandler.removeCallbacks(releaseCTap)
        mainHandler.postDelayed(releaseCTap, BUTTON_TAP_MS)
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
        flickDetector.reset()
    }

    private fun vibrateFlickConfirm() {
        val currentVibrator = vibrator ?: return
        if (!currentVibrator.hasVibrator()) return
        currentVibrator.vibrate(
            VibrationEffect.createOneShot(FLICK_VIBRATE_MS, FLICK_VIBRATE_AMPLITUDE)
        )
    }
}
