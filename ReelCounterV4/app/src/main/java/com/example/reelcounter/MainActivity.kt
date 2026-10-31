package com.example.reelcounter

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.hasPassword(this)) showGate() else showSettings()
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
        field.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
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

    private fun minutesToText(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

    private fun textToMinutes(t: String, fallback: Int): Int {
        val parts = t.trim().split(":")
        if (parts.size != 2) return fallback
        val h = parts[0].trim().toIntOrNull() ?: return fallback
        val m = parts[1].trim().toIntOrNull() ?: return fallback
        if (h < 0 || h > 23 || m < 0 || m > 59) return fallback
        return h * 60 + m
    }

    private fun num(e: EditText, fallback: Int): Int = e.text.toString().trim().toIntOrNull() ?: fallback

    // A small label above a field.
    private fun labelled(card: LinearLayout, label: String, field: EditText) {
        card.addView(Theme.soft(this, label), Theme.lp(this, 10f, 3f))
        card.addView(field, Theme.lp(this, 0f, 0f))
    }

    private fun switchRow(text: String, on: Boolean): Switch {
        val s = Switch(this)
        s.text = text
        s.textSize = 15f
        s.setTextColor(Theme.TEXT)
        s.isChecked = on
        return s
    }

    private fun section(page: LinearLayout, title: String, note: String = ""): LinearLayout {
        val card = Theme.card(this)
        card.addView(Theme.heading(this, title, 18f))
        if (note.isNotEmpty()) card.addView(Theme.soft(this, note), Theme.lp(this, 4f, 0f))
        page.addView(card, Theme.lp(this, 0f, 14f))
        return card
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
        page.addView(Theme.soft(this, "A calm way to keep an eye on short videos"), Theme.lp(this, 0f, 14f))

        // ---- Setup ----
        val setup = section(
            page, "Setup",
            "If the Accessibility switch is greyed out: Settings, Apps, Reel Counter 4, three dots, Allow restricted settings."
        )
        val btnAcc = Theme.button(this, "Turn the counter on (Accessibility)")
        btnAcc.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        setup.addView(btnAcc, Theme.lp(this, 10f, 0f))

        val btnNote = Theme.softButton(this, "Allow notifications (for the bank reminder)")
        btnNote.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 100)
            } else {
                Toast.makeText(this, "Not needed on this Android version", Toast.LENGTH_SHORT).show()
            }
        }
        setup.addView(btnNote, Theme.lp(this, 8f, 0f))

        val btnStats = Theme.softButton(this, "See my stats")
        btnStats.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        setup.addView(btnStats, Theme.lp(this, 8f, 0f))

        setup.addView(
            Theme.soft(this, "Tip: in Settings, Accessibility, Advanced settings, you can set a shortcut (for example holding both volume keys) that turns this counter on or off in one gesture."),
            Theme.lp(this, 10f, 0f)
        )

        // ---- Limits ----
        val limits = section(page, "Limits", "The bubble changes colour as the number of reels grows. At the last limit the lock screen appears.")
        val f1 = Theme.field(this, Prefs.getLimit(this, 1).toString(), true)
        val f2 = Theme.field(this, Prefs.getLimit(this, 2).toString(), true)
        val f3 = Theme.field(this, Prefs.getLimit(this, 3).toString(), true)
        val f4 = Theme.field(this, Prefs.getLimit(this, 4).toString(), true)
        labelled(limits, "Yellow at (reels)", f1)
        labelled(limits, "Orange at (reels)", f2)
        labelled(limits, "Red at (reels)", f3)
        labelled(limits, "Lock screen at (reels)", f4)

        // ---- Vibration ----
        val vibes = section(page, "Vibration", "Lengths are in milliseconds. 1000 is one second. 0 means no buzz.")
        val v1 = Theme.field(this, TenthVibration.getLimitVibe(this, 1).toString(), true)
        val v2 = Theme.field(this, TenthVibration.getLimitVibe(this, 2).toString(), true)
        val v3 = Theme.field(this, TenthVibration.getLimitVibe(this, 3).toString(), true)
        val iv = Theme.field(this, TenthVibration.getInterval(this).toString(), true)
        val ivv = Theme.field(this, TenthVibration.getIntervalVibe(this).toString(), true)
        labelled(vibes, "Buzz at the yellow limit", v1)
        labelled(vibes, "Buzz at the orange limit", v2)
        labelled(vibes, "Buzz at the red limit", v3)
        labelled(vibes, "Short buzz and happy hop every how many reels", iv)
        labelled(vibes, "Length of that short buzz", ivv)

        // ---- Messages ----
        val msgs = section(page, "Messages", "A short note shown when a limit is reached. Leave blank for none. The last one is shown on the lock screen.")
        val m1 = Theme.field(this, Prefs.getMessage(this, 1), false)
        val m2 = Theme.field(this, Prefs.getMessage(this, 2), false)
        val m3 = Theme.field(this, Prefs.getMessage(this, 3), false)
        val m4 = Theme.field(this, Prefs.getMessage(this, 4), false)
        labelled(msgs, "At the yellow limit", m1)
        labelled(msgs, "At the orange limit", m2)
        labelled(msgs, "At the red limit", m3)
        labelled(msgs, "On the lock screen", m4)

        // ---- Unlock pauses ----
        val pauses = section(page, "Unlock pauses", "How many times a day the lock screen can be paused for 15 minutes.")
        val fp = Theme.field(this, Prefs.getMaxPauses(this).toString(), true)
        labelled(pauses, "Pauses per day", fp)

        // ---- Relaxed time ----
        val relax = section(page, "Relaxed time", "During this window the lock screen stays off. The bubble still counts.")
        val relaxSwitch = switchRow("Use a relaxed window", Prefs.getRelaxEnabled(this))
        relax.addView(relaxSwitch, Theme.lp(this, 8f, 0f))
        val rd = Theme.field(this, Prefs.getRelaxDays(this), false)
        val rs = Theme.field(this, Prefs.getRelaxStartHour(this).toString(), true)
        val re = Theme.field(this, Prefs.getRelaxEndHour(this).toString(), true)
        labelled(relax, "Days (1 = Sunday, 2 = Monday ... 7 = Saturday, separated by commas)", rd)
        labelled(relax, "From hour (0 to 23)", rs)
        labelled(relax, "Until hour (0 to 23)", re)

        // ---- Bedtime ----
        val bed = section(page, "Bedtime", "Two gentle reminders at night, then the face keeps hopping every 15 minutes while you scroll.")
        val bedSwitch = switchRow("Bedtime reminders", Prefs.getBedtimeEnabled(this))
        bed.addView(bedSwitch, Theme.lp(this, 8f, 0f))
        val bt1 = Theme.field(this, minutesToText(Prefs.getBedtime1(this)), false)
        val bt2 = Theme.field(this, minutesToText(Prefs.getBedtime2(this)), false)
        val bm1 = Theme.field(this, Prefs.getBedMessage(this, 1), false)
        val bm2 = Theme.field(this, Prefs.getBedMessage(this, 2), false)
        labelled(bed, "First reminder at (HH:MM, 24 hour)", bt1)
        labelled(bed, "Second reminder at (HH:MM, 24 hour)", bt2)
        labelled(bed, "First message", bm1)
        labelled(bed, "Second message", bm2)

        // ---- Bank apps ----
        val bank = section(
            page, "Bank apps",
            "Banking apps refuse to work while an accessibility service is on. When one opens, the counter switches itself off and shows a notification. Tap it to turn the counter back on afterwards."
        )
        val bankSwitch = switchRow("Switch off for bank apps", Prefs.getBankGuard(this))
        bank.addView(bankSwitch, Theme.lp(this, 8f, 0f))
        val bw = Theme.field(this, Prefs.getBankWords(this), false)
        labelled(bank, "Words to look for in app names (separated by commas)", bw)

        // ---- Look ----
        val look = section(page, "Look", "How see-through the bubble is. 0 is solid, 80 is very faint.")
        val tr = Theme.field(this, Prefs.getTransparency(this).toString(), true)
        labelled(look, "Transparency (0 to 80)", tr)

        // ---- Password ----
        val pw = section(page, "Password", "Optional. A friend can set it so these settings cannot be changed on impulse.")
        val pwField = Theme.field(this, "", false)
        pwField.hint = if (Prefs.hasPassword(this)) "New password" else "Choose a password"
        pwField.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        labelled(pw, "Password", pwField)
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

        // ---- Save ----
        val save = Theme.button(this, "Save settings")
        save.setOnClickListener {
            val l1 = num(f1, 100)
            val l2 = num(f2, 150)
            val l3 = num(f3, 200)
            val l4 = num(f4, 300)
            if (l1 <= 0 || l2 <= l1 || l3 <= l2 || l4 <= l3) {
                Toast.makeText(this, "Limits must go up: yellow, then orange, then red, then lock", Toast.LENGTH_LONG).show()
            } else {
                Prefs.setLimits(this, l1, l2, l3)
                Prefs.setLockLimit(this, l4)
                TenthVibration.setLimitVibes(this, num(v1, 1000), num(v2, 3000), num(v3, 5000))
                TenthVibration.setInterval(this, num(iv, 10).coerceAtLeast(1))
                TenthVibration.setIntervalVibe(this, num(ivv, 300))
                Prefs.setMessage(this, 1, m1.text.toString())
                Prefs.setMessage(this, 2, m2.text.toString())
                Prefs.setMessage(this, 3, m3.text.toString())
                Prefs.setMessage(this, 4, m4.text.toString())
                Prefs.setMaxPauses(this, num(fp, 2))
                Prefs.setRelaxEnabled(this, relaxSwitch.isChecked)
                Prefs.setRelaxWindow(this, rd.text.toString().trim(), num(rs, 18).coerceIn(0, 23), num(re, 22).coerceIn(0, 23))
                Prefs.setBedtimeEnabled(this, bedSwitch.isChecked)
                Prefs.setBedtimes(
                    this,
                    textToMinutes(bt1.text.toString(), Prefs.getBedtime1(this)),
                    textToMinutes(bt2.text.toString(), Prefs.getBedtime2(this))
                )
                Prefs.setBedMessage(this, 1, bm1.text.toString())
                Prefs.setBedMessage(this, 2, bm2.text.toString())
                Prefs.setBankGuard(this, bankSwitch.isChecked)
                Prefs.setBankWords(this, bw.text.toString())
                Prefs.setTransparency(this, num(tr, 20).coerceIn(0, 80))
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
        page.addView(save, Theme.lp(this, 4f, 28f))

        setContentView(scroll)
    }
}
