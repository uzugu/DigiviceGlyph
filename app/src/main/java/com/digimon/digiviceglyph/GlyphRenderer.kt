package com.digimon.digiviceglyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

class GlyphRenderer(private val context: Context) {
    companion object {
        private const val MATRIX_SIZE = 25
        private const val LCD_WIDTH = 32
        private const val LCD_HEIGHT = 16
        private const val FIT_WIDTH = MATRIX_SIZE
        private const val FIT_HEIGHT = 13
        private const val FIT_X_OFFSET = 0
        private const val FIT_Y_OFFSET = (MATRIX_SIZE - FIT_HEIGHT) / 2
        private const val LUMA_THRESHOLD = 140
    }

    private var manager: GlyphMatrixManager? = null
    private var ready = false
    private val matrixBitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)

    fun init(mgr: GlyphMatrixManager) {
        manager = mgr
        ready = true
    }

    fun pushFrame(bitmap: Bitmap) {
        val mgr = manager ?: return
        if (!ready) return
        try {
            val matrixFrameBitmap = toMatrixBitmap(bitmap)
            val obj = GlyphMatrixObject.Builder()
                .setImageSource(matrixFrameBitmap)
                .setPosition(0, 0)
                .setBrightness(4095)
                .build()

            val frame = GlyphMatrixFrame.Builder()
                .addTop(obj)
                .build(context)

            mgr.setMatrixFrame(frame.render())
        } catch (_: Exception) {
        }
    }

    private fun toMatrixBitmap(source: Bitmap): Bitmap {
        if (source.width == LCD_WIDTH && source.height == LCD_HEIGHT) {
            return mapLcdContentToMatrix(source)
        }

        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        for (y in 0 until MATRIX_SIZE) {
            val sampleY = ((y + 0.5f) * height / MATRIX_SIZE).toInt().coerceIn(0, height - 1)
            for (x in 0 until MATRIX_SIZE) {
                val sampleX = ((x + 0.5f) * width / MATRIX_SIZE).toInt().coerceIn(0, width - 1)
                val color = source.getPixel(sampleX, sampleY)
                val luma = ((Color.red(color) * 299) + (Color.green(color) * 587) + (Color.blue(color) * 114)) / 1000
                matrixBitmap.setPixel(
                    x,
                    y,
                    if (luma < LUMA_THRESHOLD) Color.WHITE else Color.BLACK
                )
            }
        }
        return matrixBitmap
    }

    private fun mapLcdContentToMatrix(source: Bitmap): Bitmap {
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                matrixBitmap.setPixel(x, y, Color.BLACK)
            }
        }

        val lit = Array(LCD_HEIGHT) { BooleanArray(LCD_WIDTH) }
        for (y in 0 until LCD_HEIGHT) {
            for (x in 0 until LCD_WIDTH) {
                val color = source.getPixel(x, y)
                val luma = ((Color.red(color) * 299) + (Color.green(color) * 587) + (Color.blue(color) * 114)) / 1000
                lit[y][x] = luma < LUMA_THRESHOLD
            }
        }

        for (row in 0 until LCD_HEIGHT) {
            val dstRowStart = row * FIT_HEIGHT / LCD_HEIGHT
            val dstRowEndExclusive = ((row + 1) * FIT_HEIGHT + LCD_HEIGHT - 1) / LCD_HEIGHT
            for (dstCol in 0 until FIT_WIDTH) {
                val srcStart = dstCol * LCD_WIDTH / FIT_WIDTH
                val srcEndExclusive = ((dstCol + 1) * LCD_WIDTH + FIT_WIDTH - 1) / FIT_WIDTH
                var on = false
                for (srcCol in srcStart until srcEndExclusive.coerceAtMost(LCD_WIDTH)) {
                    if (lit[row][srcCol]) {
                        on = true
                        break
                    }
                }
                for (dstRow in dstRowStart until dstRowEndExclusive.coerceAtMost(FIT_HEIGHT)) {
                    matrixBitmap.setPixel(
                        FIT_X_OFFSET + dstCol,
                        FIT_Y_OFFSET + dstRow,
                        if (on) Color.BLACK else Color.WHITE
                    )
                }
            }
        }

        return matrixBitmap
    }

    fun turnOff() {
        try {
            manager?.turnOff()
        } catch (_: Exception) {
        }
    }

    fun release() {
        ready = false
        manager = null
    }
}
