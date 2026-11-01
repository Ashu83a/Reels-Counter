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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatsActivity : Activity() {

    private var range = 1 // 0 day, 1 week, 2 month, 3 year
    private var mode = 0 // 0 reels, 1 time
    private val periods = ArrayList<DayData>()
    private lateinit var colors: IntArray
    private lateinit var pie: PieChartView
    private lateinit var bars: StackedBarView
    private lateinit var legend: LinearLayout
    private lateinit var detail: LinearLayout
    private lateinit var periodsBox: LinearLayout
    private lateinit var todayCard: LinearLayout
    private lateinit var barCard: LinearLayout
    private lateinit var barTitle: TextView
    private val rangeButtons = ArrayList<TextView>()
    private val modeButtons = ArrayList<TextView>()

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
        page.addView(Theme.soft(this, "Pick a period, then flip between reels and time"), Theme.lp(this, 0f, 12f))

        page.addView(segment(listOf("Day", "Week", "Month", "Year"), rangeButtons) { setRange(it) }, Theme.lp(this, 0f, 10f))
        page.addView(segment(listOf("Reels", "Time"), modeButtons) { setMode(it) }, Theme.lp(this, 0f, 14f))

        // Day: ring chart and legend.
        todayCard = Theme.card(this)
        todayCard.addView(Theme.heading(this, "Today", 18f))
        pie = PieChartView(this)
        val pieLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 230f))
        pieLp.topMargin = Theme.dp(this, 8f)
        todayCard.addView(pie, pieLp)
        legend = LinearLayout(this)
        legend.orientation = LinearLayout.VERTICAL
        todayCard.addView(legend)
        page.addView(todayCard, Theme.lp(this, 0f, 14f))

        // Week, month and year: stacked bars and a details box.
        barCard = Theme.card(this)
        barTitle = Theme.heading(this, "Last 7 days", 18f)
        barCard.addView(barTitle)
        bars = StackedBarView(this)
        val barsLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 250f))
        barsLp.topMargin = Theme.dp(this, 8f)
        barCard.addView(bars, barsLp)
        detail = LinearLayout(this)
        detail.orientation = LinearLayout.VERTICAL
        detail.setPadding(0, Theme.dp(this, 8f), 0, 0)
        barCard.addView(detail)
        page.addView(barCard, Theme.lp(this, 0f, 14f))
        bars.onBarTap = { index -> showDetail(index) }

        periodsBox = LinearLayout(this)
        periodsBox.orientation = LinearLayout.VERTICAL
        page.addView(periodsBox)

        val export = Theme.button(this, "Share my history (CSV)")
        export.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND)
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_SUBJECT, "Reel Counter history")
            send.putExtra(Intent.EXTRA_TEXT, Prefs.buildCsv(this))
            startActivity(Intent.createChooser(send, "Share history"))
        }
        page.addView(export, Theme.lp(this, 4f, 24f))

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        UsageHelper.snapshot(this)
        refreshAll()
        buildPeriods()
        try {
            StatsWidget.refreshAll(this)
        } catch (e: Exception) {
        }
    }

    // ---- The two switches ----
    private fun segment(labels: List<String>, buttons: ArrayList<TextView>, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.background = Theme.rounded(Theme.CARD, Theme.dp(this, 14f).toFloat(), Theme.BRASS, Theme.dp(this, 1f))
        val sp = Theme.dp(this, 3f)
        row.setPadding(sp, sp, sp, sp)
        for (i in labels.indices) {
            val t = TextView(this)
            t.text = labels[i]
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setPadding(0, Theme.dp(this, 9f), 0, Theme.dp(this, 9f))
            t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            t.setOnClickListener { onPick(i) }
            buttons.add(t)
            row.addView(t)
        }
        return row
    }

    private fun styleSegments() {
        val r = Theme.dp(this, 11f).toFloat()
        for (i in rangeButtons.indices) {
            val on = i == range
            rangeButtons[i].background = Theme.rounded(if (on) Theme.GREEN else Color.TRANSPARENT, r)
            rangeButtons[i].setTextColor(if (on) Theme.CREAM else Theme.GREEN)
        }
        for (i in modeButtons.indices) {
            val on = i == mode
            modeButtons[i].background = Theme.rounded(if (on) Theme.GREEN else Color.TRANSPARENT, r)
            modeButtons[i].setTextColor(if (on) Theme.CREAM else Theme.GREEN)
        }
    }

    private fun setRange(r: Int) {
        if (r == range) return
        range = r
        refreshAll()
    }

    private fun setMode(m: Int) {
        if (m == mode) return
        mode = m
        styleSegments()
        if (range == 0) {
            updateToday()
        } else {
            bars.setMode(mode)
            showDetail(-1)
        }
    }

    // ---- Loading the numbers ----
    private fun refreshAll() {
        styleSegments()
        loadPeriods()
        if (range == 0) {
            todayCard.visibility = View.VISIBLE
            barCard.visibility = View.GONE
            updateToday()
        } else {
            todayCard.visibility = View.GONE
            barCard.visibility = View.VISIBLE
            barTitle.text = when (range) {
                1 -> "Last 7 days"
                2 -> "Last 30 days"
                else -> "Last 12 months"
            }
            bars.setData(periods, colors, mode)
            showDetail(-1)
        }
    }

    private fun loadPeriods() {
        periods.clear()
        when (range) {
            0 -> periods.addAll(buildDays(1))
            1 -> periods.addAll(buildDays(7))
            2 -> periods.addAll(buildDays(30))
            else -> periods.addAll(buildMonths())
        }
    }

    private fun buildDays(n: Int): List<DayData> {
        val na = Prefs.APPS.size
        val list = ArrayList<DayData>()
        for (i in n - 1 downTo 0) {
            val day = Prefs.dayStringBack(i)
            val reels = IntArray(na) { Prefs.getAppCount(this, Prefs.APPS[it], day) }
            val secs = IntArray(na) { Prefs.getAppSeconds(this, Prefs.APPS[it], day) }
            val usage = IntArray(na) { Prefs.getUsageSeconds(this, Prefs.APPS[it], day) }
            val label = if (n > 8) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -i)
                cal.get(Calendar.DAY_OF_MONTH).toString()
            } else {
                Prefs.dayShortLabel(i)
            }
            list.add(DayData(label, Prefs.dayLongLabel(i), reels, secs, usage))
        }
        return list
    }

    private fun buildMonths(): List<DayData> {
        val na = Prefs.APPS.size
        val list = ArrayList<DayData>()
        val today = Calendar.getInstance()
        for (m in 11 downTo 0) {
            val first = Calendar.getInstance()
            first.set(Calendar.DAY_OF_MONTH, 1)
            first.add(Calendar.MONTH, -m)
            val reels = IntArray(na)
            val secs = IntArray(na)
            val usage = IntArray(na)
            val lastDay = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (d in 1..lastDay) {
                val c2 = first.clone() as Calendar
                c2.set(Calendar.DAY_OF_MONTH, d)
                if (c2.after(today)) break
                val ds = Prefs.dayString(c2)
                for (a in 0 until na) {
                    reels[a] += Prefs.getAppCount(this, Prefs.APPS[a], ds)
                    secs[a] += Prefs.getAppSeconds(this, Prefs.APPS[a], ds)
                    usage[a] += Prefs.getUsageSeconds(this, Prefs.APPS[a], ds)
                }
            }
            val short = SimpleDateFormat("MMM", Locale.getDefault()).format(first.time)
            val long = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(first.time)
            list.add(DayData(short, long, reels, secs, usage))
        }
        return list
    }

    // ---- Day view: ring chart and legend ----
    private fun updateToday() {
        if (periods.isEmpty()) return
        val today = periods[0]
        val values = FloatArray(colors.size) {
            if (mode == 0) today.reels[it].toFloat() else today.appSecs(it).toFloat()
        }
        val big = if (mode == 0) today.totalReels().toString() else Prefs.formatTime(today.totalAppSecs())
        val small = if (mode == 0) "reels today" else "time in apps today"
        pie.setData(values, colors, big, small)

        legend.removeAllViews()
        val total = values.sum()
        if (total <= 0f) {
            legend.addView(Theme.soft(this, "Nothing counted yet today."), Theme.lp(this, 8f, 0f))
        } else {
            for (i in colors.indices) {
                if (values[i] > 0f) {
                    legend.addView(appRow(i, today), Theme.lp(this, 6f, 0f))
                }
            }
        }
    }

    // A row with the app's colour dot, its name and its numbers.
    private fun appRow(i: Int, d: DayData): LinearLayout {
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
        val numbers = if (mode == 1 && d.usage[i] > 0) {
            Prefs.formatTime(d.appSecs(i)) + " in app, " + Prefs.formatTime(d.secs[i]) + " on reels"
        } else {
            d.reels[i].toString() + " reels, " + Prefs.formatTime(d.secs[i]) + " on reels"
        }
        val v = Theme.soft(this, numbers, 13f)
        v.gravity = Gravity.END
        row.addView(v)
        return row
    }

    // ---- Tap on a bar: both numbers for each app ----
    private fun showDetail(index: Int) {
        detail.removeAllViews()
        if (index < 0 || index >= periods.size) {
            detail.addView(Theme.soft(this, "Tap a bar to see it in detail."))
            return
        }
        val d = periods[index]
        val title = Theme.body(
            this,
            d.longLabel + "  -  " + d.totalReels() + " reels, " + Prefs.formatTime(d.totalAppSecs()) + " in apps"
        )
        title.typeface = Theme.SERIF_BOLD
        title.setTextColor(Theme.GREEN)
        detail.addView(title)
        var any = false
        for (i in colors.indices) {
            if (d.reels[i] > 0 || d.secs[i] > 0 || d.usage[i] > 0) {
                any = true
                detail.addView(appRow(i, d), Theme.lp(this, 6f, 0f))
            }
        }
        if (!any) {
            detail.addView(Theme.soft(this, "Nothing counted in this period."), Theme.lp(this, 6f, 0f))
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
            val u = Prefs.sumAppUsage(this, app, days)
            if (n > 0 || s > 0 || u > 0) {
                var line = n.toString() + " reels, " + Prefs.formatTime(s) + " on reels"
                if (u > 0) line = Prefs.formatTime(if (u > s) u else s) + " in app, " + line
                addRow(card, Prefs.niceName(app), line)
            }
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
        v.gravity = Gravity.END
        row.addView(v)
        parent.addView(row, Theme.lp(this, 6f, 0f))
    }
}
