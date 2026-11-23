package com.example.reelcounter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// Reads the phone's motion sensor in windows of five seconds and reports two numbers:
// how much the phone jitters (0 is perfectly still) and how flat it lies (about 9.8 when lying flat).
// Only these two numbers are kept, for a moment. Nothing is saved or sent anywhere.
class MotionWatch(private val onWindow: (Float, Float) -> Unit) : SensorEventListener {

    private var manager: SensorManager? = null
    private var running = false
    private var windowStart = 0L
    private var n = 0
    private var sum = 0.0
    private var sumSq = 0.0
    private var sumZ = 0.0

    fun start(c: Context): Boolean {
        if (running) return true
        val m = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        val s = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        manager = m
        reset(0L)
        // About twenty readings a second.
        running = m.registerListener(this, s, 50000)
        return running
    }

    fun stop() {
        if (running) {
            manager?.unregisterListener(this)
            running = false
        }
    }

    private fun reset(now: Long) {
        windowStart = now
        n = 0
        sum = 0.0
        sumSq = 0.0
        sumZ = 0.0
    }

    override fun onSensorChanged(e: SensorEvent) {
        val now = e.timestamp / 1000000L
        if (windowStart == 0L) windowStart = now
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        val mag = sqrt((x * x + y * y + z * z).toDouble())
        n++
        sum += mag
        sumSq += mag * mag
        sumZ += z.toDouble()
        if (now - windowStart >= 5000L && n > 10) {
            val mean = sum / n
            val variance = max(0.0, sumSq / n - mean * mean)
            onWindow(sqrt(variance).toFloat(), abs((sumZ / n).toFloat()))
            reset(now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
