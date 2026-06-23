package com.digimon.digiviceglyph

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.digimon.digiviceglyph.input.GlyphInputController
import com.digimon.digiviceglyph.runtime.DigiviceRuntimeStore
import com.digimon.digiviceglyph.runtime.DigiviceV1Runtime

class DigiviceGlyphToyService : Service() {
    companion object {
        private const val TAG = "DigiviceGlyphToy"
        private const val CHANNEL_ID = "digivice_glyph_preview"
        private const val NOTIF_ID = 41
        private const val FRAME_INTERVAL_MS = 90L

        const val ACTION_START_PREVIEW = "com.digimon.digiviceglyph.START_PREVIEW"
        const val ACTION_STOP_PREVIEW = "com.digimon.digiviceglyph.STOP_PREVIEW"
    }

    private var glyphManager: Any? = null
    private var glyphManagerInited = false
    private var glyphRenderer: GlyphRenderer? = null
    private var inputController: GlyphInputController? = null
    private var runtime: DigiviceV1Runtime? = null
    private var standalonePreview = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            pushLiveFrame()
            val intervalMs = runtime?.preferredFrameIntervalMs() ?: FRAME_INTERVAL_MS
            mainHandler.postDelayed(this, intervalMs)
        }
    }

    private val messenger: Messenger? by lazy {
        if (!GlyphAvailability.isAvailable) return@lazy null
        val handler = Handler(Looper.getMainLooper()) { msg ->
            val event = msg.data?.getString(com.nothing.ketchum.GlyphToy.MSG_GLYPH_TOY_DATA)
            Log.d(TAG, "Toy message what=${msg.what}, event=$event, binder=${msg.replyTo != null}")
            if (msg.what == com.nothing.ketchum.GlyphToy.MSG_GLYPH_TOY || event != null) {
                handleToyMessage(msg)
            }
            true
        }
        Messenger(handler).also {
            Log.d(TAG, "Messenger created, binder=${it.binder}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        ensureServiceStarted()
        initGlyphManager()
        return messenger?.binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PREVIEW -> {
                standalonePreview = true
                startForeground(NOTIF_ID, buildNotification())
                initGlyphManager()
                startRuntime()
            }
            ACTION_STOP_PREVIEW -> {
                stopRuntime()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!standalonePreview) {
            stopRuntime()
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        stopRuntime()
        releaseGlyphManager()
    }

    private fun handleToyMessage(msg: Message) {
        if (!GlyphAvailability.isAvailable) return
        val data = msg.data ?: return
        val event = data.getString(com.nothing.ketchum.GlyphToy.MSG_GLYPH_TOY_DATA) ?: return

        when {
            event.startsWith(com.nothing.ketchum.GlyphToy.STATUS_PREPARE) -> {
                Log.d(TAG, "Toy prepare")
            }
            event.startsWith(com.nothing.ketchum.GlyphToy.STATUS_START) -> {
                Log.d(TAG, "Toy start")
                startRuntime()
            }
            event.startsWith(com.nothing.ketchum.GlyphToy.STATUS_END) -> {
                Log.d(TAG, "Toy end")
                if (!standalonePreview) {
                    stopRuntime()
                    releaseGlyphManager()
                    stopSelf()
                }
            }
            event == com.nothing.ketchum.GlyphToy.EVENT_ACTION_DOWN -> {
                Log.d(TAG, "Button DOWN")
                ensureInputController()?.onGlyphButtonDown()
            }
            event == com.nothing.ketchum.GlyphToy.EVENT_ACTION_UP -> {
                Log.d(TAG, "Button UP")
                ensureInputController()?.onGlyphButtonUp()
            }
            event == com.nothing.ketchum.GlyphToy.EVENT_CHANGE -> {
                Log.d(TAG, "Button CHANGE")
                ensureInputController()?.onGlyphButtonChange()
            }
            event == com.nothing.ketchum.GlyphToy.EVENT_AOD -> {
                Log.d(TAG, "AOD event received")
                pushStaticFrame()
            }
        }
    }

    private fun startRuntime() {
        if (runtime != null && inputController != null) return
        val previewRuntime = DigiviceRuntimeStore.get(applicationContext)
        val controller = GlyphInputController(this)
        controller.attach(previewRuntime)
        controller.start()
        runtime = previewRuntime
        inputController = controller
        mainHandler.removeCallbacks(frameRunnable)
        mainHandler.post(frameRunnable)
        pushLiveFrame()
    }

    private fun ensureInputController(): GlyphInputController? {
        startRuntime()
        return inputController
    }

    private fun stopRuntime() {
        mainHandler.removeCallbacks(frameRunnable)
        inputController?.stop()
        inputController = null
        runtime = null
        glyphRenderer?.turnOff()
    }

    private fun pushLiveFrame() {
        val renderer = glyphRenderer ?: return
        val frame = runtime?.renderGlyphContentFrame() ?: return
        renderer.pushFrame(frame)
    }

    private fun pushStaticFrame() {
        val renderer = glyphRenderer ?: return
        val frame = runtime?.renderGlyphContentFrame() ?: return
        renderer.pushFrame(frame)
    }

    private fun initGlyphManager() {
        if (!GlyphAvailability.isAvailable) return
        if (glyphManagerInited) return
        val mgr = com.nothing.ketchum.GlyphMatrixManager.getInstance(this)
        glyphManager = mgr
        safeGlyphManagerUninit("pre_init_cleanup")
        Log.d(TAG, "initGlyphManager: messenger=${messenger != null}")
        mgr.init(object : com.nothing.ketchum.GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                Log.d(TAG, "Glyph service connected")
                val targetDevice = Build.MODEL ?: "A024"
                val registered = mgr.register(targetDevice)
                Log.d(TAG, "Glyph registration target=$targetDevice authorized=$registered")
                val renderer = GlyphRenderer(this@DigiviceGlyphToyService)
                renderer.init(mgr)
                glyphRenderer = renderer
                pushLiveFrame()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "Glyph service disconnected")
            }
        })
        glyphManagerInited = true
    }

    private fun releaseGlyphManager() {
        if (!glyphManagerInited) return
        glyphRenderer?.release()
        glyphRenderer = null
        safeGlyphManagerUninit("release")
        glyphManagerInited = false
    }

    private fun safeGlyphManagerUninit(stage: String) {
        val mgr = glyphManager as? com.nothing.ketchum.GlyphMatrixManager ?: return
        try {
            mgr.unInit()
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Glyph manager already unbound during $stage")
        } catch (_: IllegalStateException) {
            Log.w(TAG, "Glyph manager release in invalid state during $stage")
        }
    }

    private fun ensureServiceStarted() {
        try {
            startService(Intent(this, DigiviceGlyphToyService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to keep service started; using bound-only mode", e)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
