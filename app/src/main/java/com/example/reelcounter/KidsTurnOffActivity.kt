package com.example.reelcounter

import android.app.Activity
import android.os.Bundle
import android.view.View

// Opened from the tile or the accessibility button: asks for the parent PIN, then turns Kids mode off.
class KidsTurnOffActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blank = View(this)
        blank.setBackgroundColor(Theme.BG)
        setContentView(blank)
        Kids.askPin(this, "Parent PIN to turn Kids mode off", { finish() }) {
            Kids.setOn(this, false)
            AutoKids.end(this)
            finish()
        }
    }
}
