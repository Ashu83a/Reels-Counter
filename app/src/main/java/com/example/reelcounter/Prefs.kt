package com.example.reelcounter

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Prefs {
    private const val FILE = "reels_v2"
    val APPS = listOf("instagram", "facebook", "youtube", "threads", "linkedin")

    const val DEFAULT_BANK_WORDS = "bank,sbi,hdfc,icici,axis,kotak,paytm,phonepe,paisa,bhim,upi"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun dayString(cal: Calendar = Calendar.getInstance()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

    // The day string for N days ago (0 = today).
    fun dayStringBack(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return dayString(cal)
    }

    // Short label like "Mon" for the chart.
    fun dayShortLabel(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
    }

    // Longer label like "Mon 27 Oct" for the details box.
    fun dayLongLabel(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(cal.time)
    }

    fun niceName(app: String): String = when (app) {
        "instagram" -> "Instagram"
        "facebook" -> "Facebook"
        "youtube" -> "YouTube"
        "threads" -> "Threads"
        "linkedin" -> "LinkedIn"
        else -> app
    }

    // ---- Limits: 1 yellow, 2 orange, 3 red, 4 lock (default 100 past the third) ----
    fun getLimit(c: Context, n: Int): Int = when (n) {
        1 -> p(c).getInt("limit1", 100)
        2 -> p(c).getInt("limit2", 150)
        3 -> p(c).getInt("limit3", 200)
        else -> p(c).getInt("limit4", getLimit(c, 3) + 100)
    }

    fun setLimits(c: Context, l1: Int, l2: Int, l3: Int) {
        p(c).edit().putInt("limit1", l1).putInt("limit2", l2).putInt("limit3", l3).apply()
    }

    fun setLockLimit(c: Context, l4: Int) {
        p(c).edit().putInt("limit4", l4).apply()
    }

    // Transparency from 0 (solid) to 80 (very faint)
    fun getTransparency(c: Context): Int = p(c).getInt("transparency", 20)
    fun setTransparency(c: Context, v: Int) {
        p(c).edit().putInt("transparency", v).apply()
    }

    // ---- Daily numbers (kept forever, one entry per day) ----
    fun getCount(c: Context, day: String = dayString()): Int = p(c).getInt("count_$day", 0)
    fun getSeconds(c: Context, day: String = dayString()): Int = p(c).getInt("secs_$day", 0)
    fun getAppCount(c: Context, app: String, day: String = dayString()): Int =
        p(c).getInt("count_${app}_$day", 0)
    fun getAppSeconds(c: Context, app: String, day: String = dayString()): Int =
        p(c).getInt("secs_${app}_$day", 0)

    fun addReel(c: Context, app: String): Int {
        val day = dayString()
        val total = getCount(c, day) + 1
        p(c).edit()
            .putInt("count_$day", total)
            .putInt("count_${app}_$day", getAppCount(c, app, day) + 1)
            .apply()
        return total
    }

    // Adds time to the day total and to the app's own total.
    fun addSeconds(c: Context, app: String, secs: Int) {
        if (secs <= 0) return
        val day = dayString()
        p(c).edit()
            .putInt("secs_$day", getSeconds(c, day) + secs)
            .putInt("secs_${app}_$day", getAppSeconds(c, app, day) + secs)
            .apply()
    }

    // ---- Totals for the stats screen (last [days] days, today included) ----
    fun sumCount(c: Context, days: Int): Int = sumOver(days) { getCount(c, it) }
    fun sumSeconds(c: Context, days: Int): Int = sumOver(days) { getSeconds(c, it) }
    fun sumAppCount(c: Context, app: String, days: Int): Int = sumOver(days) { getAppCount(c, app, it) }
    fun sumAppSeconds(c: Context, app: String, days: Int): Int = sumOver(days) { getAppSeconds(c, app, it) }

    private fun sumOver(days: Int, get: (String) -> Int): Int {
        var sum = 0
        val cal = Calendar.getInstance()
        for (i in 0 until days) {
            sum += get(dayString(cal))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return sum
    }

    // 725 seconds -> "12m", 3725 seconds -> "1h 2m"
    fun formatTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    // ---- Lock pauses ("unlock anyway") ----
    fun getMaxPauses(c: Context): Int = p(c).getInt("max_pauses", 2)
    fun setMaxPauses(c: Context, n: Int) {
        p(c).edit().putInt("max_pauses", n).apply()
    }

    fun getPausesUsedToday(c: Context): Int = p(c).getInt("pauses_${dayString()}", 0)

    fun getPauseUntil(c: Context): Long = p(c).getLong("pause_until", 0L)

    // Starts a 15 minute pause. Returns false if today's pauses are used up.
    fun startPause(c: Context): Boolean {
        val used = getPausesUsedToday(c)
        if (used >= getMaxPauses(c)) return false
        p(c).edit()
            .putInt("pauses_${dayString()}", used + 1)
            .putLong("pause_until", System.currentTimeMillis() + 15 * 60 * 1000L)
            .apply()
        return true
    }

    fun isPaused(c: Context): Boolean = System.currentTimeMillis() < getPauseUntil(c)

    // ---- Relaxed window: lock is off during this time ----
    fun getRelaxEnabled(c: Context): Boolean = p(c).getBoolean("relax_on", false)
    fun setRelaxEnabled(c: Context, on: Boolean) {
        p(c).edit().putBoolean("relax_on", on).apply()
    }

    // Days as text like "1,7" (1 = Sunday ... 7 = Saturday). Default Saturday and Sunday.
    fun getRelaxDays(c: Context): String = p(c).getString("relax_days", "1,7") ?: "1,7"
    fun getRelaxStartHour(c: Context): Int = p(c).getInt("relax_start", 18)
    fun getRelaxEndHour(c: Context): Int = p(c).getInt("relax_end", 22)
    fun setRelaxWindow(c: Context, days: String, startHour: Int, endHour: Int) {
        p(c).edit().putString("relax_days", days).putInt("relax_start", startHour).putInt("relax_end", endHour).apply()
    }

    fun isRelaxedNow(c: Context): Boolean {
        if (!getRelaxEnabled(c)) return false
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_WEEK).toString()
        val days = getRelaxDays(c).split(",").map { it.trim() }
        if (!days.contains(day)) return false
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour >= getRelaxStartHour(c) && hour < getRelaxEndHour(c)
    }

    // ---- Messages shown at milestones (1 to 4). Blank means no message. ----
    fun getMessage(c: Context, n: Int): String = p(c).getString("msg_$n", "") ?: ""
    fun setMessage(c: Context, n: Int, text: String) {
        p(c).edit().putString("msg_$n", text).apply()
    }

    // ---- Bedtime mode ----
    fun getBedtimeEnabled(c: Context): Boolean = p(c).getBoolean("bed_on", true)
    fun setBedtimeEnabled(c: Context, on: Boolean) {
        p(c).edit().putBoolean("bed_on", on).apply()
    }

    // Reminder times stored as minutes after midnight. 22:30 = 1350, 23:00 = 1380.
    fun getBedtime1(c: Context): Int = p(c).getInt("bed_time1", 22 * 60 + 30)
    fun getBedtime2(c: Context): Int = p(c).getInt("bed_time2", 23 * 60)
    fun setBedtimes(c: Context, t1: Int, t2: Int) {
        p(c).edit().putInt("bed_time1", t1).putInt("bed_time2", t2).apply()
    }

    fun getBedMessage(c: Context, n: Int): String {
        val default = if (n == 1) "That is enough for today. Time to wind down." else "It is time to sleep."
        return p(c).getString("bed_msg_$n", default) ?: default
    }
    fun setBedMessage(c: Context, n: Int, text: String) {
        p(c).edit().putString("bed_msg_$n", text).apply()
    }

    // Remembers which reminders already showed today so each shows once.
    fun bedReminderShownToday(c: Context, n: Int): Boolean =
        p(c).getBoolean("bed_shown_${n}_${dayString()}", false)
    fun markBedReminderShown(c: Context, n: Int) {
        p(c).edit().putBoolean("bed_shown_${n}_${dayString()}", true).apply()
    }

    fun getLastBigBubble(c: Context): Long = p(c).getLong("last_big_bubble", 0L)
    fun setLastBigBubble(c: Context, t: Long) {
        p(c).edit().putLong("last_big_bubble", t).apply()
    }

    // ---- Total time each app was open, copied from the phone's usage data ----
    fun getUsageSeconds(c: Context, app: String, day: String = dayString()): Int =
        p(c).getInt("use_${app}_$day", 0)

    // Only ever goes up, so a later partial reading never lowers a saved day.
    fun setUsageSeconds(c: Context, app: String, day: String, secs: Int) {
        if (secs > getUsageSeconds(c, app, day)) {
            p(c).edit().putInt("use_${app}_$day", secs).apply()
        }
    }

    fun sumAppUsage(c: Context, app: String, days: Int): Int = sumOver(days) { getUsageSeconds(c, app, it) }

    // ---- Status line and first-run guide ----
    fun getLastActive(c: Context): Long = p(c).getLong("last_active", 0L)
    fun setLastActive(c: Context, t: Long) {
        p(c).edit().putLong("last_active", t).apply()
    }

    fun getGuideDone(c: Context): Boolean = p(c).getBoolean("guide_done", false)
    fun setGuideDone(c: Context, done: Boolean) {
        p(c).edit().putBoolean("guide_done", done).apply()
    }

    // ---- Bank apps: the counter switches itself off when one opens ----
    fun getBankGuard(c: Context): Boolean = p(c).getBoolean("bank_guard", true)
    fun setBankGuard(c: Context, on: Boolean) {
        p(c).edit().putBoolean("bank_guard", on).apply()
    }

    // Words that are looked for inside an app's package name, separated by commas.
    fun getBankWords(c: Context): String = p(c).getString("bank_words", DEFAULT_BANK_WORDS) ?: DEFAULT_BANK_WORDS
    fun setBankWords(c: Context, text: String) {
        p(c).edit().putString("bank_words", text).apply()
    }

    fun isBankPackage(c: Context, pkg: String): Boolean {
        if (!getBankGuard(c)) return false
        val name = pkg.lowercase()
        val words = getBankWords(c).split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return words.any { name.contains(it) }
    }

    // ---- Accountability password (kept as a scrambled value, not plain text) ----
    fun hasPassword(c: Context): Boolean = p(c).getString("pw_hash", "")!!.isNotEmpty()

    fun setPassword(c: Context, pw: String) {
        p(c).edit().putString("pw_hash", scramble(pw)).apply()
    }

    fun clearPassword(c: Context) {
        p(c).edit().remove("pw_hash").apply()
    }

    fun checkPassword(c: Context, pw: String): Boolean {
        if (!hasPassword(c)) return true
        return p(c).getString("pw_hash", "") == scramble(pw)
    }

    private fun scramble(pw: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(("reelcounter:" + pw).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---- Streaks: a good day is a day under your first limit ----
    // Counts back from yesterday while each day was under limit 1 and had some use recorded.
    fun currentStreak(c: Context): Int {
        val limit = getLimit(c, 1)
        var streak = 0
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        for (i in 0 until 365) {
            val day = dayString(cal)
            val count = getCount(c, day)
            val hasData = p(c).contains("count_$day")
            if (!hasData || count >= limit) break
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    // ---- CSV export of the full history ----
    fun buildCsv(c: Context): String {
        val days = p(c).all.keys
            .filter { it.startsWith("count_20") && it.length == 16 }
            .map { it.removePrefix("count_") }
            .sorted()
        val sb = StringBuilder()
        sb.append("date,reels,seconds")
        for (app in APPS) {
            sb.append(',').append(app).append("_reels,").append(app).append("_reel_seconds,").append(app).append("_app_seconds")
        }
        sb.append('\n')
        for (d in days) {
            sb.append(d).append(',')
            sb.append(getCount(c, d)).append(',')
            sb.append(getSeconds(c, d))
            for (app in APPS) {
                sb.append(',').append(getAppCount(c, app, d))
                sb.append(',').append(getAppSeconds(c, app, d))
                sb.append(',').append(getUsageSeconds(c, app, d))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    // ---- Long videos (not reels): separate record, never affects limits ----
    val LONG_APPS = listOf("youtube", "facebook")

    fun getLongCount(c: Context, app: String, day: String = dayString()): Int =
        p(c).getInt("long_count_${app}_$day", 0)

    fun getLongSeconds(c: Context, app: String, day: String = dayString()): Int =
        p(c).getInt("long_secs_${app}_$day", 0)

    fun addLongVideo(c: Context, app: String) {
        val day = dayString()
        p(c).edit().putInt("long_count_${app}_$day", getLongCount(c, app, day) + 1).apply()
    }

    fun addLongSeconds(c: Context, app: String, secs: Int) {
        if (secs <= 0) return
        val day = dayString()
        p(c).edit().putInt("long_secs_${app}_$day", getLongSeconds(c, app, day) + secs).apply()
    }

    fun sumLongCount(c: Context, app: String, days: Int): Int = sumOver(days) { getLongCount(c, app, it) }
    fun sumLongSeconds(c: Context, app: String, days: Int): Int = sumOver(days) { getLongSeconds(c, app, it) }
}
