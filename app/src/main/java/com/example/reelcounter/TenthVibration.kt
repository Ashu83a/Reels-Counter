package com.example.reelcounter

import android.content.Context

object TenthVibration {
    private fun p(c: Context) = c.getSharedPreferences("reels_v2", Context.MODE_PRIVATE)

    // Length in milliseconds of the buzz at limit 1, 2 or 3.
    fun getLimitVibe(c: Context, n: Int): Int = when (n) {
        1 -> p(c).getInt("vibe_limit1", 1000)
        2 -> p(c).getInt("vibe_limit2", 3000)
        else -> p(c).getInt("vibe_limit3", 5000)
    }

    fun setLimitVibes(c: Context, a: Int, b: Int, d: Int) {
        p(c).edit().putInt("vibe_limit1", a).putInt("vibe_limit2", b).putInt("vibe_limit3", d).apply()
    }

    // Every how many reels the short buzz and the happy hop happen. Default 10.
    fun getInterval(c: Context): Int = p(c).getInt("interval", 10)

    fun setInterval(c: Context, n: Int) {
        p(c).edit().putInt("interval", n).apply()
    }

    // Length in milliseconds of the buzz on every interval reel. Default 300.
    fun getIntervalVibe(c: Context): Int = p(c).getInt("tenth_vibe_ms", 300)

    fun setIntervalVibe(c: Context, ms: Int) {
        p(c).edit().putInt("tenth_vibe_ms", ms).apply()
    }
}
