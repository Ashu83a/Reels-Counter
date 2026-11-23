package com.example.reelcounter

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.security.MessageDigest

// Kids mode: a puppy takes over, reels are paced, and time limits apply.
// It helps parents, but it is not full parental control: a determined child can still
// leave the app or switch off Accessibility.
object Kids {
    // After this long away from reels, a new sitting starts.
    private const val REST_SECONDS = 10 * 60L

    private fun p(c: Context) = c.getSharedPreferences("reels_v2", Context.MODE_PRIVATE)

    // Kids mode switched on by hand (settings or the tile) stays on until the PIN turns it off.
    fun manualOn(c: Context): Boolean = p(c).getBoolean("kids_on", false)
    fun setOn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("kids_on", v).apply()
    }

    // Auto Kids mode: 0 off, 1 soft, 2 full. Set by the signals, ended by the screen turning off.
    fun autoLevel(c: Context): Int = p(c).getInt("kids_auto_level", 0)
    fun setAutoLevel(c: Context, v: Int) {
        p(c).edit().putInt("kids_auto_level", v).apply()
    }

    // Kids mode in any form: the puppy, the pacing note and the play reminders.
    fun on(c: Context): Boolean = manualOn(c) || autoLevel(c) > 0

    // The strict rules (swipe hold, limits, rest time, games, Play Store) apply in manual
    // Kids mode and in full auto Kids mode. Soft auto mode only reminds.
    fun hard(c: Context): Boolean = manualOn(c) || autoLevel(c) == 2

    // ---- The parent PIN (kept as a hash, never as the digits) ----
    private fun hash(pin: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("reelcounter-kids:" + pin).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun hasPin(c: Context): Boolean = (p(c).getString("kids_pin", "") ?: "").isNotEmpty()
    fun setPin(c: Context, pin: String) {
        p(c).edit().putString("kids_pin", hash(pin)).apply()
    }
    fun checkPin(c: Context, pin: String): Boolean {
        val saved = p(c).getString("kids_pin", "") ?: ""
        return saved.isNotEmpty() && saved == hash(pin)
    }

    // ---- Settings ----
    fun minSecs(c: Context): Int = p(c).getInt("kids_min_secs", 8)
    fun setMinSecs(c: Context, v: Int) {
        p(c).edit().putInt("kids_min_secs", v).apply()
    }

    // True: swipes are held back. False: only a friendly reminder is shown.
    fun hold(c: Context): Boolean = hard(c) && p(c).getBoolean("kids_hold", true)
    fun setHold(c: Context, v: Boolean) {
        p(c).edit().putBoolean("kids_hold", v).apply()
    }

    fun sessionMin(c: Context): Int = p(c).getInt("kids_session_min", 15)
    fun setSessionMin(c: Context, v: Int) {
        p(c).edit().putInt("kids_session_min", v).apply()
    }

    fun dailyMin(c: Context): Int = p(c).getInt("kids_daily_min", 45)
    fun setDailyMin(c: Context, v: Int) {
        p(c).edit().putInt("kids_daily_min", v).apply()
    }

    fun nudgeOn(c: Context): Boolean = p(c).getBoolean("kids_nudge", true)
    fun setNudgeOn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("kids_nudge", v).apply()
    }

    fun nudgeMin(c: Context): Int = p(c).getInt("kids_nudge_min", 5)
    fun setNudgeMin(c: Context, v: Int) {
        p(c).edit().putInt("kids_nudge_min", v).apply()
    }

    fun appOn(c: Context, app: String): Boolean = p(c).getBoolean("kids_app_$app", app != "linkedin")
    fun setAppOn(c: Context, app: String, v: Boolean) {
        p(c).edit().putBoolean("kids_app_$app", v).apply()
    }

    // ---- What the child watched (only while Kids mode is on) ----
    fun reels(c: Context, day: String = Prefs.dayString()): Int = p(c).getInt("kc_$day", 0)
    fun secs(c: Context, day: String = Prefs.dayString()): Int = p(c).getInt("ks_$day", 0)

    fun addReel(c: Context) {
        val day = Prefs.dayString()
        p(c).edit().putInt("kc_$day", reels(c, day) + 1).apply()
    }

    // One second more on reels: counts for the day and for the current sitting.
    fun addSecond(c: Context, now: Long) {
        val day = Prefs.dayString()
        val last = p(c).getLong("kids_sess_last", 0L)
        var sess = p(c).getInt("kids_sess_secs", 0)
        if (last == 0L || now - last > REST_SECONDS * 1000L) sess = 0
        sess++
        p(c).edit()
            .putInt("ks_$day", secs(c, day) + 1)
            .putInt("kids_sess_secs", sess)
            .putLong("kids_sess_last", now)
            .apply()
    }

    // Seconds used in the current sitting (0 if the child has rested long enough).
    fun sessionSecs(c: Context, now: Long): Int {
        val last = p(c).getLong("kids_sess_last", 0L)
        if (last == 0L || now - last > REST_SECONDS * 1000L) return 0
        return p(c).getInt("kids_sess_secs", 0)
    }

    private fun bonus(c: Context): Int = p(c).getInt("kids_bonus_" + Prefs.dayString(), 0)

    // Seconds left before time is up, or null when no limit is set.
    fun remainingSecs(c: Context, now: Long): Long? {
        if (!hard(c)) return null
        var best: Long? = null
        val sm = sessionMin(c)
        if (sm > 0) best = sm * 60L - sessionSecs(c, now)
        val dm = dailyMin(c)
        if (dm > 0) {
            val r = dm * 60L + bonus(c) - secs(c)
            best = if (best == null) r else minOf(best, r)
        }
        return best
    }

    // 0 = free to watch, 1 = time is up, 2 = it is rest time (night).
    fun blockReason(c: Context, now: Long): Int {
        if (!hard(c)) return 0
        if (quietBlocked(c, now)) return 2
        val r = remainingSecs(c, now)
        if (r != null && r <= 0L) return 1
        return 0
    }

    fun blocked(c: Context, now: Long): Boolean = blockReason(c, now) != 0

    // ---- Games: one shared umbrella limit, and the Play Store guard ----
    fun gameSecs(c: Context, day: String = Prefs.dayString()): Int = p(c).getInt("kg_$day", 0)

    private fun gameBonus(c: Context): Int = p(c).getInt("kids_gbonus_" + Prefs.dayString(), 0)

    // One second more in a game: counts for the day's games and for the current sitting.
    fun addGameSecond(c: Context, now: Long) {
        val day = Prefs.dayString()
        val last = p(c).getLong("kids_sess_last", 0L)
        var sess = p(c).getInt("kids_sess_secs", 0)
        if (last == 0L || now - last > REST_SECONDS * 1000L) sess = 0
        sess++
        p(c).edit()
            .putInt("kg_$day", gameSecs(c, day) + 1)
            .putInt("kids_sess_secs", sess)
            .putLong("kids_sess_last", now)
            .apply()
    }

    // Seconds of games left today (the umbrella), never negative.
    fun gameRemaining(c: Context): Long =
        maxOf(0L, Games.umbrellaMin(c) * 60L + gameBonus(c) - gameSecs(c))

    // 0 = free, 1 = sitting is up, 2 = rest time, 3 = games are done for today.
    fun gameBlockReason(c: Context, now: Long): Int {
        if (!hard(c)) return 0
        if (quietBlocked(c, now)) return 2
        val sm = sessionMin(c)
        if (sm > 0 && sm * 60L - sessionSecs(c, now) <= 0L) return 1
        if (gameRemaining(c) <= 0L) return 3
        return 0
    }

    // A parent gives more game time: the umbrella and the sitting both get that many minutes.
    fun grantGames(c: Context, minutes: Int) {
        p(c).edit().putInt("kids_gbonus_" + Prefs.dayString(), gameBonus(c) + minutes * 60).apply()
        grant(c, minutes)
    }

    // The Play Store asks for the parent PIN; a parent can open it for a while.
    fun storeBlocked(c: Context, now: Long): Boolean =
        hard(c) && Games.storeGuard(c) && now >= p(c).getLong("kids_store_until", 0L)

    fun allowStore(c: Context, minutes: Int) {
        p(c).edit().putLong("kids_store_until", System.currentTimeMillis() + minutes * 60000L).apply()
    }

    // ---- Rest time: a full block at night ----
    fun quietOn(c: Context): Boolean = p(c).getBoolean("kids_quiet", true)
    fun setQuietOn(c: Context, v: Boolean) {
        p(c).edit().putBoolean("kids_quiet", v).apply()
    }

    fun quietStart(c: Context): Int = p(c).getInt("kids_quiet_start", 19 * 60 + 30)
    fun setQuietStart(c: Context, v: Int) {
        p(c).edit().putInt("kids_quiet_start", v).apply()
    }

    fun quietEnd(c: Context): Int = p(c).getInt("kids_quiet_end", 7 * 60)
    fun setQuietEnd(c: Context, v: Int) {
        p(c).edit().putInt("kids_quiet_end", v).apply()
    }

    private fun minutesNow(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    // True during the rest hours, even if a parent has allowed a few minutes.
    fun quietNow(c: Context): Boolean {
        if (!quietOn(c)) return false
        val s = quietStart(c)
        val e = quietEnd(c)
        if (s == e) return false
        val m = minutesNow()
        return if (s < e) (m >= s && m < e) else (m >= s || m < e)
    }

    // A parent can allow a few minutes during rest time with the PIN.
    fun quietPassUntil(c: Context): Long = p(c).getLong("kids_quiet_pass", 0L)
    fun passQuiet(c: Context, minutes: Int) {
        p(c).edit().putLong("kids_quiet_pass", System.currentTimeMillis() + minutes * 60000L).apply()
    }

    fun quietBlocked(c: Context, now: Long): Boolean = hard(c) && quietNow(c) && now >= quietPassUntil(c)

    // Minutes until rest time starts, or -1 when it is off or already on.
    fun minutesToQuiet(c: Context): Int {
        if (!quietOn(c) || quietNow(c)) return -1
        return (quietStart(c) - minutesNow() + 1440) % 1440
    }

    // A parent gives more time: the sitting restarts with that many minutes left, and the day gets them too.
    fun grant(c: Context, minutes: Int) {
        val secs = minutes * 60
        val sm = sessionMin(c)
        val e = p(c).edit()
        if (sm > 0) e.putInt("kids_sess_secs", maxOf(0, sm * 60 - secs)).putLong("kids_sess_last", System.currentTimeMillis())
        e.putInt("kids_bonus_" + Prefs.dayString(), bonus(c) + secs)
        e.apply()
    }

    // ---- PIN dialogs ----
    fun askPin(a: Activity, title: String, onDismiss: (() -> Unit)? = null, onOk: () -> Unit) {
        val field = EditText(a)
        field.hint = "Parent PIN"
        field.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        field.filters = arrayOf(InputFilter.LengthFilter(4))
        AlertDialog.Builder(a)
            .setTitle(title)
            .setView(field)
            .setPositiveButton("OK") { _, _ ->
                if (checkPin(a, field.text.toString())) onOk() else Toast.makeText(a, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { onDismiss?.invoke() }
            .show()
    }

    fun askNewPin(a: Activity, onDone: (Boolean) -> Unit) {
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        val pad = Theme.dp(a, 20f)
        box.setPadding(pad, Theme.dp(a, 8f), pad, 0)
        val one = EditText(a)
        one.hint = "Choose a 4-digit PIN"
        val two = EditText(a)
        two.hint = "Type it again"
        for (f in listOf(one, two)) {
            f.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            f.filters = arrayOf(InputFilter.LengthFilter(4))
            box.addView(f)
        }
        AlertDialog.Builder(a)
            .setTitle("Parent PIN")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val a1 = one.text.toString()
                if (a1.length == 4 && a1 == two.text.toString()) {
                    setPin(a, a1)
                    onDone(true)
                } else {
                    Toast.makeText(a, "The PIN needs 4 digits, typed the same twice", Toast.LENGTH_LONG).show()
                    onDone(false)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> onDone(false) }
            .setOnCancelListener { onDone(false) }
            .show()
    }
}
