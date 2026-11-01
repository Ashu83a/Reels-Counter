package com.example.reelcounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// A small yellow character: big eyes behind round glasses, little legs and arms.
// It strolls along the pill, waves, taps its foot, glances at the user and blinks
// at uneven, natural intervals. headPx is the width of the head in pixels.
class FaceView(context: Context, private val headPx: Int) : View(context) {

    private companion object {
        const val STATE_WALK = 0
        const val STATE_TAP = 1
        const val STATE_WAIT = 2
        const val STATE_WAVE = 3
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
    private val glassesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A2A12.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7FC4F2.toInt()
        style = Paint.Style.FILL
    }
    private val limbOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A3E0A.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val limbFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7C948.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val blobFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7C948.toInt()
        style = Paint.Style.FILL
    }
    private val blobRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5A3E0A.toInt()
        style = Paint.Style.STROKE
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

    // Smoothly eased pose values
    private var mouth = 1f
    private var brow = 0f
    private var eyeOpen = 1f
    private var tear = 0f
    private var turn = 0f
    private var swing = 0f
    private var wave = 0f
    private var cheer = 0f
    private var sit = 0f
    private var bobPx = 0f

    private var lookX = 0f
    private var lookY = 0.3f
    private var lookTx = 0f
    private var lookTy = 0.3f
    private var lookTimer = 1f

    private var blinkT = -1f
    private var blinkWait = 1.2f
    private var blink = 0f

    private var hopT = -1f

    private val handX = FloatArray(2)
    private val handY = FloatArray(2)

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((headPx * 1.7f).toInt(), (headPx * 1.375f).toInt())
    }

    // 0 happy, 1 worried, 2 sad, 3 crying
    fun setMood(m: Int) {
        mood = m.coerceIn(0, 3)
    }

    // How far the character may stroll in pixels (the pill width minus the head width).
    fun setTravel(px: Float) {
        travel = max(0f, px)
        if (posX > travel / 2f) posX = travel / 2f
        if (posX < -travel / 2f) posX = -travel / 2f
    }

    // One quick happy hop with both arms up, used on milestones.
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
        if (roll < 0.55f) {
            startState(STATE_WALK, rand(2.5f, 5f))
        } else if (roll < 0.70f) {
            startState(STATE_TAP, rand(1f, 1.8f))
        } else if (roll < 0.85f && mood <= 1) {
            startState(STATE_WAVE, rand(1.2f, 2f))
        } else {
            startState(STATE_WAIT, rand(1f, 2.2f))
        }
    }

    private fun step(dt: Float) {
        t += dt
        val r = headPx / 2f

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

        // Expression and pose ease smoothly towards their targets.
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

        val k2 = min(1f, dt * 6f)
        turn += ((if (state == STATE_WALK) dir * 0.12f else 0f) - turn) * k2
        swing += ((if (state == STATE_WALK) sf else 0f) - swing) * k2
        wave += ((if (state == STATE_WAVE) 1f else 0f) - wave) * k2
        cheer += ((if (hopT >= 0f) 1f else 0f) - cheer) * k2
        sit += ((if (mood == 3) 1f else 0f) - sit) * k2

        // Movement: walk, tap, wave or wait.
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
                    phase += dt * TWO_PI * 2.2f * sf
                    bounce = abs(sin(phase)) * 0.12f * hf
                    tilt = dir * 6f * sin(phase) * hf
                }
            }
            STATE_TAP -> {
                phase += dt * TWO_PI * 3.4f
                bounce = abs(sin(phase)) * 0.05f
                tilt = sin(phase) * 2f
                if (stateLeft <= 0f) pickNext()
            }
            STATE_WAVE -> {
                tilt = sin(t * 2f) * 1.5f
                if (stateLeft <= 0f) pickNext()
            }
            else -> {
                tilt = sin(t * 1.6f) * (if (mood == 3) 4f else 1.5f)
                if (mood == 3) bounce = abs(sin(t * 9f)) * 0.03f
                if (stateLeft <= 0f) pickNext()
            }
        }

        // Glancing: mostly ahead while walking, mostly at the user when standing.
        lookTimer -= dt
        if (lookTimer <= 0f) {
            lookTimer = rand(0.5f, 1.8f)
            val roll = Random.nextFloat()
            if (state == STATE_WALK) {
                if (roll < 0.7f) {
                    lookTx = dir * 0.9f
                    lookTy = 0f
                } else {
                    lookTx = 0f
                    lookTy = 0.3f
                }
            } else if (roll < 0.7f) {
                lookTx = 0f
                lookTy = 0.3f
            } else if (roll < 0.85f) {
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

        bobPx = bounce * r
        translationX = posX
        translationY = -hop * r
        rotation = tilt * 0.5f
    }

    // A limb with a dark outline and a yellow middle.
    private fun drawLimb(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
        limbOutline.strokeWidth = w
        limbFill.strokeWidth = w * 0.62f
        canvas.drawLine(x0, y0, x1, y1, limbOutline)
        canvas.drawLine(x0, y0, x1, y1, limbFill)
    }

    private fun drawBlob(canvas: Canvas, x: Float, y: Float, rad: Float) {
        canvas.drawCircle(x, y, rad, blobFill)
        blobRim.strokeWidth = rad * 0.35f
        canvas.drawCircle(x, y, rad, blobRim)
    }

    private fun drawLegs(canvas: Canvas, cx: Float, cy: Float, hr: Float, r: Float) {
        val hipY = cy + hr * 0.85f
        val footBase = r * 1.2f + hr * 0.85f + r * 0.5f
        val lw = r * 0.22f
        for (side in 0..1) {
            val s = if (side == 0) -1f else 1f
            val hipX = cx + s * hr * 0.32f
            val ph = if (side == 0) phase else phase + ONE_PI
            var fx = hipX + sin(ph) * r * 0.3f * swing
            var fy = footBase - max(0f, cos(ph)) * r * 0.12f * swing
            if (state == STATE_TAP && side == 1) fy -= abs(sin(phase)) * r * 0.16f
            // Sitting (crying): feet out to the sides.
            fx += s * r * 0.3f * sit
            fy -= r * 0.22f * sit
            drawLimb(canvas, hipX, hipY, fx, fy, lw)
            drawBlob(canvas, fx, fy, lw * 0.8f)
        }
    }

    private fun drawArms(canvas: Canvas, cx: Float, cy: Float, hr: Float, r: Float) {
        val aw = r * 0.2f
        for (side in 0..1) {
            val s = if (side == 0) -1f else 1f
            val sx = cx + s * hr * 0.92f
            val sy = cy + hr * 0.2f
            val ph = if (side == 0) phase + ONE_PI else phase
            var hx = sx + s * r * 0.28f + sin(ph) * r * 0.14f * swing
            var hy = sy + r * 0.62f - max(0f, sin(ph)) * r * 0.1f * swing
            if (side == 1 && wave > 0.01f) {
                val wx = sx + r * 0.4f + sin(t * 14f) * r * 0.15f
                val wy = sy - r * 0.55f
                hx += (wx - hx) * wave
                hy += (wy - hy) * wave
            }
            if (cheer > 0.01f) {
                val chx = sx + s * r * 0.45f + sin(t * 18f + side) * r * 0.12f
                val chy = sy - r * 0.7f
                hx += (chx - hx) * cheer
                hy += (chy - hy) * cheer
            }
            if (sit > 0.01f) {
                // Crying: a hand comes up to rub the eye.
                val shx = cx + s * hr * 0.5f
                val shy = cy + hr * 0.02f + sin(t * 8f + side * 2f) * r * 0.05f
                hx += (shx - hx) * sit
                hy += (shy - hy) * sit
            }
            drawLimb(canvas, sx, sy, hx, hy, aw)
            handX[side] = hx
            handY[side] = hy
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = headPx / 2f
        val cx = width / 2f
        val cy = r * 1.2f - bobPx
        val hr = r * 0.94f

        drawLegs(canvas, cx, cy, hr, r)
        drawArms(canvas, cx, cy, hr, r)

        outlinePaint.strokeWidth = hr * 0.07f
        canvas.drawCircle(cx, cy, hr, facePaint)
        canvas.drawCircle(cx, cy, hr, outlinePaint)

        // The face features slide a little in the walking direction, like a turned head.
        val shift = turn * hr
        val er = hr * 0.29f
        val ey = cy - hr * 0.12f
        val exL = cx - hr * 0.36f + shift
        val exR = cx + hr * 0.36f + shift
        val hs = max(0.08f, eyeOpen * (1f - blink))
        drawEye(canvas, exL, ey, er, hs)
        drawEye(canvas, exR, ey, er, hs)

        // Round glasses
        glassesPaint.strokeWidth = hr * 0.07f
        canvas.drawCircle(exL, ey, er * 1.12f, glassesPaint)
        canvas.drawCircle(exR, ey, er * 1.12f, glassesPaint)
        canvas.drawLine(exL + er * 1.12f, ey, exR - er * 1.12f, ey, glassesPaint)

        if (brow > 0.05f) {
            linePaint.strokeWidth = hr * 0.07f
            linePaint.alpha = (brow * 255f).toInt().coerceIn(0, 255)
            val yb = ey - er * 1.5f
            canvas.drawLine(exL - er * 0.9f, yb + brow * er * 0.35f, exL + er * 0.8f, yb - brow * er * 0.45f, linePaint)
            canvas.drawLine(exR - er * 0.8f, yb - brow * er * 0.45f, exR + er * 0.9f, yb + brow * er * 0.35f, linePaint)
            linePaint.alpha = 255
        }

        val my = cy + hr * 0.45f
        val mw = hr * 0.34f
        var ctrl = my + mouth * hr * 0.5f
        if (mood == 1) ctrl += sin(t * 10f) * hr * 0.04f
        linePaint.strokeWidth = hr * 0.09f
        linePaint.alpha = 255
        tmpPath.reset()
        tmpPath.moveTo(cx + shift - mw, my)
        tmpPath.quadTo(cx + shift, ctrl, cx + shift + mw, my)
        canvas.drawPath(tmpPath, linePaint)

        if (tear > 0.02f) {
            for (i in 0..1) {
                val tx = if (i == 0) exL else exR
                val p = (t * 0.9f + i * 0.5f) % 1f
                tearPaint.alpha = (tear * (1f - p) * 255f).toInt().coerceIn(0, 255)
                canvas.drawCircle(tx, ey + er * 0.8f + p * hr * 0.55f, hr * 0.08f, tearPaint)
            }
        }

        // Hands go on top so they can reach the face.
        for (side in 0..1) {
            drawBlob(canvas, handX[side], handY[side], r * 0.17f)
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
