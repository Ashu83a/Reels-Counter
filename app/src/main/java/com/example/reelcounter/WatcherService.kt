package com.example.reelcounter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

// Runs quietly in the background. When a social app comes to the front and the counter
// is switched off, it shows a notification: "Reel Counter is off. Tap to turn it on."
// Android does not allow an app to switch its own Accessibility service on, so the tap
// takes you straight to the right Settings page.
class WatcherService : Service() {

    companion object {
        const val CHANNEL_QUIET = "watcher_quiet"
        const val CHANNEL_ALERT = "watcher_alert"
        const val ONGOING_ID = 41
        const val ALERT_ID = 42
        const val CHECK_MS = 2000L
        const val NAG_PAUSE_MS = 2L * 60L * 1000L

        private val SOCIAL = setOf(
            "com.instagram.android",
            "com.instagram.barcelona",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.linkedin.android"
        )

        fun start(c: Context) {
            try {
                val i = Intent(c, WatcherService::class.java)
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {
            }
        }

        fun stop(c: Context) {
            try {
                c.stopService(Intent(c, WatcherService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastPkg = ""
    private var lastAlertTime = 0L
    private var lastEventTime = 0L

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        makeChannels()
        val note = ongoingNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ONGOING_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ONGOING_ID, note)
            }
        } catch (e: Exception) {
            stopSelf()
            return
        }
        lastEventTime = System.currentTimeMillis() - 5000L
        handler.postDelayed(tick, CHECK_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun makeChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_QUIET, "Reel Counter running", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, "Reel Counter is off", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun ongoingNotification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_QUIET) else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Reel Counter is watching for social apps")
            .setContentText("It will remind you if the counter is off")
            .setOngoing(true)
            .build()
    }

    // The social app that came to the front since the last check, if any.
    private fun newForegroundApp(): String? {
        return try {
            val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = manager.queryEvents(lastEventTime, now)
            lastEventTime = now
            val e = UsageEvents.Event()
            var found: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED || e.eventType == 1) {
                    lastPkg = e.packageName ?: ""
                    if (lastPkg in SOCIAL) found = lastPkg
                }
            }
            found
        } catch (ex: Exception) {
            null
        }
    }

    private fun check() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        if (!SysCheck.usageAccess(this)) return
        val pkg = newForegroundApp() ?: return
        if (SysCheck.accessibilityOn(this)) {
            cancelAlert()
            return
        }
        if (Prefs.isBankPackage(this, pkg)) return
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < NAG_PAUSE_MS) return
        lastAlertTime = now
        showAlert()
    }

    private fun cancelAlert() {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(ALERT_ID)
        } catch (e: Exception) {
        }
    }

    private fun showAlert() {
        try {
            val open = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            if (Build.VERSION.SDK_INT >= 33) {
                // Opens the page of this one service when the phone supports it.
                val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                details.putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(this, ReelService::class.java).flattenToString())
                if (details.resolveActivity(packageManager) != null) {
                    details.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    postAlert(details)
                    return
                }
            }
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            postAlert(open)
        } catch (e: Exception) {
        }
    }

    private fun postAlert(open: Intent) {
        val pending = PendingIntent.getActivity(
            this, 2, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ALERT) else Notification.Builder(this)
        val note = b.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reel Counter is off")
            .setContentText("Tap to turn it on, then it counts your reels again")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ALERT_ID, note)
    }
}
