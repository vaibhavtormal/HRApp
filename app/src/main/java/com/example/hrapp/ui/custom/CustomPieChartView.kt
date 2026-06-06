package com.example.hrapp.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CustomPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    private var data: Map<String, Float> = emptyMap()
    private var colors: List<Int> = emptyList()

    fun setData(newData: Map<String, Float>, newColors: List<Int>) {
        data = newData
        colors = newColors
        invalidate() // Re-draw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()

        val size = Math.min(width, height) * 0.6f
        val left = (width - size) / 2
        val top = (height - size) / 2.5f
        rectF.set(left, top, left + size, top + size)

        val total = data.values.sum()
        if (total == 0f) return

        var startAngle = 0f
        var colorIndex = 0

        // Draw Arcs
        data.forEach { (label, value) ->
            val sweepAngle = (value / total) * 360f
            paint.color = colors.getOrElse(colorIndex) { Color.GRAY }
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
            colorIndex++
        }

        // Draw Center cutout for donut effect
        paint.color = Color.parseColor("#0F172A") // Matches bg_dark
        canvas.drawCircle(rectF.centerX(), rectF.centerY(), size * 0.3f, paint)

        // Draw Legend below chart
        var legendY = top + size + 40f
        var legendX = 40f
        colorIndex = 0

        paint.textSize = 28f
        paint.isFakeBoldText = true

        data.forEach { (label, value) ->
            paint.color = colors.getOrElse(colorIndex) { Color.GRAY }
            canvas.drawRect(legendX, legendY - 20f, legendX + 24f, legendY, paint)

            paint.color = Color.WHITE
            val pct = (value / total * 100).toInt()
            canvas.drawText("$label: $pct%", legendX + 36f, legendY, paint)

            legendX += (width - 80f) / 3f // Horizontal spacing
            colorIndex++
        }
    }
}
