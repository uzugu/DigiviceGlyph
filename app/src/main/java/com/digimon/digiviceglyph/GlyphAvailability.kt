package com.digimon.digiviceglyph

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
}
