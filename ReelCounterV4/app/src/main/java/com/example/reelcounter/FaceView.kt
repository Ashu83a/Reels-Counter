package com.example.reelcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// A yellow face with big eyes. It floats along the pill, bobs, taps, waits,
// glances at the user and blinks at uneven, natural intervals.
class FaceView(context: Context) : View(context) {

    private companion object {
        const val STATE_WALK = 0
        const val STATE_TAP = 1
        const val STATE_WAIT = 2
        val TWO_PI = (2.0 * PI).toFloat()
        val ONE_PI = PI.toFloat()
    }

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7C948.toInt()
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8860B.toInt()
        style = Paint.Style.STROKE
    }
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val eyeRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A6D1B.toInt()
        style = Paint.Style.STROKE
    }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2B2118.toInt()
        style = Paint.Style.FILL
    }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A3E0A.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7FC4F2.toInt()
        style = Paint.Style.FILL
    }
    private val tmpRect = RectF()
    private val tmpPath = Path()

    private var mood = 0
    private var travel = 0f
    private var running = false
    private var lastNs = 0L
    private var t = 0f

    private var state = STATE_WAIT
    private var stateLeft = 1f
    private var dir = 1f
    private var posX = 0f
    private var phase = 0f

    private var mouth = 1f
    private var brow = 0f
    private var eyeOpen = 1f
    private var tear = 0f

    private var lookX = 0f
    private var lookY = 0.3f
    private var lookTx = 0f
    private var lookTy = 0.3f
    private var lookTimer = 1f

    private var blinkT = -1f
    private var blinkWait = 1.2f
    private var blink = 0f

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

    // 0 happy, 1 worried, 2 sad, 3 crying
    fun setMood(m: Int) {
        mood = m.coerceIn(0, 3)
    }

    // How far the face may float in pixels (the pill width minus the face width).
    fun setTravel(px: Float) {
        travel = max(0f, px)
        if (posX > travel / 2f) posX = travel / 2f
        if (posX < -travel / 2f) posX = -travel / 2f
    }

    // One quick happy hop, used on milestones.
    fun celebrate() {
        hopT = 0f
    }

    fun start() {
        if (running) return
        running = true
        lastNs = System.nanoTime()
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
        super.onDetachedFromWindow()
    }

    private fun rand(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)

    private fun startState(s: Int, secs: Float) {
        state = s
        stateLeft = secs
        phase = 0f
    }

    private fun pickNext() {
        if (mood == 3 || travel < 1f) {
            startState(STATE_WAIT, rand(1.5f, 3f))
            return
        }
        val roll = Random.nextFloat()
        if (roll < 0.62f) {
            startState(STATE_WALK, rand(2.5f, 5f))
        } else if (roll < 0.82f) {
            startState(STATE_TAP, rand(1f, 1.8f))
        } else {
            startState(STATE_WAIT, rand(1f, 2.2f))
        }
    }

    private fun step(dt: Float) {
        t += dt

        val sf = when (mood) {
            0 -> 1f
            1 -> 0.6f
            2 -> 0.35f
            else -> 0f
        }
        val hf = when (mood) {
            0 -> 1f
            1 -> 0.6f
            2 -> 0.3f
            else -> 0f
        }

        // Face expression eases smoothly towards the mood.
        val tMouth = when (mood) {
            0 -> 1f
            1 -> 0f
            2 -> -0.8f
            else -> -1f
        }
        val tBrow = when (mood) {
            0 -> 0f
            1 -> 0.6f
            2 -> 0.85f
            else -> 1f
        }
        val tEye = when (mood) {
            0 -> 1f
            1 -> 0.95f
            2 -> 0.8f
            else -> 0.68f
        }
        val tTear = if (mood == 3) 1f else 0f
        val k = min(1f, dt * 4f)
        mouth += (tMouth - mouth) * k
        brow += (tBrow - brow) * k
        eyeOpen += (tEye - eyeOpen) * k
        tear += (tTear - tear) * k

        // Movement: walk, tap or wait.
        var bounce = 0f
        var tilt = 0f
        stateLeft -= dt
        when (state) {
            STATE_WALK -> {
                if (travel < 1f || sf <= 0f) {
                    startState(STATE_WAIT, 1f)
                } else {
                    posX += dir * travel * 0.4f * sf * dt
                    val half = travel / 2f
                    if (posX >= half) {
                        posX = half
                        dir = -1f
                        startState(STATE_WAIT, rand(0.3f, 0.8f))
                    } else if (posX <= -half) {
                        posX = -half
                        dir = 1f
                        startState(STATE_WAIT, rand(0.3f, 0.8f))
                    } else if (stateLeft <= 0f) {
                        pickNext()
                    }
                    phase += dt * TWO_PI * 2.6f * sf
                    bounce = abs(sin(phase)) * 0.14f * hf
                    tilt = dir * 7f * sin(phase) * hf
                }
            }
            STATE_TAP -> {
                phase += dt * TWO_PI * 3.4f
                bounce = abs(sin(phase)) * 0.1f * max(hf, 0.5f)
                tilt = sin(phase) * 2.5f
                if (stateLeft <= 0f) pickNext()
            }
            else -> {
                tilt = sin(t * 1.6f) * (if (mood == 3) 4f else 1.5f)
                if (mood == 3) bounce = abs(sin(t * 9f)) * 0.03f
                if (stateLeft <= 0f) pickNext()
            }
        }

        // Glancing: mostly at the user, sometimes sideways.
        lookTimer -= dt
        if (lookTimer <= 0f) {
            lookTimer = rand(0.5f, 1.8f)
            val roll = Random.nextFloat()
            if (roll < 0.55f) {
                lookTx = 0f
                lookTy = 0.3f
            } else if (roll < 0.8f) {
                lookTx = dir * 0.9f
                lookTy = 0f
            } else {
                lookTx = -dir * 0.9f
                lookTy = -0.1f
            }
        }
        val kl = min(1f, dt * 9f)
        lookX += (lookTx - lookX) * kl
        lookY += (lookTy - lookY) * kl

        // Blinking at uneven intervals, sometimes a quick double blink.
        if (blinkT >= 0f) {
            blinkT += dt / 0.16f
            if (blinkT >= 1f) {
                blinkT = -1f
                blinkWait = if (Random.nextFloat() < 0.18f) 0.12f else rand(1.6f, 5.5f)
            }
        } else {
            blinkWait -= dt
            if (blinkWait <= 0f) blinkT = 0f
        }
        blink = if (blinkT >= 0f) sin(blinkT * ONE_PI) else 0f

        // Happy hop on milestones.
        var hop = 0f
        if (hopT >= 0f) {
            hopT += dt / 0.5f
            if (hopT >= 1f) {
                hopT = -1f
            } else {
                hop = sin(hopT * ONE_PI) * 0.45f
            }
        }

        val r = min(width, height) / 2f
        translationX = posX
        translationY = -(bounce + hop) * r
        rotation = tilt
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) / 2f * 0.94f
        val cx = w / 2f
        val cy = h / 2f

        outlinePaint.strokeWidth = r * 0.07f
        canvas.drawCircle(cx, cy, r, facePaint)
        canvas.drawCircle(cx, cy, r, outlinePaint)

        val er = r * 0.29f
        val ey = cy - r * 0.12f
        val exL = cx - r * 0.36f
        val exR = cx + r * 0.36f
        val hs = max(0.08f, eyeOpen * (1f - blink))
        drawEye(canvas, exL, ey, er, hs)
        drawEye(canvas, exR, ey, er, hs)

        if (brow > 0.05f) {
            linePaint.strokeWidth = r * 0.07f
            linePaint.alpha = (brow * 255f).toInt().coerceIn(0, 255)
            val yb = ey - er * 1.45f
            canvas.drawLine(exL - er * 0.9f, yb + brow * er * 0.35f, exL + er * 0.8f, yb - brow * er * 0.45f, linePaint)
            canvas.drawLine(exR - er * 0.8f, yb - brow * er * 0.45f, exR + er * 0.9f, yb + brow * er * 0.35f, linePaint)
            linePaint.alpha = 255
        }

        val my = cy + r * 0.45f
        val mw = r * 0.34f
        var ctrl = my + mouth * r * 0.5f
        if (mood == 1) ctrl += sin(t * 10f) * r * 0.04f
        linePaint.strokeWidth = r * 0.09f
        linePaint.alpha = 255
        tmpPath.reset()
        tmpPath.moveTo(cx - mw, my)
        tmpPath.quadTo(cx, ctrl, cx + mw, my)
        canvas.drawPath(tmpPath, linePaint)

        if (tear > 0.02f) {
            for (i in 0..1) {
                val tx = if (i == 0) exL else exR
                val p = (t * 0.9f + i * 0.5f) % 1f
                tearPaint.alpha = (tear * (1f - p) * 255f).toInt().coerceIn(0, 255)
                canvas.drawCircle(tx, ey + er * 0.8f + p * r * 0.55f, r * 0.08f, tearPaint)
            }
        }
    }

    private fun drawEye(canvas: Canvas, ex: Float, ey: Float, er: Float, hs: Float) {
        tmpRect.set(ex - er, ey - er * hs, ex + er, ey + er * hs)
        canvas.drawOval(tmpRect, eyeWhitePaint)
        eyeRimPaint.strokeWidth = er * 0.1f
        canvas.drawOval(tmpRect, eyeRimPaint)
        val pr = er * 0.52f
        val px = ex + lookX * er * 0.36f
        val py = ey + lookY * er * 0.3f * hs
        tmpRect.set(px - pr, py - pr * hs, px + pr, py + pr * hs)
        canvas.drawOval(tmpRect, pupilPaint)
        if (hs > 0.5f) {
            canvas.drawCircle(px - pr * 0.3f, py - pr * 0.3f * hs, pr * 0.28f, glintPaint)
        }
    }
}
