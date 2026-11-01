package com.example.reelcounter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// One bar of the chart (a day or a month). Arrays follow the order of Prefs.APPS.
// reels: reels counted. secs: time on reels. usage: time the app was open (0 if unknown).
class DayData(
    val label: String,
    val longLabel: String,
    val reels: IntArray,
    val secs: IntArray,
    val usage: IntArray
) {
    fun totalReels(): Int = reels.sum()
    fun totalSecs(): Int = secs.sum()

    // Time in the app, never less than the time on reels.
    fun appSecs(a: Int): Int = if (usage[a] > secs[a]) usage[a] else secs[a]

    fun totalAppSecs(): Int {
        var s = 0
        for (a in reels.indices) s += appSecs(a)
        return s
    }
}

// Stacked bars on one shared scale.
// Reels view (mode 0): each bar is split by app colour, showing reels counted.
// Time view (mode 1): each app has its own colour for reel time, with a lighter tint
// of that colour above it for the rest of its time in the app.
// Tapping a bar selects that day or month.
class StackedBarView(context: Context) : View(context) {

    private var days: List<DayData> = emptyList()
    private var baseColors = IntArray(0)
    private var segColors = IntArray(0)
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
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.GREEN
        typeface = Theme.SERIF_BOLD
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
        this.baseColors = colors
        this.mode = mode
        segColors = IntArray(colors.size * 2)
        for (a in colors.indices) {
            segColors[a * 2] = colors[a]
            segColors[a * 2 + 1] = tint(colors[a])
        }
        selected = -1
        // A different number of bars starts from nothing, so the bars grow from the floor.
        fromFrac = emptyArray()
        toFrac = emptyArray()
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

    // A lighter version of a colour, for the "rest of the time in the app" part.
    private fun tint(c: Int): Int {
        val r = Color.red(c) + ((255 - Color.red(c)) * 0.55f).toInt()
        val g = Color.green(c) + ((255 - Color.green(c)) * 0.55f).toInt()
        val b = Color.blue(c) + ((255 - Color.blue(c)) * 0.55f).toInt()
        return Color.rgb(r, g, b)
    }

    // ---- Heights as fractions of the axis, animated when the data or the mode changes ----
    // Each app has two parts: reel part first, then the tint part (time view only).
    private fun computeFractions(): Array<FloatArray> {
        val na = baseColors.size
        val values = Array(days.size) { d ->
            FloatArray(na * 2) { k ->
                val a = k / 2
                val isTint = k % 2 == 1
                if (mode == 0) {
                    if (isTint) 0f else days[d].reels[a].toFloat()
                } else {
                    val reel = days[d].secs[a].toFloat()
                    if (isTint) max(0f, days[d].appSecs(a).toFloat() - reel) else reel
                }
            }
        }
        var biggest = 0f
        for (row in values) {
            val s = row.sum()
            if (s > biggest) biggest = s
        }
        axisMax = niceMax(biggest)
        return Array(days.size) { d -> FloatArray(na * 2) { k -> values[d][k] / axisMax } }
    }

    private fun currentFractions(): Array<FloatArray> {
        return Array(toFrac.size) { d ->
            FloatArray(toFrac[d].size) { k ->
                fromFrac[d][k] + (toFrac[d][k] - fromFrac[d][k]) * progress
            }
        }
    }

