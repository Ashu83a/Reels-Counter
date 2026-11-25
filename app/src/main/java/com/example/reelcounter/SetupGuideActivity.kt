package com.example.reelcounter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// A walkthrough for the first start: one card per step, each with a button that
// opens the right Settings page, and a tick once the step is done.
class SetupGuideActivity : Activity() {

    private class Step(
        val title: String,
        val text: String,
        val button: String,
        val check: (() -> Boolean)?,
        val markKey: String? = null,
        val optional: Boolean = false,
        val action: () -> Unit
    )

    private val steps = ArrayList<Step>()
    private val statusViews = ArrayList<TextView>()
    private val cards = ArrayList<LinearLayout>()
    private var progress: TextView? = null

    private fun marked(key: String): Boolean =
        getSharedPreferences("reels_v2", MODE_PRIVATE).getBoolean(key, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        steps.add(
            Step(
                "Allow restricted settings",
                "Android blocks Accessibility for apps installed from a file. Open the app info page, tap the three dots at the top right, then tap Allow restricted settings. If you do not see the three dots, skip this step.",
                "Open app info",
                { marked("guide_restricted") },
                "guide_restricted"
            ) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        )
        steps.add(
            Step(
                "Turn on the counter",
                "Find Reel Counter 4 in the Accessibility list, open it and switch it on. This is what lets the app see when you are watching reels. Nothing leaves your phone.",
                "Open Accessibility",
                { SysCheck.accessibilityOn(this) }
            ) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )
        steps.add(
            Step(
                "Allow notifications",
                "Used for one thing only: a reminder to turn the counter back on after it switches itself off for a bank app.",
                "Allow notifications",
                { SysCheck.notificationsOn(this) }
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 100)
                } else {
                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    startActivity(i)
                }
            }
        )
        steps.add(
            Step(
                "Stop battery limits",
                "Some phones put apps to sleep to save battery, and then the counter misses reels. Choose Unrestricted or Allow background activity. On OnePlus, Xiaomi and similar phones, also switch on Auto-launch or Run in background on the app's battery page.",
                "Open battery settings",
                { SysCheck.batteryFree(this) }
            ) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        )
        steps.add(
            Step(
                "Allow usage access",
                "Lets the app read how long each app was open, so the stats can show your total time in each app. Find Reel Counter 4 in the list and switch it on.",
                "Open usage access",
                { SysCheck.usageAccess(this) }
            ) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )
        steps.add(
            Step(
                "Restart prompt",
                "Banks make the counter switch itself off. This helper notices when you open a social app and the counter is off, and shows a notification you tap to switch it back on. It needs usage access (the step above) and the notification step.",
                "Start the restart prompt",
                { Prefs.getWatcherOn(this) && SysCheck.usageAccess(this) }
            ) {
                Prefs.setWatcherOn(this, true)
                if (SysCheck.usageAccess(this)) {
                    WatcherService.start(this)
                } else {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        )
        steps.add(
            Step(
                "Display over other apps",
                "Only needed if the bubble does not show on your phone even though the counter is on. Most phones do not need it.",
                "Open the setting",
                { SysCheck.overlayOn(this) },
                null,
                true
            ) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        )
        steps.add(
            Step(
                "Add the Kids mode tile",
                "When a child grabs the phone, one tap on this tile switches Kids mode on. Swipe down twice from the top of the screen, tap the pencil or plus, find Kids mode, and drag it into your quick panel. Turning it off asks for the parent PIN.",
                "Show me how",
                { marked("guide_tile") },
                "guide_tile",
                true
            ) {
                android.widget.Toast.makeText(this, "Swipe down twice, tap the pencil, drag Kids mode into the panel", android.widget.Toast.LENGTH_LONG).show()
            }
        )

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.BG)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        val pad = Theme.dp(this, 16f)
        page.setPadding(pad, pad, pad, pad)
        scroll.addView(page)

        page.addView(Theme.heading(this, "Let us set things up", 26f), Theme.lp(this, 8f, 2f))
        page.addView(
            Theme.soft(this, "Do the steps in this order. Each one gets a green Done mark once your phone has the access. The next step to do is outlined."),
            Theme.lp(this, 0f, 8f)
        )
        val pr = Theme.heading(this, "", 16f)
        progress = pr
        page.addView(pr, Theme.lp(this, 0f, 12f))

        for (i in steps.indices) {
            val st = steps[i]
            val card = Theme.card(this)
            card.addView(Theme.heading(this, (i + 1).toString() + ". " + st.title + (if (st.optional) " (optional)" else ""), 18f))
            card.addView(Theme.body(this, st.text, 14f), Theme.lp(this, 6f, 0f))
            val status = Theme.soft(this, "", 13f)
            statusViews.add(status)
            card.addView(status, Theme.lp(this, 6f, 0f))
            val b = Theme.softButton(this, st.button)
            b.setOnClickListener { st.action() }
            card.addView(b, Theme.lp(this, 8f, 0f))
            val key = st.markKey
            if (key != null) {
                val tick = Theme.softButton(this, "I have done this")
                tick.setOnClickListener {
                    getSharedPreferences("reels_v2", MODE_PRIVATE).edit().putBoolean(key, true).apply()
                    refresh()
                }
                card.addView(tick, Theme.lp(this, 6f, 0f))
            }
            cards.add(card)
            page.addView(card, Theme.lp(this, 0f, 12f))
        }

        val done = Theme.button(this, "Done")
        done.setOnClickListener {
            Prefs.setGuideDone(this, true)
            finish()
        }
        page.addView(done, Theme.lp(this, 4f, 24f))

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // Marks every step, outlines the next one to do, and updates the progress line.
    private fun refresh() {
        var done = 0
        var required = 0
        var nextFound = false
        val r = Theme.dp(this, 14f).toFloat()
        for (i in steps.indices) {
            val st = steps[i]
            val check = st.check
            val ok = check != null && check()
            if (!st.optional) {
                required++
                if (ok) done++
            }
            val view = statusViews[i]
            if (ok) {
                view.text = "Done"
                view.setTextColor(Theme.GREEN)
                cards[i].background = Theme.rounded(Theme.CARD, r, Theme.GREEN, Theme.dp(this, 2f))
            } else {
                view.text = if (st.optional) "Not needed on most phones" else "Not done yet"
                view.setTextColor(Theme.BRASS)
                if (!nextFound && !st.optional) {
                    nextFound = true
                    view.text = "Next step"
                    cards[i].background = Theme.rounded(Theme.CARD, r, Theme.BRASS, Theme.dp(this, 3f))
                } else {
                    cards[i].background = Theme.rounded(Theme.CARD, r, Theme.DIVIDER, Theme.dp(this, 1f))
                }
            }
        }
        progress?.text = if (done == required) "All $required steps done" else "$done of $required steps done"
        progress?.setTextColor(if (done == required) Theme.GREEN else Theme.BRASS)
    }
}
