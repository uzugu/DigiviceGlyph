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
    companion object {
        private const val PREVIEW_INTERVAL_MS = 90L
    }

    private lateinit var deviceStatus: TextView
    private lateinit var portStatus: TextView
    private lateinit var previewView: GlyphPreviewView
    private lateinit var runtime: DigiviceV1Runtime
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewTicker = object : Runnable {
        override fun run() {
            previewView.setBitmap(runtime.renderPhoneFrame())
            previewHandler.postDelayed(this, PREVIEW_INTERVAL_MS)
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) {
            startPreviewService()
        } else {
            Toast.makeText(this, R.string.notification_required, Toast.LENGTH_LONG).show()
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

        updateGlyphStatus()

        portStatus.text = getString(R.string.port_status_text)

        btnStartPreview.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startPreviewService()
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
        previewView.setBitmap(runtime.renderPhoneFrame())
    }

    override fun onResume() {
        super.onResume()
        updateGlyphStatus()
        previewHandler.removeCallbacks(previewTicker)
        previewHandler.post(previewTicker)
    }

    override fun onPause() {
        super.onPause()
        previewHandler.removeCallbacks(previewTicker)
    }

    private fun startPreviewService() {
        if (GlyphAvailability.isAvailable && !GlyphAvailability.isMasterGlyphEnabled(this)) {
            Toast.makeText(this, R.string.device_status_master_disabled, Toast.LENGTH_LONG).show()
        }
        val intent = Intent(this, DigiviceGlyphToyService::class.java).apply {
            action = DigiviceGlyphToyService.ACTION_START_PREVIEW
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, R.string.preview_started, Toast.LENGTH_SHORT).show()
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
