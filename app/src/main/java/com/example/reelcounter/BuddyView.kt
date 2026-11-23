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
// "small" uses the lighter pictures with no props, for the little bubble.
class BuddyView(context: Context, private val heightPx: Int, private val small: Boolean = false) : View(context) {

    companion object {
        const val FRESH = 0
        const val CURIOUS = 1
        const val CONCERNED = 2
        const val TIRED = 3
        const val DRAINED = 4
        const val WORN = 5
        const val COLLAPSED = 6
        const val BREAK = 7
        const val SLEEPING = 8
        const val RECOVERED = 9
        const val ENCOURAGE = 10
        const val TROPHY = 11
        const val LOVE = 12
        const val CONFUSED = 13

        // Older names, kept so every screen still finds its picture.
        const val HAPPY = FRESH
        const val WORRIED = DRAINED
        const val SAD = BREAK
        const val PROUD = TROPHY
        const val EXCITED = ENCOURAGE
        const val SLEEPY = SLEEPING
        const val RELAXED = LOVE
        const val ENCOURAGING = ENCOURAGE

        private val FULL = intArrayOf(
            R.drawable.bb_fresh, R.drawable.bb_curious, R.drawable.bb_concerned,
            R.drawable.bb_tired, R.drawable.bb_drained, R.drawable.bb_worn,
            R.drawable.bb_collapsed, R.drawable.bb_break, R.drawable.bb_sleeping,
            R.drawable.bb_recovered, R.drawable.bb_encourage, R.drawable.bb_trophy,
            R.drawable.bb_love, R.drawable.bb_confused
        )
        private val LITE = intArrayOf(
            R.drawable.bb_fresh_b, R.drawable.bb_curious_b, R.drawable.bb_concerned_b,
            R.drawable.bb_tired_b, R.drawable.bb_drained_b, R.drawable.bb_worn_b,
            R.drawable.bb_collapsed_b, R.drawable.bb_break_b, R.drawable.bb_sleeping_b,
            R.drawable.bb_recovered_b, R.drawable.bb_encourage_b, R.drawable.bb_trophy_b,
            R.drawable.bb_love_b, R.drawable.bb_confused_b
        )
        // The puppy of Kids mode: the same expressions, mapped to his ten pictures.
        private val PUP = intArrayOf(
            R.drawable.pp_happy, R.drawable.pp_curious, R.drawable.pp_worried,
            R.drawable.pp_resting, R.drawable.pp_sad, R.drawable.pp_sad,
            R.drawable.pp_sleep, R.drawable.pp_sleep, R.drawable.pp_sleep,
            R.drawable.pp_cheer, R.drawable.pp_thumbs, R.drawable.pp_cheer,
            R.drawable.pp_play, R.drawable.pp_calm
        )
        private const val FADE_SECONDS = 0.35f

        // The picture for an expression, for places that cannot use the view (the home-screen widget).
        fun drawableFor(context: Context, small: Boolean, expr: Int): Int {
            val list = if (Kids.on(context)) PUP else if (small) LITE else FULL
            return list[expr.coerceIn(0, list.size - 1)]
        }
    }

    private val puppy = Kids.on(context)
    private val RES = if (puppy) PUP else if (small) LITE else FULL

    private val bitmaps = arrayOfNulls<Bitmap>(RES.size)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()

    private var current = FRESH
    private var previous = FRESH
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
