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
        val action: () -> Unit
    )

    private val steps = ArrayList<Step>()
    private val statusViews = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        steps.add(
            Step(
                "Allow restricted settings",
                "Android blocks Accessibility for apps installed from a file. Open the app info page, tap the three dots at the top right, then tap Allow restricted settings. If you do not see the three dots, skip this step.",
                "Open app info",
                null
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
                "Display over other apps",
                "Only needed if the bubble does not show on your phone even though the counter is on. Most phones do not need it.",
                "Open the setting",
                { SysCheck.overlayOn(this) }
            ) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
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
            Theme.soft(this, "A few steps so the counter works well on your phone. Tap each button, do the step, then come back here."),
            Theme.lp(this, 0f, 14f)
        )

        for (i in steps.indices) {
            val st = steps[i]
            val card = Theme.card(this)
            card.addView(Theme.heading(this, (i + 1).toString() + ". " + st.title, 18f))
            card.addView(Theme.body(this, st.text, 14f), Theme.lp(this, 6f, 0f))
            val status = Theme.soft(this, "", 13f)
            statusViews.add(status)
            card.addView(status, Theme.lp(this, 6f, 0f))
            val b = Theme.softButton(this, st.button)
            b.setOnClickListener { st.action() }
            card.addView(b, Theme.lp(this, 8f, 0f))
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
        for (i in steps.indices) {
            val check = steps[i].check
            val view = statusViews[i]
            if (check == null) {
                view.text = ""
            } else if (check()) {
                view.text = "Done"
                view.setTextColor(Theme.GREEN)
            } else {
                view.text = "Not done yet"
                view.setTextColor(Theme.BRASS)
            }
        }
    }
}
