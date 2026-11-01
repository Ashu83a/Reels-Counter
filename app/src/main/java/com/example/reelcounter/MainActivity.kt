package com.example.reelcounter

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// The settings screen. Every change is saved the moment it is made.
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var statusText: TextView? = null

    private var relaxDays = ""
    private var relaxStart = 18
    private var relaxEnd = 22
    private var bedTime1 = 22 * 60 + 30
    private var bedTime2 = 23 * 60

    private val statusRefresh = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 4000L)
        }
    }

    // A slider with a minimum, a maximum and a step size.
    private inner class Slider(
        val min: Int, val max: Int, val step: Int,
        val label: TextView, val seek: SeekBar, val fmt: (Int) -> String
    ) {
        fun value(): Int = min + seek.progress * step

        fun set(v: Int) {
            val p = ((v - min).toFloat() / step).roundToInt().coerceIn(0, (max - min) / step)
            seek.progress = p
            label.text = fmt(value())
        }

        fun snapUp(x: Int): Int {
            val k = ceil((x - min).toFloat() / step).toInt().coerceIn(0, (max - min) / step)
            return min + k * step
        }

        fun snapDown(x: Int): Int {
            val k = floor((x - min).toFloat() / step).toInt().coerceIn(0, (max - min) / step)
            return min + k * step
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.hasPassword(this)) {
            showGate()
        } else {
            showSettings()
            if (!Prefs.getGuideDone(this)) {
                startActivity(Intent(this, SetupGuideActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        handler.postDelayed(statusRefresh, 4000L)
    }

    override fun onPause() {
        handler.removeCallbacks(statusRefresh)
        super.onPause()
    }

    // ---- Password screen (only if a password was set) ----
    private fun showGate() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        val pad = Theme.dp(this, 28f)
        layout.setPadding(pad, pad, pad, pad)
        layout.setBackgroundColor(Theme.BG)

        layout.addView(Theme.heading(this, "Reel Counter 4", 28f), Theme.lp(this, 0f, 4f))
        layout.addView(Theme.soft(this, "Enter your password to open the settings."), Theme.lp(this, 0f, 16f))

        val field = Theme.field(this, "", false)
        field.hint = "Password"
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(field, Theme.lp(this, 0f, 12f))

        val open = Theme.button(this, "Open")
        open.setOnClickListener {
            if (Prefs.checkPassword(this, field.text.toString())) {
                showSettings()
            } else {
                Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(open, Theme.lp(this, 0f, 0f))
        setContentView(layout)
    }

    // ---- Small building blocks ----
    private fun minutesToText(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

    // Returns minutes after midnight, or -1 if the text is not a valid HH:MM time.
    private fun textToMinutes(t: String): Int {
        val parts = t.trim().split(":")
        if (parts.size != 2) return -1
        val h = parts[0].trim().toIntOrNull() ?: return -1
        val m = parts[1].trim().toIntOrNull() ?: return -1
        if (h < 0 || h > 23 || m < 0 || m > 59) return -1
        return h * 60 + m
    }

    private fun onText(e: EditText, block: (String) -> Unit) {
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                block(s?.toString() ?: "")
            }
        })
    }

    private fun section(page: LinearLayout, title: String, note: String = ""): LinearLayout {
        val card = Theme.card(this)
        card.addView(Theme.heading(this, title, 18f))
        if (note.isNotEmpty()) card.addView(Theme.soft(this, note), Theme.lp(this, 4f, 0f))
        page.addView(card, Theme.lp(this, 0f, 14f))
        return card
    }

    // A title with a small "i" icon. Tapping the icon opens or closes a short explanation.
    // Returns the text view on the right, where sliders show their value.
    private fun header(card: LinearLayout, title: String, info: String): TextView {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(
            Theme.body(this, title, 15f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val value = Theme.soft(this, "", 14f)
        value.typeface = Theme.SERIF_BOLD
        row.addView(value)

        val icon = TextView(this)
        icon.text = "i"
        icon.textSize = 13f
        icon.typeface = Theme.SERIF_BOLD
        icon.gravity = Gravity.CENTER
        icon.setTextColor(Theme.BRASS)
        icon.background = Theme.rounded(Theme.CARD, Theme.dp(this, 12f).toFloat(), Theme.BRASS, Theme.dp(this, 1f))
        val size = Theme.dp(this, 24f)
        val iconLp = LinearLayout.LayoutParams(size, size)
        iconLp.leftMargin = Theme.dp(this, 10f)
        row.addView(icon, iconLp)
        card.addView(row, Theme.lp(this, 14f, 0f))

        val infoText = Theme.soft(this, info, 13f)
        infoText.visibility = View.GONE
        card.addView(infoText, Theme.lp(this, 4f, 0f))
        icon.setOnClickListener {
            infoText.visibility = if (infoText.visibility == View.GONE) View.VISIBLE else View.GONE
        }
        return value
    }

    private fun slider(
        card: LinearLayout, title: String, info: String,
        min: Int, max: Int, step: Int, current: Int,
        fmt: (Int) -> String, onChange: (Int) -> Unit
    ): Slider {
        val label = header(card, title, info)
        val seek = SeekBar(this)
        seek.max = (max - min) / step
        seek.progressTintList = ColorStateList.valueOf(Theme.GREEN)
        seek.thumbTintList = ColorStateList.valueOf(Theme.GREEN)
        card.addView(seek, Theme.lp(this, 4f, 0f))
        val s = Slider(min, max, step, label, seek, fmt)
        s.set(current)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = fmt(s.value())
                if (fromUser) onChange(s.value())
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        return s
    }

    private fun toggle(card: LinearLayout, title: String, info: String, on: Boolean, onChange: (Boolean) -> Unit): Switch {
        header(card, title, info)
        val s = Switch(this)
        s.text = if (on) "On" else "Off"
        s.textSize = 14f
        s.setTextColor(Theme.TEXT)
        s.isChecked = on
        s.setOnCheckedChangeListener { _, checked ->
            s.text = if (checked) "On" else "Off"
            onChange(checked)
        }
        card.addView(s, Theme.lp(this, 4f, 0f))
        return s
    }

    private fun textSetting(card: LinearLayout, title: String, info: String, value: String, onChange: (String) -> Unit): EditText {
        header(card, title, info)
        val e = Theme.field(this, value, false)
        card.addView(e, Theme.lp(this, 4f, 0f))
        onText(e, onChange)
        return e
    }

    // Keeps the four limits in order (yellow, orange, red, lock) and saves them.
    private fun enforceLimits(s: List<Slider>, changed: Int) {
        val v = IntArray(4) { s[it].value() }
        for (j in changed + 1 until 4) {
            if (v[j] <= v[j - 1]) v[j] = s[j].snapUp(v[j - 1] + 1)
        }
        for (j in changed - 1 downTo 0) {
            if (v[j] >= v[j + 1]) v[j] = s[j].snapDown(v[j + 1] - 1)
        }
        for (j in 0 until 4) {
            if (v[j] != s[j].value()) s[j].set(v[j])
        }
        Prefs.setLimits(this, v[0], v[1], v[2])
        Prefs.setLockLimit(this, v[3])
    }

    private fun refreshStatus() {
        val view = statusText ?: return
        val sb = StringBuilder()
        sb.append("Counter service: ")
            .append(if (SysCheck.accessibilityOn(this)) "on" else "OFF, turn it on in Accessibility").append('\n')
        sb.append("Battery: ")
            .append(if (SysCheck.batteryFree(this)) "unrestricted, good" else "the phone may put the counter to sleep").append('\n')
        sb.append("Usage access: ")
            .append(if (SysCheck.usageAccess(this)) "allowed" else "not allowed yet").append('\n')
        val last = Prefs.getLastActive(this)
        sb.append("Last reel seen: ").append(if (last == 0L) "never" else SysCheck.agoText(last))
        view.text = sb.toString()
    }

    // ---- The settings screen ----
    private fun showSettings() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.BG)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        val pad = Theme.dp(this, 16f)
        page.setPadding(pad, pad, pad, pad)
        scroll.addView(page)

        page.addView(Theme.heading(this, "Reel Counter 4", 28f), Theme.lp(this, 8f, 2f))
        page.addView(Theme.soft(this, "A calm way to keep an eye on short videos. Changes are saved as you make them."), Theme.lp(this, 0f, 14f))

        // ---- Status and shortcuts ----
        val status = section(page, "Status")
        val st = Theme.body(this, "", 14f)
        statusText = st
        status.addView(st, Theme.lp(this, 6f, 0f))
        val btnGuide = Theme.button(this, "Setup guide")
        btnGuide.setOnClickListener { startActivity(Intent(this, SetupGuideActivity::class.java)) }
        status.addView(btnGuide, Theme.lp(this, 10f, 0f))
        val btnStats = Theme.softButton(this, "See my stats")
        btnStats.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        status.addView(btnStats, Theme.lp(this, 8f, 0f))
        val btnDiag = Theme.softButton(this, "What the counter sees (for fixing problems)")
        btnDiag.setOnClickListener { startActivity(Intent(this, DiagnosticActivity::class.java)) }
        status.addView(btnDiag, Theme.lp(this, 8f, 0f))
        status.addView(
            Theme.soft(this, "Tip: in Settings, Accessibility, Advanced settings, you can set a shortcut (for example holding both volume keys) that turns this counter on or off in one gesture."),
            Theme.lp(this, 10f, 0f)
        )

        // ---- Limits ----
        val limits = section(page, "Limits", "The bubble changes colour as the reels add up. At the last limit the lock screen appears.")
        val ls = ArrayList<Slider>()
        val reels = { v: Int -> "$v reels" }
        ls.add(slider(limits, "Yellow limit", "The first warning. The bubble turns yellow, the face looks worried and you get the first buzz.", 25, 250, 25, Prefs.getLimit(this, 1), reels) { enforceLimits(ls, 0) })
        ls.add(slider(limits, "Orange limit", "The second warning. The bubble turns orange and the buzz is longer.", 50, 500, 50, Prefs.getLimit(this, 2), reels) { enforceLimits(ls, 1) })
        ls.add(slider(limits, "Red limit", "The third warning. The bubble turns red and the face looks sad.", 100, 750, 50, Prefs.getLimit(this, 3), reels) { enforceLimits(ls, 2) })
        ls.add(slider(limits, "Lock screen at", "When the count reaches this number, the lock screen covers the app. The face cries.", 200, 1000, 100, Prefs.getLimit(this, 4), reels) { enforceLimits(ls, 3) })
        enforceLimits(ls, 0)

        // ---- Vibration ----
        val vibes = section(page, "Vibration", "How long the phone buzzes. 0 means no buzz.")
        val secs = { v: Int -> String.format(Locale.US, "%.1f s", v / 1000f) }
        val vs = ArrayList<Slider>()
        val saveVibes = { _: Int -> TenthVibration.setLimitVibes(this, vs[0].value(), vs[1].value(), vs[2].value()) }
        vs.add(slider(vibes, "Buzz at the yellow limit", "How long the phone buzzes when you reach the yellow limit.", 0, 5000, 100, TenthVibration.getLimitVibe(this, 1), secs, saveVibes))
        vs.add(slider(vibes, "Buzz at the orange limit", "How long the phone buzzes at the orange limit.", 0, 5000, 100, TenthVibration.getLimitVibe(this, 2), secs, saveVibes))
        vs.add(slider(vibes, "Buzz at the red limit", "How long the phone buzzes at the red limit.", 0, 5000, 100, TenthVibration.getLimitVibe(this, 3), secs, saveVibes))
        slider(vibes, "Regular buzz every", "A short buzz and a happy hop of the face after this many reels. A gentle reminder that you are still scrolling.", 5, 50, 5, TenthVibration.getInterval(this), { v: Int -> "$v reels" }) { TenthVibration.setInterval(this, it) }
        slider(vibes, "Length of the regular buzz", "How long the regular buzz lasts. 0 turns it off.", 0, 5000, 100, TenthVibration.getIntervalVibe(this), secs) { TenthVibration.setIntervalVibe(this, it) }

        // ---- Messages ----
        val msgs = section(page, "Messages", "A short note shown when a limit is reached. Leave blank for none.")
        textSetting(msgs, "At the yellow limit", "Shown for a few seconds when you reach the yellow limit.", Prefs.getMessage(this, 1)) { Prefs.setMessage(this, 1, it) }
        textSetting(msgs, "At the orange limit", "Shown when you reach the orange limit.", Prefs.getMessage(this, 2)) { Prefs.setMessage(this, 2, it) }
        textSetting(msgs, "At the red limit", "Shown when you reach the red limit.", Prefs.getMessage(this, 3)) { Prefs.setMessage(this, 3, it) }
        textSetting(msgs, "On the lock screen", "Shown on the lock screen, above a small suggestion like taking a walk.", Prefs.getMessage(this, 4)) { Prefs.setMessage(this, 4, it) }

        // ---- Unlock pauses ----
        val pauses = section(page, "Unlock pauses")
        slider(pauses, "Pauses per day", "How many times a day you can pause the lock screen for 15 minutes. 0 means no pauses at all.", 0, 5, 1, Prefs.getMaxPauses(this), { v: Int -> "$v per day" }) { Prefs.setMaxPauses(this, it) }

        // ---- Relaxed time ----
        val relax = section(page, "Relaxed time", "During this window the lock screen stays off. The bubble still counts.")
        relaxDays = Prefs.getRelaxDays(this)
        relaxStart = Prefs.getRelaxStartHour(this)
        relaxEnd = Prefs.getRelaxEndHour(this)
        toggle(relax, "Use a relaxed window", "Turn on to let yourself scroll without the lock during chosen days and hours.", Prefs.getRelaxEnabled(this)) { Prefs.setRelaxEnabled(this, it) }
        textSetting(relax, "Days", "Numbers separated by commas: 1 is Sunday, 2 Monday, 3 Tuesday, 4 Wednesday, 5 Thursday, 6 Friday, 7 Saturday.", relaxDays) {
            relaxDays = it.trim()
            Prefs.setRelaxWindow(this, relaxDays, relaxStart, relaxEnd)
        }
        slider(relax, "From hour", "The relaxed window starts at this hour of the day (24 hour clock).", 0, 23, 1, relaxStart, { v: Int -> String.format(Locale.US, "%02d:00", v) }) {
            relaxStart = it
            Prefs.setRelaxWindow(this, relaxDays, relaxStart, relaxEnd)
        }
        slider(relax, "Until hour", "The relaxed window ends at this hour of the day.", 0, 23, 1, relaxEnd, { v: Int -> String.format(Locale.US, "%02d:00", v) }) {
            relaxEnd = it
            Prefs.setRelaxWindow(this, relaxDays, relaxStart, relaxEnd)
        }

        // ---- Bedtime ----
        val bed = section(page, "Bedtime", "Two gentle reminders at night, then the face keeps hopping every 15 minutes while you scroll.")
        bedTime1 = Prefs.getBedtime1(this)
        bedTime2 = Prefs.getBedtime2(this)
        toggle(bed, "Bedtime reminders", "Turn on to get reminded to sleep when you are still scrolling late at night.", Prefs.getBedtimeEnabled(this)) { Prefs.setBedtimeEnabled(this, it) }
        textSetting(bed, "First reminder at (HH:MM)", "The time of the first reminder, on a 24 hour clock, for example 22:30.", minutesToText(bedTime1)) {
            val m = textToMinutes(it)
            if (m >= 0) {
                bedTime1 = m
                Prefs.setBedtimes(this, bedTime1, bedTime2)
            }
        }
        textSetting(bed, "Second reminder at (HH:MM)", "The time of the second reminder, for example 23:00.", minutesToText(bedTime2)) {
            val m = textToMinutes(it)
            if (m >= 0) {
                bedTime2 = m
                Prefs.setBedtimes(this, bedTime1, bedTime2)
            }
        }
        textSetting(bed, "First message", "What the first reminder says.", Prefs.getBedMessage(this, 1)) { Prefs.setBedMessage(this, 1, it) }
        textSetting(bed, "Second message", "What the second reminder says.", Prefs.getBedMessage(this, 2)) { Prefs.setBedMessage(this, 2, it) }

        // ---- Bank apps ----
        val bank = section(
            page, "Bank apps",
            "Banking apps refuse to work while an accessibility service is on. When one opens, the counter switches itself off and shows a notification. Tap it to turn the counter back on afterwards."
        )
        toggle(bank, "Switch off for bank apps", "When on, the counter switches itself off as soon as a bank or payment app opens.", Prefs.getBankGuard(this)) { Prefs.setBankGuard(this, it) }
        textSetting(bank, "Words to look for in app names", "Any app whose technical name contains one of these words counts as a bank app. Separate the words with commas.", Prefs.getBankWords(this)) { Prefs.setBankWords(this, it) }

        // ---- Look ----
        val look = section(page, "Look")
        slider(look, "Bubble transparency", "How see-through the bubble is. 0% is solid, 80% is very faint.", 0, 80, 5, Prefs.getTransparency(this), { v: Int -> "$v%" }) { Prefs.setTransparency(this, it) }

        // ---- Password ----
        val pw = section(page, "Password", "Optional. A friend can set it so these settings cannot be changed on impulse.")
        header(pw, "Password", "When set, the settings ask for it every time the app opens. Remove it here if you no longer want it.")
        val pwField = Theme.field(this, "", false)
        pwField.hint = if (Prefs.hasPassword(this)) "New password" else "Choose a password"
        pwField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        pw.addView(pwField, Theme.lp(this, 4f, 0f))
        val pwSet = Theme.softButton(this, "Set password")
        pwSet.setOnClickListener {
            val text = pwField.text.toString()
            if (text.length < 3) {
                Toast.makeText(this, "Use at least 3 characters", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setPassword(this, text)
                pwField.setText("")
                Toast.makeText(this, "Password set", Toast.LENGTH_SHORT).show()
            }
        }
        pw.addView(pwSet, Theme.lp(this, 8f, 0f))
        if (Prefs.hasPassword(this)) {
            val pwClear = Theme.softButton(this, "Remove password")
            pwClear.setOnClickListener {
                Prefs.clearPassword(this)
                Toast.makeText(this, "Password removed", Toast.LENGTH_SHORT).show()
            }
            pw.addView(pwClear, Theme.lp(this, 8f, 0f))
        }

        page.addView(View(this), Theme.lp(this, 0f, 24f))
        setContentView(scroll)
        refreshStatus()
    }
}
