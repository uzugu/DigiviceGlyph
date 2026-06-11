package com.digimon.digiviceglyph.runtime

import android.graphics.Bitmap
import android.graphics.Color
import com.digimon.digiviceglyph.input.GlyphButton
import com.digimon.digiviceglyph.input.GlyphButtonSink

class DigivicePreviewRuntime : GlyphButtonSink {
    companion object {
        private const val SIZE = 25
        private const val ON = -0x1
        private const val OFF = Color.BLACK
    }

    private val pixels = IntArray(SIZE * SIZE)
    private val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

    private var buttonA = false
    private var buttonB = false
    private var buttonC = false
    private var frameCounter = 0
    private var lastAction = "IDLE"
    private var lastActionAtMs = 0L

    override fun onButtonDown(button: GlyphButton) {
        when (button) {
            GlyphButton.A -> buttonA = true
            GlyphButton.B -> buttonB = true
            GlyphButton.C -> buttonC = true
        }
        lastAction = "${button.name} DOWN"
        lastActionAtMs = System.currentTimeMillis()
    }

    override fun onButtonUp(button: GlyphButton) {
        when (button) {
            GlyphButton.A -> buttonA = false
            GlyphButton.B -> buttonB = false
            GlyphButton.C -> buttonC = false
        }
        lastAction = "${button.name} UP"
        lastActionAtMs = System.currentTimeMillis()
    }

    override fun triggerStep() {
        lastAction = "STEP"
        lastActionAtMs = System.currentTimeMillis()
    }

    fun renderFrame(): Bitmap {
        frameCounter++
        pixels.fill(OFF)
        drawFrame()
        drawHeader()
        drawWalker()
        drawButtonColumn()
        drawPulseBar()
        bitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        return bitmap
    }

    private fun drawFrame() {
        for (x in 2..20) {
            setPixel(x, 5)
            setPixel(x, 19)
        }
        for (y in 5..19) {
            setPixel(2, y)
            setPixel(20, y)
        }
        for (x in 4..18) {
            setPixel(x, 7)
            setPixel(x, 17)
        }
        for (y in 7..17) {
            setPixel(4, y)
            setPixel(18, y)
        }
    }

    private fun drawHeader() {
        drawGlyphChar('D', 2, 0)
        drawGlyphChar('V', 7, 0)
        drawGlyphChar('1', 12, 0)

        val recent = System.currentTimeMillis() - lastActionAtMs < 900L
        if (recent) {
            val actionCode = when {
                lastAction.startsWith("A") -> "A"
                lastAction.startsWith("B") -> "B"
                lastAction.startsWith("C") -> "C"
                else -> "I"
            }
            drawGlyphChar(actionCode[0], 18, 0)
        }
    }

    private fun drawWalker() {
        val x = 6 + (frameCounter / 2 % 9)
        val y = 10 + if ((frameCounter / 6) % 2 == 0) 0 else 1
        setPixel(x, y)
        setPixel(x + 1, y)
        setPixel(x + 2, y)
        setPixel(x + 1, y - 1)
        if (frameCounter % 4 < 2) {
            setPixel(x, y + 1)
            setPixel(x + 2, y + 1)
        } else {
            setPixel(x + 1, y + 1)
        }
    }

    private fun drawButtonColumn() {
        drawButton(22, 8, buttonA)
        drawButton(22, 12, buttonB)
        drawButton(22, 16, buttonC)
    }

    private fun drawButton(x: Int, y: Int, active: Boolean) {
        if (active) {
            for (dy in -1..1) {
                for (dx in -1..1) {
                    setPixel(x + dx, y + dy)
                }
            }
        } else {
            setPixel(x, y)
            setPixel(x - 1, y)
            setPixel(x + 1, y)
            setPixel(x, y - 1)
            setPixel(x, y + 1)
        }
    }

    private fun drawPulseBar() {
        val lit = 1 + (frameCounter % 8)
        for (i in 0 until 8) {
            if (i < lit) {
                setPixel(5 + i, 22)
            }
        }
    }

    private fun drawGlyphChar(char: Char, left: Int, top: Int) {
        val pattern = when (char) {
            'D' -> arrayOf(
                "1110",
                "1001",
                "1001",
                "1001",
                "1110"
            )
            'V' -> arrayOf(
                "1001",
                "1001",
                "1001",
                "0101",
                "0010"
            )
            '1' -> arrayOf(
                "0010",
                "0110",
                "0010",
                "0010",
                "0111"
            )
            'A' -> arrayOf(
                "0110",
                "1001",
                "1111",
                "1001",
                "1001"
            )
            'B' -> arrayOf(
                "1110",
                "1001",
                "1110",
                "1001",
                "1110"
            )
            'C' -> arrayOf(
                "0111",
                "1000",
                "1000",
                "1000",
                "0111"
            )
            else -> return
        }
        for (row in pattern.indices) {
            for (col in pattern[row].indices) {
                if (pattern[row][col] == '1') {
                    setPixel(left + col, top + row)
                }
            }
        }
    }

    private fun setPixel(x: Int, y: Int) {
        if (x !in 0 until SIZE || y !in 0 until SIZE) return
        pixels[y * SIZE + x] = ON
    }
}
