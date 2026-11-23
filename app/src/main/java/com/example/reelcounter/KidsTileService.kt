package com.example.reelcounter

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

// The Kids mode tile in the quick settings panel: one tap to switch Kids mode on when a child
// grabs the phone. Turning it off asks for the parent PIN.
class KidsTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        if (!Kids.hasPin(this)) {
            // Set a parent PIN first, so a child cannot simply switch it off again.
            open(Intent(this, MainActivity::class.java))
            return
        }
        if (Kids.manualOn(this)) {
            open(Intent(this, KidsTurnOffActivity::class.java))
        } else {
            Kids.setOn(this, true)
            AutoKids.end(this)
            refresh()
        }
    }

    @Suppress("DEPRECATION")
    private fun open(i: Intent) {
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 5, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            startActivityAndCollapse(i)
        }
    }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (Kids.manualOn(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Kids mode"
        t.updateTile()
    }
}
