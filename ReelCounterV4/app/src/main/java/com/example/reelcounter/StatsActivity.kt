package com.example.reelcounter

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class StatsActivity : Activity() {

    private var mode = 0 // 0 = reels, 1 = time
    private val week = ArrayList<DayData>()
    private lateinit var colors: IntArray
    private lateinit var pie: PieChartView
    private lateinit var bars: StackedBarView
    private lateinit var legend: LinearLayout
    private lateinit var detail: LinearLayout
    private lateinit var periodsBox: LinearLayout
    private lateinit var btnReels: TextView
    private lateinit var btnTime: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        colors = IntArray(Prefs.APPS.size) { Theme.appColor(Prefs.APPS[it]) }

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Theme.BG)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        val pad = Theme.dp(this, 16f)
        page.setPadding(pad, pad, pad, pad)
        scroll.addView(page)

        page.addView(Theme.heading(this, "Your stats", 28f), Theme.lp(this, 8f, 2f))
        page.addView(Theme.soft(this, "Today, and the last 7 days"), Theme.lp(this, 0f, 12f))

        // The Reels | Time switch controls both charts.
        val switchRow = LinearLayout(this)
        switchRow.orientation = LinearLayout.HORIZONTAL
        switchRow.background = Theme.rounded(Theme.CARD, Theme.dp(this, 14f).toFloat(), Theme.BRASS, Theme.dp(this, 1f))
        val sp = Theme.dp(this, 3f)
        switchRow.setPadding(sp, sp, sp, sp)
        btnReels = switchButton("Reels")
        btnTime = switchButton("Time")
        btnReels.setOnClickListener { setMode(0) }
        btnTime.setOnClickListener { setMode(1) }
        switchRow.addView(btnReels)
        switchRow.addView(btnTime)
        page.addView(switchRow, Theme.lp(this, 0f, 14f))

        // Today: ring chart and legend.
        val todayCard = Theme.card(this)
        todayCard.addView(Theme.heading(this, "Today", 18f))
        pie = PieChartView(this)
        val pieLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 230f))
        pieLp.topMargin = Theme.dp(this, 8f)
        todayCard.addView(pie, pieLp)
        legend = LinearLayout(this)
        legend.orientation = LinearLayout.VERTICAL
        todayCard.addView(legend)
        page.addView(todayCard, Theme.lp(this, 0f, 14f))

        // Last 7 days: stacked bars and a details box.
        val weekCard = Theme.card(this)
        weekCard.addView(Theme.heading(this, "Last 7 days", 18f))
        bars = StackedBarView(this)
        val barsLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 240f))
        barsLp.topMargin = Theme.dp(this, 8f)
        weekCard.addView(bars, barsLp)
        detail = LinearLayout(this)
        detail.orientation = LinearLayout.VERTICAL
        detail.setPadding(0, Theme.dp(this, 8f), 0, 0)
        weekCard.addView(detail)
        page.addView(weekCard, Theme.lp(this, 0f, 14f))
        bars.onBarTap = { index -> showDetail(index) }

        // Streak and period summaries are rebuilt every time the screen opens.
        periodsBox = LinearLayout(this)
        periodsBox.orientation = LinearLayout.VERTICAL
        page.addView(periodsBox)

        val export = Theme.button(this, "Share my history (CSV)")
        export.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND)
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_SUBJECT, "Reel Counter history")
            send.putExtra(Intent.EXTRA_TEXT, buildFullCsv())
            startActivity(Intent.createChooser(send, "Share history"))
        }
        page.addView(export, Theme.lp(this, 4f, 24f))

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        loadWeek()
        styleSwitch()
        bars.setData(week, colors, mode)
        bars.clearSelection()
        updateToday()
        showDetail(-1)
        buildPeriods()
        try {
            StatsWidget.refreshAll(this)
        } catch (e: Exception) {
        }
    }

    private fun loadWeek() {
        week.clear()
        for (i in 6 downTo 0) {
            val day = Prefs.dayStringBack(i)
            val reels = IntArray(Prefs.APPS.size) { Prefs.getAppCount(this, Prefs.APPS[it], day) }
            val secs = IntArray(Prefs.APPS.size) { Prefs.getAppSeconds(this, Prefs.APPS[it], day) }
            week.add(DayData(Prefs.dayShortLabel(i), Prefs.dayLongLabel(i), reels, secs))
        }
    }

    // ---- Reels | Time switch ----
    private fun switchButton(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 15f
        t.gravity = Gravity.CENTER
        t.setPadding(0, Theme.dp(this, 10f), 0, Theme.dp(this, 10f))
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        return t
    }

    private fun styleSwitch() {
        val r = Theme.dp(this, 11f).toFloat()
        btnReels.background = Theme.rounded(if (mode == 0) Theme.GREEN else Color.TRANSPARENT, r)
        btnReels.setTextColor(if (mode == 0) Theme.CREAM else Theme.GREEN)
        btnTime.background = Theme.rounded(if (mode == 1) Theme.GREEN else Color.TRANSPARENT, r)
        btnTime.setTextColor(if (mode == 1) Theme.CREAM else Theme.GREEN)
    }

    private fun setMode(m: Int) {
        if (m == mode) return
        mode = m
        styleSwitch()
        updateToday()
        bars.setMode(mode)
    }

    // ---- Today: ring chart and legend ----
    private fun updateToday() {
        if (week.isEmpty()) return
        val today = week[week.size - 1]
        val values = FloatArray(colors.size) {
            if (mode == 0) today.reels[it].toFloat() else today.secs[it].toFloat()
        }
        val big = if (mode == 0) today.totalReels().toString() else Prefs.formatTime(today.totalSecs())
        val small = if (mode == 0) "reels today" else "time today"
        pie.setData(values, colors, big, small)

        legend.removeAllViews()
        val total = values.sum()
        if (total <= 0f) {
            legend.addView(Theme.soft(this, "Nothing counted yet today."), Theme.lp(this, 8f, 0f))
        } else {
            for (i in colors.indices) {
                if (values[i] > 0f) {
                    val percent = (values[i] / total * 100f).toInt()
                    legend.addView(appRow(i, today, percent), Theme.lp(this, 6f, 0f))
                }
            }
        }
    }

    // A row with the app's colour dot, its name and its numbers.
    private fun appRow(i: Int, d: DayData, percent: Int): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val dot = View(this)
        dot.background = Theme.rounded(colors[i], Theme.dp(this, 6f).toFloat())
        val dotLp = LinearLayout.LayoutParams(Theme.dp(this, 12f), Theme.dp(this, 12f))
        dotLp.rightMargin = Theme.dp(this, 10f)
        row.addView(dot, dotLp)
        row.addView(
            Theme.body(this, Prefs.niceName(Prefs.APPS[i])),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        var numbers = d.reels[i].toString() + " reels, " + Prefs.formatTime(d.secs[i])
        if (percent >= 0) numbers += "  (" + percent + "%)"
        row.addView(Theme.soft(this, numbers, 13f))
        return row
    }

    // ---- Tap on a bar: both numbers for each app on that day ----
    private fun showDetail(index: Int) {
        detail.removeAllViews()
        if (index < 0 || index >= week.size) {
            detail.addView(Theme.soft(this, "Tap a bar to see that day in detail."))
            return
        }
        val d = week[index]
        val title = Theme.body(this, d.longLabel + "  -  " + d.totalReels() + " reels, " + Prefs.formatTime(d.totalSecs()))
        title.typeface = Theme.SERIF_BOLD
        title.setTextColor(Theme.GREEN)
        detail.addView(title)
        var any = false
        for (i in colors.indices) {
            if (d.reels[i] > 0 || d.secs[i] > 0) {
                any = true
                detail.addView(appRow(i, d, -1), Theme.lp(this, 6f, 0f))
            }
        }
        if (!any) {
            detail.addView(Theme.soft(this, "No reels counted this day."), Theme.lp(this, 6f, 0f))
        }
    }

    // ---- Streak and totals ----
    private fun buildPeriods() {
        periodsBox.removeAllViews()

        val streakCard = Theme.card(this)
        streakCard.addView(Theme.heading(this, "Streak", 18f))
        val streak = Prefs.currentStreak(this)
        val text = if (streak == 0) {
            "No streak yet. A good day is a day under your first limit."
        } else {
            "$streak good day" + (if (streak == 1) "" else "s") + " in a row"
        }
        streakCard.addView(Theme.body(this, text), Theme.lp(this, 6f, 0f))
        val badge = when {
            streak >= 30 -> "Badge: One month"
            streak >= 14 -> "Badge: Two weeks"
            streak >= 7 -> "Badge: One week"
            streak >= 3 -> "Badge: Getting started"
            else -> ""
        }
        if (badge.isNotEmpty()) {
            val b = Theme.body(this, badge)
            b.typeface = Theme.SERIF_BOLD
            b.setTextColor(Theme.BRASS)
            streakCard.addView(b, Theme.lp(this, 6f, 0f))
        }
        periodsBox.addView(streakCard, Theme.lp(this, 0f, 14f))

        periodsBox.addView(periodCard("Today in numbers", 1), Theme.lp(this, 0f, 14f))
        periodsBox.addView(periodCard("Last 7 days in numbers", 7), Theme.lp(this, 0f, 14f))
        periodsBox.addView(periodCard("Last 30 days in numbers", 30), Theme.lp(this, 0f, 14f))
    }

    private fun periodCard(title: String, days: Int): LinearLayout {
        val card = Theme.card(this)
        card.addView(Theme.heading(this, title, 18f))
        val reels = Prefs.sumCount(this, days)
        addRow(card, "Reels", reels.toString())
        addRow(card, "Time on reels", Prefs.formatTime(Prefs.sumSeconds(this, days)))
        if (days > 1) addRow(card, "Average per day", (reels / days).toString() + " reels")
        for (app in Prefs.APPS) {
            val n = Prefs.sumAppCount(this, app, days)
            val s = Prefs.sumAppSeconds(this, app, days)
            if (n > 0 || s > 0) addRow(card, Prefs.niceName(app), n.toString() + " reels, " + Prefs.formatTime(s))
        }
        for (app in Prefs.LONG_APPS) {
            val n = Prefs.sumLongCount(this, app, days)
            val s = Prefs.sumLongSeconds(this, app, days)
            if (n > 0 || s > 0) addRow(card, Prefs.niceName(app) + " long videos", n.toString() + ", " + Prefs.formatTime(s))
        }
        return card
    }

    private fun addRow(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(
            Theme.body(this, label, 14f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val v = Theme.body(this, value, 14f)
        v.typeface = Theme.SERIF_BOLD
        row.addView(v)
        parent.addView(row, Theme.lp(this, 6f, 0f))
    }

    // ---- CSV text with the full history and the long-video record ----
    private fun buildFullCsv(): String {
        val sb = StringBuilder(Prefs.buildCsv(this))
        sb.append('\n').append("Long videos (not reels)\n")
        sb.append("date,app,videos,seconds\n")
        for (i in 364 downTo 0) {
            val day = Prefs.dayStringBack(i)
            for (app in Prefs.LONG_APPS) {
                val n = Prefs.getLongCount(this, app, day)
                val s = Prefs.getLongSeconds(this, app, day)
                if (n > 0 || s > 0) {
                    sb.append(day).append(',').append(app).append(',').append(n).append(',').append(s).append('\n')
                }
            }
        }
        return sb.toString()
    }
}
