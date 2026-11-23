package com.example.reelcounter

import android.content.Context

// Auto Kids mode: signals add points, and enough points inside a short window start Kids mode
// by themselves. Two levels: soft (the puppy, pacing reminders and play reminders, nothing is
// blocked) and full (all the Kids rules). Nothing is saved except the settings; the points
// live in memory for two minutes only.
object AutoKids {
    const val FULL_AT = 5
    const val WINDOW_MS = 120000L

    private fun p(c: Context) = c.getSharedPreferences("reels_v2", Context.MODE_PRIVATE)

    // ---- Settings ----
    fun enabled(c: Context): Boolean = p(c).getBoolean("auto_on", false)
    fun setEnabled(c: Context, v: Boolean) {
        p(c).edit().putBoolean("auto_on", v).apply()
    }

    // 0 low (needs 4 points), 1 medium (3), 2 high (2).
    fun sensitivity(c: Context): Int = p(c).getInt("auto_sens", 1)
    fun setSensitivity(c: Context, v: Int) {
        p(c).edit().putInt("auto_sens", v).apply()
    }

    fun need(c: Context): Int = when (sensitivity(c)) {
        0 -> 4
        2 -> 2
        else -> 3
    }

    // Minutes with the screen off before auto Kids mode ends. 0 means the moment the screen goes off.
    fun endAfterMin(c: Context): Int = p(c).getInt("auto_end_min", 1)
    fun setEndAfterMin(c: Context, v: Int) {
        p(c).edit().putInt("auto_end_min", v).apply()
    }

    // Minutes of adult mode after a manual exit.
    fun passMin(c: Context): Int = p(c).getInt("auto_pass_min", 30)
    fun setPassMin(c: Context, v: Int) {
        p(c).edit().putInt("auto_pass_min", v).apply()
    }

    fun passActive(c: Context, now: Long): Boolean = now < p(c).getLong("auto_pass_until", 0L)

    // ---- Trigger settings ----
    const val DEFAULT_APPS = "com.google.android.apps.youtube.kids"
    const val DEFAULT_WORDS = "nursery rhymes,cartoon,cocomelon,baby shark,kids songs,toys unboxing,peppa pig,bluey,phonics,abc song,learn colors,surprise eggs"

    // Apps that switch Kids mode on straight away (package names, separated by commas).
    fun appsText(c: Context): String = p(c).getString("auto_apps", DEFAULT_APPS) ?: DEFAULT_APPS
    fun setAppsText(c: Context, v: String) {
        p(c).edit().putString("auto_apps", v).apply()
    }
    fun kidsApps(c: Context): Set<String> = appsText(c).split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    // Words on screen that suggest a child is watching (separated by commas).
    fun wordsText(c: Context): String = p(c).getString("auto_words", DEFAULT_WORDS) ?: DEFAULT_WORDS
    fun setWordsText(c: Context, v: String) {
        p(c).edit().putString("auto_words", v).apply()
    }
    fun words(c: Context): List<String> = wordsText(c).lowercase().split(",").map { it.trim() }.filter { it.length >= 3 }

    // Child hours: one supporting point while the clock is inside them.
    fun hoursOn(c: Context): Boolean = p(c).getBoolean("auto_hours_on", false)
    fun setHoursOn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("auto_hours_on", v).apply()
    }
    fun hoursFrom(c: Context): Int = p(c).getInt("auto_hours_from", 17 * 60)
    fun setHoursFrom(c: Context, v: Int) {
        p(c).edit().putInt("auto_hours_from", v).apply()
    }
    fun hoursTo(c: Context): Int = p(c).getInt("auto_hours_to", 20 * 60)
    fun setHoursTo(c: Context, v: Int) {
        p(c).edit().putInt("auto_hours_to", v).apply()
    }

    fun inChildHours(c: Context): Boolean {
        if (!hoursOn(c)) return false
        val s = hoursFrom(c)
        val e = hoursTo(c)
        if (s == e) return false
        val cal = java.util.Calendar.getInstance()
        val m = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return if (s < e) (m >= s && m < e) else (m >= s || m < e)
    }

    // Motion signals: the phone's motion sensor (unsteady hand, lying still).
    fun motionOn(c: Context): Boolean = p(c).getBoolean("auto_motion", true)
    fun setMotionOn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("auto_motion", v).apply()
    }

    // How much jitter counts as an unsteady hand, in hundredths of a metre per second squared.
    fun jitterPct(c: Context): Int = p(c).getInt("auto_jitter", 60)
    fun setJitterPct(c: Context, v: Int) {
        p(c).edit().putInt("auto_jitter", v).apply()
    }
    fun jitterThreshold(c: Context): Float = jitterPct(c) / 100f

    // ---- The points ----
    private val events = ArrayList<Triple<Long, Int, String>>()

    // Adds points for a signal. Returns the new level (1 soft, 2 full) if the level went up, else 0.
    @Synchronized
    fun add(c: Context, points: Int, tag: String, now: Long): Int {
        if (!enabled(c) || Kids.manualOn(c) || passActive(c, now)) return 0
        events.removeAll { now - it.first > WINDOW_MS }
        // The same signal counts once inside the window, at its strongest.
        val same = events.firstOrNull { it.third == tag }
        if (same != null) {
            if (same.second >= points) return 0
            events.remove(same)
        }
        events.add(Triple(now, points, tag))
        val sum = events.sumOf { it.second } + (if (inChildHours(c)) 1 else 0)
        val level = Kids.autoLevel(c)
        val want = if (sum >= FULL_AT) 2 else if (sum >= need(c)) 1 else 0
        if (want > level) {
            Kids.setAutoLevel(c, want)
            return want
        }
        return 0
    }

    fun escalate(c: Context) {
        Kids.setAutoLevel(c, 2)
    }

    @Synchronized
    fun end(c: Context) {
        Kids.setAutoLevel(c, 0)
        events.clear()
    }

    // An adult takes the phone back: auto mode ends and stays away for a while.
    fun exit(c: Context, now: Long) {
        end(c)
        p(c).edit().putLong("auto_pass_until", now + passMin(c) * 60000L).apply()
    }
}