    private fun retarget() {
        val newFrac = computeFractions()
        val shown = if (toFrac.size == days.size && fromFrac.size == days.size) {
            currentFractions()
        } else {
            Array(days.size) { FloatArray(baseColors.size * 2) }
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
            val steps = floatArrayOf(10f, 20f, 30f, 50f, 75f, 100f, 150f, 200f, 300f, 400f, 500f, 750f, 1000f, 1500f, 2000f, 3000f, 5000f, 10000f, 20000f)
            for (s in steps) {
                if (maxV <= s) return s
            }
            return ceil((maxV / 10000f).toDouble()).toFloat() * 10000f
        }
        // Time in seconds: 10m, 20m, 30m, 1h, 1.5h, 2h, 3h, 4h, 6h, 8h, 12h, then whole days
        val steps = floatArrayOf(600f, 1200f, 1800f, 3600f, 5400f, 7200f, 10800f, 14400f, 21600f, 28800f, 43200f, 86400f,
            172800f, 360000f, 720000f, 1440000f, 2880000f)
        for (s in steps) {
            if (maxV <= s) return s
        }
        return ceil((maxV / 86400f).toDouble()).toFloat() * 86400f
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

    // A short number written above a bar.
    private fun barNumber(d: DayData): String {
        if (mode == 0) return d.totalReels().toString()
        val s = d.totalAppSecs()
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h${String.format(Locale.US, "%02d", m)}" else "${m}m"
    }

    override fun onDraw(canvas: Canvas) {
        if (days.isEmpty() || toFrac.size != days.size) return

        val n = days.size
        val dense = n > 12
        val w = width.toFloat()
        val h = height.toFloat()
        val left = dp(34f)
        val right = dp(4f)
        val top = dp(22f)
        val labelsHeight = dp(22f)
        val baseline = h - labelsHeight
        val chartH = baseline - top
        val slot = (w - left - right) / n
        val barW = slot * (if (dense) 0.7f else 0.56f)

        axisPaint.textSize = dp(10f)
        dayPaint.textSize = if (dense) dp(9f) else dp(11f)
        numberPaint.textSize = if (dense) dp(9f) else dp(10.5f)

        // Grid lines at 0, half and full.
        for (i in 0..2) {
            val frac = i / 2f
            val y = baseline - chartH * frac
            canvas.drawLine(left, y, w - right, y, gridPaint)
            canvas.drawText(axisLabel(axisMax * frac), left - dp(5f), y + dp(3.5f), axisPaint)
        }

        val frac = currentFractions()
        val radius = min(dp(6f), barW / 2f)

        // The tallest bar always gets its number, even when the chart is crowded.
        var tallest = 0
        var tallestSum = -1f
        for (i in 0 until n) {
            var sum = 0f
            for (k in toFrac[i].indices) sum += toFrac[i][k]
            if (sum > tallestSum) {
                tallestSum = sum
                tallest = i
            }
        }

        for (i in 0 until n) {
            val x0 = left + slot * i + (slot - barW) / 2f
            val cx = left + slot * i + slot / 2f

            if (i == selected) {
                rect.set(left + slot * i + dp(0.5f), top - dp(14f), left + slot * (i + 1) - dp(0.5f), h - dp(2f))
                canvas.drawRoundRect(rect, dp(6f), dp(6f), highlightPaint)
            }

            var sum = 0f
            for (k in segColors.indices) sum += frac[i][k]
            var barTop = baseline
            if (sum > 0.002f) {
                barTop = baseline - sum * chartH
                path.reset()
                rect.set(x0, barTop, x0 + barW, baseline)
                path.addRoundRect(rect, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
                canvas.save()
                canvas.clipPath(path)
                var y = baseline
                for (k in segColors.indices) {
                    val segH = frac[i][k] * chartH
                    if (segH <= 0f) continue
                    barPaint.color = segColors[k]
                    canvas.drawRect(x0, y - segH, x0 + barW, y, barPaint)
                    y -= segH
                }
                canvas.restore()
            }

            // The number above the bar.
            val showNumber = !dense || i == tallest || i >= n - 2
            if (showNumber && (sum > 0.002f || !dense)) {
                canvas.drawText(barNumber(days[i]), cx, barTop - dp(4f), numberPaint)
            }

            // The day or month name under the bar. On a crowded chart only every fifth.
            val showLabel = !dense || (n - 1 - i) % 5 == 0
            if (showLabel) {
                dayPaint.isFakeBoldText = (i == selected)
                canvas.drawText(days[i].label, cx, baseline + dp(15f), dayPaint)
            }
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
