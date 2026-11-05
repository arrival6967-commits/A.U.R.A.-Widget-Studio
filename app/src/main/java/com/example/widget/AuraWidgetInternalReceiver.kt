package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.WidgetTapBehavior
import com.example.util.AppDataSourceManager

/**
 * Non-exported internal BroadcastReceiver handling widget button taps,
 * timer/stopwatch controls, and minute tick alarms.
 *
 * android:exported="false" in AndroidManifest.xml guarantees that third-party applications
 * cannot send spoofed intents to trigger widget updates or alter timer state.
 */
class AuraWidgetInternalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = AppWidgetManager.getInstance(context)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        when (intent.action) {
            AuraAppWidgetProvider.ACTION_STOPWATCH_TOGGLE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(
                        AuraAppWidgetProvider.getWidgetPrefsName(appWidgetId),
                        Context.MODE_PRIVATE
                    )
                    val isRunning = prefs.getBoolean("stopwatch_running", false)
                    val accumulated = prefs.getLong("stopwatch_accumulated_ms", 0L)
                    val now = SystemClock.elapsedRealtime()

                    if (isRunning) {
                        val base = prefs.getLong("stopwatch_base", now)
                        val elapsed = (now - base).coerceAtLeast(0L)
                        prefs.edit()
                            .putBoolean("stopwatch_running", false)
                            .putLong("stopwatch_accumulated_ms", elapsed)
                            .commit()
                    } else {
                        val newBase = now - accumulated
                        prefs.edit()
                            .putBoolean("stopwatch_running", true)
                            .putLong("stopwatch_base", newBase)
                            .commit()
                    }
                    AuraAppWidgetProvider.updateAppWidget(context, manager, appWidgetId)
                }
            }

            AuraAppWidgetProvider.ACTION_STOPWATCH_RESET -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(
                        AuraAppWidgetProvider.getWidgetPrefsName(appWidgetId),
                        Context.MODE_PRIVATE
                    )
                    prefs.edit()
                        .putBoolean("stopwatch_running", false)
                        .putLong("stopwatch_accumulated_ms", 0L)
                        .putLong("stopwatch_base", SystemClock.elapsedRealtime())
                        .commit()
                    AuraAppWidgetProvider.updateAppWidget(context, manager, appWidgetId)
                }
            }

            AuraAppWidgetProvider.ACTION_TIMER_TOGGLE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(
                        AuraAppWidgetProvider.getWidgetPrefsName(appWidgetId),
                        Context.MODE_PRIVATE
                    )
                    val isRunning = prefs.getBoolean("timer_running", false)
                    val duration = prefs.getLong("timer_duration_ms", 300_000L)
                    val remaining = prefs.getLong("timer_remaining_ms", duration)
                    val now = SystemClock.elapsedRealtime()

                    if (isRunning) {
                        val target = prefs.getLong("timer_target", now + remaining)
                        val rem = (target - now).coerceAtLeast(0L)
                        prefs.edit()
                            .putBoolean("timer_running", false)
                            .putLong("timer_remaining_ms", rem)
                            .commit()
                    } else {
                        val activeRem = if (remaining <= 0L) duration else remaining
                        val target = now + activeRem
                        prefs.edit()
                            .putBoolean("timer_running", true)
                            .putLong("timer_target", target)
                            .putLong("timer_remaining_ms", activeRem)
                            .commit()
                    }
                    AuraAppWidgetProvider.updateAppWidget(context, manager, appWidgetId)
                }
            }

            AuraAppWidgetProvider.ACTION_TIMER_RESET -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(
                        AuraAppWidgetProvider.getWidgetPrefsName(appWidgetId),
                        Context.MODE_PRIVATE
                    )
                    val duration = prefs.getLong("timer_duration_ms", 300_000L)
                    prefs.edit()
                        .putBoolean("timer_running", false)
                        .putLong("timer_remaining_ms", duration)
                        .putLong("timer_target", SystemClock.elapsedRealtime() + duration)
                        .commit()
                    AuraAppWidgetProvider.updateAppWidget(context, manager, appWidgetId)
                }
            }

            AuraAppWidgetProvider.ACTION_MINUTE_TICK -> {
                val component = ComponentName(context, AuraAppWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                for (id in ids) {
                    AuraAppWidgetProvider.updateAppWidget(context, manager, id)
                }
                AuraAppWidgetProvider.scheduleNextMinuteAlarm(context)
            }

            AuraAppWidgetProvider.ACTION_WIDGET_TAP_INTERACTION -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(
                        AuraAppWidgetProvider.getWidgetPrefsName(appWidgetId),
                        Context.MODE_PRIVATE
                    )
                    val currentPhase = prefs.getFloat("animPhase", 0.25f)
                    val nextPhase = (currentPhase + 0.22f) % 1.0f
                    prefs.edit().putFloat("animPhase", nextPhase).commit()

                    val widget = AuraAppWidgetProvider.loadWidgetForAppWidgetId(context, appWidgetId)
                    val tapBehavior = WidgetTapBehavior.fromString(widget.tapBehavior)

                    // Refresh ONLY this specific widget
                    AuraAppWidgetProvider.updateAppWidget(context, manager, appWidgetId)

                    // If configured to open external app, launch ONLY that app
                    if (tapBehavior == WidgetTapBehavior.OPEN_SOURCE_APP) {
                        val externalIntent = AppDataSourceManager.buildSourceAppLaunchIntent(context, widget)
                        if (externalIntent != null && externalIntent.component?.packageName != context.packageName) {
                            try {
                                context.startActivity(externalIntent)
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                }
            }

            AuraAppWidgetProvider.ACTION_NOOP -> {
                // Safely ignored
            }
        }
    }
}
