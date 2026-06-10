package com.digimon.digiviceglyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class GlyphPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B120E")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#547A52")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = false
        isDither = false
    }

    private var bitmap: Bitmap? = null
    private val srcRect = Rect()
    private val dstRect = Rect()

    fun setBitmap(value: Bitmap?) {
        bitmap = value
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (width * 1.18f).toInt()
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRoundRect(0f, 0f, w, h, 32f, 32f, backgroundPaint)
        canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, 30f, 30f, borderPaint)

        val inset = (width * 0.08f).toInt()
        val displayTop = (height * 0.14f).toInt()
        val displayHeight = (height * 0.58f).toInt()
        val displayRect = Rect(inset, displayTop, width - inset, displayTop + displayHeight)

        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#13201A")
        }
        canvas.drawRoundRect(
            displayRect.left.toFloat(),
            displayRect.top.toFloat(),
            displayRect.right.toFloat(),
            displayRect.bottom.toFloat(),
            20f,
            20f,
            panelPaint
        )

        val bmp = bitmap
        if (bmp != null) {
            srcRect.set(0, 0, bmp.width, bmp.height)
            val padding = 18
            dstRect.set(
                displayRect.left + padding,
                displayRect.top + padding,
                displayRect.right - padding,
                displayRect.bottom - padding
            )
            canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D5E4D1")
            textSize = 32f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("DIGIVICE V1", w / 2f, h - 54f, labelPaint)
    }
}
