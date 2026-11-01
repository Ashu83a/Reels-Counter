package com.example.reelcounter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// A ring chart: one coloured slice per app, with the total in the middle
// and the share written on each slice that is big enough.
class PieChartView(context: Context) : View(context) {

    private var values = FloatArray(0)
    private var colors = IntArray(0)
    private var bigText = ""
    private var smallText = ""
    private var progress = 1f
    private var anim: ValueAnimator? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.GREEN
        typeface = Theme.SERIF_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.TEXT_SOFT
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        typeface = Theme.SERIF_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()

    fun setData(values: FloatArray, colors: IntArray, bigText: String, smallText: String) {
        this.values = values
        this.colors = colors
        this.bigText = bigText
        this.smallText = smallText
        anim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 700L
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        a.start()
        anim = a
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val stroke = size * 0.17f
        val cx = w / 2f
        val cy = h / 2f
        val radius = size / 2f - stroke / 2f - size * 0.02f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        ringPaint.strokeWidth = stroke
        ringPaint.color = Theme.DIVIDER
        canvas.drawArc(rect, 0f, 360f, false, ringPaint)

        var total = 0f
        var slices = 0
        for (v in values) {
            if (v > 0f) {
                total += v
                slices++
            }
        }
        labelPaint.textSize = stroke * 0.36f
        if (total > 0f) {
            val gap = if (slices > 1) 2f else 0f
            var start = -90f
            for (i in values.indices) {
                val v = values[i]
                if (v <= 0f) continue
                val share = v / total
                val sweep = share * 360f * progress
                ringPaint.color = colors[i]
                val drawSweep = if (sweep - gap > 0.5f) sweep - gap else sweep
                canvas.drawArc(rect, start + gap / 2f, drawSweep, false, ringPaint)

                // The share, written on the slice once it has grown.
                if (share >= 0.08f && progress > 0.95f) {
                    val mid = (start + sweep / 2f) * PI.toFloat() / 180f
                    val lx = cx + radius * cos(mid)
                    val ly = cy + radius * sin(mid)
                    val text = (share * 100f + 0.5f).toInt().toString() + "%"
                    canvas.drawText(text, lx, ly - (labelPaint.ascent() + labelPaint.descent()) / 2f, labelPaint)
                }
                start += sweep
            }
        }

        bigPaint.textSize = size * 0.19f
        smallPaint.textSize = size * 0.07f
        val bigY = cy - (bigPaint.ascent() + bigPaint.descent()) / 2f - size * 0.015f
        canvas.drawText(bigText, cx, bigY, bigPaint)
        canvas.drawText(smallText, cx, bigY + size * 0.11f, smallPaint)
    }
}
