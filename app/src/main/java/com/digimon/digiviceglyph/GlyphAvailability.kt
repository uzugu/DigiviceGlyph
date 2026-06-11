package com.digimon.digiviceglyph

import android.content.Context
import android.provider.Settings

object GlyphAvailability {
    private var isNothingDevice: Boolean? = null

    val isAvailable: Boolean
        get() {
            if (isNothingDevice != null) return isNothingDevice!!
            isNothingDevice = try {
                Class.forName("com.nothing.ketchum.GlyphMatrixManager")
                android.os.Build.MANUFACTURER.equals("Nothing", ignoreCase = true)
            } catch (_: Exception) {
                false
            } catch (_: Error) {
                false
            }
            return isNothingDevice!!
        }

    fun isMasterGlyphEnabled(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, "led_effect_enable", 1) == 1
    }
}
