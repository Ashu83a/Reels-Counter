package com.example.reelcounter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

// "Time is up": the puppy asks to rest. A parent can give more time with the PIN.
class KidsStopActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val asked = intent.getIntExtra("reason", 0)
        val reason = if (asked != 0) asked else Kids.blockReason(this, System.currentTimeMillis())
        val rest = reason == 2
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        val pad = Theme.dp(this, 28f)
        layout.setPadding(pad, pad, pad, pad)
        layout.setBackgroundColor(Theme.BG)

        val pup = BuddyView(this, Theme.dp(this, 230f))
        pup.setExpression(if (reason == 4) BuddyView.CURIOUS else BuddyView.SLEEPING)
        layout.addView(pup, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = Theme.heading(
            this,
            when (reason) {
                2 -> "Rest time"
                3 -> "Games are done for today"
                4 -> "Ask a grown-up"
                else -> "Time is up for now"
            },
            28f
        )
        title.gravity = Gravity.CENTER
        layout.addView(title, Theme.lp(this, 12f, 6f))

        val message = TextView(this)
        message.text = when (reason) {
            2 -> "The phone is sleeping now. Sweet dreams! See you in the morning."
            3 -> "That was fun! Now let's play with toys, draw, or go outside."
            4 -> "The Play Store is for grown-ups. Let's ask Mum or Dad."
            else -> "Let's rest our eyes. We can play with toys, draw, or go outside!"
        }
        message.textSize = 18f
        message.typeface = Theme.SERIF
        message.setTextColor(Theme.TEXT)
        message.gravity = Gravity.CENTER
        layout.addView(message, Theme.lp(this, 0f, 24f))

        val home = Theme.button(this, "Go to home screen")
        home.setOnClickListener { goHome() }
        layout.addView(home, Theme.lp(this, 0f, 12f))

        val parent = Theme.softButton(
            this,
            when (reason) {
                2 -> "Parent: allow 10 minutes"
                3 -> "Parent: give 10 more game minutes"
                4 -> "Parent: open the Play Store for 10 minutes"
                else -> "Parent: give 10 more minutes"
            }
        )
        parent.setOnClickListener {
            Kids.askPin(this, "Parent PIN") {
                when (reason) {
                    2 -> Kids.passQuiet(this, 10)
                    3 -> Kids.grantGames(this, 10)
                    4 -> Kids.allowStore(this, 10)
                    else -> Kids.grant(this, 10)
                }
                finish()
            }
        }
        layout.addView(parent, Theme.lp(this, 0f, 0f))

        setContentView(layout)
    }

    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_HOME)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
        finish()
    }
}
