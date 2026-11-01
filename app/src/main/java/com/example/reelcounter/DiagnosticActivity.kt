package com.example.reelcounter

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// Shows what the counter sees right now. Open a reels screen in an app, come back here
// (or use split screen) and send a screenshot of this page so problems can be fixed.
class DiagnosticActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var out: TextView

    private val refresh = object : Runnable {
        override fun run() {
            show()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.BG)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        val pad = Theme.dp(this, 16f)
        page.setPadding(pad, pad, pad, pad)
        scroll.addView(page)

        page.addView(Theme.heading(this, "What the counter sees", 24f), Theme.lp(this, 8f, 4f))
        page.addView(
            Theme.soft(this, "Open Instagram, YouTube or another app on a reels screen, swipe a few times, then come back here and take a screenshot. This page updates every second."),
            Theme.lp(this, 0f, 12f)
        )
        val card = Theme.card(this)
        out = Theme.body(this, "", 13f)
        card.addView(out)
        page.addView(card, Theme.lp(this, 0f, 14f))

        val back = Theme.button(this, "Back")
        back.setOnClickListener { finish() }
        page.addView(back, Theme.lp(this, 4f, 24f))
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun show() {
        val kindName = when (Diag.kind) {
            1 -> "reels screen"
            2 -> "long video screen"
            else -> "other screen"
        }
        val sb = StringBuilder()
        sb.append("Phone: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
            .append(", Android ").append(android.os.Build.VERSION.RELEASE).append('\n')
        sb.append("Service: ").append(if (SysCheck.accessibilityOn(this)) "on" else "OFF").append('\n')
        sb.append("Battery unrestricted: ").append(SysCheck.batteryFree(this)).append('\n')
        sb.append("Checks done: ").append(Diag.evals).append(", reels counted: ").append(Diag.counted).append('\n')
        sb.append("Last check: ").append(if (Diag.time == 0L) "never" else SysCheck.agoText(Diag.time)).append('\n')
        sb.append("App: ").append(Diag.pkg.ifEmpty { "-" }).append('\n')
        sb.append("Judged as: ").append(kindName).append('\n')
        sb.append("Full-screen pager found: ").append(Diag.pager).append(", fallback active: ").append(Diag.fallback).append('\n')
        sb.append("Selected tabs: ").append(Diag.selected.ifEmpty { "-" }).append('\n')
        sb.append("Screen labels: ").append(Diag.ids.ifEmpty { "-" }).append('\n')
        sb.append("Text seen: ").append(Diag.items.ifEmpty { "-" })
        out.text = sb.toString()
    }
}
