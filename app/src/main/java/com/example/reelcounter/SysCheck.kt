package com.example.reelcounter

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

// Small checks used by the status line and the setup guide.
object SysCheck {

    fun accessibilityOn(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(c, ReelService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(":").any { it.equals(full, true) || it.equals(short, true) }
    }

    @Suppress("DEPRECATION")
    fun usageAccess(c: Context): Boolean {
        return try {
            val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
            } else {
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun batteryFree(c: Context): Boolean {
        return try {
            (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun notificationsOn(c: Context): Boolean {
        return try {
            (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
        } catch (e: Exception) {
            false
        }
    }

    fun overlayOn(c: Context): Boolean = Settings.canDrawOverlays(c)

    // "just now", "5 min ago", "3 h ago", "2 days ago"
    fun agoText(time: Long): String {
        val minutes = (System.currentTimeMillis() - time) / 60000L
        return when {
            minutes < 1L -> "just now"
            minutes < 60L -> "$minutes min ago"
            minutes < 48L * 60L -> "${minutes / 60L} h ago"
            else -> "${minutes / (24L * 60L)} days ago"
        }
    }
}
