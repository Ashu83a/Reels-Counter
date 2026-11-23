package com.example.reelcounter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// The first screen: today's count, a jar that fills toward your limit, and Brain Buddy
// standing on top of the pile with the face your day has earned.
class TodayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.firstDay(this)
        if (!Prefs.getGuideDone(this)) {
            startActivity(Intent(this, SetupGuideActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        WatcherService.apply(this)
        build()
    }

    private fun build() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.BG)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        val pad = Theme.dp(this, 16f)
        page.setPadding(pad, pad, pad, pad)
        scroll.addView(page)

        // Title and streak.
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Theme.heading(this, "Today", 28f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val streak = Prefs.currentStreak(this)
        val chip = TextView(this)
        chip.text = if (streak == 0) "No streak yet" else "$streak day" + (if (streak == 1) "" else "s") + " in a row"
        chip.textSize = 13f
        chip.typeface = Theme.SERIF_BOLD
        chip.setTextColor(Theme.GREEN)
        val cp = Theme.dp(this, 12f)
        chip.setPadding(cp, Theme.dp(this, 6f), cp, Theme.dp(this, 6f))
        chip.background = Theme.rounded(Theme.CARD, Theme.dp(this, 16f).toFloat(), Theme.BRASS, Theme.dp(this, 1f))
        top.addView(chip)
        page.addView(top, Theme.lp(this, 8f, 10f))

        // Auto Kids mode is on: how to get back to adult mode.
        val auto = Kids.autoLevel(this)
        if (auto > 0) {
            val card = Theme.card(this)
            card.addView(Theme.heading(this, if (auto == 1) "Auto Kids mode is helping gently" else "Auto Kids mode is on", 16f))
            card.addView(Theme.body(this, "Turning the screen off ends it. Or hold the button for three seconds.", 14f), Theme.lp(this, 2f, 8f))
            val adult = Theme.softButton(this, "I am an adult (hold 3 seconds)")
            val h = android.os.Handler(android.os.Looper.getMainLooper())
            val exit = Runnable {
                AutoKids.exit(this, System.currentTimeMillis())
                build()
            }
            adult.setOnTouchListener { _, e ->
                if (e.action == android.view.MotionEvent.ACTION_DOWN) {
                    h.postDelayed(exit, 3000L)
                } else if (e.action == android.view.MotionEvent.ACTION_UP || e.action == android.view.MotionEvent.ACTION_CANCEL) {
                    h.removeCallbacks(exit)
                }
                true
            }
            card.addView(adult)
            if (Kids.hasPin(this)) {
                val pin = Theme.softButton(this, "Use the parent PIN instead")
                pin.setOnClickListener {
                    Kids.askPin(this, "Parent PIN") {
                        AutoKids.exit(this, System.currentTimeMillis())
                        build()
                    }
                }
                card.addView(pin, Theme.lp(this, 6f, 0f))
            }
            page.addView(card, Theme.lp(this, 0f, 12f))
        }

        // Setup not finished: a small banner that continues it.
        val setupDone = SysCheck.setupDone(this)
        if (setupDone < SysCheck.SETUP_TOTAL) {
            val banner = Theme.card(this)
            banner.addView(Theme.heading(this, "Finish setting up", 16f))
            banner.addView(Theme.body(this, "$setupDone of ${SysCheck.SETUP_TOTAL} steps done. The counter needs the rest to work well.", 14f), Theme.lp(this, 2f, 8f))
            val go = Theme.softButton(this, "Continue setup")
            go.setOnClickListener { startActivity(Intent(this, SetupGuideActivity::class.java)) }
            banner.addView(go)
            page.addView(banner, Theme.lp(this, 0f, 12f))
        }

        // Yesterday's goal, celebrated once.
        val today = Prefs.dayString()
        if (Prefs.getGoalOn(this) && streak >= 1 &&
            Prefs.getCelebratedDay(this) != today && Prefs.dayWasGood(this, Prefs.dayStringBack(1))
        ) {
            Prefs.setCelebratedDay(this, today)
            val card = Theme.card(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            val trophy = BuddyView(this, Theme.dp(this, 90f))
            trophy.setExpression(BuddyView.TROPHY)
            card.addView(trophy)
            val words = LinearLayout(this)
            words.orientation = LinearLayout.VERTICAL
            words.addView(Theme.heading(this, "Well done", 18f))
            words.addView(
                Theme.body(this, "You stayed under your goal yesterday. $streak day" + (if (streak == 1) "" else "s") + " in a row.", 14f),
                Theme.lp(this, 2f, 0f)
            )
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.leftMargin = Theme.dp(this, 10f)
            card.addView(words, lp)
            page.addView(card, Theme.lp(this, 0f, 12f))
        }

        // The big number.
        val count = Prefs.getCount(this)
        val big = TextView(this)
        big.text = count.toString()
        big.textSize = 68f
        big.typeface = Theme.SERIF_BOLD
        big.setTextColor(Theme.GREEN)
        big.gravity = Gravity.CENTER
        page.addView(big, Theme.lp(this, 4f, 0f))
        page.addView(centered(Theme.soft(this, "reels today", 15f)), Theme.lp(this, 0f, 2f))
        page.addView(centered(Theme.body(this, Prefs.formatTime(Prefs.getSeconds(this)) + " on reels", 14f)), Theme.lp(this, 0f, 10f))

        // The jar with Brain Buddy on the pile.
        val l1 = Prefs.getLimit(this, 1)
        val l2 = Prefs.getLimit(this, 2)
        val l3 = Prefs.getLimit(this, 3)
        val l4 = Prefs.getLimit(this, 4)
        val jarBox = FrameLayout(this)
        jarBox.clipChildren = false
        val jar = JarView(this)
        jar.setData(count, l1, l2, l3, l4)
        jarBox.addView(jar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (Prefs.getBuddyOn(this)) {
            val size = Theme.dp(this, 64f)
            val buddy = BuddyView(this, size, true)
            buddy.setExpression(Damage.stage(this))
            // The holder is moved to the pile; Brain Buddy keeps his own hop and breathing inside it.
            val holder = FrameLayout(this)
            holder.addView(buddy, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            jarBox.addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            jar.onPile = { px, py, spacing ->
                val x = (px - size / 2f).coerceIn(0f, (jar.width - size).toFloat().coerceAtLeast(0f))
                val y = (py - size * 0.86f).coerceAtLeast(-size * 0.5f)
                holder.translationX = x
                holder.translationY = y + spacing * 0.2f
            }
        }
        page.addView(jarBox, Theme.lp(this, 36f, 6f))

        val left = l4 - count
        val leftText = if (left > 0) "$left left before the lock" else "${-left} past the lock limit"
        page.addView(centered(Theme.heading(this, leftText, 18f)), Theme.lp(this, 4f, 14f))

        // Daily goal.
        if (Prefs.getGoalOn(this)) {
            val goal = Prefs.getGoal(this)
            val card = Theme.card(this)
            card.addView(Theme.heading(this, "Daily goal", 16f))
            val under = count <= goal
            card.addView(
                Theme.body(this, if (under) "$count of $goal reels. Stay under it and your streak grows." else "$count of $goal reels. Over the goal for today.", 14f),
                Theme.lp(this, 4f, 8f)
            )
            val track = LinearLayout(this)
            track.orientation = LinearLayout.HORIZONTAL
            track.background = Theme.rounded(Theme.DIVIDER, Theme.dp(this, 5f).toFloat())
            val fillWeight = (count.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
            val fill = View(this)
            fill.background = Theme.rounded(if (under) Theme.GREEN else Theme.BURGUNDY, Theme.dp(this, 5f).toFloat())
            track.addView(fill, LinearLayout.LayoutParams(0, Theme.dp(this, 10f), fillWeight.coerceAtLeast(0.001f)))
            track.addView(View(this), LinearLayout.LayoutParams(0, Theme.dp(this, 10f), (1f - fillWeight).coerceAtLeast(0.001f)))
            card.addView(track, Theme.lp(this, 0f, 0f))
            page.addView(card, Theme.lp(this, 0f, 14f))
        }

        // Where to go next.
        val stats = Theme.button(this, "See my stats")
        stats.setOnClickListener { startActivity(Intent(this, StatsActivity::class.java)) }
        page.addView(stats, Theme.lp(this, 0f, 8f))
        val settings = Theme.softButton(this, "Settings")
        settings.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        page.addView(settings, Theme.lp(this, 0f, 24f))

        setContentView(scroll)
    }

    private fun centered(t: TextView): TextView {
        t.gravity = Gravity.CENTER
        return t
    }
}
