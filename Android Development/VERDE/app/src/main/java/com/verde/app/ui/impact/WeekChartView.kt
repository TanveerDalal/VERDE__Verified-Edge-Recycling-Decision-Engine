package com.verde.app.ui.impact

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.verde.app.R
import com.verde.app.core.DayCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Column chart of scans per day for the last 7 days (Impact page).
 * Today's column is bright green, other days are soft green, empty days show a short grey stub.
 * The columns grow up from zero when [setDays] is called.
 */
class WeekChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var days: List<DayCount> = emptyList()
    private var growth = 1f                                    // 0 → 1 while the columns grow

    private val density = resources.displayMetrics.density
    private val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val bar = RectF()

    private val todayPaint = paint(R.color.verde_green)
    private val dayPaint = paint(R.color.verde_green_soft).apply { alpha = 150 }
    private val emptyPaint = paint(R.color.verde_text_faint).apply { alpha = 60 }
    private val labelPaint = textPaint(R.color.verde_text_faint, 11f)
    private val todayLabelPaint = textPaint(R.color.verde_text_main, 11f).apply { typeface = Typeface.DEFAULT_BOLD }
    private val countPaint = textPaint(R.color.verde_text_main, 12f).apply { typeface = Typeface.DEFAULT_BOLD }

    fun setDays(newDays: List<DayCount>, animate: Boolean = true) {
        days = newDays
        contentDescription = newDays.joinToString { "${dayFormat.format(Date(it.dayStart))}: ${it.count}" }
        if (!animate) {
            growth = 1f
            invalidate()
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { growth = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return

        val labelSpace = 22 * density                         // day names under the columns
        val countSpace = 18 * density                         // numbers above the columns
        val chartTop = paddingTop + countSpace
        val chartBottom = height - paddingBottom - labelSpace
        val chartHeight = chartBottom - chartTop
        val slot = (width - paddingLeft - paddingRight) / days.size.toFloat()
        val barWidth = minOf(slot * 0.55f, 34 * density)
        val radius = 8 * density
        val most = max(1, days.maxOf { it.count })

        days.forEachIndexed { index, day ->
            val isToday = index == days.lastIndex
            val centre = paddingLeft + slot * index + slot / 2

            if (day.count == 0) {
                val stub = 4 * density
                bar.set(centre - barWidth / 2, chartBottom - stub, centre + barWidth / 2, chartBottom)
                canvas.drawRoundRect(bar, stub / 2, stub / 2, emptyPaint)
            } else {
                val top = chartBottom - chartHeight * (day.count / most.toFloat()) * growth
                bar.set(centre - barWidth / 2, top, centre + barWidth / 2, chartBottom)
                canvas.drawRoundRect(bar, radius, radius, if (isToday) todayPaint else dayPaint)
                if (growth > 0.95f) canvas.drawText(day.count.toString(), centre, top - 6 * density, countPaint)
            }

            val label = dayFormat.format(Date(day.dayStart))
            canvas.drawText(label, centre, height - paddingBottom - 6 * density, if (isToday) todayLabelPaint else labelPaint)
        }
    }

    private fun paint(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colour)
    }

    private fun textPaint(colour: Int, sizeSp: Float) = paint(colour).apply {
        textAlign = Paint.Align.CENTER
        textSize = sizeSp * resources.displayMetrics.scaledDensity
    }
}
