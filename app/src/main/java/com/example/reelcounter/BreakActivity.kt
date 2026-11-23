package com.example.reelcounter

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView

// A calm break at the red limit: a slow breathing circle and, if you like, the break sign.
// It never blocks. It closes itself after a few seconds, or when you tap the button.
class BreakActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null

    // A circle that grows while you breathe in and shrinks while you breathe out.
    private class BreathView(context: Context) : View(context) {
        var level = 0f
        var word = "Breathe in"
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.GREEN }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Theme.BRASS
            style = Paint.Style.STROKE
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Theme.CREAM
            textAlign = Paint.Align.CENTER
            typeface = Theme.SERIF_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val maxR = min(width, height) / 2f
            val r = maxR * (0.45f + 0.55f * level)
            ring.strokeWidth = Theme.dp(context, 2f).toFloat()
            canvas.drawCircle(cx, cy, maxR - ring.strokeWidth, ring)
            canvas.drawCircle(cx, cy, r - ring.strokeWidth * 2f, fill)
            text.textSize = Theme.dp(context, 20f).toFloat()
            canvas.drawText(word, cx, cy + text.textSize / 3f, text)
        }

        private fun min(a: Int, b: Int): Int = if (a < b) a else b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = Prefs.getBreakMode(this)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        val pad = Theme.dp(this, 24f)
        layout.setPadding(pad, pad, pad, pad)
        layout.setBackgroundColor(Theme.BG)

        val buddy = BuddyView(this, Theme.dp(this, if (mode == 2) 210f else 120f))
        buddy.setExpression(if (mode == 2) BuddyView.BREAK else BuddyView.LOVE)
        layout.addView(buddy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = Theme.heading(this, "Pause for a breath", 24f)
        title.gravity = Gravity.CENTER
        layout.addView(title, Theme.lp(this, 10f, 12f))

        val breath = BreathView(this)
        layout.addView(breath, LinearLayout.LayoutParams(Theme.dp(this, 230f), Theme.dp(this, 230f)))

        val hint = Theme.soft(this, "Slowly, with the circle. It closes by itself.", 14f)
        hint.gravity = Gravity.CENTER
        layout.addView(hint, Theme.lp(this, 12f, 16f))

        val done = Theme.softButton(this, "I am back")
        done.setOnClickListener { finish() }
        layout.addView(done, Theme.lp(this, 0f, 0f))

        setContentView(layout)

        // Four seconds in, four seconds out.
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 4000L
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        var last = 0f
        a.addUpdateListener {
            val v = it.animatedValue as Float
            breath.word = if (v >= last) "Breathe in" else "Breathe out"
            last = v
            breath.level = v
            breath.invalidate()
        }
        a.start()
        animator = a
        handler.postDelayed({ finish() }, 12000L)
    }

    override fun onDestroy() {
        animator?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
