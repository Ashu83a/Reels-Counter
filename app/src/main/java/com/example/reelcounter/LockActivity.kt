package com.example.reelcounter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class LockActivity : Activity() {

    private val suggestions = listOf(
        "Take a short walk.",
        "Drink a glass of water.",
        "Call someone you like.",
        "Stretch for two minutes.",
        "Look out of the window for a minute."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        val pad = Theme.dp(this, 28f)
        layout.setPadding(pad, pad, pad, pad)
        layout.setBackgroundColor(Theme.BURGUNDY)

        // A big crying face.
        val face = FaceView(this, Theme.dp(this, 110f))
        face.setMood(3)
        face.setTravel(0f)
        layout.addView(face, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val total = Prefs.getCount(this)
        val title = TextView(this)
        title.text = "$total reels today\n" + Prefs.formatTime(Prefs.getSeconds(this)) + " spent"
        title.textSize = 26f
        title.typeface = Theme.SERIF_BOLD
        title.setTextColor(Theme.CREAM)
        title.gravity = Gravity.CENTER
        layout.addView(title, Theme.lp(this, 20f, 0f))

        val own = Prefs.getMessage(this, 4)
        val message = TextView(this)
        message.text = (if (own.isNotEmpty()) own else "You have gone past your limit.") + "\n\n" + suggestions.random()
        message.textSize = 18f
        message.typeface = Theme.SERIF
        message.setTextColor(Theme.CREAM)
        message.gravity = Gravity.CENTER
        layout.addView(message, Theme.lp(this, 16f, 24f))

        val left = Prefs.getMaxPauses(this) - Prefs.getPausesUsedToday(this)
        val unlock = Theme.softButton(this, if (left > 0) "Unlock anyway (15 minutes, $left left today)" else "No unlocks left today")
        unlock.isEnabled = left > 0
        unlock.setOnClickListener {
            Prefs.startPause(this)
            finish()
        }
        layout.addView(unlock, Theme.lp(this, 0f, 10f))

        val home = Theme.button(this, "Go to home screen", Theme.BRASS, Theme.GREEN_DARK)
        home.setOnClickListener { goHome() }
        layout.addView(home, Theme.lp(this, 0f, 0f))

        setContentView(layout)
    }

    // Back button also leaves to the home screen, so the lock is never a trap.
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
