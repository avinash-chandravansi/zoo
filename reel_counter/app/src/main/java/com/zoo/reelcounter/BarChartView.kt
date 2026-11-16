package com.zoo.reelcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max

/** Simple 7-day bar chart with a dashed line at the daily limit. */
class BarChartView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var data: List<Pair<String, Int>> = emptyList()
    private var limit = 50

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent) }
    private val overPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.bad) }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.track) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.muted); textSize = sp(11f); textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text); textSize = sp(11f); textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val limitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.muted); strokeWidth = dp(1f); style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val r = RectF()

    fun setData(days: List<Pair<String, Int>>, limit: Int) {
        data = days
        this.limit = limit
        contentDescription = days.joinToString(", ") { "${it.first}: ${it.second} reels" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (data.isEmpty()) return
        val labelH = sp(11f) + dp(8f)
        val valueH = sp(11f) + dp(6f)
        val top = valueH
        val bottom = height - labelH
        val chartH = bottom - top
        val maxV = max(limit, data.maxOf { it.second }).coerceAtLeast(1)
        val slot = width / data.size.toFloat()
        val barW = slot * 0.46f
        val radius = dp(6f)

        val limitY = bottom - chartH * limit / maxV
        canvas.drawLine(0f, limitY, width.toFloat(), limitY, limitPaint)

        data.forEachIndexed { i, (label, v) ->
            val cx = slot * i + slot / 2
            val h = if (v == 0) dp(4f) else max(dp(4f), chartH * v / maxV)
            r.set(cx - barW / 2, bottom - h, cx + barW / 2, bottom)
            val p = when {
                v == 0 -> emptyPaint
                v >= limit -> overPaint
                else -> barPaint
            }
            canvas.drawRoundRect(r, radius, radius, p)
            if (v > 0) canvas.drawText(v.toString(), cx, bottom - h - dp(5f), valuePaint)
            canvas.drawText(label, cx, height - dp(2f), labelPaint)
        }
    }
}
