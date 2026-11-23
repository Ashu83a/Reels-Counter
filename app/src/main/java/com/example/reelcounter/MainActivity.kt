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
        if (Kids.on(this) && Kids.hasPin(this)) {
            showPinGate()
        } else if (Prefs.hasPassword(this)) {
            showGate()
        } else {
            showSettings()
            if (!Prefs.getGuideDone(this)) {
                startActivity(Intent(this, SetupGuideActivity::class.java))
            }
        }
        applyWatcher()
    }

    // Starts or stops the restart prompt according to its setting.
    private fun applyWatcher() {
        if (Prefs.getWatcherOn(this) && SysCheck.usageAccess(this)) {
            WatcherService.start(this)
        } else if (!Prefs.getWatcherOn(this)) {
            WatcherService.stop(this)
        }
    }

    override fun onResume() {
        super.onResume()
        applyWatcher()
        refreshStatus()
        handler.postDelayed(statusRefresh, 4000L)
    }

    override fun onPause() {
        handler.removeCallbacks(statusRefresh)
        stopMotionTest()
        super.onPause()
    }

    // ---- Parent PIN screen (while Kids mode is on) ----
    private var kidsUnlocked = false

    private fun showPinGate() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        val pad = Theme.dp(this, 28f)
        layout.setPadding(pad, pad, pad, pad)
        layout.setBackgroundColor(Theme.BG)
        val buddy = BuddyView(this, Theme.dp(this, 130f))
        buddy.setExpression(BuddyView.FRESH)
        layout.addView(buddy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(Theme.heading(this, "Kids mode is on", 26f), Theme.lp(this, 8f, 4f))
        layout.addView(Theme.soft(this, "Enter the parent PIN to open the settings."), Theme.lp(this, 0f, 16f))
        val field = Theme.field(this, "", false)
        field.hint = "Parent PIN"
        field.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        layout.addView(field, Theme.lp(this, 0f, 12f))
        val open = Theme.button(this, "Open")
        open.setOnClickListener {
            if (Kids.checkPin(this, field.text.toString())) {
                kidsUnlocked = true
                showSettings()
            } else {
                Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(open, Theme.lp(this, 0f, 0f))
        setContentView(layout)
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
        sb.append("Restart prompt: ")
            .append(if (!Prefs.getWatcherOn(this)) "off" else if (SysCheck.usageAccess(this)) "on" else "needs usage access").append('\n')
        val last = Prefs.getLastActive(this)
        sb.append("Last reel seen: ").append(if (last == 0L) "never" else SysCheck.agoText(last))
        view.text = sb.toString()
    }

    // ---- The settings screens: one hub, and one page for each group ----
    private var screen = ""

    private fun showSettings() {
        showHub()
    }

    override fun onBackPressed() {
        if (screen.isNotEmpty()) {
            showHub()
        } else {
            super.onBackPressed()
        }
    }

    private fun newScreen(title: String, subtitle: String, back: Boolean): LinearLayout {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.BG)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        val pad = Theme.dp(this, 16f)
        page.setPadding(pad, pad, pad, pad)
        scroll.addView(page)
        if (back) {
            val b = Theme.softButton(this, "Back to all settings")
            b.setOnClickListener { showHub() }
            page.addView(b, Theme.lp(this, 0f, 10f))
        }
        page.addView(Theme.heading(this, title, if (back) 24f else 28f), Theme.lp(this, 8f, 2f))
        if (subtitle.isNotEmpty()) page.addView(Theme.soft(this, subtitle), Theme.lp(this, 0f, 14f))
        setContentView(scroll)
        return page
    }

    // A tappable row on the hub: a title, one line of summary and an arrow.
    private fun tile(page: LinearLayout, title: String, summary: String, open: () -> Unit) {
        val card = Theme.card(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(Theme.heading(this, title, 17f))
        texts.addView(Theme.soft(this, summary, 13f), Theme.lp(this, 2f, 0f))
        card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val arrow = TextView(this)
        arrow.text = ">"
        arrow.textSize = 22f
        arrow.typeface = Theme.SERIF_BOLD
        arrow.setTextColor(Theme.BRASS)
        card.addView(arrow)
        card.setOnClickListener { open() }
        page.addView(card, Theme.lp(this, 0f, 10f))
    }

    private fun showHub() {
        stopMotionTest()
        screen = ""
        val page = newScreen("Reel Counter 4", "A calm way to keep an eye on short videos. Changes are saved as you make them.", false)
        buildStatus(page)

        page.addView(Theme.heading(this, "Settings", 18f), Theme.lp(this, 6f, 8f))
        val setupDone = SysCheck.setupDone(this)
        tile(page, "Permissions and access", if (setupDone == SysCheck.SETUP_TOTAL) "All ${SysCheck.SETUP_TOTAL} steps done" else "$setupDone of ${SysCheck.SETUP_TOTAL} steps done, tap to continue") {
            startActivity(Intent(this, SetupGuideActivity::class.java))
        }
        tile(page, "Limits", "Yellow ${Prefs.getLimit(this, 1)}, orange ${Prefs.getLimit(this, 2)}, red ${Prefs.getLimit(this, 3)}, lock ${Prefs.getLimit(this, 4)}") { openSection("limits") }
        tile(page, "Daily goal", if (Prefs.getGoalOn(this)) "Stay under ${Prefs.getGoal(this)} reels a day" else "Off") { openSection("goal") }
        tile(page, "Bubble and Brain Buddy", if (Prefs.getBuddyOn(this)) "Brain Buddy on, size and position" else "Brain Buddy off") { openSection("bubble") }
        tile(page, "Kids mode", if (Kids.manualOn(this)) "On, the puppy is watching" else if (Kids.autoLevel(this) > 0) "Auto Kids mode is on" else "Off") {
            if (Kids.hasPin(this) && !kidsUnlocked) {
                Kids.askPin(this, "Parent PIN") {
                    kidsUnlocked = true
                    openSection("kids")
                }
            } else {
                openSection("kids")
            }
        }
        tile(page, "Auto Kids mode", if (AutoKids.enabled(this)) (if (Kids.autoLevel(this) > 0) "On, running now" else "On") else "Off") {
            if (Kids.hasPin(this) && !kidsUnlocked) {
                Kids.askPin(this, "Parent PIN") {
                    kidsUnlocked = true
                    openSection("auto")
                }
            } else {
                openSection("auto")
            }
        }
        tile(page, "Schedules", scheduleSummary()) { openSection("schedules") }
        tile(page, "Break prompt and open-app card", "Small calm nudges from Brain Buddy") { openSection("prompts") }
        tile(page, "Vibration", "How long the phone buzzes") { openSection("vibration") }
        tile(page, "Messages", "Notes shown at each limit") { openSection("messages") }
        tile(page, "Lock and relaxed time", "Unlock pauses and relaxed windows") { openSection("lock") }
        tile(page, "Bedtime", if (Prefs.getBedtimeEnabled(this)) "Reminders on" else "Reminders off") { openSection("bedtime") }
        tile(page, "Bank apps and restart", "Switch off for banks, reminder to turn back on") { openSection("bank") }
        tile(page, "Password", if (Prefs.hasPassword(this)) "A password is set" else "Not set") { openSection("password") }
        page.addView(View(this), Theme.lp(this, 0f, 24f))
        refreshStatus()
    }

    private fun openSection(key: String) {
        stopMotionTest()
        screen = key
        statusText = null
        val titles = when (key) {
            "limits" -> Pair("Limits", "The bubble changes colour as the reels add up. At the last limit the lock screen appears.")
            "goal" -> Pair("Daily goal", "A number of reels to stay under each day. Every day you do, your streak grows.")
            "bubble" -> Pair("Bubble and Brain Buddy", "How the small counter looks.")
            "kids" -> Pair("Kids mode", "For young children. A puppy takes over, reels are paced and time limits apply.")
            "auto" -> Pair("Auto Kids mode", "The app notices when a child has the phone and switches to Kids mode by itself.")
            "schedules" -> Pair("Schedules", "Gentle limits by time of day. None of them blocks you: each one shows a calm screen you can dismiss.")
            "prompts" -> Pair("Break prompt and open-app card", "Small calm nudges from Brain Buddy. Neither one ever blocks you.")
            "vibration" -> Pair("Vibration", "How long the phone buzzes. 0 means no buzz.")
            "messages" -> Pair("Messages", "A short note shown when a limit is reached. Leave blank for none.")
            "lock" -> Pair("Lock and relaxed time", "When the lock screen can be paused or stays off.")
            "bedtime" -> Pair("Bedtime", "Two gentle reminders at night.")
            "bank" -> Pair("Bank apps and restart", "Keeps your banking apps working.")
            else -> Pair("Password", "Optional protection for these settings.")
        }
        val page = newScreen(titles.first, titles.second, true)
        when (key) {
            "limits" -> buildLimits(page)
            "goal" -> buildGoal(page)
            "bubble" -> buildBubble(page)
            "kids" -> buildKids(page)
            "auto" -> buildAuto(page)
            "schedules" -> buildSchedules(page)
            "prompts" -> buildPrompts(page)
            "vibration" -> buildVibration(page)
            "messages" -> buildMessages(page)
            "lock" -> buildLock(page)
            "bedtime" -> buildBedtime(page)
            "bank" -> buildBank(page)
            else -> buildPassword(page)
        }
        page.addView(View(this), Theme.lp(this, 0f, 24f))
    }

    private fun buildStatus(page: LinearLayout) {
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

    }

    private fun buildLimits(page: LinearLayout) {
        val limits = section(page, "Limit steps")
        val ls = ArrayList<Slider>()
        val reels = { v: Int -> "$v reels" }
        ls.add(slider(limits, "Yellow limit", "The first warning. The bubble turns yellow, Brain Buddy starts to look concerned and you get the first buzz.", 25, 250, 25, Prefs.getLimit(this, 1), reels) { enforceLimits(ls, 0) })
        ls.add(slider(limits, "Orange limit", "The second warning. The bubble turns orange and the buzz is longer.", 50, 500, 50, Prefs.getLimit(this, 2), reels) { enforceLimits(ls, 1) })
        ls.add(slider(limits, "Red limit", "The third warning. The bubble turns red and Brain Buddy looks worn out.", 100, 750, 50, Prefs.getLimit(this, 3), reels) { enforceLimits(ls, 2) })
        ls.add(slider(limits, "Lock screen at", "When the count reaches this number, the lock screen covers the app, with Brain Buddy holding the break sign.", 200, 1000, 100, Prefs.getLimit(this, 4), reels) { enforceLimits(ls, 3) })
        enforceLimits(ls, 0)

    }

    // ---- Auto Kids mode page, with a live motion readout ----
    private var motionTest: MotionWatch? = null
    private var motionText: TextView? = null

    private fun stopMotionTest() {
        motionTest?.stop()
        motionTest = null
        motionText = null
    }

    private fun buildAuto(page: LinearLayout) {
        val main = section(page, "Auto Kids mode", "Signals add points. Enough points within two minutes switch Kids mode on. Three points (medium) start a gentle level with only the puppy and reminders; five points, or a kids app, start full Kids mode.")
        toggle(main, "Switch to Kids mode automatically", "Off until you turn it on, so adults are not surprised. Turn it on once you are happy with the signals below.", AutoKids.enabled(this)) { AutoKids.setEnabled(this, it) }
        slider(main, "Sensitivity", "How many points start the gentle level: low needs 4, medium 3, high 2.", 0, 2, 1, AutoKids.sensitivity(this),
            { v: Int -> if (v == 0) "Low" else if (v == 1) "Medium" else "High" }) { AutoKids.setSensitivity(this, it) }
        if (Kids.autoLevel(this) > 0) {
            val off = Theme.softButton(this, "End auto Kids mode now")
            off.setOnClickListener {
                AutoKids.end(this)
                openSection("auto")
            }
            main.addView(off, Theme.lp(this, 10f, 0f))
        }

        val ending = section(page, "Getting out", "Turning the screen off with the power button ends auto Kids mode. An adult can also hold the I am an adult button for three seconds.")
        slider(ending, "End after the screen is off", "How long the screen stays off before auto Kids mode ends. At once ends it the moment the screen turns off.", 0, 10, 1, AutoKids.endAfterMin(this),
            { v: Int -> if (v == 0) "At once" else "$v minute" + (if (v == 1) "" else "s") }) { AutoKids.setEndAfterMin(this, it) }
        slider(ending, "Adult mode lasts", "After an adult takes the phone back, auto Kids mode stays away for this long.", 5, 60, 5, AutoKids.passMin(this),
            { v: Int -> "$v minutes" }) { AutoKids.setPassMin(this, it) }

        val sig = section(page, "Signals", "A kids app is worth 5 points, kids words 3, fast swiping 2. Button mashing, child hours, an unsteady hand, lying still and opening a game are worth 1 each.")
        textSetting(sig, "Kids apps", "Package names separated by commas. Opening one switches on full Kids mode at once.", AutoKids.appsText(this)) { AutoKids.setAppsText(this, it) }
        textSetting(sig, "Kids words", "Words on screen that suggest a child is watching, separated by commas. They are checked on the phone and never saved.", AutoKids.wordsText(this)) { AutoKids.setWordsText(this, it) }
        toggle(sig, "Child hours", "Times when a child is likely to use the phone. Worth one point inside them.", AutoKids.hoursOn(this)) { AutoKids.setHoursOn(this, it) }
        slider(sig, "Child hours from", "When child hours start.", 0, 1410, 30, AutoKids.hoursFrom(this), { v: Int -> minutesToText(v) }) { AutoKids.setHoursFrom(this, it) }
        slider(sig, "Child hours until", "When child hours end.", 0, 1410, 30, AutoKids.hoursTo(this), { v: Int -> minutesToText(v) }) { AutoKids.setHoursTo(this, it) }
        toggle(sig, "Use the motion sensor", "An unsteady hand and a phone lying still while reels are swiped. Each is worth one point and can never switch Kids mode on alone. Some phones limit sensors in the background, so test it.", AutoKids.motionOn(this)) { AutoKids.setMotionOn(this, it) }
        slider(sig, "Unsteady hand threshold", "How much jitter counts as an unsteady hand. Lower is more sensitive. Compare with the live numbers below.", 20, 200, 10, AutoKids.jitterPct(this),
            { v: Int -> String.format(java.util.Locale.US, "%.2f", v / 100f) }) { AutoKids.setJitterPct(this, it) }

        val test = section(page, "Motion test", "Live numbers from the motion sensor, updated every five seconds. Hold the phone as you normally do, then let a child hold it, and compare.")
        val readout = Theme.body(this, "Waiting for the sensor...", 15f)
        test.addView(readout, Theme.lp(this, 6f, 0f))
        stopMotionTest()
        motionText = readout
        val watch = MotionWatch { jitter, flat ->
            runOnUiThread {
                motionText?.text = String.format(java.util.Locale.US, "Jitter %.2f (unsteady above %.2f)\nFlat reading %.1f (about 9.8 when lying flat)", jitter, AutoKids.jitterThreshold(this), flat)
            }
        }
        if (watch.start(this)) {
            motionTest = watch
        } else {
            readout.text = "This phone did not give the motion sensor to the app."
        }
    }

    private fun buildKids(page: LinearLayout) {
        val main = section(page, "Kids mode", "It helps, but it is not full parental control: a determined child can still leave the app or switch off Accessibility. While Kids mode is on, these settings ask for the parent PIN.")
        var kidsSwitch: Switch? = null
        kidsSwitch = toggle(main, "Kids mode on", "Turn it on while a child uses the phone. The puppy replaces Brain Buddy and the rules below apply.", Kids.manualOn(this)) { on ->
            if (on && !Kids.hasPin(this)) {
                Kids.askNewPin(this) { ok ->
                    if (ok) {
                        Kids.setOn(this, true)
                    } else {
                        kidsSwitch?.isChecked = false
                    }
                }
            } else {
                Kids.setOn(this, on)
            }
        }
        val pin = Theme.softButton(this, if (Kids.hasPin(this)) "Change the parent PIN" else "Set the parent PIN")
        pin.setOnClickListener { Kids.askNewPin(this) { } }
        main.addView(pin, Theme.lp(this, 10f, 0f))

        val pace = section(page, "Swipe pacing", "Children often swipe again and again within a second. Pacing asks them to stay with each reel for a little while.")
        slider(pace, "Minimum time per reel", "How long to stay on each reel before the next one. 0 turns pacing off.", 0, 30, 1, Kids.minSecs(this),
            { v: Int -> if (v == 0) "Off" else "$v seconds" }) { Kids.setMinSecs(this, it) }
        toggle(pace, "Hold the swipe back", "On: swiping is held back until the time is up, and the puppy says why. Off: swiping works, and the puppy only gives a friendly reminder.", Kids.hold(this)) { Kids.setHold(this, it) }

        val time = section(page, "Time limits", "Two minutes before the end the puppy gives a warning. At the end a rest screen appears, and a parent can give more time with the PIN.")
        slider(time, "Each sitting", "How long a child can watch before a rest. A sitting restarts after 10 minutes away from reels. 0 means no limit.", 0, 60, 5, Kids.sessionMin(this),
            { v: Int -> if (v == 0) "No limit" else "$v minutes" }) { Kids.setSessionMin(this, it) }
        slider(time, "Each day", "The total for one day. 0 means no limit.", 0, 180, 15, Kids.dailyMin(this),
            { v: Int -> if (v == 0) "No limit" else "$v minutes" }) { Kids.setDailyMin(this, it) }

        val rest = section(page, "Rest time", "At night the phone rests. Any reel use shows the sleeping puppy, and a parent can allow 10 minutes with the PIN. Five minutes before, the puppy gives a heads-up.")
        toggle(rest, "Rest time at night", "Turn it off if you do not want a night block.", Kids.quietOn(this)) { Kids.setQuietOn(this, it) }
        slider(rest, "From", "When rest time starts.", 0, 1410, 30, Kids.quietStart(this), { v: Int -> minutesToText(v) }) { Kids.setQuietStart(this, it) }
        slider(rest, "Until", "When rest time ends. It can be after midnight.", 0, 1410, 30, Kids.quietEnd(this), { v: Int -> minutesToText(v) }) { Kids.setQuietEnd(this, it) }

        val games = section(page, "Games and Play Store", "One shared limit covers every game, including a game that was installed today. The app cannot close a game; it covers it with the rest screen.")
        slider(games, "All games, each day", "One shared total for all games together. 0 blocks games completely.", 0, 120, 5, Games.umbrellaMin(this),
            { v: Int -> if (v == 0) "Games blocked" else "$v minutes" }) { Games.setUmbrellaMin(this, it) }
        toggle(games, "Treat new apps like games", "Apps installed recently that you have not allowed count as games. This covers a game installed in the same session.", Games.newAppRule(this)) { Games.setNewAppRule(this, it) }
        slider(games, "New means the last", "How many days an app counts as new.", 1, 30, 1, Games.newAppDays(this),
            { v: Int -> "$v day" + (if (v == 1) "" else "s") }) { Games.setNewAppDays(this, it) }
        toggle(games, "Play Store needs the PIN", "In Kids mode, opening the Play Store asks for the parent PIN. It only covers the Play Store screen; Google's own Parental controls (Play Store, Settings, Family) can also require approval for installs.", Games.storeGuard(this)) { Games.setStoreGuard(this, it) }

        val gameList = section(page, "Apps that count as games", "Switch an app on to allow it, so it stays free of the games limit. Learning games are a good use for this.")
        val found = Games.candidates(this)
        if (found.isEmpty()) {
            gameList.addView(Theme.body(this, "No games found yet. Games and recently installed apps will appear here.", 14f), Theme.lp(this, 6f, 0f))
        }
        val okSet = Games.allowed(this)
        for (g in found) {
            toggle(gameList, g.second, "Allowed means it does not count against the games limit.", okSet.contains(g.first)) { Games.setAllowed(this, g.first, it) }
        }

        val nudge = section(page, "Puppy reminders", "Now and then the puppy suggests something else to do.")
        toggle(nudge, "Remind the child", "Messages like Let's play with your toys, Let's go outside, or Can you draw something?", Kids.nudgeOn(this)) { Kids.setNudgeOn(this, it) }
        slider(nudge, "Remind every", "After this many minutes of watching reels.", 1, 15, 1, Kids.nudgeMin(this),
            { v: Int -> "$v minute" + (if (v == 1) "" else "s") }) { Kids.setNudgeMin(this, it) }

        val apps = section(page, "Apps", "Kids mode only watches the apps you switch on here.")
        val names = mapOf("instagram" to "Instagram", "youtube" to "YouTube", "facebook" to "Facebook", "threads" to "Threads", "linkedin" to "LinkedIn")
        for (app in Prefs.APPS) {
            toggle(apps, names[app] ?: app, "", Kids.appOn(this, app)) { Kids.setAppOn(this, app, it) }
        }

        val report = section(page, "Parent report", "What the child watched while Kids mode was on.")
        for (i in 0..6) {
            val day = Prefs.dayStringBack(i)
            val r = Kids.reels(this, day)
            val s = Kids.secs(this, day)
            val line = Prefs.dayLongLabel(i) + ": " + r + " reel" + (if (r == 1) "" else "s") + ", " + Prefs.formatTime(s) + ", games " + Prefs.formatTime(Kids.gameSecs(this, day))
            report.addView(Theme.body(this, line, 14f), Theme.lp(this, 6f, 0f))
        }
    }

    private fun scheduleSummary(): String {
        val on = Schedules.load(this).count { it.on }
        return if (on == 0) "None running" else "$on running"
    }

    private fun buildPrompts(page: LinearLayout) {
        val b = section(page, "Break prompt", "At the red limit Brain Buddy can offer a short, calm break. You can close it any time.")
        slider(b, "At the red limit show", "Off shows nothing. Breathing only shows a slow breathing circle. The third choice adds Brain Buddy holding the break sign.", 0, 2, 1, Prefs.getBreakMode(this),
            { v: Int -> if (v == 0) "Off" else if (v == 1) "Breathing" else "Sign and breathing" }) { Prefs.setBreakMode(this, it) }
        val c = section(page, "Open-app card", "A brief hello when you open Instagram, YouTube, Facebook, Threads or LinkedIn.")
        toggle(c, "Show the open-app card", "For about three seconds it shows today's count and time, with Brain Buddy. No button, no waiting. At most once every ten minutes.", Prefs.getOpenCardOn(this)) { Prefs.setOpenCardOn(this, it) }
    }

    private fun buildSchedules(page: LinearLayout) {
        val list = Schedules.load(this)
        val save = { Schedules.save(this, list) }
        val time = { m: Int -> minutesToText(m) }
        for (s in list) {
            val card = section(page, s.name, Schedules.summary(s))
            toggle(card, "Use this schedule", "Turn on to start applying it. It shows a calm screen that you can dismiss.", s.on) { s.on = it; save() }
            when (s.kind) {
                Schedules.NIGHT -> {
                    textSetting(card, "Days", "Numbers separated by commas: 1 is Sunday, 2 Monday, 3 Tuesday, 4 Wednesday, 5 Thursday, 6 Friday, 7 Saturday.", s.days) { s.days = it.trim(); save() }
                    slider(card, "From", "When the night starts.", 0, 1410, 30, s.start, time) { s.start = it; save() }
                    slider(card, "Until", "When the night ends. It can be after midnight.", 0, 1410, 30, s.end, time) { s.end = it; save() }
                }
                Schedules.PACING -> {
                    slider(card, "Reels", "How many reels in the time below trip the schedule.", 5, 100, 5, s.cap, { v: Int -> "$v reels" }) { s.cap = it; save() }
                    slider(card, "Within", "The length of the time slice, in hours.", 1, 6, 1, s.hours, { v: Int -> "$v hour" + (if (v == 1) "" else "s") }) { s.hours = it; save() }
                }
                Schedules.DAILY -> {
                    slider(card, "At reels", "Once today's count reaches this number, the calm screen appears.", 25, 1000, 25, s.cap, { v: Int -> "$v reels" }) { s.cap = it; save() }
                }
                else -> {
                    textSetting(card, "Name", "What this schedule is called.", s.name) { s.name = Schedules.clean(it); save() }
                    textSetting(card, "Days", "Numbers separated by commas: 1 is Sunday, 2 Monday, 3 Tuesday, 4 Wednesday, 5 Thursday, 6 Friday, 7 Saturday.", s.days) { s.days = it.trim(); save() }
                    slider(card, "From", "When it starts.", 0, 1410, 30, s.start, time) { s.start = it; save() }
                    slider(card, "Until", "When it ends. It can be after midnight.", 0, 1410, 30, s.end, time) { s.end = it; save() }
                    slider(card, "After", "0 means the calm screen shows for the whole time. Otherwise it shows once you reach this many reels within the time.", 0, 200, 5, s.cap, { v: Int -> if (v == 0) "No reels" else "$v reels" }) { s.cap = it; save() }
                    val del = Theme.softButton(this, "Delete this schedule")
                    del.setOnClickListener {
                        list.remove(s)
                        save()
                        openSection("schedules")
                    }
                    card.addView(del, Theme.lp(this, 12f, 0f))
                }
            }
        }
        val add = Theme.button(this, "Add a schedule")
        add.setOnClickListener {
            list.add(Schedule(Schedules.CUSTOM, "Custom " + (list.size - 2), true, Schedules.ALL_DAYS, 9 * 60, 17 * 60, 0, 0))
            save()
            openSection("schedules")
        }
        page.addView(add, Theme.lp(this, 0f, 8f))
    }

    private fun buildGoal(page: LinearLayout) {
        val g = section(page, "Daily goal", "Reach the end of the day at or under your goal and your streak grows by one. Staying under on a day you did not scroll counts too.")
        toggle(g, "Use a daily goal", "When off, a good day means a day under your yellow limit.", Prefs.getGoalOn(this)) { Prefs.setGoalOn(this, it) }
        slider(g, "Stay under", "The number of reels you want to stay under each day.", 25, 500, 25, Prefs.getGoal(this), { v: Int -> "$v reels" }) { Prefs.setGoal(this, it) }
    }

    private fun buildBubble(page: LinearLayout) {
        val buddyCard = section(page, "Brain Buddy", "The bubble is a small pill with the reel count and your reel time. Changes show the next time the bubble appears.")
        var insideSwitch: Switch? = null
        toggle(buddyCard, "Show Brain Buddy", "Turn him off to keep only the counter pill.", Prefs.getBuddyOn(this)) { on ->
            Prefs.setBuddyOn(this, on)
            insideSwitch?.isEnabled = on
        }
        insideSwitch = toggle(buddyCard, "Inside the bubble", "Off: he stands on top of the bubble, half in and half out. On: he sits inside the bubble, on the left.", Prefs.getBuddyPos(this) == 1) { Prefs.setBuddyPos(this, if (it) 1 else 0) }
        insideSwitch?.isEnabled = Prefs.getBuddyOn(this)

        slider(buddyCard, "Bubble transparency", "How see-through the bubble is. 0% is solid, 80% is very faint.", 0, 80, 5, Prefs.getTransparency(this), { v: Int -> "$v%" }) { Prefs.setTransparency(this, it) }

    }

    private fun buildVibration(page: LinearLayout) {
        val vibes = section(page, "Vibration", "How long the phone buzzes. 0 means no buzz.")
        val secs = { v: Int -> String.format(Locale.US, "%.1f s", v / 1000f) }
        val vs = ArrayList<Slider>()
        val saveVibes = { _: Int -> TenthVibration.setLimitVibes(this, vs[0].value(), vs[1].value(), vs[2].value()) }
        vs.add(slider(vibes, "Buzz at the yellow limit", "How long the phone buzzes when you reach the yellow limit.", 0, 5000, 100, TenthVibration.getLimitVibe(this, 1), secs, saveVibes))
        vs.add(slider(vibes, "Buzz at the orange limit", "How long the phone buzzes at the orange limit.", 0, 5000, 100, TenthVibration.getLimitVibe(this, 2), secs, saveVibes))
        vs.add(slider(vibes, "Buzz at the red limit", "How long the phone buzzes at the red limit.", 0, 5000, 100, TenthVibration.getLimitVibe(this, 3), secs, saveVibes))
        slider(vibes, "Regular buzz every", "A short buzz and a happy hop of Brain Buddy after this many reels. A gentle reminder that you are still scrolling.", 5, 50, 5, TenthVibration.getInterval(this), { v: Int -> "$v reels" }) { TenthVibration.setInterval(this, it) }
        slider(vibes, "Length of the regular buzz", "How long the regular buzz lasts. 0 turns it off.", 0, 5000, 100, TenthVibration.getIntervalVibe(this), secs) { TenthVibration.setIntervalVibe(this, it) }

    }

    private fun buildMessages(page: LinearLayout) {
        val msgs = section(page, "Messages", "A short note shown when a limit is reached. Leave blank for none.")
        textSetting(msgs, "At the yellow limit", "Shown for a few seconds when you reach the yellow limit.", Prefs.getMessage(this, 1)) { Prefs.setMessage(this, 1, it) }
        textSetting(msgs, "At the orange limit", "Shown when you reach the orange limit.", Prefs.getMessage(this, 2)) { Prefs.setMessage(this, 2, it) }
        textSetting(msgs, "At the red limit", "Shown when you reach the red limit.", Prefs.getMessage(this, 3)) { Prefs.setMessage(this, 3, it) }
        textSetting(msgs, "On the lock screen", "Shown on the lock screen, above a small suggestion like taking a walk.", Prefs.getMessage(this, 4)) { Prefs.setMessage(this, 4, it) }

    }

    private fun buildLock(page: LinearLayout) {
        val pauses = section(page, "Unlock pauses")
        slider(pauses, "Pauses per day", "How many times a day you can pause the lock screen for 15 minutes. 0 means no pauses at all.", 0, 5, 1, Prefs.getMaxPauses(this), { v: Int -> "$v per day" }) { Prefs.setMaxPauses(this, it) }

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

    }

    private fun buildBedtime(page: LinearLayout) {
        val bed = section(page, "Bedtime", "Two gentle reminders at night, then Brain Buddy keeps hopping every 15 minutes while you scroll.")
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

    }

    private fun buildBank(page: LinearLayout) {
        val restart = section(
            page, "Restart prompt",
            "Android does not let an app switch its own accessibility service on. So this small helper watches for social apps, and if the counter is off it shows a notification. Tap it to switch the counter back on."
        )
        toggle(restart, "Remind me when the counter is off", "When on, opening Instagram, YouTube, Facebook, Threads or LinkedIn while the counter is off shows a notification that takes you to the right switch. It needs Usage access and shows a small quiet notification while it runs.", Prefs.getWatcherOn(this)) {
            Prefs.setWatcherOn(this, it)
            applyWatcher()
        }

        val bank = section(
            page, "Bank apps",
            "Banking apps refuse to work while an accessibility service is on. When one opens, the counter switches itself off and shows a notification. Tap it to turn the counter back on afterwards."
        )
        toggle(bank, "Switch off for bank apps", "When on, the counter switches itself off as soon as a bank or payment app opens.", Prefs.getBankGuard(this)) { Prefs.setBankGuard(this, it) }
        textSetting(bank, "Words to look for in app names", "Any app whose technical name contains one of these words counts as a bank app. Separate the words with commas.", Prefs.getBankWords(this)) { Prefs.setBankWords(this, it) }

    }

    private fun buildPassword(page: LinearLayout) {
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

    }
}
