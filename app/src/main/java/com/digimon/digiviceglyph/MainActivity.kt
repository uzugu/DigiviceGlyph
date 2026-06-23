package com.digimon.digiviceglyph

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.digimon.digiviceglyph.input.GlyphButton
import com.digimon.digiviceglyph.runtime.DigiviceRuntimeStore
import com.digimon.digiviceglyph.runtime.DigiviceV1Runtime

class MainActivity : AppCompatActivity() {
    private lateinit var deviceStatus: TextView
    private lateinit var portStatus: TextView
    private lateinit var previewView: GlyphPreviewView
    private lateinit var btnAutoRun: Button
    private lateinit var btnIdleClock: Button
    private lateinit var btnSound: Button
    private lateinit var btnStepMultiplier: Button
    private lateinit var btnWalk490: Button
    private lateinit var runtime: DigiviceV1Runtime
    private var activityRecognitionPromptedThisLaunch = false
    private var pendingPreviewStartAfterActivityPermission = false
    private var pendingPreviewToastAfterActivityPermission = false
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewTicker = object : Runnable {
        override fun run() {
            previewView.setBitmap(runtime.renderPhoneFrame())
            previewHandler.postDelayed(this, runtime.preferredFrameIntervalMs())
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) {
            ensureActivityRecognitionThenStartPreview(showToast = true)
        } else {
            Toast.makeText(this, R.string.notification_required, Toast.LENGTH_LONG).show()
        }
    }

