package com.example.reelcounter

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

// Reads how long each app was open from the phone's own usage data (needs Usage Access)
// and keeps a copy in Prefs, because the phone only remembers recent days in detail.
object UsageHelper {

    private val PACKAGES = mapOf(
        "instagram" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "youtube" to listOf("com.google.android.youtube"),
        "threads" to listOf("com.instagram.barcelona"),
        "linkedin" to listOf("com.linkedin.android")
    )

    // Copies the last 7 days (today included) into Prefs.
    fun snapshot(c: Context) {
        if (!SysCheck.usageAccess(c)) return
        try {
            val manager = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            for (daysAgo in 0..6) {
                val start = Calendar.getInstance()
                start.add(Calendar.DAY_OF_YEAR, -daysAgo)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
                val end = start.clone() as Calendar
                end.add(Calendar.DAY_OF_YEAR, 1)
                val endMs = if (daysAgo == 0) System.currentTimeMillis() else end.timeInMillis
                val stats = manager.queryAndAggregateUsageStats(start.timeInMillis, endMs)
                val day = Prefs.dayString(start)
                for ((app, packages) in PACKAGES) {
                    var ms = 0L
                    for (pkg in packages) {
                        val u = stats[pkg]
                        if (u != null) ms += u.totalTimeInForeground
                    }
                    Prefs.setUsageSeconds(c, app, day, (ms / 1000L).toInt())
                }
            }
        } catch (e: Exception) {
        }
    }
}
