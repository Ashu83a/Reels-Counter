package com.example.reelcounter

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// The screen that opens when you tap the "Reel Counter is off" notification.
// Android never lets an app switch its own Accessibility service back on, so this screen
// gets you to the right switch in one tap, and tells you what to press once you are there.
class RestartActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var buddy: BuddyView? = null
    private var titleText: TextView? = null
    private var message: TextView? = null
    private var turnOn: android.widget.Button? = null
    private var steps: LinearLayout? = null
    private var opened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.BG)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val pad = Theme.dp(this, 24f)
        col.setPadding(pad, Theme.dp(this, 48f), pad, pad)
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val b = BuddyView(this, Theme.dp(this, 150f))
        b.setExpression(BuddyView.SLEEPING)
        col.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        buddy = b

        val t = Theme.heading(this, "Reel Counter is off", 26f)
        t.gravity = Gravity.CENTER
        col.addView(t, Theme.lp(this, 12f, 6f))
        titleText = t

        val m = Theme.body(this, "Your phone keeps this switch to itself, so the app cannot turn it on alone. One tap below takes you straight to it.")
        m.gravity = Gravity.CENTER
        col.addView(m, Theme.lp(this, 0f, 18f))
        message = m

        val go = Theme.button(this, "Turn it on")
        go.setOnClickListener { openAccessibility() }
        col.addView(go, Theme.lp(this, 0f, 10f))
        turnOn = go

        val app = Theme.softButton(this, "Open Reel Counter")
        app.setOnClickListener {
            startActivity(Intent(this, TodayActivity::class.java))
            finish()
        }
        col.addView(app, Theme.lp(this, 0f, 18f))

        val card = Theme.card(this)
        card.addView(Theme.heading(this, "When the page opens", 16f))
        val list = listOf(
            "1. If you see a list, tap Installed apps (or Downloaded apps).",
            "2. Tap Reel Counter 4.",
            "3. Switch it on and press Allow."
        )
        for (s in list) card.addView(Theme.body(this, s, 14f), Theme.lp(this, 6f, 0f))
        col.addView(card, Theme.lp(this, 0f, 0f))
        steps = card

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // The first time this screen appears, go straight to the switch.
        if (!opened && !SysCheck.accessibilityOn(this)) {
            opened = true
            handler.postDelayed({ openAccessibility() }, 150L)
        }
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun refresh() {
        if (SysCheck.accessibilityOn(this)) {
            buddy?.setExpression(BuddyView.ENCOURAGE)
            titleText?.text = "All set, counting again"
            message?.text = "Reel Counter is on. You can go back to your app now."
            turnOn?.visibility = android.view.View.GONE
            steps?.visibility = android.view.View.GONE
            handler.postDelayed({ finish() }, 2200L)
        } else {
            buddy?.setExpression(BuddyView.SLEEPING)
            titleText?.text = "Reel Counter is off"
            turnOn?.visibility = android.view.View.VISIBLE
            steps?.visibility = android.view.View.VISIBLE
        }
    }

    private fun openAccessibility() {
        try {
            val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            details.putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(this, ReelService::class.java).flattenToString())
            startActivity(details)
            return
        } catch (e: Exception) {
        }
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        } catch (e: Exception) {
        }
        Toast.makeText(this, "Open Settings, then Accessibility, then Reel Counter 4", Toast.LENGTH_LONG).show()
    }
}
