package com.example.reelcounter

import android.content.Context
import kotlin.math.max

// Brain damage: one number that rises with every reel and falls slowly while you rest.
// The Brain Buddy picture follows it, so he drifts from fresh to worn out as the day goes on
// and slowly recovers when you leave reels alone. It does not reset at midnight.
object Damage {

    // Change this one number to make recovery faster or slower:
    // minutes away from reels that clear one reel's worth of damage.
    const val RECOVER_MINUTES_PER_REEL = 3.0

    private fun p(c: Context) = c.getSharedPreferences("reels_v2", Context.MODE_PRIVATE)

    // Effective reels right now: what was stored, minus what resting since the last reel has cleared.
    fun effective(c: Context): Float {
        val sp = p(c)
        val stored = sp.getFloat("eff_reels", 0f)
        val stamp = sp.getLong("eff_stamp", 0L)
        if (stored <= 0f || stamp == 0L) return 0f
        val minutes = (System.currentTimeMillis() - stamp) / 60000.0
        return max(0f, stored - (minutes / RECOVER_MINUTES_PER_REEL).toFloat())
    }

    fun addReel(c: Context) {
        val e = effective(c) + 1f
        p(c).edit().putFloat("eff_reels", e).putLong("eff_stamp", System.currentTimeMillis()).apply()
    }

    // 0 to 100. Your four limits are the anchor points, so each limit lands on a clear step:
    // yellow limit = 30, orange = 55, red = 80, lock = 100.
    fun percent(c: Context): Float {
        val e = effective(c)
        val xs = floatArrayOf(
            0f,
            Prefs.getLimit(c, 1).toFloat(),
            Prefs.getLimit(c, 2).toFloat(),
            Prefs.getLimit(c, 3).toFloat(),
            Prefs.getLimit(c, 4).toFloat()
        )
        val ys = floatArrayOf(0f, 30f, 55f, 80f, 100f)
        if (e >= xs[4]) return 100f
        for (i in 1..4) {
            if (e <= xs[i]) {
                val span = xs[i] - xs[i - 1]
                if (span <= 0f) return ys[i]
                return ys[i - 1] + (ys[i] - ys[i - 1]) * (e - xs[i - 1]) / span
            }
        }
        return 100f
    }

    // Seven even steps from fresh to collapsed.
    fun stageFor(pct: Float): Int = when {
        pct < 15f -> BuddyView.FRESH
        pct < 30f -> BuddyView.CURIOUS
        pct < 45f -> BuddyView.CONCERNED
        pct < 60f -> BuddyView.TIRED
        pct < 75f -> BuddyView.DRAINED
        pct < 90f -> BuddyView.WORN
        else -> BuddyView.COLLAPSED
    }

    fun stage(c: Context): Int = stageFor(percent(c))
}
