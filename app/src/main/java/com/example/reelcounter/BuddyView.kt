package com.example.reelcounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Brain Buddy: shows one picture per expression, fades from one to the next,
// breathes gently and does a small hop on milestones. It never walks.
class BuddyView(context: Context, private val heightPx: Int) : View(context) {

    companion object {
        const val HAPPY = 0
        const val CURIOUS = 1
        const val CONCERNED = 2
        const val WORRIED = 3
        const val SAD = 4
        const val PROUD = 5
        const val EXCITED = 6
        const val SLEEPY = 7
        const val RELAXED = 8
        const val ENCOURAGING = 9

        private val RES = intArrayOf(
            R.drawable.buddy_happy, R.drawable.buddy_curious, R.drawable.buddy_concerned,
            R.drawable.buddy_worried, R.drawable.buddy_sad, R.drawable.buddy_proud,
            R.drawable.buddy_excited, R.drawable.buddy_sleepy, R.drawable.buddy_relaxed,
            R.drawable.buddy_encouraging
        )
        private const val FADE_SECONDS = 0.35f
    }

    private val bitmaps = arrayOfNulls<Bitmap>(RES.size)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()

    private var current = HAPPY
    private var previous = HAPPY
    private var fade = 1f
    private var running = false
    private var lastNs = 0L
    private var t = 0f
    private var hopT = -1f

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = (now - lastNs) / 1000000000f
            if (dt >= 0.033f) {
                lastNs = now
                step(min(dt, 0.1f))
                invalidate()
            }
            postOnAnimation(this)
        }
    }

    // The bitmaps are square, so the view is as wide as it is tall.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(heightPx, heightPx)
    }

    private fun bitmapFor(index: Int): Bitmap? {
        var b = bitmaps[index]
        if (b == null) {
            try {
                val o = BitmapFactory.Options()
                o.inScaled = false
                val full = BitmapFactory.decodeResource(resources, RES[index], o)
                val target = max(heightPx * 2, 64)
                b = if (full != null && full.width > target) {
                    val s = Bitmap.createScaledBitmap(full, target, target, true)
                    if (s !== full) full.recycle()
                    s
                } else {
                    full
                }
                bitmaps[index] = b
            } catch (e: Throwable) {
                b = null
            }
        }
        return b
    }

    // Choose the expression. It fades in over a third of a second.
    fun setExpression(e: Int) {
        val n = e.coerceIn(0, RES.size - 1)
        if (n == current) return
        previous = current
        current = n
        fade = 0f
        bitmapFor(n)
    }

    // One quick happy hop, used on milestones.
    fun celebrate() {
        hopT = 0f
    }

    fun start() {
        if (running) return
        running = true
        lastNs = System.nanoTime()
        bitmapFor(current)
        postOnAnimation(ticker)
    }

    fun stop() {
        running = false
        removeCallbacks(ticker)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        for (i in bitmaps.indices) bitmaps[i] = null
        super.onDetachedFromWindow()
    }

    private fun step(dt: Float) {
        t += dt
        if (fade < 1f) fade = min(1f, fade + dt / FADE_SECONDS)

        var hop = 0f
        if (hopT >= 0f) {
            hopT += dt / 0.5f
            if (hopT >= 1f) {
                hopT = -1f
            } else {
                hop = sin(hopT * PI.toFloat()) * 0.2f
            }
        }
        // A very small breathing movement, so the character feels alive.
        val breathe = sin(t * 2.2f) * 0.012f
        translationY = -hop * heightPx
        scaleX = 1f - breathe * 0.5f
        scaleY = 1f + breathe
        pivotX = width / 2f
        pivotY = height * 0.97f
    }

    private fun drawBitmap(canvas: Canvas, b: Bitmap?, alpha: Int) {
        if (b == null || alpha <= 0) return
        paint.alpha = alpha
        src.set(0, 0, b.width, b.height)
        dst.set(0, 0, width, height)
        canvas.drawBitmap(b, src, dst, paint)
    }

    override fun onDraw(canvas: Canvas) {
        if (fade < 1f) {
            drawBitmap(canvas, bitmapFor(previous), ((1f - fade) * 255f).toInt())
            drawBitmap(canvas, bitmapFor(current), (fade * 255f).toInt())
        } else {
            drawBitmap(canvas, bitmapFor(current), 255)
        }
    }
}
