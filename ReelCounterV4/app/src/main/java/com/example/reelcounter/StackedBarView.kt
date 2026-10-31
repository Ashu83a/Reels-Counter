package com.example.reelcounter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Locale
import kotlin.math.ceil

// One day of numbers: reels and seconds for each app (same order as Prefs.APPS).
class DayData(
    val label: String,
    val longLabel: String,
    val reels: IntArray,
    val secs: IntArray
) {
    fun totalReels(): Int = reels.sum()
    fun totalSecs(): Int = secs.sum()
}

// Stacked bars, one per day, split by app colour, all on one shared scale.
// Mode 0 shows reels, mode 1 shows time. Tapping a bar selects that day.
class StackedBarView(context: Context) : View(context) {

    private var days: List<DayData> = emptyList()
    private var colors = IntArray(0)
    private var mode = 0
    private var axisMax = 10f
    private var selected = -1

    private var fromFrac: Array<FloatArray> = emptyArray()
    private var toFrac: Array<FloatArray> = emptyArray()
    private var progress = 1f
    private var anim: ValueAnimator? = null

    var onBarTap: ((Int) -> Unit)? = null

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.DIVIDER
        strokeWidth = 1f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.TEXT_SOFT
        textAlign = Paint.Align.RIGHT
    }
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.TEXT
        textAlign = Paint.Align.CENTER
    }
    private val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.TEXT_SOFT
        textAlign = Paint.Align.CENTER
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.BRASS_SOFT
        alpha = 90
    }
    private val path = Path()
    private val rect = RectF()

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    fun setData(days: List<DayData>, colors: IntArray, mode: Int) {
        this.days = days
        this.colors = colors
        this.mode = mode
        if (selected >= days.size) selected = -1
        retarget()
    }

    fun setMode(mode: Int) {
        this.mode = mode
        retarget()
    }

    fun clearSelection() {
        selected = -1
        invalidate()
    }

    // ---- Heights as fractions of the axis, animated when the data or the mode changes ----
    private fun computeFractions(): Array<FloatArray> {
        var biggest = 0f
        val values = Array(days.size) { d ->
            FloatArray(colors.size) { a ->
                if (mode == 0) days[d].reels[a].toFloat() else days[d].secs[a].toFloat()
            }
        }
        for (row in values) {
            val s = row.sum()
            if (s > biggest) biggest = s
        }
        axisMax = niceMax(biggest)
        return Array(days.size) { d -> FloatArray(colors.size) { a -> values[d][a] / axisMax } }
    }

    private fun currentFractions(): Array<FloatArray> {
        return Array(toFrac.size) { d ->
            FloatArray(toFrac[d].size) { a ->
                fromFrac[d][a] + (toFrac[d][a] - fromFrac[d][a]) * progress
            }
        }
    }

    private fun retarget() {
        val newFrac = computeFractions()
        val shown = if (toFrac.size == days.size && fromFrac.size == days.size) {
            currentFractions()
        } else {
            Array(days.size) { FloatArray(colors.size) }
        }
        fromFrac = shown
        toFrac = newFrac
        anim?.cancel()
        progress = 0f
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 600L
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
        invalidate()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    private fun niceMax(maxV: Float): Float {
        if (mode == 0) {
            val steps = floatArrayOf(10f, 20f, 30f, 50f, 75f, 100f, 150f, 200f, 300f, 400f, 500f, 750f, 1000f, 1500f, 2000f, 3000f)
            for (s in steps) {
                if (maxV <= s) return s
            }
            return ceil((maxV / 1000f).toDouble()).toFloat() * 1000f
        }
        // Time in seconds: 10m, 20m, 30m, 1h, 1.5h, 2h, 3h, 4h, 6h, 8h
        val steps = floatArrayOf(600f, 1200f, 1800f, 3600f, 5400f, 7200f, 10800f, 14400f, 21600f, 28800f)
        for (s in steps) {
            if (maxV <= s) return s
        }
        return ceil((maxV / 3600f).toDouble()).toFloat() * 3600f
    }

    private fun axisLabel(value: Float): String {
        if (mode == 0) return value.toInt().toString()
        val minutes = value / 60f
        if (minutes >= 60f) {
            val hours = minutes / 60f
            return if (hours == hours.toInt().toFloat()) "${hours.toInt()}h" else String.format(Locale.US, "%.1fh", hours)
        }
        return "${minutes.toInt()}m"
    }

    private fun totalLabel(d: DayData): String {
        if (mode == 0) return d.totalReels().toString()
        val s = d.totalSecs()
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h${String.format(Locale.US, "%02d", m)}" else "${m}m"
    }

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty() || toFrac.size != days.size) return

        val w = width.toFloat()
        val h = height.toFloat()
        val left = dp(34f)
        val right = dp(4f)
        val top = dp(10f)
        val labelsHeight = dp(36f)
        val baseline = h - labelsHeight
        val chartH = baseline - top
        val slot = (w - left - right) / days.size
        val barW = slot * 0.56f

        axisPaint.textSize = dp(10f)
        dayPaint.textSize = dp(11f)
        totalPaint.textSize = dp(10f)

        // Grid lines at 0, half and full.
        for (i in 0..2) {
            val frac = i / 2f
            val y = baseline - chartH * frac
            canvas.drawLine(left, y, w - right, y, gridPaint)
            canvas.drawText(axisLabel(axisMax * frac), left - dp(5f), y + dp(3.5f), axisPaint)
        }

        val frac = currentFractions()
        val radius = dp(6f)

        for (i in days.indices) {
            val x0 = left + slot * i + (slot - barW) / 2f

            if (i == selected) {
                rect.set(left + slot * i + dp(1f), top, left + slot * (i + 1) - dp(1f), h - dp(2f))
                canvas.drawRoundRect(rect, dp(8f), dp(8f), highlightPaint)
            }

            var sum = 0f
            for (a in colors.indices) sum += frac[i][a]
            if (sum > 0.002f) {
                val barTop = baseline - sum * chartH
                path.reset()
                rect.set(x0, barTop, x0 + barW, baseline)
                path.addRoundRect(rect, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
                canvas.save()
                canvas.clipPath(path)
                var y = baseline
                for (a in colors.indices) {
                    val segH = frac[i][a] * chartH
                    if (segH <= 0f) continue
                    barPaint.color = colors[a]
                    canvas.drawRect(x0, y - segH, x0 + barW, y, barPaint)
                    y -= segH
                }
                canvas.restore()
            }

            val cx = left + slot * i + slot / 2f
            dayPaint.isFakeBoldText = (i == selected)
            canvas.drawText(days[i].label, cx, baseline + dp(15f), dayPaint)
            canvas.drawText(totalLabel(days[i]), cx, baseline + dp(29f), totalPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (days.isEmpty()) return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action == MotionEvent.ACTION_UP) {
            val left = dp(34f)
            val right = dp(4f)
            val slot = (width.toFloat() - left - right) / days.size
            val idx = ((event.x - left) / slot).toInt()
            if (event.x >= left && idx in days.indices) {
                selected = if (selected == idx) -1 else idx
                invalidate()
                onBarTap?.invoke(selected)
            }
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
