package com.digimon.digiviceglyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

class GlyphRenderer(private val context: Context) {
    companion object {
        private const val TAG = "DigiviceGlyphRender"
        private const val MATRIX_SIZE = 25
        private const val LCD_WIDTH = 32
        private const val LCD_HEIGHT = 16
        private const val H_CROP_LEFT = (LCD_WIDTH - MATRIX_SIZE) / 2
        private const val FIT_Y_OFFSET = (MATRIX_SIZE - LCD_HEIGHT) / 2
        private const val LUMA_THRESHOLD = 140
        private const val LOG_INTERVAL_FRAMES = 60
    }

    private var manager: GlyphMatrixManager? = null
    private var ready = false
    private val outputBitmaps = arrayOfNulls<Bitmap>(2)
    private val outputPixels = IntArray(MATRIX_SIZE * MATRIX_SIZE)
    private var outputBitmapIndex = 0
    private var frameCount = 0

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

            val payload = frame.render()
            mgr.setMatrixFrame(payload)
            logFrameSummary(payload)
        } catch (error: Exception) {
            Log.e(TAG, "Failed to push Glyph frame", error)
        }
    }

    private fun toMatrixBitmap(source: Bitmap): Bitmap {
        outputPixels.fill(Color.BLACK)

        if (source.width == LCD_WIDTH && source.height == LCD_HEIGHT) {
            return mapLcdContentToMatrix(source)
        }
        if (source.width == MATRIX_SIZE && source.height == MATRIX_SIZE) {
            return mapMatrixBitmap(source)
        }

        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        for (y in 0 until MATRIX_SIZE) {
            val sampleY = ((y + 0.5f) * height / MATRIX_SIZE).toInt().coerceIn(0, height - 1)
            for (x in 0 until MATRIX_SIZE) {
                val sampleX = ((x + 0.5f) * width / MATRIX_SIZE).toInt().coerceIn(0, width - 1)
                val color = source.getPixel(sampleX, sampleY)
                val luma = ((Color.red(color) * 299) + (Color.green(color) * 587) + (Color.blue(color) * 114)) / 1000
                if (luma < LUMA_THRESHOLD) {
                    outputPixels[y * MATRIX_SIZE + x] = Color.WHITE
                }
            }
        }
        return writeOutputBitmap()
    }

    private fun mapMatrixBitmap(source: Bitmap): Bitmap {
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                val color = source.getPixel(x, y)
                val luma = ((Color.red(color) * 299) + (Color.green(color) * 587) + (Color.blue(color) * 114)) / 1000
                if (luma < LUMA_THRESHOLD) {
                    outputPixels[y * MATRIX_SIZE + x] = Color.WHITE
                }
            }
        }
        return writeOutputBitmap()
    }

    private fun mapLcdContentToMatrix(source: Bitmap): Bitmap {
        for (row in 0 until LCD_HEIGHT) {
            val dstRow = row + FIT_Y_OFFSET
            for (matrixCol in 0 until MATRIX_SIZE) {
                val sourceCol = matrixCol + H_CROP_LEFT
                val color = source.getPixel(sourceCol, row)
                val luma = ((Color.red(color) * 299) + (Color.green(color) * 587) + (Color.blue(color) * 114)) / 1000
                if (luma < LUMA_THRESHOLD) {
                    outputPixels[dstRow * MATRIX_SIZE + matrixCol] = Color.WHITE
                }
            }
        }

        return writeOutputBitmap()
    }

    private fun writeOutputBitmap(): Bitmap {
        val index = outputBitmapIndex
        var bitmap = outputBitmaps[index]
        if (bitmap == null || bitmap.isRecycled) {
            bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            outputBitmaps[index] = bitmap
        }
        outputBitmapIndex = (index + 1) % outputBitmaps.size
        bitmap.setPixels(outputPixels, 0, MATRIX_SIZE, 0, 0, MATRIX_SIZE, MATRIX_SIZE)
        return bitmap
    }

    private fun logFrameSummary(payload: IntArray) {
        frameCount++
        if (frameCount != 1 && frameCount % LOG_INTERVAL_FRAMES != 0) return

        var sourceLitCount = 0
        var minX = MATRIX_SIZE
        var maxX = -1
        var minY = MATRIX_SIZE
        var maxY = -1
        for (y in 0 until MATRIX_SIZE) {
            for (x in 0 until MATRIX_SIZE) {
                if (outputPixels[y * MATRIX_SIZE + x] == Color.BLACK) continue
                sourceLitCount++
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
            }
        }
        var payloadLitCount = 0
        var payloadMinX = MATRIX_SIZE
        var payloadMaxX = -1
        var payloadMinY = MATRIX_SIZE
        var payloadMaxY = -1
        for (index in payload.indices) {
            if (payload[index] == 0) continue
            val x = index % MATRIX_SIZE
            val y = index / MATRIX_SIZE
            payloadLitCount++
            payloadMinX = minOf(payloadMinX, x)
            payloadMaxX = maxOf(payloadMaxX, x)
            payloadMinY = minOf(payloadMinY, y)
            payloadMaxY = maxOf(payloadMaxY, y)
        }
        Log.d(
            TAG,
            "frame=$frameCount sourceLit=$sourceLitCount sourceBounds=[$minX,$minY]-[$maxX,$maxY] " +
                "payloadLit=$payloadLitCount payloadBounds=[$payloadMinX,$payloadMinY]-[$payloadMaxX,$payloadMaxY] " +
                "cropLeft=$H_CROP_LEFT"
        )
    }

    fun turnOff() {
        try {
            manager?.turnOff()
        } catch (error: Exception) {
            Log.e(TAG, "Failed to turn off Glyph matrix", error)
        }
    }

    fun release() {
        ready = false
        manager = null
    }
}