    private val requestActivityRecognitionPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val shouldStartPreview = pendingPreviewStartAfterActivityPermission
        val showPreviewToast = pendingPreviewToastAfterActivityPermission
        pendingPreviewStartAfterActivityPermission = false
        pendingPreviewToastAfterActivityPermission = false
        if (granted || Build.VERSION.SDK_INT < 29) {
            if (shouldStartPreview) {
                startPreviewService(showToast = showPreviewToast)
            }
        } else if (shouldStartPreview) {
            Toast.makeText(this, R.string.activity_recognition_required, Toast.LENGTH_LONG).show()
            startPreviewService(showToast = showPreviewToast)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        runtime = DigiviceRuntimeStore.get(applicationContext)
        deviceStatus = findViewById(R.id.deviceStatus)
        portStatus = findViewById(R.id.portStatus)
        previewView = findViewById(R.id.previewView)
        val btnStartPreview = findViewById<Button>(R.id.btnStartPreview)
        val btnStopPreview = findViewById<Button>(R.id.btnStopPreview)
        val btnOpenSettings = findViewById<Button>(R.id.btnOpenSettings)
        val btnA = findViewById<Button>(R.id.btnA)
        val btnB = findViewById<Button>(R.id.btnB)
        val btnC = findViewById<Button>(R.id.btnC)
        btnAutoRun = findViewById(R.id.btnAutoRun)
        btnIdleClock = findViewById(R.id.btnIdleClock)
        btnSound = findViewById(R.id.btnSound)
        btnStepMultiplier = findViewById(R.id.btnStepMultiplier)
        btnWalk490 = findViewById(R.id.btnWalk490)

        updateGlyphStatus()

        portStatus.text = getString(R.string.port_status_text)

        btnStartPreview.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ensureActivityRecognitionThenStartPreview(showToast = true)
            }
        }

        btnStopPreview.setOnClickListener {
            startService(
                Intent(this, DigiviceGlyphToyService::class.java).apply {
                    action = DigiviceGlyphToyService.ACTION_STOP_PREVIEW
                }
            )
        }

        btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }

        bindRuntimeButton(btnA, GlyphButton.A)
        bindRuntimeButton(btnB, GlyphButton.B)
        bindRuntimeButton(btnC, GlyphButton.C)
        btnAutoRun.setOnClickListener {
            runtime.toggleAutorun()
            updateAutorunButton()
            previewView.setBitmap(runtime.renderPhoneFrame())
        }
        btnIdleClock.setOnClickListener {
            runtime.toggleIdleClock()
            updateIdleClockButton()
            previewView.setBitmap(runtime.renderPhoneFrame())
        }
        btnSound.setOnClickListener {
            runtime.toggleSound()
            updateSoundButton()
            previewView.setBitmap(runtime.renderPhoneFrame())
        }
        btnStepMultiplier.setOnClickListener {
            runtime.cycleStepMultiplier()
            updateStepMultiplierButton()
        }
        btnWalk490.setOnClickListener {
            runtime.triggerRawSteps(490)
            previewView.setBitmap(runtime.renderPhoneFrame())
        }
        updateAutorunButton()
        updateIdleClockButton()
        updateSoundButton()
        updateStepMultiplierButton()
        previewView.setBitmap(runtime.renderPhoneFrame())
        maybeEnsureWalkingServiceOnOpen()
    }

    override fun onResume() {
        super.onResume()
        updateGlyphStatus()
        updateAutorunButton()
        updateIdleClockButton()
        updateStepMultiplierButton()
        previewHandler.removeCallbacks(previewTicker)
        previewHandler.post(previewTicker)
    }

    override fun onPause() {
        super.onPause()
        previewHandler.removeCallbacks(previewTicker)
    }

    private fun startPreviewService(showToast: Boolean = true) {
        if (GlyphAvailability.isAvailable && !GlyphAvailability.isMasterGlyphEnabled(this)) {
            Toast.makeText(this, R.string.device_status_master_disabled, Toast.LENGTH_LONG).show()
        }
        val intent = Intent(this, DigiviceGlyphToyService::class.java).apply {
            action = DigiviceGlyphToyService.ACTION_START_PREVIEW
        }
        ContextCompat.startForegroundService(this, intent)
        if (showToast) {
            Toast.makeText(this, R.string.preview_started, Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureActivityRecognitionThenStartPreview(showToast: Boolean) {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            activityRecognitionPromptedThisLaunch = true
            pendingPreviewStartAfterActivityPermission = true
            pendingPreviewToastAfterActivityPermission = showToast
            requestActivityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return
        }
        startPreviewService(showToast = showToast)
    }

    private fun maybeEnsureWalkingServiceOnOpen() {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            if (activityRecognitionPromptedThisLaunch) return
            activityRecognitionPromptedThisLaunch = true
            pendingPreviewStartAfterActivityPermission = true
            pendingPreviewToastAfterActivityPermission = false
            requestActivityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return
        }
        startPreviewService(showToast = false)
    }

    private fun updateGlyphStatus() {
        deviceStatus.text = getString(
            when {
                !GlyphAvailability.isAvailable -> R.string.device_status_unavailable
                !GlyphAvailability.isMasterGlyphEnabled(this) -> R.string.device_status_master_disabled
                else -> R.string.device_status_available
            }
        )
    }

    private fun updateAutorunButton() {
        btnAutoRun.text = getString(
            if (runtime.isAutorunEnabled()) R.string.autorun_toggle_on else R.string.autorun_toggle_off
        )
    }

    private fun updateSoundButton() {
        btnSound.text = getString(
            if (runtime.isSoundEnabled()) R.string.sound_toggle_on else R.string.sound_toggle_off
        )
    }

    private fun updateIdleClockButton() {
        btnIdleClock.text = getString(
            if (runtime.isIdleClockEnabled()) R.string.idle_clock_toggle_on else R.string.idle_clock_toggle_off
        )
    }

    private fun updateStepMultiplierButton() {
        btnStepMultiplier.text = getString(R.string.step_multiplier_format, runtime.currentStepMultiplier())
    }

    private fun bindRuntimeButton(button: Button, glyphButton: GlyphButton) {
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    runtime.onButtonDown(glyphButton)
                    previewView.setBitmap(runtime.renderPhoneFrame())
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    runtime.onButtonUp(glyphButton)
                    previewView.setBitmap(runtime.renderPhoneFrame())
                    view.isPressed = false
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
        button.setOnClickListener { _: View -> }
    }
}
