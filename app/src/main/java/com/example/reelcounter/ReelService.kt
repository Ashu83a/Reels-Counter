package com.example.reelcounter

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Locale

class ReelService : AccessibilityService() {

    private companion object {
        const val KIND_NONE = 0
        const val KIND_REEL = 1
        const val POLL_MS = 1500L
        const val CHANNEL = "bank_pause"
        const val NOTE_ID = 1
        const val BUDDY_DP = 60f
        const val PILL_W_DP = 78f
        const val TOP_Y_DP = 22f
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null

    // The bubble: a pill with Brain Buddy standing on its top edge.
    private var bubbleRoot: FrameLayout? = null
    private var pillBg: GradientDrawable? = null
    private var countText: TextView? = null
    private var timerText: TextView? = null
    private var face: BuddyView? = null
    private var overrideExpr = -1
    private var overrideUntil = 0L
    private var shownColor = 0
    private var targetColor = 0
    private var colorAnim: ValueAnimator? = null
    private var glowAnim: ValueAnimator? = null

    // Watching: checks the screen every second and a half, and counts seconds.
    private var watching = false
    private var lastEvalTime = 0L
    private var lastKeep = false
    private var rootMisses = 0
    private var lastSnapshot = 0L
    private val rect = Rect()
    private var screenH = 2000
    private var activeKind = KIND_NONE
    private var activeApp = ""
    private var lastCheckTime = 0L

    // Reel counting
    private var lastSigItems: Set<String> = emptySet()
    private var lastCountTime = 0L

    // Fallback for screens we do not recognise: several full-screen swipes in a row.
    private var fallbackItems: Set<String> = emptySet()
    private var fallbackHits = 0
    private var fallbackFirstTime = 0L
    private var fallbackLastChange = 0L
    private var fallbackUntil = 0L

    private val reelApps = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.barcelona",
        "com.linkedin.android"
    )

    private val igReelIds = listOf("clips_viewer", "clips_video_container", "clips_swipe_refresh")
    private val ytShortIds = listOf("reel_player", "reel_recycler", "reel_watch", "shorts_player")

