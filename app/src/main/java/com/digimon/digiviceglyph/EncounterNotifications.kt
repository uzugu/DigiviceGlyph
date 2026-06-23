package com.digimon.digiviceglyph

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object EncounterNotifications {
    const val ACTION_SHOW_ENCOUNTER = "com.digimon.digiviceglyph.SHOW_ENCOUNTER"
    const val EXTRA_LAUNCHED_FROM_NOTIFICATION = "com.digimon.digiviceglyph.EXTRA_LAUNCHED_FROM_NOTIFICATION"
    const val EXTRA_ENCOUNTER_DATA = "com.digimon.digiviceglyph.EXTRA_ENCOUNTER_DATA"
    const val EXTRA_ENCOUNTER_TYPE = "com.digimon.digiviceglyph.EXTRA_ENCOUNTER_TYPE"
    const val EXTRA_DISTANCE = "com.digimon.digiviceglyph.EXTRA_DISTANCE"
    const val EXTRA_STEPS = "com.digimon.digiviceglyph.EXTRA_STEPS"
    const val EXTRA_ENERGY = "com.digimon.digiviceglyph.EXTRA_ENERGY"
    const val EXTRA_AREA = "com.digimon.digiviceglyph.EXTRA_AREA"

    private const val CHANNEL_ID = "v1_encounters"
    private const val REQUEST_CODE = 49043
    private const val NOTIFICATION_ID = 49043

    data class EncounterPayload(
        val type: String,
        val distance: Int,
        val steps: Int,
        val energy: Int,
        val area: Int
    )

    fun schedule(
        context: Context,
        stepsRemaining: Int,
        encounterType: String,
        distance: Int,
        steps: Int,
        energy: Int,
        area: Int
    ) {
        val delaySeconds = stepsRemaining.coerceIn(1, Int.MAX_VALUE / 1000)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = System.currentTimeMillis() + (delaySeconds * 1000L)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            receiverPendingIntent(context, encounterType, distance, steps, energy, area)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(receiverPendingIntent(context, "battle", 0, 0, 0, 0))
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID)
    }

    fun post(
        context: Context,
        encounterType: String,
        distance: Int,
        steps: Int,
        energy: Int,
        area: Int
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(context, encounterType, distance, steps, energy, area)
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.encounter_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        context: Context,
        encounterType: String,
        distance: Int,
        steps: Int,
        energy: Int,
        area: Int
    ): Notification {
        val payload = EncounterPayload(encounterType, distance, steps, energy, area)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_LAUNCHED_FROM_NOTIFICATION, true)
            writeEncounterExtras(this, payload)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(titleForType(context, encounterType))
            .setContentText(context.getString(R.string.encounter_notification_text, distance, steps, energy))
            .setSmallIcon(android.R.drawable.presence_away)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun receiverPendingIntent(
        context: Context,
        encounterType: String,
        distance: Int,
        steps: Int,
        energy: Int,
        area: Int
    ): PendingIntent {
        val payload = EncounterPayload(encounterType, distance, steps, energy, area)
        val intent = Intent(context, EncounterNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_ENCOUNTER
            writeEncounterExtras(this, payload)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun titleForType(context: Context, encounterType: String): String =
        when (encounterType) {
            "boss" -> context.getString(R.string.encounter_notification_boss)
            "battle" -> context.getString(R.string.encounter_notification_battle)
            else -> context.getString(R.string.encounter_notification_event)
        }

    fun readEncounter(intent: Intent?): EncounterPayload? {
        if (intent == null) return null
        parseEncounterData(intent.getStringExtra(EXTRA_ENCOUNTER_DATA))?.let { return it }
        val encounterType = intent.getStringExtra(EXTRA_ENCOUNTER_TYPE) ?: return null
        return EncounterPayload(
            type = encounterType,
            distance = intent.getIntExtra(EXTRA_DISTANCE, 0),
            steps = intent.getIntExtra(EXTRA_STEPS, 0),
            energy = intent.getIntExtra(EXTRA_ENERGY, 0),
            area = intent.getIntExtra(EXTRA_AREA, 0)
        )
    }

    private fun writeEncounterExtras(intent: Intent, payload: EncounterPayload) {
        intent.putExtra(EXTRA_ENCOUNTER_DATA, encodeEncounterData(payload))
        intent.putExtra(EXTRA_ENCOUNTER_TYPE, payload.type)
        intent.putExtra(EXTRA_DISTANCE, payload.distance)
        intent.putExtra(EXTRA_STEPS, payload.steps)
        intent.putExtra(EXTRA_ENERGY, payload.energy)
        intent.putExtra(EXTRA_AREA, payload.area)
    }

    private fun encodeEncounterData(payload: EncounterPayload): String =
        "type:${payload.type};distance:${payload.distance};steps:${payload.steps};energy:${payload.energy};area:${payload.area}"

    private fun parseEncounterData(data: String?): EncounterPayload? {
        if (data.isNullOrBlank()) return null
        val pairs = data.split(';')
            .mapNotNull { entry ->
                val separator = entry.indexOf(':')
                if (separator <= 0 || separator >= entry.lastIndex) {
                    null
                } else {
                    entry.substring(0, separator) to entry.substring(separator + 1)
                }
            }
            .toMap()
        val type = pairs["type"] ?: return null
        return EncounterPayload(
            type = type,
            distance = pairs["distance"]?.toIntOrNull() ?: 0,
            steps = pairs["steps"]?.toIntOrNull() ?: 0,
            energy = pairs["energy"]?.toIntOrNull() ?: 0,
            area = pairs["area"]?.toIntOrNull() ?: 0
        )
    }
}
