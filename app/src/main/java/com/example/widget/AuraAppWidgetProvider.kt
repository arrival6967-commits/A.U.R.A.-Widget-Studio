package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.WidgetAnimationStyle
import com.example.data.WidgetCategory
import com.example.data.WidgetClockVariation
import com.example.data.WidgetDataSourceType
import com.example.data.WidgetDesign
import com.example.data.WidgetFontFamily
import com.example.data.WidgetSizePreset
import com.example.data.WidgetStylePreset
import com.example.data.WidgetTapBehavior
import com.example.ui.components.WidgetBitmapPainter
import com.example.util.AppDataSourceManager
import kotlin.math.min

/**
 * AppWidgetProvider that enforces:
 * 1) Multi-widget instance isolation: Each widget placed on the home screen stores and maintains
 *    its own distinct WidgetDesign configuration via aura_widget_config_<id>. Adding or updating
 *    one widget never overwrites other widgets!
 * 2) Live OS-level time ticking: Supports TextClock (HH:mm and HH:mm:ss), AnalogClock,
 *    and real interactive Chronometer for live Stopwatch & Countdown Timer with Start/Pause/Reset.
 * 3) Universal text drop shadows.
 * 4) External app launches directly into Google Weather, Google Keep, Clock, or Calendar
 *    without opening AuraWidget AI.
 */
class AuraAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val prefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE)
            if (!prefs.contains("title")) {
                // Newly added widget instance: assign the latest pending design without touching existing widgets
                val pending = loadPendingPinnedWidget(context)
                saveWidgetForAppWidgetId(context, appWidgetId, pending)
            }
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleNextMinuteAlarm(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            context.getSharedPreferences(getWidgetPrefsName(id), Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        when (intent.action) {
            ACTION_STOPWATCH_TOGGLE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE)
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
                    updateAppWidget(context, manager, appWidgetId)
                }
            }

            ACTION_STOPWATCH_RESET -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("stopwatch_running", false)
                        .putLong("stopwatch_accumulated_ms", 0L)
                        .putLong("stopwatch_base", SystemClock.elapsedRealtime())
                        .commit()
                    updateAppWidget(context, manager, appWidgetId)
                }
            }

            ACTION_TIMER_TOGGLE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE)
                    val isRunning = prefs.getBoolean("timer_running", false)
                    val duration = prefs.getLong("timer_duration_ms", 300_000L) // Default 5 mins
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
                    updateAppWidget(context, manager, appWidgetId)
                }
            }

            ACTION_TIMER_RESET -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE)
                    val duration = prefs.getLong("timer_duration_ms", 300_000L)
                    prefs.edit()
                        .putBoolean("timer_running", false)
                        .putLong("timer_remaining_ms", duration)
                        .putLong("timer_target", SystemClock.elapsedRealtime() + duration)
                        .commit()
                    updateAppWidget(context, manager, appWidgetId)
                }
            }

            ACTION_MINUTE_TICK,
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_USER_PRESENT -> {
                val component = ComponentName(context, AuraAppWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                for (id in ids) {
                    updateAppWidget(context, manager, id)
                }
                scheduleNextMinuteAlarm(context)
            }

            ACTION_WIDGET_TAP_INTERACTION -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE)
                    val currentPhase = prefs.getFloat("animPhase", 0.25f)
                    val nextPhase = (currentPhase + 0.22f) % 1.0f
                    prefs.edit().putFloat("animPhase", nextPhase).commit()

                    val widget = loadWidgetForAppWidgetId(context, appWidgetId)
                    val tapBehavior = WidgetTapBehavior.fromString(widget.tapBehavior)

                    // Refresh ONLY this specific widget
                    updateAppWidget(context, manager, appWidgetId)

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
        }
    }

    companion object {
        private const val GLOBAL_PENDING_PREFS = "aura_pending_widget_prefs"
        const val ACTION_WIDGET_TAP_INTERACTION = "com.example.widget.ACTION_WIDGET_TAP_INTERACTION"
        const val ACTION_MINUTE_TICK = "com.example.widget.ACTION_MINUTE_TICK"
        const val ACTION_STOPWATCH_TOGGLE = "com.example.widget.ACTION_STOPWATCH_TOGGLE"
        const val ACTION_STOPWATCH_RESET = "com.example.widget.ACTION_STOPWATCH_RESET"
        const val ACTION_TIMER_TOGGLE = "com.example.widget.ACTION_TIMER_TOGGLE"
        const val ACTION_TIMER_RESET = "com.example.widget.ACTION_TIMER_RESET"

        fun scheduleNextMinuteAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val now = System.currentTimeMillis()
                val nextMinute = (now / 60000L + 1L) * 60000L + 200L

                val intent = Intent(context, AuraAppWidgetProvider::class.java).apply {
                    action = ACTION_MINUTE_TICK
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    7777,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, nextMinute, pi)
                    } catch (secEx: SecurityException) {
                        alarmManager.set(AlarmManager.RTC, nextMinute, pi)
                    }
                } else {
                    alarmManager.setExact(AlarmManager.RTC, nextMinute, pi)
                }
            } catch (ignored: Exception) {
            }
        }

        fun getWidgetPrefsName(appWidgetId: Int): String = "aura_widget_config_$appWidgetId"

        private val allClockHmIds = intArrayOf(
            R.id.widget_text_clock_hm_google_sans,
            R.id.widget_text_clock_hm_montserrat,
            R.id.widget_text_clock_hm_playfair,
            R.id.widget_text_clock_hm_jetbrains,
            R.id.widget_text_clock_hm_comfortaa,
            R.id.widget_text_clock_hm_rubik,
            R.id.widget_text_clock_hm_space_grotesk,
            R.id.widget_text_clock_hm_plus_jakarta
        )

        private val allClockHmsIds = intArrayOf(
            R.id.widget_text_clock_hms_google_sans,
            R.id.widget_text_clock_hms_montserrat,
            R.id.widget_text_clock_hms_playfair,
            R.id.widget_text_clock_hms_jetbrains,
            R.id.widget_text_clock_hms_comfortaa,
            R.id.widget_text_clock_hms_rubik,
            R.id.widget_text_clock_hms_space_grotesk,
            R.id.widget_text_clock_hms_plus_jakarta
        )

        private val allChronometerIds = intArrayOf(
            R.id.widget_chronometer_google_sans,
            R.id.widget_chronometer_montserrat,
            R.id.widget_chronometer_jetbrains,
            R.id.widget_chronometer_generic
        )

        private fun getClockHmViewId(font: WidgetFontFamily): Int = when (font) {
            WidgetFontFamily.GOOGLE_SANS -> R.id.widget_text_clock_hm_google_sans
            WidgetFontFamily.MONTSERRAT -> R.id.widget_text_clock_hm_montserrat
            WidgetFontFamily.PLAYFAIR_DISPLAY -> R.id.widget_text_clock_hm_playfair
            WidgetFontFamily.JETBRAINS_MONO -> R.id.widget_text_clock_hm_jetbrains
            WidgetFontFamily.COMFORTAA -> R.id.widget_text_clock_hm_comfortaa
            WidgetFontFamily.RUBIK -> R.id.widget_text_clock_hm_rubik
            WidgetFontFamily.SPACE_GROTESK -> R.id.widget_text_clock_hm_space_grotesk
            WidgetFontFamily.PLUS_JAKARTA -> R.id.widget_text_clock_hm_plus_jakarta
        }

        private fun getClockHmsViewId(font: WidgetFontFamily): Int = when (font) {
            WidgetFontFamily.GOOGLE_SANS -> R.id.widget_text_clock_hms_google_sans
            WidgetFontFamily.MONTSERRAT -> R.id.widget_text_clock_hms_montserrat
            WidgetFontFamily.PLAYFAIR_DISPLAY -> R.id.widget_text_clock_hms_playfair
            WidgetFontFamily.JETBRAINS_MONO -> R.id.widget_text_clock_hms_jetbrains
            WidgetFontFamily.COMFORTAA -> R.id.widget_text_clock_hms_comfortaa
            WidgetFontFamily.RUBIK -> R.id.widget_text_clock_hms_rubik
            WidgetFontFamily.SPACE_GROTESK -> R.id.widget_text_clock_hms_space_grotesk
            WidgetFontFamily.PLUS_JAKARTA -> R.id.widget_text_clock_hms_plus_jakarta
        }

        private fun getChronometerViewId(font: WidgetFontFamily): Int = when (font) {
            WidgetFontFamily.GOOGLE_SANS -> R.id.widget_chronometer_google_sans
            WidgetFontFamily.MONTSERRAT -> R.id.widget_chronometer_montserrat
            WidgetFontFamily.JETBRAINS_MONO -> R.id.widget_chronometer_jetbrains
            else -> R.id.widget_chronometer_generic
        }

        fun savePendingPinnedWidget(context: Context, widget: WidgetDesign) {
            context.getSharedPreferences(GLOBAL_PENDING_PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", widget.title)
                .putString("subtitle", widget.subtitle)
                .putString("primaryValue", widget.primaryValue)
                .putString("secondaryValue", widget.secondaryValue)
                .putString("stylePreset", widget.stylePreset)
                .putString("category", widget.category)
                .putString("sizePreset", widget.sizePreset)
                .putString("primaryColorHex", widget.primaryColorHex)
                .putString("secondaryColorHex", widget.secondaryColorHex)
                .putString("surfaceColorHex", widget.surfaceColorHex)
                .putFloat("glassOpacity", widget.glassOpacity.toFloat())
                .putFloat("blurRadiusDp", widget.blurRadiusDp.toFloat())
                .putFloat("specularIntensity", widget.specularIntensity.toFloat())
                .putFloat("cornerRadiusDp", widget.cornerRadiusDp.toFloat())
                .putString("iconName", widget.iconName)
                .putString("customIconBase64", widget.customIconBase64)
                .putString("dataSourceType", widget.dataSourceType)
                .putString("sourcePackageHint", widget.sourcePackageHint)
                .putString("tapBehavior", widget.tapBehavior)
                .putBoolean("isAnimated", widget.isAnimated)
                .putString("animationStyle", widget.animationStyle)
                .putString("fontFamily", widget.fontFamily)
                .putString("clockVariation", widget.clockVariation)
                .putBoolean("autoDarkAdapt", widget.autoDarkAdapt)
                .putBoolean("showForecast3Day", widget.showForecast3Day)
                .putString("locationName", widget.locationName)
                .putString("forecastJson", widget.forecastJson ?: "")
                .commit()
        }

        fun loadPendingPinnedWidget(context: Context): WidgetDesign {
            val prefs = context.getSharedPreferences(GLOBAL_PENDING_PREFS, Context.MODE_PRIVATE)
            val forecastRaw = prefs.getString("forecastJson", null)
            return WidgetDesign(
                title = prefs.getString("title", "Google Погода • Москва") ?: "Google Погода • Москва",
                subtitle = prefs.getString("subtitle", "Ощущается как +19°C • Ясно") ?: "",
                primaryValue = prefs.getString("primaryValue", "+22°C") ?: "+22°C",
                secondaryValue = prefs.getString("secondaryValue", "Google Weather") ?: "",
                stylePreset = prefs.getString("stylePreset", WidgetStylePreset.LIQUID_GLASS.name)
                    ?: WidgetStylePreset.LIQUID_GLASS.name,
                category = prefs.getString("category", WidgetCategory.WEATHER.name)
                    ?: WidgetCategory.WEATHER.name,
                sizePreset = prefs.getString("sizePreset", WidgetSizePreset.WIDE_4X2.name)
                    ?: WidgetSizePreset.WIDE_4X2.name,
                primaryColorHex = prefs.getString("primaryColorHex", "#38BDF8") ?: "#38BDF8",
                secondaryColorHex = prefs.getString("secondaryColorHex", "#A855F7") ?: "#A855F7",
                surfaceColorHex = prefs.getString("surfaceColorHex", "#0F172A") ?: "#0F172A",
                glassOpacity = prefs.getFloat("glassOpacity", 0.35f).toDouble(),
                blurRadiusDp = prefs.getFloat("blurRadiusDp", 24f).toDouble(),
                specularIntensity = prefs.getFloat("specularIntensity", 0.85f).toDouble(),
                cornerRadiusDp = prefs.getFloat("cornerRadiusDp", 28f).toDouble(),
                iconName = prefs.getString("iconName", "WbSunny") ?: "WbSunny",
                customIconBase64 = prefs.getString("customIconBase64", null),
                dataSourceType = prefs.getString("dataSourceType", WidgetDataSourceType.GOOGLE_WEATHER.name)
                    ?: WidgetDataSourceType.GOOGLE_WEATHER.name,
                sourcePackageHint = prefs.getString("sourcePackageHint", "") ?: "",
                tapBehavior = prefs.getString("tapBehavior", WidgetTapBehavior.REFRESH_WIDGET.name)
                    ?: WidgetTapBehavior.REFRESH_WIDGET.name,
                isAnimated = prefs.getBoolean("isAnimated", true),
                animationStyle = prefs.getString("animationStyle", WidgetAnimationStyle.LIQUID_SHIMMER.name)
                    ?: WidgetAnimationStyle.LIQUID_SHIMMER.name,
                fontFamily = prefs.getString("fontFamily", WidgetFontFamily.GOOGLE_SANS.name)
                    ?: WidgetFontFamily.GOOGLE_SANS.name,
                clockVariation = prefs.getString("clockVariation", WidgetClockVariation.DIGITAL_HM.name)
                    ?: WidgetClockVariation.DIGITAL_HM.name,
                autoDarkAdapt = prefs.getBoolean("autoDarkAdapt", true),
                showForecast3Day = prefs.getBoolean("showForecast3Day", false),
                locationName = prefs.getString("locationName", "") ?: "",
                forecastJson = if (forecastRaw.isNullOrBlank()) null else forecastRaw
            )
        }

        fun saveWidgetForAppWidgetId(context: Context, appWidgetId: Int, widget: WidgetDesign) {
            context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE).edit()
                .putString("title", widget.title)
                .putString("subtitle", widget.subtitle)
                .putString("primaryValue", widget.primaryValue)
                .putString("secondaryValue", widget.secondaryValue)
                .putString("stylePreset", widget.stylePreset)
                .putString("category", widget.category)
                .putString("sizePreset", widget.sizePreset)
                .putString("primaryColorHex", widget.primaryColorHex)
                .putString("secondaryColorHex", widget.secondaryColorHex)
                .putString("surfaceColorHex", widget.surfaceColorHex)
                .putFloat("glassOpacity", widget.glassOpacity.toFloat())
                .putFloat("blurRadiusDp", widget.blurRadiusDp.toFloat())
                .putFloat("specularIntensity", widget.specularIntensity.toFloat())
                .putFloat("cornerRadiusDp", widget.cornerRadiusDp.toFloat())
                .putString("iconName", widget.iconName)
                .putString("customIconBase64", widget.customIconBase64)
                .putString("dataSourceType", widget.dataSourceType)
                .putString("sourcePackageHint", widget.sourcePackageHint)
                .putString("tapBehavior", widget.tapBehavior)
                .putBoolean("isAnimated", widget.isAnimated)
                .putString("animationStyle", widget.animationStyle)
                .putString("fontFamily", widget.fontFamily)
                .putString("clockVariation", widget.clockVariation)
                .putBoolean("autoDarkAdapt", widget.autoDarkAdapt)
                .putBoolean("showForecast3Day", widget.showForecast3Day)
                .putString("locationName", widget.locationName)
                .putString("forecastJson", widget.forecastJson ?: "")
                .commit()
        }

        fun loadWidgetForAppWidgetId(context: Context, appWidgetId: Int): WidgetDesign {
            val prefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE)
            if (!prefs.contains("title")) {
                return loadPendingPinnedWidget(context)
            }
            val forecastRaw = prefs.getString("forecastJson", null)
            return WidgetDesign(
                title = prefs.getString("title", "Виджет") ?: "Виджет",
                subtitle = prefs.getString("subtitle", "") ?: "",
                primaryValue = prefs.getString("primaryValue", "") ?: "",
                secondaryValue = prefs.getString("secondaryValue", "") ?: "",
                stylePreset = prefs.getString("stylePreset", WidgetStylePreset.LIQUID_GLASS.name)
                    ?: WidgetStylePreset.LIQUID_GLASS.name,
                category = prefs.getString("category", WidgetCategory.WEATHER.name)
                    ?: WidgetCategory.WEATHER.name,
                sizePreset = prefs.getString("sizePreset", WidgetSizePreset.WIDE_4X2.name)
                    ?: WidgetSizePreset.WIDE_4X2.name,
                primaryColorHex = prefs.getString("primaryColorHex", "#38BDF8") ?: "#38BDF8",
                secondaryColorHex = prefs.getString("secondaryColorHex", "#A855F7") ?: "#A855F7",
                surfaceColorHex = prefs.getString("surfaceColorHex", "#0F172A") ?: "#0F172A",
                glassOpacity = prefs.getFloat("glassOpacity", 0.35f).toDouble(),
                blurRadiusDp = prefs.getFloat("blurRadiusDp", 24f).toDouble(),
                specularIntensity = prefs.getFloat("specularIntensity", 0.85f).toDouble(),
                cornerRadiusDp = prefs.getFloat("cornerRadiusDp", 28f).toDouble(),
                iconName = prefs.getString("iconName", "WbSunny") ?: "WbSunny",
                customIconBase64 = prefs.getString("customIconBase64", null),
                dataSourceType = prefs.getString("dataSourceType", WidgetDataSourceType.GOOGLE_WEATHER.name)
                    ?: WidgetDataSourceType.GOOGLE_WEATHER.name,
                sourcePackageHint = prefs.getString("sourcePackageHint", "") ?: "",
                tapBehavior = prefs.getString("tapBehavior", WidgetTapBehavior.REFRESH_WIDGET.name)
                    ?: WidgetTapBehavior.REFRESH_WIDGET.name,
                isAnimated = prefs.getBoolean("isAnimated", true),
                animationStyle = prefs.getString("animationStyle", WidgetAnimationStyle.LIQUID_SHIMMER.name)
                    ?: WidgetAnimationStyle.LIQUID_SHIMMER.name,
                fontFamily = prefs.getString("fontFamily", WidgetFontFamily.GOOGLE_SANS.name)
                    ?: WidgetFontFamily.GOOGLE_SANS.name,
                clockVariation = prefs.getString("clockVariation", WidgetClockVariation.DIGITAL_HM.name)
                    ?: WidgetClockVariation.DIGITAL_HM.name,
                autoDarkAdapt = prefs.getBoolean("autoDarkAdapt", true),
                showForecast3Day = prefs.getBoolean("showForecast3Day", false),
                locationName = prefs.getString("locationName", "") ?: "",
                forecastJson = if (forecastRaw.isNullOrBlank()) null else forecastRaw
            )
        }

        fun saveActiveWidgetToLocalCache(context: Context, widget: WidgetDesign) {
            savePendingPinnedWidget(context, widget)
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, AuraAppWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                val prefs = context.getSharedPreferences(getWidgetPrefsName(id), Context.MODE_PRIVATE)
                if (!prefs.contains("title")) {
                    saveWidgetForAppWidgetId(context, id, widget)
                } else if (ids.size == 1) {
                    val existing = loadWidgetForAppWidgetId(context, id)
                    if (existing.category.equals(widget.category, ignoreCase = true)) {
                        saveWidgetForAppWidgetId(context, id, widget)
                    }
                }
                updateAppWidget(context, manager, id)
            }
            scheduleNextMinuteAlarm(context)
        }

        fun requestPinWidgetToLauncher(context: Context, widget: WidgetDesign): Boolean {
            savePendingPinnedWidget(context, widget)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
                val myProvider = ComponentName(context, AuraAppWidgetProvider::class.java)
                return appWidgetManager.requestPinAppWidget(myProvider, null, null)
            }
            return false
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(getWidgetPrefsName(appWidgetId), Context.MODE_PRIVATE)
            val widget = loadWidgetForAppWidgetId(context, appWidgetId)
            val phase = prefs.getFloat("animPhase", 0.25f)

            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isSystemDark = uiMode == Configuration.UI_MODE_NIGHT_YES

            val telemetry = AppDataSourceManager.resolveLiveTelemetry(context, widget)

            // Dynamically determine widget dimensions from launcher options without squashing or stretching
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val isPortrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val minW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
            val maxW = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
            val minH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
            val maxH = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0

            val density = context.resources.displayMetrics.density
            val defaultWidthDp = when (WidgetSizePreset.fromString(widget.sizePreset)) {
                WidgetSizePreset.SMALL_2X2 -> 160
                WidgetSizePreset.WIDE_4X2 -> 320
                WidgetSizePreset.LARGE_4X4 -> 320
            }
            val defaultHeightDp = when (WidgetSizePreset.fromString(widget.sizePreset)) {
                WidgetSizePreset.SMALL_2X2 -> 160
                WidgetSizePreset.WIDE_4X2 -> 140
                WidgetSizePreset.LARGE_4X4 -> 280
            }

            val cellWidthDp = when {
                isPortrait && minW > 30 -> minW
                !isPortrait && maxW > 30 -> maxW
                minW > 30 -> minW
                else -> defaultWidthDp
            }
            val cellHeightDp = when {
                isPortrait && maxH > 30 -> maxH
                !isPortrait && minH > 30 -> minH
                minH > 30 -> minH
                else -> defaultHeightDp
            }

            val targetWidthPx = ((cellWidthDp * density) * 1.5f).toInt().coerceIn(360, 1600)
            val targetHeightPx = ((cellHeightDp * density) * 1.5f).toInt().coerceIn(240, 1600)

            val isClock = widget.category.equals("CLOCK", ignoreCase = true) ||
                widget.dataSourceType == WidgetDataSourceType.LIVE_CLOCK.name
            val clockVar = WidgetClockVariation.fromString(widget.clockVariation)
            val isInteractiveChrono = isClock && (clockVar == WidgetClockVariation.STOPWATCH || clockVar == WidgetClockVariation.TIMER)

            // For DIGITAL_HM and DIGITAL_HMS, draw time with the exact custom font on the bitmap!
            // Only skip center text for Stopwatch and Timer where interactive Chronometer buttons are needed.
            val highResBitmap = WidgetBitmapPainter.renderHighResWidgetBitmap(
                widget = widget,
                telemetry = telemetry,
                effectiveDark = isSystemDark,
                animationPhase = phase,
                widthPx = targetWidthPx,
                heightPx = targetHeightPx,
                context = context,
                skipCenterText = isInteractiveChrono
            )

            val views = RemoteViews(context.packageName, R.layout.aura_app_widget)
            views.setImageViewBitmap(R.id.widget_canvas_image, highResBitmap)

            // Always hide TextClock views so the custom font on the bitmap is visible without distortion or Roboto fallback
            allClockHmIds.forEach { views.setViewVisibility(it, View.GONE) }
            allClockHmsIds.forEach { views.setViewVisibility(it, View.GONE) }

            if (isInteractiveChrono) {
                views.setViewVisibility(R.id.widget_clock_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_chronometer_container, View.VISIBLE)
                allChronometerIds.forEach { views.setViewVisibility(it, View.GONE) }

                val font = WidgetFontFamily.fromString(widget.fontFamily)

                when (clockVar) {
                    WidgetClockVariation.STOPWATCH -> {
                        views.setViewVisibility(R.id.widget_chronometer_container, View.VISIBLE)
                        val chronoId = getChronometerViewId(font)
                        views.setViewVisibility(chronoId, View.VISIBLE)

                        val isRunning = prefs.getBoolean("stopwatch_running", false)
                        val accumulated = prefs.getLong("stopwatch_accumulated_ms", 0L)
                        val now = SystemClock.elapsedRealtime()
                        val baseTime = if (isRunning) {
                            prefs.getLong("stopwatch_base", now)
                        } else {
                            now - accumulated
                        }

                        views.setChronometer(chronoId, baseTime, null, isRunning)
                        views.setTextViewText(R.id.widget_btn_toggle, if (isRunning) "Пауза" else "Старт")

                        // Bind stopwatch interactive intents
                        val toggleIntent = Intent(context, AuraAppWidgetProvider::class.java).apply {
                            action = ACTION_STOPWATCH_TOGGLE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        val pendingToggle = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 20 + 1,
                            toggleIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_btn_toggle, pendingToggle)

                        val resetIntent = Intent(context, AuraAppWidgetProvider::class.java).apply {
                            action = ACTION_STOPWATCH_RESET
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        val pendingReset = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 20 + 2,
                            resetIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_btn_reset, pendingReset)
                    }

                    WidgetClockVariation.TIMER -> {
                        views.setViewVisibility(R.id.widget_chronometer_container, View.VISIBLE)
                        val chronoId = getChronometerViewId(font)
                        views.setViewVisibility(chronoId, View.VISIBLE)

                        val isRunning = prefs.getBoolean("timer_running", false)
                        val duration = prefs.getLong("timer_duration_ms", 300_000L)
                        val remaining = prefs.getLong("timer_remaining_ms", duration)
                        val now = SystemClock.elapsedRealtime()
                        val targetTime = if (isRunning) {
                            prefs.getLong("timer_target", now + remaining)
                        } else {
                            now + remaining
                        }

                        views.setChronometer(chronoId, targetTime, null, isRunning)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            views.setChronometerCountDown(chronoId, true)
                        }
                        views.setTextViewText(R.id.widget_btn_toggle, if (isRunning) "Пауза" else "Старт")

                        // Bind timer interactive intents
                        val toggleIntent = Intent(context, AuraAppWidgetProvider::class.java).apply {
                            action = ACTION_TIMER_TOGGLE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        val pendingToggle = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 20 + 3,
                            toggleIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_btn_toggle, pendingToggle)

                        val resetIntent = Intent(context, AuraAppWidgetProvider::class.java).apply {
                            action = ACTION_TIMER_RESET
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        val pendingReset = PendingIntent.getBroadcast(
                            context,
                            appWidgetId * 20 + 4,
                            resetIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_btn_reset, pendingReset)
                    }

                    else -> {}
                }
            } else {
                views.setViewVisibility(R.id.widget_clock_container, View.GONE)
            }

            // Tap handling: NEVER opens AuraWidget AI
            val tapBehavior = WidgetTapBehavior.fromString(widget.tapBehavior)
            if (tapBehavior == WidgetTapBehavior.NONE) {
                val noopIntent = Intent(context, AuraAppWidgetProvider::class.java).apply {
                    action = "com.example.widget.ACTION_NOOP"
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingNoop = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 20 + 5,
                    noopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingNoop)
            } else {
                val broadcastIntent = Intent(context, AuraAppWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_TAP_INTERACTION
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val pendingBroadcast = PendingIntent.getBroadcast(
                    context,
                    appWidgetId * 20 + 6,
                    broadcastIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingBroadcast)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            scheduleNextMinuteAlarm(context)
        }
    }
}
