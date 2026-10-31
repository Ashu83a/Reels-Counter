package com.example.reelcounter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class StatsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            manager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_stats)
            val reels = Prefs.getCount(context)
            val time = Prefs.formatTime(Prefs.getSeconds(context))
            views.setTextViewText(R.id.widget_text, "$reels reels today\n$time")

            val open = Intent(context, StatsActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_text, pending)
            return views
        }

        // Called by the app to refresh every widget on the home screen.
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatsWidget::class.java))
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context))
            }
        }
    }
}
