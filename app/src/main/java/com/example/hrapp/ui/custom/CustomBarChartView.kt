package com.example.hrapp.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CustomBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var data: Map<String, Double> = emptyMap()
    private var color: Int = Color.BLUE

    fun setData(newData: Map<String, Double>, newColor: Int) {
        data = newData
        color = newColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()

        val paddingLeft = 40f
        val paddingRight = 40f
        val paddingTop = 40f
        val paddingBottom = 80f

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        val maxValue = data.values.maxOrNull() ?: 1.0
        val scale = if (maxValue > 0) chartHeight / maxValue else 1.0

        val barCount = data.size
        val barSpacing = 30f
        val barWidth = (chartWidth - (barSpacing * (barCount - 1))) / barCount

        var xOffset = paddingLeft
        var index = 0

        paint.textSize = 24f
        paint.isFakeBoldText = true

        data.forEach { (category, valDouble) ->
            val barHeight = (valDouble * scale).toFloat()
            val left = xOffset
            val top = height - paddingBottom - barHeight
            val right = left + barWidth
            val bottom = height - paddingBottom

            // Draw Bar
            paint.color = color
            canvas.drawRect(left, top, right, bottom, paint)

            // Draw Category Labels below chart
            paint.color = Color.WHITE
            canvas.drawText(category, left + 4f, height - 40f, paint)

            // Draw amounts on top of bars
            paint.color = Color.parseColor("#9CA3AF") // Gray text
            val amountLabel = "₹" + valDouble.toInt().toString()
            canvas.drawText(amountLabel, left + 4f, top - 12f, paint)

            xOffset += barWidth + barSpacing
            index++
        }

        // Draw Base line
        paint.color = Color.DKGRAY
        paint.strokeWidth = 3f
        canvas.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom, paint)
    }
}
