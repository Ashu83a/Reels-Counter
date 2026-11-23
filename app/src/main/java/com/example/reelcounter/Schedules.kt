package com.example.reelcounter

import android.content.Context
import java.util.Calendar

// One schedule. kind: 0 night, 1 pacing, 2 at my limit (daily cap), 3 custom.
// days: numbers separated by commas, 1 is Sunday ... 7 is Saturday.
// start and end: minutes after midnight. cap: reels. hours: length of the pacing slice.
class Schedule(
    var kind: Int,
    var name: String,
    var on: Boolean,
    var days: String,
    var start: Int,
    var end: Int,
    var cap: Int,
    var hours: Int
)

// Gentle schedules for adults. None of them blocks anything: when one trips, a calm
// screen appears that can be dismissed. (Full blocking is for Kids mode.)
object Schedules {
    const val NIGHT = 0
    const val PACING = 1
    const val DAILY = 2
    const val CUSTOM = 3
    const val ALL_DAYS = "1,2,3,4,5,6,7"

    private fun p(c: Context) = c.getSharedPreferences("reels_v2", Context.MODE_PRIVATE)

    private fun defaults(): MutableList<Schedule> = mutableListOf(
        Schedule(NIGHT, "Night", false, ALL_DAYS, 23 * 60, 7 * 60, 0, 0),
        Schedule(PACING, "Pacing", false, ALL_DAYS, 0, 0, 20, 2),
        Schedule(DAILY, "At my limit", false, ALL_DAYS, 0, 0, 200, 0)
    )

    fun clean(name: String): String {
        val t = name.replace("|", " ").replace("\n", " ").trim()
        return if (t.isEmpty()) "Custom" else t.take(24)
    }

    fun load(c: Context): MutableList<Schedule> {
        val raw = p(c).getString("schedules", null) ?: return defaults()
        return try {
            val list = ArrayList<Schedule>()
            for (line in raw.split("\n")) {
                if (line.isBlank()) continue
                val f = line.split("|")
                list.add(
                    Schedule(
                        f[0].toInt(), f[1], f[2] == "1", f[3],
                        f[4].toInt(), f[5].toInt(), f[6].toInt(), f[7].toInt()
                    )
                )
            }
            if (list.isEmpty()) defaults() else list
        } catch (e: Exception) {
            defaults()
        }
    }

    fun save(c: Context, list: List<Schedule>) {
        val sb = StringBuilder()
        for (s in list) {
            sb.append(s.kind).append('|').append(clean(s.name)).append('|').append(if (s.on) "1" else "0")
                .append('|').append(s.days).append('|').append(s.start).append('|').append(s.end)
                .append('|').append(s.cap).append('|').append(s.hours).append('\n')
        }
        p(c).edit().putString("schedules", sb.toString()).apply()
    }

    private fun dayList(s: Schedule): List<Int> = s.days.split(",").mapNotNull { it.trim().toIntOrNull() }

    private fun inWindow(s: Schedule, minutes: Int, dow: Int): Boolean {
        if (s.start == s.end) return false
        val days = dayList(s)
        val yesterday = if (dow == 1) 7 else dow - 1
        return if (s.start < s.end) {
            dow in days && minutes >= s.start && minutes < s.end
        } else {
            (minutes >= s.start && dow in days) || (minutes < s.end && yesterday in days)
        }
    }

    // Reels counted in the last [hours] hours (hour by hour, so it is close but not exact).
    fun reelsInLastHours(c: Context, hours: Int): Int {
        var sum = 0
        val cal = Calendar.getInstance()
        for (i in 0 until hours) {
            val day = Prefs.dayString(cal)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            for (app in Prefs.APPS) sum += Prefs.getHourCount(c, app, day, hour)
            cal.add(Calendar.HOUR_OF_DAY, -1)
        }
        return sum
    }

    // The first schedule that applies right now, or null.
    fun tripped(c: Context): Schedule? {
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        for (s in load(c)) {
            if (!s.on) continue
            when (s.kind) {
                NIGHT -> if (inWindow(s, minutes, dow)) return s
                PACING -> if (reelsInLastHours(c, s.hours) >= s.cap) return s
                DAILY -> if (Prefs.getCount(c) >= s.cap) return s
                else -> if (inWindow(s, minutes, dow)) {
                    if (s.cap <= 0) return s
                    val sinceStart = ((minutes - s.start + 1440) % 1440) / 60 + 1
                    if (reelsInLastHours(c, sinceStart) >= s.cap) return s
                }
            }
        }
        return null
    }

    // A short sentence about the schedule, for the settings page.
    fun summary(s: Schedule): String {
        fun t(m: Int) = String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60)
        return when (s.kind) {
            NIGHT -> "A calm screen between ${t(s.start)} and ${t(s.end)}."
            PACING -> "A calm screen after ${s.cap} reels within ${s.hours} hour" + (if (s.hours == 1) "." else "s.")
            DAILY -> "A calm screen once you reach ${s.cap} reels in a day."
            else -> if (s.cap <= 0) "A calm screen from ${t(s.start)} to ${t(s.end)}." else "From ${t(s.start)} to ${t(s.end)}, after ${s.cap} reels."
        }
    }
}
