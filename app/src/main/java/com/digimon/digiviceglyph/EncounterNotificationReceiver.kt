package com.digimon.digiviceglyph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class EncounterNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != EncounterNotifications.ACTION_SHOW_ENCOUNTER) return
        val encounter = EncounterNotifications.readEncounter(intent) ?: return
        EncounterNotifications.post(
            context,
            encounter.type,
            encounter.distance,
            encounter.steps,
            encounter.energy,
            encounter.area
        )
    }
}
