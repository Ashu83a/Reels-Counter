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
        const val BUDDY_DP = 30f      // Brain Buddy standing on the pill (half of the old size)
        const val BUDDY_IN_DP = 28f   // Brain Buddy inside the pill, on the left
        const val PILL_W_DP = 58f     // three quarters of the old pill
        const val TOP_Y_DP = 22f
        const val GOODBYE_MS = 1800L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null

    // The bubble: a pill with Brain Buddy standing on its top edge.
    private var bubbleRoot: FrameLayout? = null
    private var pillBg: GradientDrawable? = null
    private var countText: TextView? = null
    private var timerText: TextView? = null
    private var face: BuddyView? = null
    private var cardRoot: android.view.View? = null
    private val cardRemove = Runnable { removeCard() }
    private var lastReelAppEvent = 0L
    private var lastCardTime = 0L
    private var lastSchedCheck = 0L

    // Kids mode
    private var holdRoot: android.widget.FrameLayout? = null
    private var holdHint: android.view.View? = null
    private val holdRemove = Runnable { removeHold() }
    private val hintHide = Runnable { holdHint?.visibility = android.view.View.GONE }
    private var lastKidsReel = 0L
    private var lastKidsStop = 0L
    private var kidsWarned = false

    // Auto Kids mode signals
    private val swipeTimes = ArrayList<Long>()
    private val tapTimes = ArrayList<Long>()
    private var lastWordScan = 0L
    private var motion: MotionWatch? = null
    private var shakyWindows = 0
    private var stillSeconds = 0

    // Auto Kids mode
    private var offAt = 0L
    private var receiverOn = false
    private var autoRoot: android.view.View? = null
    private val autoRemove = Runnable { removeAutoCard() }
    private val holdAdult = Runnable { adultNow() }
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val now = System.currentTimeMillis()
            val level = Kids.autoLevel(this@ReelService)
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                offAt = now
                if (level > 0 && AutoKids.endAfterMin(this@ReelService) == 0) autoEnded()
            } else if (intent?.action == Intent.ACTION_SCREEN_ON) {
                // Back on after the chosen time off: an adult has probably taken over.
                if (level > 0 && offAt > 0L && now - offAt >= AutoKids.endAfterMin(this@ReelService) * 60000L) autoEnded()
            }
        }
    }

    // Which app is in front, for games and the Play Store in Kids mode.
    private var fgPkg = ""
    private var fgKind = 0 // 0 other, 1 a game that counts, 2 the Play Store
    private var gameWarned = false
    private val fgTicker = object : Runnable {
        override fun run() {
            fgTick()
            if (fgKind != 0) handler.postDelayed(this, 1000L)
        }
    }
    private var quietWarnedDay = ""
    private var nudgeIdx = 0
    private val kidsNudges = listOf(
        Pair(BuddyView.LOVE, "Let's play with your toys!"),
        Pair(BuddyView.RECOVERED, "Let's go outside and play!"),
        Pair(BuddyView.CURIOUS, "Can you draw something?"),
        Pair(BuddyView.ENCOURAGE, "Let's give someone a big hug!"),
        Pair(BuddyView.CONFUSED, "Let's have some water and stretch!")
    )
    private var leaving = false
    private val removeRunnable = Runnable { removeBubbleNow() }
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
                kidsTick(System.currentTimeMillis())
                updateTimerText()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        registerScreenReceiver()
        registerKidsButton()
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

        // Quick repeated taps on buttons are a sign of a small child. Clicks are read for nothing else.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            if (AutoKids.enabled(this)) trackTap(System.currentTimeMillis())
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) updateForeground(eventPkg)

        if (eventPkg in reelApps) {
            maybeShowCard(System.currentTimeMillis())
            maybeScanWords(System.currentTimeMillis())
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
        removeBubbleNow()
        removeCard()
        removeHold()
        removeAutoCard()
        stopMotion()
        handler.removeCallbacks(fgTicker)
        if (receiverOn) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
            }
            receiverOn = false
        }
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
        startMotion()
        if (watching) return
        watching = true
        handler.postDelayed(pollRunnable, POLL_MS)
        handler.postDelayed(secondTicker, 1000L)
    }

    private fun stopWatching() {
        stopMotion()
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
        if (Kids.on(this) && Kids.appOn(this, app) && Kids.blocked(this, now)) openKidsStop()
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
        checkSchedules(now)
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
        Damage.addReel(this)
        trackSwipe(System.currentTimeMillis())
        if (Kids.on(this) && Kids.appOn(this, activeApp)) kidsNewReel(System.currentTimeMillis())
        val l1 = Prefs.getLimit(this, 1)
        val l2 = Prefs.getLimit(this, 2)
        val l3 = Prefs.getLimit(this, 3)
        val l4 = Prefs.getLimit(this, 4)

        // Buzz and show your own message exactly once when a limit is reached.
        var limitBuzz = false
        when (total) {
            l1 -> { vibrate(TenthVibration.getLimitVibe(this, 1).toLong()); showMessage(1); limitBuzz = true }
            l2 -> { vibrate(TenthVibration.getLimitVibe(this, 2).toLong()); showMessage(2); limitBuzz = true }
            l3 -> {
                vibrate(TenthVibration.getLimitVibe(this, 3).toLong())
                showMessage(3)
                limitBuzz = true
                if (Prefs.getBreakMode(this) != 0) openBreak()
            }
        }

        // Your own buzz on every interval reel, unless a limit buzz just played.
        val interval = TenthVibration.getInterval(this)
        val intervalBuzz = TenthVibration.getIntervalVibe(this)
        if (!limitBuzz && total % interval == 0 && intervalBuzz > 0) {
            vibrate(intervalBuzz.toLong())
        }

        // Milestones: Brain Buddy hops and the pill glows. His picture follows his damage
        // and stays, so there is no quick flash of another picture.
        if (total % 50 == 0 || total % interval == 0 || limitBuzz) {
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

    // A calm breathing break at the red limit. It never blocks.
    private fun openBreak() {
        try {
            val i = Intent(this, BreakActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    // A schedule (night, pacing, at my limit, custom) shows a calm screen that can be dismissed.
    private fun checkSchedules(now: Long) {
        if (now - lastSchedCheck < 4000L) return
        lastSchedCheck = now
        if (now < Prefs.getSchedSnooze(this)) return
        if (Prefs.isPaused(this) || Prefs.isRelaxedNow(this)) return
        val s = Schedules.tripped(this) ?: return
        Prefs.setSchedSnooze(this, now + 60000L)
        try {
            val i = Intent(this, ScheduleActivity::class.java)
            i.putExtra("kind", s.kind)
            i.putExtra("name", s.name)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    // ---- The open-app card: a brief hello from Brain Buddy when you open a social app ----
    private fun maybeShowCard(now: Long) {
        val gap = now - lastReelAppEvent
        lastReelAppEvent = now
        if (gap < 60000L) return
        if (!Prefs.getOpenCardOn(this)) return
        if (now - lastCardTime < 10L * 60L * 1000L) return
        lastCardTime = now
        showOpenCard()
    }

    private fun showOpenCard() {
        showCard(
            Damage.stage(this),
            Prefs.getCount(this).toString() + " reels today",
            Prefs.formatTime(Prefs.getSeconds(this)) + " on reels",
            2800L
        )
    }

    // A small card from Brain Buddy (or the puppy in Kids mode) at the top of the screen.
    private fun showCard(expr: Int, text1: String, text2: String, ms: Long) {
        removeCard()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.background = Theme.rounded(Theme.CARD, Theme.dp(this, 18f).toFloat(), Theme.BRASS, Theme.dp(this, 1f))
        box.setPadding(Theme.dp(this, 10f), Theme.dp(this, 8f), Theme.dp(this, 16f), Theme.dp(this, 8f))
        if (Prefs.getBuddyOn(this)) {
            val size = Theme.dp(this, 42f)
            val b = BuddyView(this, size, true)
            b.setExpression(expr)
            box.addView(b, LinearLayout.LayoutParams(size, size))
        }
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val line1 = TextView(this)
        line1.text = text1
        line1.textSize = 15f
        line1.typeface = Theme.SERIF_BOLD
        line1.setTextColor(Theme.GREEN)
        val line2 = TextView(this)
        line2.text = text2
        line2.textSize = 12f
        line2.setTextColor(Theme.TEXT_SOFT)
        if (text2.isEmpty()) line2.visibility = android.view.View.GONE
        col.addView(line1)
        col.addView(line2)
        val colLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        colLp.leftMargin = Theme.dp(this, 8f)
        box.addView(col, colLp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = Theme.dp(this, 90f)
        try {
            box.alpha = 0f
            windowManager?.addView(box, params)
            cardRoot = box
            box.animate().alpha(1f).setDuration(250L).start()
            handler.postDelayed(cardRemove, ms)
        } catch (e: Exception) {
            cardRoot = null
        }
    }

    // ---- The accessibility button: one tap switches Kids mode on, the PIN turns it off ----
    private val kidsButton = object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController) {
            kidsShortcut()
        }
    }

    private fun registerKidsButton() {
        try {
            val controller = accessibilityButtonController
            if (controller.isAccessibilityButtonAvailable) controller.registerAccessibilityButtonCallback(kidsButton)
        } catch (e: Exception) {
        }
    }

    private fun kidsShortcut() {
        try {
            if (!Kids.hasPin(this)) {
                // Set a parent PIN first.
                val i = Intent(this, MainActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(i)
            } else if (Kids.manualOn(this)) {
                val i = Intent(this, KidsTurnOffActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(i)
            } else {
                Kids.setOn(this, true)
                AutoKids.end(this)
                showCard(BuddyView.FRESH, "Kids mode is on", "Turn it off with the parent PIN", 3000L)
            }
        } catch (e: Exception) {
        }
    }

    // ---- Auto Kids mode signals ----
    // About five reels in twenty seconds is a very fast swiper.
    private fun trackSwipe(now: Long) {
        swipeTimes.add(now)
        swipeTimes.removeAll { now - it > 60000L }
        if (swipeTimes.count { now - it <= 20000L } >= 5) autoTrigger(2, "swipe")
    }

    // Six button taps within three seconds.
    private fun trackTap(now: Long) {
        tapTimes.add(now)
        tapTimes.removeAll { now - it > 3000L }
        if (tapTimes.size >= 6) {
            tapTimes.clear()
            autoTrigger(1, "taps")
        }
    }

    // Looks for kids words in the visible text, at most every eight seconds. Nothing is saved.
    private fun maybeScanWords(now: Long) {
        if (now - lastWordScan < 8000L) return
        if (!AutoKids.enabled(this) || Kids.manualOn(this) || Kids.autoLevel(this) == 2 || AutoKids.passActive(this, now)) return
        lastWordScan = now
        val words = AutoKids.words(this)
        if (words.isEmpty()) return
        val root = rootInActiveWindow ?: return
        var budget = 300
        var hit = false
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || hit || budget <= 0 || depth > 14) return
            budget--
            val t = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).lowercase()
            if (t.length > 2) {
                for (w in words) {
                    if (t.contains(w)) {
                        hit = true
                        break
                    }
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        if (hit) autoTrigger(3, "words")
    }

    // The motion sensor runs only while a reel screen is open.
    private fun startMotion() {
        if (!AutoKids.enabled(this) || !AutoKids.motionOn(this)) return
        if (motion == null) motion = MotionWatch { jitter, flat -> onMotion(jitter, flat) }
        motion?.start(this)
    }

    private fun stopMotion() {
        motion?.stop()
        shakyWindows = 0
        stillSeconds = 0
    }

    private fun onMotion(jitter: Float, flat: Float) {
        val now = System.currentTimeMillis()
        // An unsteady hand: lots of jitter in two windows in a row.
        if (jitter > AutoKids.jitterThreshold(this)) {
            shakyWindows++
            if (shakyWindows >= 2) autoTrigger(1, "shaky")
        } else {
            shakyWindows = 0
        }
        // Lying still: almost no movement for a minute while reels keep being swiped, phone fairly flat.
        if (jitter < 0.04f) {
            stillSeconds += 5
            val recent = swipeTimes.count { now - it <= 60000L }
            if (stillSeconds >= 60 && recent >= 3 && flat > 5.5f) autoTrigger(1, "still")
        } else {
            stillSeconds = 0
        }
    }

    // ---- Auto Kids mode ----
    // A signal adds points. If the level goes up, a card tells the adult what happened.
    private fun autoTrigger(points: Int, tag: String) {
        val level = AutoKids.add(this, points, tag, System.currentTimeMillis())
        if (level > 0) showAutoCard(level)
    }

    private fun autoEnded() {
        AutoKids.end(this)
        removeAutoCard()
        removeHold()
        kidsWarned = false
    }

    // An adult holds the button for three seconds: adult mode for a while.
    private fun adultNow() {
        AutoKids.exit(this, System.currentTimeMillis())
        removeAutoCard()
        removeHold()
        showCard(BuddyView.FRESH, "Adult mode", "Auto Kids mode is paused for " + AutoKids.passMin(this) + " minutes", 3500L)
    }

    private fun registerScreenReceiver() {
        if (receiverOn) return
        val f = android.content.IntentFilter()
        f.addAction(Intent.ACTION_SCREEN_OFF)
        f.addAction(Intent.ACTION_SCREEN_ON)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, f)
            }
            receiverOn = true
        } catch (e: Exception) {
        }
    }

    // A card you can touch, shown for a few seconds when auto Kids mode starts.
    private fun showAutoCard(level: Int) {
        removeAutoCard()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = Theme.rounded(Theme.CARD, Theme.dp(this, 18f).toFloat(), Theme.BRASS, Theme.dp(this, 1f))
        box.setPadding(Theme.dp(this, 14f), Theme.dp(this, 10f), Theme.dp(this, 14f), Theme.dp(this, 12f))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val size = Theme.dp(this, 48f)
        val pup = BuddyView(this, size, true)
        pup.setExpression(BuddyView.FRESH)
        row.addView(pup, LinearLayout.LayoutParams(size, size))
        val words = TextView(this)
        words.text = if (level == 1) "Looks like a child is watching. Kids mode is helping gently." else "Looks like a child is watching. Kids mode is on."
        words.textSize = 14f
        words.typeface = Theme.SERIF_BOLD
        words.setTextColor(Theme.GREEN)
        val wlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        wlp.leftMargin = Theme.dp(this, 8f)
        row.addView(words, wlp)
        box.addView(row)

        if (level == 1) {
            val full = Theme.softButton(this, "Switch to full Kids mode")
            full.setOnClickListener {
                AutoKids.escalate(this)
                removeAutoCard()
            }
            box.addView(full, Theme.lp(this, 8f, 0f))
        }
        val adult = Theme.softButton(this, "I am an adult (hold 3 seconds)")
        adult.setOnTouchListener { _, e ->
            if (e.action == android.view.MotionEvent.ACTION_DOWN) {
                handler.postDelayed(holdAdult, 3000L)
            } else if (e.action == android.view.MotionEvent.ACTION_UP || e.action == android.view.MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(holdAdult)
            }
            true
        }
        box.addView(adult, Theme.lp(this, 6f, 0f))

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = Theme.dp(this, 80f)
        try {
            windowManager?.addView(box, params)
            autoRoot = box
            handler.postDelayed(autoRemove, 12000L)
        } catch (e: Exception) {
            autoRoot = null
        }
    }

    private fun removeAutoCard() {
        handler.removeCallbacks(autoRemove)
        handler.removeCallbacks(holdAdult)
        autoRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        autoRoot = null
    }

    // ---- Kids mode ----
    private fun kidsNewReel(now: Long) {
        Kids.addReel(this)
        val gap = now - lastKidsReel
        lastKidsReel = now
        val min = Kids.minSecs(this)
        if (min <= 0) return
        if (Kids.hold(this)) {
            startSwipeHold(min)
        } else if (gap in 1 until min * 1000L) {
            showCard(BuddyView.CURIOUS, "Let's watch this one a bit longer", "", 2200L)
        }
    }

    // Every second on a reel in Kids mode: time limits, the two-minute warning and play reminders.
    private fun kidsTick(now: Long) {
        if (!Kids.on(this) || !Kids.appOn(this, activeApp)) return
        if (Kids.quietBlocked(this, now)) {
            openKidsStop()
            return
        }
        // Five minutes before rest time, a gentle heads-up, once a day.
        val toRest = Kids.minutesToQuiet(this)
        if (toRest in 1..5 && quietWarnedDay != Prefs.dayString()) {
            quietWarnedDay = Prefs.dayString()
            showCard(BuddyView.SLEEPING, "Bedtime in $toRest minute" + (if (toRest == 1) "" else "s"), "Let's get ready to rest", 5000L)
        }
        Kids.addSecond(this, now)
        val rem = Kids.remainingSecs(this, now)
        if (rem != null) {
            if (rem <= 0L) {
                openKidsStop()
                return
            }
            if (rem <= 120L && !kidsWarned) {
                kidsWarned = true
                showCard(BuddyView.CURIOUS, "Two minutes left", "Then it is time to rest", 4000L)
            }
            if (rem > 120L) kidsWarned = false
        }
        if (Kids.nudgeOn(this)) {
            val every = Kids.nudgeMin(this) * 60
            val used = Kids.sessionSecs(this, now)
            if (every > 0 && used > 0 && used % every == 0 && (rem == null || rem > 150L)) {
                val n = kidsNudges[nudgeIdx % kidsNudges.size]
                nudgeIdx++
                showCard(n.first, n.second, "", 4000L)
            }
        }
    }

    // Called when a new window comes to the front. Tracks games and the Play Store in Kids mode.
    private fun updateForeground(pkg: String) {
        if (pkg == fgPkg) return
        // Keyboards, the notification shade and similar helpers do not count as leaving the app.
        if (pkg == "com.android.systemui" || pkg == "android" || pkg.contains("inputmethod") ||
            pkg.contains("honeyboard") || pkg.contains("keyboard")
        ) return
        fgPkg = pkg
        if (AutoKids.enabled(this)) {
            if (pkg in AutoKids.kidsApps(this)) {
                autoTrigger(AutoKids.FULL_AT, "app")
            } else if (pkg !in reelApps && pkg != packageName && Games.isCandidate(this, pkg)) {
                autoTrigger(1, "game")
            }
        }
        val kind = when {
            !Kids.on(this) -> 0
            pkg == Games.STORE -> if (Games.storeGuard(this)) 2 else 0
            pkg in reelApps || pkg == packageName -> 0
            Games.counts(this, pkg) -> 1
            else -> 0
        }
        if (kind == 0 && fgKind == 0) return
        fgKind = kind
        gameWarned = false
        handler.removeCallbacks(fgTicker)
        if (kind != 0) {
            fgTick()
            handler.postDelayed(fgTicker, 1000L)
        }
    }

    // Every second while a counted game or the Play Store is in front in Kids mode.
    private fun fgTick() {
        val now = System.currentTimeMillis()
        val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!power.isInteractive) return
        if (!Kids.on(this)) {
            fgKind = 0
            return
        }
        if (fgKind == 2) {
            if (Kids.storeBlocked(this, now)) openKidsStop(4)
            return
        }
        if (fgKind == 1) {
            val reason = Kids.gameBlockReason(this, now)
            if (reason != 0) {
                openKidsStop(reason)
                return
            }
            Kids.addGameSecond(this, now)
            val left = Kids.gameRemaining(this)
            if (left <= 120L && !gameWarned) {
                gameWarned = true
                showCard(BuddyView.CURIOUS, "Two minutes of games left", "Then it is time to rest", 4000L)
            }
            if (left > 120L) gameWarned = false
        }
    }

    private fun openKidsStop(reason: Int = 0) {
        val now = System.currentTimeMillis()
        if (now - lastKidsStop < 4000L) return
        lastKidsStop = now
        try {
            val i = Intent(this, KidsStopActivity::class.java)
            i.putExtra("reason", reason)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    // For a few seconds after each new reel, swipes are held back with a friendly note.
    private fun startSwipeHold(secs: Int) {
        removeHold()
        val box = android.widget.FrameLayout(this)
        val hint = LinearLayout(this)
        hint.orientation = LinearLayout.HORIZONTAL
        hint.gravity = Gravity.CENTER_VERTICAL
        hint.background = Theme.rounded(Theme.CARD, Theme.dp(this, 18f).toFloat(), Theme.BRASS, Theme.dp(this, 1f))
        hint.setPadding(Theme.dp(this, 10f), Theme.dp(this, 8f), Theme.dp(this, 16f), Theme.dp(this, 8f))
        val size = Theme.dp(this, 56f)
        val pup = BuddyView(this, size, true)
        pup.setExpression(BuddyView.CURIOUS)
        hint.addView(pup, LinearLayout.LayoutParams(size, size))
        val words = TextView(this)
        words.text = "Let's watch this one a bit longer"
        words.textSize = 16f
        words.typeface = Theme.SERIF_BOLD
        words.setTextColor(Theme.GREEN)
        words.maxWidth = Theme.dp(this, 200f)
        val wlp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        wlp.leftMargin = Theme.dp(this, 8f)
        hint.addView(words, wlp)
        hint.visibility = android.view.View.GONE
        box.addView(hint, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        box.setOnTouchListener { _, e ->
            if (e.action == android.view.MotionEvent.ACTION_DOWN) {
                hint.visibility = android.view.View.VISIBLE
                handler.removeCallbacks(hintHide)
                handler.postDelayed(hintHide, 1500L)
            }
            true
        }
        // The bottom of the screen stays free, so the phone's own buttons still work.
        val h = resources.displayMetrics.heightPixels - Theme.dp(this, 120f)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        try {
            windowManager?.addView(box, params)
            holdRoot = box
            holdHint = hint
            handler.postDelayed(holdRemove, secs * 1000L)
        } catch (e: Exception) {
            holdRoot = null
            holdHint = null
        }
    }

    private fun removeHold() {
        handler.removeCallbacks(holdRemove)
        handler.removeCallbacks(hintHide)
        holdRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        holdRoot = null
        holdHint = null
    }

    private fun removeCard() {
        handler.removeCallbacks(cardRemove)
        cardRoot?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
        }
        cardRoot = null
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
            val open = Intent(this, RestartActivity::class.java)
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

    // Which Brain Buddy picture to show. It follows his damage, which rises with every reel
    // and slowly falls while you rest. Only bedtime reminders and the goodbye override it.
    private fun expressionFor(total: Int): Int {
        val now = System.currentTimeMillis()
        if (leaving) return BuddyView.ENCOURAGE
        if (overrideExpr >= 0 && now < overrideUntil) return overrideExpr
        if (Prefs.isPaused(this) || Prefs.isRelaxedNow(this)) return BuddyView.LOVE
        return Damage.stage(this)
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
        if (bubbleRoot != null) {
            // Back on a reel screen while the happy goodbye was playing: keep the bubble.
            if (leaving) {
                leaving = false
                handler.removeCallbacks(removeRunnable)
                refreshBubble()
            }
            return
        }
        val buddyOn = Prefs.getBuddyOn(this)
        val inside = buddyOn && Prefs.getBuddyPos(this) == 1
        val onTop = buddyOn && !inside
        val buddySize = Theme.dp(this, if (inside) BUDDY_IN_DP else BUDDY_DP)
        // On top, the character's feet stand on the pill edge, so only a little overlaps the pill.
        val overlap = (buddySize * 0.16f).toInt()

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = Theme.dp(this, 16f).toFloat()

        val count = TextView(this)
        count.setTextColor(Color.WHITE)
        count.textSize = 12f
        count.typeface = Theme.SERIF_BOLD
        count.gravity = Gravity.CENTER
        count.includeFontPadding = false

        val timer = TextView(this)
        timer.setTextColor(Color.argb(215, 255, 255, 255))
        timer.textSize = 7f
        timer.gravity = Gravity.CENTER
        timer.includeFontPadding = false

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(count)
        col.addView(timer)

        val pill = LinearLayout(this)
        pill.background = bg
        pill.clipChildren = false
        pill.setPadding(
            Theme.dp(this, 8f), Theme.dp(this, 7f),
            Theme.dp(this, 8f), Theme.dp(this, 5f)
        )
        var faceView: BuddyView? = null
        if (inside) {
            pill.orientation = LinearLayout.HORIZONTAL
            pill.gravity = Gravity.CENTER_VERTICAL
            faceView = BuddyView(this, buddySize, true)
            pill.addView(faceView, LinearLayout.LayoutParams(buddySize, buddySize))
            val colLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            colLp.leftMargin = Theme.dp(this, 4f)
            pill.addView(col, colLp)
        } else {
            pill.orientation = LinearLayout.VERTICAL
            pill.gravity = Gravity.CENTER_HORIZONTAL
            pill.minimumWidth = Theme.dp(this, PILL_W_DP)
            pill.addView(col)
        }

        val root = FrameLayout(this)
        root.clipChildren = false

        val pillLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        pillLp.topMargin = if (onTop) buddySize - overlap else 0
        pillLp.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(pill, pillLp)

        if (onTop) {
            faceView = BuddyView(this, buddySize, true)
            val faceLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            faceLp.topMargin = 0
            faceLp.gravity = Gravity.CENTER_HORIZONTAL
            root.addView(faceView, faceLp)
        }

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
            leaving = false
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

    // Leaving a reel screen: Brain Buddy gives a happy thumbs-up for a moment, then the bubble goes.
    private fun hideBubble() {
        removeHold()
        if (bubbleRoot == null || leaving) return
        val f = face
        if (f != null) {
            leaving = true
            f.setExpression(BuddyView.ENCOURAGE)
            f.celebrate()
            handler.removeCallbacks(removeRunnable)
            handler.postDelayed(removeRunnable, GOODBYE_MS)
            return
        }
        removeBubbleNow()
    }

    private fun removeBubbleNow() {
        handler.removeCallbacks(removeRunnable)
        leaving = false
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
