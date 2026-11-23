package com.example.reelcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// A jar of dots. One dot for every reel allowed before the lock limit.
// Filled dots are coloured by the limit band they fall in: green, yellow, orange, red.
// It tells the activity where the top of the pile is, so Brain Buddy can stand on it.
class JarView(context: Context) : View(context) {

    private var count = 0
    private var l1 = 100
    private var l2 = 150
    private var l3 = 200
    private var l4 = 300

    // Called with the centre x and the top y of the pile, in this view's own pixels.
    var onPile: ((Float, Float, Float) -> Unit)? = null

    private val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.BRASS
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.DIVIDER
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val rect = RectF()

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun cols(): Int = min(30, max(12, ceil(sqrt(l4 * 1.3)).toInt()))
    private fun rows(): Int = ceil(l4 / cols().toFloat()).toInt()

    fun setData(count: Int, l1: Int, l2: Int, l3: Int, l4: Int) {
        this.count = count
        this.l1 = l1
        this.l2 = l2
        this.l3 = l3
        this.l4 = max(1, l4)
        requestLayout()
        invalidate()
    }

    private fun sideGap(): Float = dp(10f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val spacing = (w - 2 * sideGap()) / cols()
        val h = (rows() * spacing + dp(26f)).toInt()
        setMeasuredDimension(w, h)
    }

    private fun bandColor(index: Int): Int = when {
        index < l1 -> Theme.GREEN
        index < l2 -> Color.rgb(249, 168, 37)
        index < l3 -> Color.rgb(245, 124, 0)
        else -> Color.rgb(211, 47, 47)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val c = cols()
        val spacing = (w - 2 * sideGap()) / c
        val radius = spacing * 0.34f
        val bottom = height - dp(14f)
        val left = sideGap()

        // The jar walls.
        wall.strokeWidth = dp(2f)
        rect.set(left - dp(2f), dp(4f), w - left + dp(2f), height - dp(6f))
        canvas.drawRoundRect(rect, dp(18f), dp(18f), wall)

        val filled = min(count, l4)
        for (i in 0 until l4) {
            val row = i / c
            val col = i % c
            val cx = left + (col + 0.5f) * spacing
            val cy = bottom - (row + 0.5f) * spacing
            if (i < filled) {
                dot.color = bandColor(i)
                canvas.drawCircle(cx, cy, radius, dot)
            } else {
                empty.strokeWidth = dp(1.2f)
                canvas.drawCircle(cx, cy, radius * 0.9f, empty)
            }
        }

        // Where Brain Buddy stands: on the top row of the pile.
        val last = max(0, filled - 1)
        val topRow = last / c
        val topCol = last % c
        val px = left + (topCol + 0.5f) * spacing
        val py = bottom - (topRow + 1) * spacing
        onPile?.invoke(px, py, spacing)
    }
}
