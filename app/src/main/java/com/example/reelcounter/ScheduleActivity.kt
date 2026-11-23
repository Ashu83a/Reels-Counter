package com.example.reelcounter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

// The calm screen shown when a schedule trips. It never traps you: carry on or go home.
class ScheduleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getIntExtra("kind", Schedules.CUSTOM)
        val name = intent.getStringExtra("name") ?: "Schedule"

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        val pad = Theme.dp(this, 28f)
        layout.setPadding(pad, pad, pad, pad)
        layout.setBackgroundColor(Theme.GREEN_DARK)

        val night = kind == Schedules.NIGHT
        val buddy = BuddyView(this, Theme.dp(this, 200f))
        buddy.setExpression(if (night) BuddyView.SLEEPING else BuddyView.BREAK)
        layout.addView(buddy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = TextView(this)
        title.text = Prefs.getCount(this).toString() + " reels today"
        title.textSize = 26f
        title.typeface = Theme.SERIF_BOLD
        title.setTextColor(Theme.CREAM)
        title.gravity = Gravity.CENTER
        layout.addView(title, Theme.lp(this, 16f, 0f))

        val message = TextView(this)
        message.text = when (kind) {
            Schedules.NIGHT -> "It is late. Brain Buddy would love some sleep, and so would you."
            Schedules.PACING -> "A lot of reels in a short while. A small break would help."
            Schedules.DAILY -> "You have reached the limit you set for today."
            else -> "$name is on. This is a good moment for a break."
        }
        message.textSize = 18f
        message.typeface = Theme.SERIF
        message.setTextColor(Theme.CREAM)
        message.gravity = Gravity.CENTER
        layout.addView(message, Theme.lp(this, 12f, 24f))

        val carry = Theme.softButton(this, "Carry on (reminds again in 15 minutes)")
        carry.setOnClickListener {
            Prefs.setSchedSnooze(this, System.currentTimeMillis() + 15L * 60L * 1000L)
            finish()
        }
        layout.addView(carry, Theme.lp(this, 0f, 10f))

        val home = Theme.button(this, "Go to home screen", Theme.BRASS, Theme.GREEN_DARK)
        home.setOnClickListener { goHome() }
        layout.addView(home, Theme.lp(this, 0f, 0f))

        setContentView(layout)
    }

    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        Prefs.setSchedSnooze(this, System.currentTimeMillis() + 15L * 60L * 1000L)
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_HOME)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
        finish()
    }
}
