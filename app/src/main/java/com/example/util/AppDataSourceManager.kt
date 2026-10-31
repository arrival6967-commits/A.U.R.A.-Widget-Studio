package com.example.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import com.example.data.WidgetCategory
import com.example.data.WidgetDataSourceType
import com.example.data.WidgetDesign
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveResolvedWidgetTelemetry(
    val title: String,
    val primaryValue: String,
    val subtitle: String,
    val secondaryValue: String,
    val isProviderInstalled: Boolean,
    val locationName: String = "",
    val forecast3Day: List<DailyForecastItem> = emptyList(),
    val isCalendar: Boolean = false,
    val calendarDayNumber: String = "",
    val calendarDayOfWeek: String = "",
    val calendarMonthYear: String = "",
    val calendarEvents: List<String> = emptyList()
)

object AppDataSourceManager {

    private val weatherPackages = listOf(
        "com.google.android.googlequicksearchbox",
        "com.sec.android.daemonapp",
        "com.miui.weather2",
        "com.coloros.weather2"
    )

    private val keepPackages = listOf("com.google.android.keep")

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isNotesInstalled(context: Context): Boolean {
        return keepPackages.any { isPackageInstalled(context, it) }
    }

    fun buildSourceAppLaunchIntent(context: Context, widget: WidgetDesign): Intent? {
        val source = WidgetDataSourceType.fromString(widget.dataSourceType)
        val pm = context.packageManager

        // 1. Check explicit package hint first
        if (widget.sourcePackageHint.isNotBlank()) {
            val explicit = pm.getLaunchIntentForPackage(widget.sourcePackageHint)
            if (explicit != null) {
                explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return explicit
            }
        }

        // 2. Fallback by data source category (NEVER opens AuraWidget AI)
        val candidatePackages = when (source) {
            WidgetDataSourceType.LIVE_CLOCK -> listOf(
                "com.google.android.deskclock",
                "com.sec.android.app.clockpackage",
                "com.android.deskclock",
                "com.coloros.alarmclock"
            )
            WidgetDataSourceType.GOOGLE_WEATHER -> weatherPackages
            WidgetDataSourceType.NOTES -> keepPackages
            WidgetDataSourceType.CALENDAR_EVENTS -> listOf(
                "com.google.android.calendar",
                "com.samsung.android.calendar",
                "com.android.calendar"
            )
            WidgetDataSourceType.MEDIA_SESSION -> listOf(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.apple.android.music"
            )
            WidgetDataSourceType.SYSTEM_BATTERY -> listOf("com.android.settings")
            WidgetDataSourceType.STATIC_CUSTOM -> emptyList()
        }

        for (pkg in candidatePackages) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return launch
            }
        }

        return when (source) {
            WidgetDataSourceType.LIVE_CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            WidgetDataSourceType.SYSTEM_BATTERY -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            else -> null
        }
    }

    fun resolveLiveTelemetry(
        context: Context,
        widget: WidgetDesign,
        nowMillis: Long = System.currentTimeMillis()
    ): LiveResolvedWidgetTelemetry {
        val source = WidgetDataSourceType.fromString(widget.dataSourceType)
        val category = WidgetCategory.fromString(widget.category)
        val ruLocale = Locale.forLanguageTag("ru")
        val timeStr = SimpleDateFormat("HH:mm:ss", ruLocale).format(Date(nowMillis))
        val shortTimeStr = SimpleDateFormat("HH:mm", ruLocale).format(Date(nowMillis))
        val dateStr = SimpleDateFormat("EEEE, d MMMM", ruLocale)
            .format(Date(nowMillis))
            .replaceFirstChar { it.uppercase() }

        val dayNumber = SimpleDateFormat("d", ruLocale).format(Date(nowMillis))
        val dayOfWeek = SimpleDateFormat("EEEE", ruLocale).format(Date(nowMillis)).replaceFirstChar { it.uppercase() }
        val monthYear = SimpleDateFormat("MMMM yyyy", ruLocale).format(Date(nowMillis)).replaceFirstChar { it.uppercase() }

        // If category is CALENDAR or source is CALENDAR_EVENTS: Clean month calendar without fake meetings
        if (category == WidgetCategory.CALENDAR || source == WidgetDataSourceType.CALENDAR_EVENTS) {
            return LiveResolvedWidgetTelemetry(
                title = widget.title.ifBlank { "Календарь" },
                primaryValue = "$dayNumber $monthYear",
                subtitle = "$dayOfWeek • $monthYear",
                secondaryValue = dayOfWeek,
                isProviderInstalled = true,
                isCalendar = true,
                calendarDayNumber = dayNumber,
                calendarDayOfWeek = dayOfWeek,
                calendarMonthYear = monthYear,
                calendarEvents = emptyList()
            )
        }

        return when (source) {
            WidgetDataSourceType.LIVE_CLOCK -> {
                LiveResolvedWidgetTelemetry(
                    title = widget.title.ifBlank { "Часы" },
                    primaryValue = timeStr,
                    subtitle = dateStr,
                    secondaryValue = "Часы",
                    isProviderInstalled = true
                )
            }

            WidgetDataSourceType.SYSTEM_BATTERY -> {
                val batteryStatus = context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 92
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310) ?: 310
                val tempC = tempTenths / 10.0

                LiveResolvedWidgetTelemetry(
                    title = widget.title.ifBlank { "Заряд и Система" },
                    primaryValue = "$pct%",
                    subtitle = if (isCharging) "Быстрая зарядка • ${tempC}°C • $shortTimeStr" else "От аккумулятора • ${tempC}°C • $shortTimeStr",
                    secondaryValue = if (isCharging) "CHARGING ⚡" else "BATTERY LIVE",
                    isProviderInstalled = true
                )
            }

            WidgetDataSourceType.GOOGLE_WEATHER -> {
                val realWeather = RealWeatherManager.getCachedOrFallback(context)
                val installed = weatherPackages.any { isPackageInstalled(context, it) }

                // If widget design already has custom forecastJson, prioritize it; otherwise use real weather forecast
                val forecastList = if (!widget.forecastJson.isNullOrBlank()) {
                    RealWeatherManager.parseForecastJson(widget.forecastJson)
                } else {
                    realWeather.forecast3Day
                }

                val city = if (widget.locationName.isNotBlank() && widget.locationName != "Москва") {
                    widget.locationName
                } else {
                    realWeather.locationName
                }

                val titleText = if (widget.title.isNotBlank() && !widget.title.startsWith("Google Погода")) {
                    widget.title
                } else {
                    "Погода • $city"
                }

                LiveResolvedWidgetTelemetry(
                    title = titleText,
                    primaryValue = realWeather.temperature,
                    subtitle = "${realWeather.conditionText} • Ощущается ${realWeather.apparentTemperature} • $shortTimeStr",
                    secondaryValue = city,
                    isProviderInstalled = installed,
                    locationName = city,
                    forecast3Day = forecastList
                )
            }

            WidgetDataSourceType.NOTES -> {
                val installed = isNotesInstalled(context)
                LiveResolvedWidgetTelemetry(
                    title = widget.title.ifBlank { "Заметки Google Keep" },
                    primaryValue = widget.primaryValue.ifBlank { "Быстрая запись" },
                    subtitle = widget.subtitle.ifBlank { "Синхронизировано с Google Keep" },
                    secondaryValue = "Google Keep",
                    isProviderInstalled = installed
                )
            }

            WidgetDataSourceType.MEDIA_SESSION -> {
                LiveResolvedWidgetTelemetry(
                    title = widget.title,
                    primaryValue = widget.primaryValue,
                    subtitle = widget.subtitle,
                    secondaryValue = "Media Session",
                    isProviderInstalled = true
                )
            }

            WidgetDataSourceType.CALENDAR_EVENTS -> {
                LiveResolvedWidgetTelemetry(
                    title = widget.title.ifBlank { "Календарь" },
                    primaryValue = "$dayNumber $monthYear",
                    subtitle = "$dayOfWeek • $monthYear",
                    secondaryValue = "Календарь",
                    isProviderInstalled = true,
                    isCalendar = true,
                    calendarDayNumber = dayNumber,
                    calendarDayOfWeek = dayOfWeek,
                    calendarMonthYear = monthYear,
                    calendarEvents = emptyList()
                )
            }

            WidgetDataSourceType.STATIC_CUSTOM -> {
                LiveResolvedWidgetTelemetry(
                    title = widget.title,
                    primaryValue = widget.primaryValue,
                    subtitle = widget.subtitle,
                    secondaryValue = widget.secondaryValue,
                    isProviderInstalled = true
                )
            }
        }
    }
}