    private class ScreenInfo {
        val ids = HashSet<String>()
        val selectedIds = HashSet<String>()
        val items = LinkedHashSet<String>()
        var hasPager = false
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!watching) return
            if (evaluate(false)) {
                handler.postDelayed(this, POLL_MS)
            } else {
                stopWatching()
            }
        }
    }

    // Every second on a reel screen adds one second to today's reel time.
    private val secondTicker = object : Runnable {
        override fun run() {
            if (!watching) return
            val fresh = System.currentTimeMillis() - lastCheckTime < 4000L
            if (fresh && activeKind == KIND_REEL) {
                Prefs.addSeconds(this@ReelService, activeApp, 1)
                updateTimerText()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTE_ID)
        } catch (e: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPkg = event.packageName?.toString() ?: return

        // Bank apps refuse to run while an accessibility service is on.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && Prefs.isBankPackage(this, eventPkg)) {
            pauseForBank()
            return
        }

        if (eventPkg !in reelApps && !watching) return
        val keep = evaluate(event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        if (keep) {
            startWatching()
        } else if (watching) {
            stopWatching()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        stopWatching()
        super.onDestroy()
    }

    private fun appKey(pkg: String): String = when (pkg) {
        "com.instagram.android" -> "instagram"
        "com.instagram.barcelona" -> "threads"
        "com.google.android.youtube" -> "youtube"
        "com.linkedin.android" -> "linkedin"
        else -> "facebook"
    }

    // ---- Watching ----
    private fun startWatching() {
        if (watching) return
        watching = true
        handler.postDelayed(pollRunnable, POLL_MS)
        handler.postDelayed(secondTicker, 1000L)
    }

    private fun stopWatching() {
        watching = false
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(secondTicker)
        activeKind = KIND_NONE
        hideBubble()
    }

    private fun finish(keep: Boolean): Boolean {
        lastKeep = keep
        return keep
    }

    // Looks at the screen. Returns true while a supported app is in front.
    private fun evaluate(throttled: Boolean): Boolean {
        val now = System.currentTimeMillis()
        if (throttled && now - lastEvalTime < 400L) return lastKeep
        lastEvalTime = now

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return finish(false)

        var root: AccessibilityNodeInfo? = rootInActiveWindow
        if (root == null) root = findAppWindowRoot()
        if (root == null) {
            rootMisses++
            return finish(rootMisses < 3)
        }
        rootMisses = 0
        val pkg = root.packageName?.toString() ?: return finish(false)
        if (now - lastSnapshot > 600000L) {
            lastSnapshot = now
            UsageHelper.snapshot(this)
        }

        if (Prefs.isBankPackage(this, pkg)) {
            pauseForBank()
            return finish(false)
        }
        if (pkg !in reelApps) return finish(false)

        val app = appKey(pkg)
        screenH = resources.displayMetrics.heightPixels
        val info = ScreenInfo()
        walk(root, info, 0, IntArray(1))

        var kind = KIND_NONE
        if (isReelScreen(pkg, root, info, now)) {
            kind = KIND_REEL
        }
        activeKind = kind
        activeApp = app
        lastCheckTime = now
        Diag.update(pkg, kind, info.ids, info.selectedIds, info.items, info.hasPager, now < fallbackUntil)
        if (kind == KIND_REEL) Prefs.setLastActive(this, now)

        if (kind == KIND_NONE) {
            hideBubble()
            return finish(true)
        }

        // A reel screen: show the bubble and count a new reel when the screen content changes.
        showBubble()
        val items = info.items
        val common = items.count { it in lastSigItems }
        val changed = items.isNotEmpty() && common * 2 < items.size
        if (changed && now - lastCountTime > 1200L) {
            lastSigItems = HashSet(items)
            lastCountTime = now
            val total = Prefs.addReel(this, app)
            Diag.counted = Diag.counted + 1
            onNewReel(total)
        }
        refreshBubble()
        checkBedtime()
        return finish(true)
    }

    // ---- Reading the screen (one pass over the nodes) ----
    private fun walk(node: AccessibilityNodeInfo?, info: ScreenInfo, depth: Int, count: IntArray) {
        if (node == null || depth > 30 || count[0] > 1200) return
        count[0]++
        val id = node.viewIdResourceName
        if (id != null) {
            val shortId = id.substringAfter(":id/")
            if (info.ids.size < 600) info.ids.add(shortId)
            if (node.isSelected) info.selectedIds.add(shortId)
        }
        val cls = node.className?.toString()
        if (cls != null && cls.contains("Pager") && node.isScrollable) info.hasPager = true
        if (info.items.size < 30 && node.isVisibleToUser) {
            node.getBoundsInScreen(rect)
            val mid = (rect.top + rect.bottom) / 2
            if (mid > screenH * 0.12f && mid < screenH * 0.82f) {
                val d = node.contentDescription?.toString()
                if (d != null && usable(d)) info.items.add(d.take(40))
                val t = node.text?.toString()
                if (t != null && usable(t)) info.items.add(t.take(40))
            }
        }
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), info, depth + 1, count)
        }
    }

    // Text that can tell one reel from the next: long enough and containing letters.
    private fun usable(text: String): Boolean = text.length >= 6 && text.any { it.isLetter() }

    // Some phones give no active window; look through all windows for the app instead.
    private fun findAppWindowRoot(): AccessibilityNodeInfo? {
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val r = w.root ?: continue
                val p = r.packageName?.toString() ?: continue
                if (p in reelApps) return r
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun hasAnyId(info: ScreenInfo, parts: List<String>): Boolean {
        for (id in info.ids) {
            for (part in parts) {
                if (id.contains(part)) return true
            }
        }
        return false
    }

    private fun findTextContains(root: AccessibilityNodeInfo, word: String): Boolean {
        val found = root.findAccessibilityNodeInfosByText(word)
        return found != null && found.isNotEmpty()
    }

    // ---- Is this a Reels or Shorts screen? ----
    private fun isReelScreen(pkg: String, root: AccessibilityNodeInfo, info: ScreenInfo, now: Long): Boolean {
        val known = when (pkg) {
            "com.instagram.android" -> hasAnyId(info, igReelIds) || info.selectedIds.contains("clips_tab")
            "com.google.android.youtube" -> hasAnyId(info, ytShortIds)
            "com.facebook.katana", "com.facebook.lite" -> findTextContains(root, "Reels")
            else -> false
        }
        if (known) {
            fallbackUntil = 0L
            fallbackHits = 0
            return true
        }
        if (pkg == "com.google.android.youtube") return false

        // Not recognised. If the whole screen is replaced three times in a row inside
        // a full-screen pager, treat it as a reel screen for the next 20 seconds.
        if (info.hasPager && info.items.isNotEmpty()) {
            val replaced = fallbackItems.isNotEmpty() && info.items.none { it in fallbackItems }
            if (replaced && now - fallbackLastChange > 1200L) {
                fallbackLastChange = now
                if (now - fallbackFirstTime > 25000L) {
                    fallbackFirstTime = now
                    fallbackHits = 1
                } else {
                    fallbackHits++
                }
                if (fallbackHits >= 3) fallbackUntil = now + 20000L
            }
            if (fallbackItems.isEmpty() || replaced) fallbackItems = HashSet(info.items)
        } else {
            fallbackHits = 0
        }
        return now < fallbackUntil
    }

    // ---- What happens on each new reel ----
    private fun onNewReel(total: Int) {
        val l1 = Prefs.getLimit(this, 1)
        val l2 = Prefs.getLimit(this, 2)
        val l3 = Prefs.getLimit(this, 3)
        val l4 = Prefs.getLimit(this, 4)

        // Buzz and show your own message exactly once when a limit is reached.
        var limitBuzz = false
        when (total) {
            l1 -> { vibrate(TenthVibration.getLimitVibe(this, 1).toLong()); showMessage(1); limitBuzz = true }
            l2 -> { vibrate(TenthVibration.getLimitVibe(this, 2).toLong()); showMessage(2); limitBuzz = true }
            l3 -> { vibrate(TenthVibration.getLimitVibe(this, 3).toLong()); showMessage(3); limitBuzz = true }
        }

        // Your own buzz on every interval reel, unless a limit buzz just played.
        val interval = TenthVibration.getInterval(this)
        val intervalBuzz = TenthVibration.getIntervalVibe(this)
        if (!limitBuzz && total % interval == 0 && intervalBuzz > 0) {
            vibrate(intervalBuzz.toLong())
        }

        // Milestones: Brain Buddy hops and the pill glows. The size never changes.
        if (total % 50 == 0 || total % interval == 0 || limitBuzz) {
            if (total % 50 == 0) {
                showMoment(BuddyView.EXCITED, 2500L)
            } else if (!limitBuzz) {
                showMoment(BuddyView.ENCOURAGING, 1800L)
            }
            celebrate()
        }

        try {
            StatsWidget.refreshAll(this)
        } catch (e: Exception) {
        }

        // The lock, unless paused or inside a relaxed window.
        if (total >= l4 && !Prefs.isPaused(this) && !Prefs.isRelaxedNow(this)) {
            val i = Intent(this, LockActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(i)
        }
    }

    private fun showMessage(n: Int) {
        val text = Prefs.getMessage(this, n)
        if (text.isNotEmpty()) {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }

    private fun vibrate(millis: Long) {
        if (millis <= 0L) return
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(millis)
            }
        } catch (e: Exception) {
        }
    }

    // ---- Bedtime mode ----
    private fun checkBedtime() {
        if (!Prefs.getBedtimeEnabled(this)) return
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val t1 = Prefs.getBedtime1(this)
        val t2 = Prefs.getBedtime2(this)

        // Only act in the night window, from the first reminder until 5 in the morning.
        val inNight = minutes >= t1 || minutes < 5 * 60
        if (!inNight) return

        if (minutes >= t2 && !Prefs.bedReminderShownToday(this, 2)) {
            Prefs.markBedReminderShown(this, 2)
            Prefs.markBedReminderShown(this, 1)
            Toast.makeText(this, Prefs.getBedMessage(this, 2), Toast.LENGTH_LONG).show()
        } else if (minutes >= t1 && minutes < t2 && !Prefs.bedReminderShownToday(this, 1)) {
            Prefs.markBedReminderShown(this, 1)
            Toast.makeText(this, Prefs.getBedMessage(this, 1), Toast.LENGTH_LONG).show()
        }

        // After the first reminder, the face hops and the pill glows for 5 seconds every 15 minutes.
        val now = System.currentTimeMillis()
        if (minutes >= t1 && now - Prefs.getLastBigBubble(this) > 15 * 60 * 1000L) {
            Prefs.setLastBigBubble(this, now)
            showMoment(BuddyView.SLEEPY, 5000L)
            for (i in 0 until 5) {
                handler.postDelayed({ celebrate() }, i * 1000L)
            }
        }
    }

    // ---- Bank apps ----
    private fun pauseForBank() {
        stopWatching()
        Toast.makeText(this, "Reel Counter is off while your bank app is open", Toast.LENGTH_LONG).show()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Bank pause", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val open = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            val pending = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val note = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Reel Counter is off")
                .setContentText("Switched off for your bank app. Tap to turn it back on when you are done.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTE_ID, note)
        } catch (e: Exception) {
        }
        try {
            disableSelf()
        } catch (e: Exception) {
        }
    }

    // ---- The bubble ----
    private fun colorFor(total: Int): Int {
        return when {
            total >= Prefs.getLimit(this, 4) -> Color.rgb(136, 14, 79)
            total >= Prefs.getLimit(this, 3) -> Color.rgb(211, 47, 47)
            total >= Prefs.getLimit(this, 2) -> Color.rgb(245, 124, 0)
            total >= Prefs.getLimit(this, 1) -> Color.rgb(249, 168, 37)
            else -> Color.rgb(51, 51, 51)
        }
    }

    // Which Brain Buddy picture to show: the limit decides, a short moment can override it.
    private fun expressionFor(total: Int): Int {
        val now = System.currentTimeMillis()
        if (overrideExpr >= 0 && now < overrideUntil) return overrideExpr
        if (total >= Prefs.getLimit(this, 4)) return BuddyView.SAD
        if (total >= Prefs.getLimit(this, 3)) return BuddyView.WORRIED
        if (total >= Prefs.getLimit(this, 2)) return BuddyView.CONCERNED
        if (total >= Prefs.getLimit(this, 1)) return BuddyView.CURIOUS
        if (Prefs.isPaused(this) || Prefs.isRelaxedNow(this)) return BuddyView.RELAXED
        return BuddyView.HAPPY
    }

    // Show a different picture for a few seconds, then go back to the limit's picture.
    private fun showMoment(expr: Int, millis: Long) {
        overrideExpr = expr
        overrideUntil = System.currentTimeMillis() + millis
        face?.setExpression(expr)
        handler.postDelayed({
            if (System.currentTimeMillis() >= overrideUntil) {
                overrideExpr = -1
                face?.setExpression(expressionFor(Prefs.getCount(this)))
            }
        }, millis + 100L)
    }

    private fun showBubble() {
        if (bubbleRoot != null) return
        val buddySize = Theme.dp(this, BUDDY_DP)
        // The character's feet stand on the pill edge, so only a little of it overlaps the pill.
        val overlap = (buddySize * 0.16f).toInt()

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = Theme.dp(this, 22f).toFloat()

        val count = TextView(this)
        count.setTextColor(Color.WHITE)
        count.textSize = 16f
        count.typeface = Theme.SERIF_BOLD
        count.gravity = Gravity.CENTER
        count.includeFontPadding = false

        val timer = TextView(this)
        timer.setTextColor(Color.argb(215, 255, 255, 255))
        timer.textSize = 8f
        timer.gravity = Gravity.CENTER
        timer.includeFontPadding = false

        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.VERTICAL
        pill.gravity = Gravity.CENTER_HORIZONTAL
        pill.background = bg
        pill.minimumWidth = Theme.dp(this, PILL_W_DP)
        pill.setPadding(
            Theme.dp(this, 10f), Theme.dp(this, 9f),
            Theme.dp(this, 10f), Theme.dp(this, 6f)
        )
        pill.addView(count)
        pill.addView(timer)

        val root = FrameLayout(this)
        root.clipChildren = false

        val pillLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pillLp.topMargin = buddySize - overlap
        pillLp.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(pill, pillLp)

        val faceView = BuddyView(this, buddySize)
        val faceLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        faceLp.topMargin = 0
        faceLp.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(faceView, faceLp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = Theme.dp(this, TOP_Y_DP)
        if (Build.VERSION.SDK_INT >= 30) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try {
            windowManager?.addView(root, params)
            bubbleRoot = root
            pillBg = bg
            countText = count
            timerText = timer
            face = faceView
            shownColor = 0
            targetColor = 0
            refreshBubble()
        } catch (e: Exception) {
            bubbleRoot = null
        }
    }

    private fun refreshBubble() {
        if (bubbleRoot == null) return
        val total = Prefs.getCount(this)
        countText?.text = total.toString()
        updateTimerText()
        face?.setExpression(expressionFor(total))
        fadeColorTo(colorFor(total))
        // Transparency setting: 0 is solid, 80 is very faint.
        val alpha = ((100 - Prefs.getTransparency(this)) * 255) / 100
        pillBg?.alpha = alpha.coerceIn(40, 255)
    }

    // The pill colour slides smoothly from one shade to the next.
    private fun fadeColorTo(target: Int) {
        if (target == targetColor) return
        targetColor = target
        colorAnim?.cancel()
        if (shownColor == 0) {
            shownColor = target
            pillBg?.setColor(target)
            return
        }
        val a = ValueAnimator.ofArgb(shownColor, target)
        a.duration = 1000L
        a.addUpdateListener {
            shownColor = it.animatedValue as Int
            pillBg?.setColor(shownColor)
        }
        a.start()
        colorAnim = a
    }

    private fun updateTimerText() {
        val s = Prefs.getSeconds(this)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        timerText?.text = if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.US, "%d:%02d", m, sec)
        }
    }

    // A happy hop and a soft glow around the pill.
    private fun celebrate() {
        face?.celebrate()
        glowAnim?.cancel()
        val a = ValueAnimator.ofFloat(0f, 1f, 0f)
        a.duration = 900L
        a.addUpdateListener {
            val f = it.animatedValue as Float
            pillBg?.setStroke(Theme.dp(this, 2f), Color.argb((f * 220).toInt(), 255, 255, 255))
        }
        a.start()
        glowAnim = a
    }

    private fun hideBubble() {
        colorAnim?.cancel()
        glowAnim?.cancel()
        colorAnim = null
        glowAnim = null
        face?.stop()
        bubbleRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        bubbleRoot = null
        pillBg = null
        countText = null
        timerText = null
        face = null
        shownColor = 0
        targetColor = 0
    }
}
