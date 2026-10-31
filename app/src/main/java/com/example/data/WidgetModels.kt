package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WidgetStylePreset(val label: String, val badge: String) {
    LIQUID_GLASS("Liquid Glass", "Apple iOS 26 Style"),
    M3_EXPRESSIVE("M3 Expressive", "Android 16 Dynamic"),
    CYBER_NEON("Cyber Obsidian", "Neon Chromatic"),
    MINIMAL_FROST("Minimal Frost", "Pure Translucent");

    companion object {
        fun fromString(value: String?): WidgetStylePreset =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: LIQUID_GLASS
    }
}

enum class WidgetCategory(val label: String) {
    WEATHER("Погода"),
    CLOCK("Часы и Время"),
    NOTES("Заметки"),
    FITNESS("Активность"),
    MUSIC("Музыка"),
    BATTERY("Заряд и Система"),
    CALENDAR("События"),
    FINANCE("Финансы"),
    CUSTOM("Кастомный");

    companion object {
        fun fromString(value: String?): WidgetCategory =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: CUSTOM
    }
}

enum class WidgetSizePreset(val label: String, val gridLabel: String) {
    SMALL_2X2("Компактный", "2×2"),
    WIDE_4X2("Широкий", "4×2"),
    LARGE_4X4("Большой", "4×4");

    companion object {
        fun fromString(value: String?): WidgetSizePreset =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: WIDE_4X2
    }
}

enum class WidgetDataSourceType(
    val label: String,
    val providerSubtitle: String,
    val defaultPackage: String,
    val iconHint: String
) {
    LIVE_CLOCK(
        "Живые часы (Реальное время)",
        "Системное время с секундным ходом",
        "com.google.android.deskclock",
        "AccessTime"
    ),
    GOOGLE_WEATHER(
        "Google Погода",
        "Метеоданные, температура и УФ-индекс",
        "com.google.android.googlequicksearchbox",
        "WbSunny"
    ),
    NOTES(
        "Заметки",
        "Быстрые заметки Google Keep",
        "com.google.android.keep",
        "EditNote"
    ),
    SYSTEM_BATTERY(
        "Батарея и Телеметрия",
        "Реальный уровень заряда, вольтаж и статус питания",
        "com.android.settings",
        "BatteryChargingFull"
    ),
    MEDIA_SESSION(
        "Активный плеер (Музыка)",
        "Текущий трек из Spotify / YouTube Music / системы",
        "com.google.android.music",
        "MusicNote"
    ),
    CALENDAR_EVENTS(
        "Календарь",
        "Сетка месяца 2×2 с текущим днем",
        "com.google.android.calendar",
        "CalendarMonth"
    ),
    STATIC_CUSTOM(
        "Свои данные / AI Текст",
        "Пользовательский текст или сгенерированный блок",
        "",
        "AutoAwesome"
    );

    companion object {
        fun fromString(value: String?): WidgetDataSourceType = when (value?.uppercase()) {
            "GOOGLE_KEEP", "MICROSOFT_ONENOTE", "SAMSUNG_NOTES", "XIAOMI_NOTES", "OPPO_NOTES", "NOTES" -> NOTES
            else -> entries.find { it.name.equals(value, ignoreCase = true) } ?: GOOGLE_WEATHER
        }
    }
}

enum class WidgetClockVariation(val label: String, val subtitle: String) {
    DIGITAL_HM("Только часы и минуты", "Цифровые часы ЧЧ:ММ без секунд"),
    DIGITAL_HMS("Часы, минуты и секунды", "Цифровые часы ЧЧ:ММ:СС с секундами"),
    TIMER("Таймер", "Обратный отсчет со стартом и сбросом"),
    STOPWATCH("Секундомер", "Точный секундомер реального времени");

    companion object {
        fun fromString(value: String?): WidgetClockVariation =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: DIGITAL_HM
    }
}

enum class WidgetTapBehavior(val label: String, val subtitle: String) {
    REFRESH_WIDGET(
        "Обновить данные / Кадр анимации",
        "Не открывает A.U.R.A. Widgets — мгновенно обновляет виджет на месте"
    ),
    OPEN_SOURCE_APP(
        "Открыть приложение-источник",
        "Открывает Погоду, Заметки (Keep/OneNote/Samsung/Xiaomi) или Часы"
    ),
    NONE(
        "Ничего не открывать",
        "Полная блокировка перехода в приложение при касании виджета"
    );

    companion object {
        fun fromString(value: String?): WidgetTapBehavior =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: REFRESH_WIDGET
    }
}

enum class WidgetAnimationStyle(val label: String) {
    LIQUID_SHIMMER("Перелив Liquid Glass"),
    PULSE_GLOW("Неоновое дыхание"),
    TICKING_SWEEP("Секундный импульс"),
    ORBITAL_WAVE("Орбитальная волна"),
    NONE("Без анимации");

    companion object {
        fun fromString(value: String?): WidgetAnimationStyle =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: LIQUID_SHIMMER
    }
}

enum class WidgetFontFamily(
    val label: String,
    val styleBadge: String,
    val fontResName: String
) {
    GOOGLE_SANS("Google Sans", "Фирменный стиль Google", "google_sans"),
    MONTSERRAT("Montserrat", "Геометрический модерн", "montserrat"),
    PLAYFAIR_DISPLAY("Playfair Display", "Журнальная антиква", "playfair_display"),
    JETBRAINS_MONO("JetBrains Mono", "Техно-моноширинный", "jetbrains_mono"),
    COMFORTAA("Comfortaa", "Мягкий округлый", "comfortaa"),
    RUBIK("Rubik", "Выразительный гротеск", "rubik"),
    SPACE_GROTESK("Space Grotesk", "Инженерный дисплей", "space_grotesk"),
    PLUS_JAKARTA("Plus Jakarta Sans", "Чистый интерфейс", "plus_jakarta_sans");

    companion object {
        fun fromString(value: String?): WidgetFontFamily =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: GOOGLE_SANS
    }
}

enum class WallpaperPreset(
    val label: String,
    val subtitle: String,
    val defaultPalette: List<String>
) {
    AURORA_BOREALIS(
        "Aurora Liquid",
        "Холодный циан, глубокий индиго и неон",
        listOf("#38BDF8", "#A855F7", "#F472B6", "#0F172A", "#22D3EE")
    ),
    SOLAR_FLARE(
        "Sunset Expressive",
        "Теплый янтарь, терракота и закатный пурпур",
        listOf("#FB923C", "#F43F5E", "#A855F7", "#1E1B4B", "#FBBF24")
    ),
    CYBER_OBSIDIAN(
        "Obsidian Chrome",
        "Изумрудный неон, жидкий хром и графит",
        listOf("#10B981", "#06B6D4", "#6366F1", "#090D16", "#34D399")
    ),
    EMERALD_MIST(
        "Forest Glass",
        "Мягкий шалфей, бирюза и утренний туман",
        listOf("#2DD4BF", "#4ADE80", "#38BDF8", "#064E3B", "#A7F3D0")
    ),
    CUSTOM_PHOTO(
        "Свои обои",
        "Авто-извлечение палитры из вашего фото",
        listOf("#60A5FA", "#C084FC", "#F472B6", "#1E293B", "#38BDF8")
    );

    companion object {
        fun fromString(value: String?): WallpaperPreset =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: AURORA_BOREALIS
    }
}

enum class DarkThemeMode(val label: String) {
    SYSTEM("Авто (Система)"),
    LIGHT("Светлая"),
    DARK("Темная");

    companion object {
        fun fromString(value: String?): DarkThemeMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

@IgnoreExtraProperties
data class WidgetDesign(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Google Погода • Москва",
    val subtitle: String = "Ощущается как +19°C • Ясно • УФ 2",
    val primaryValue: String = "+22°C",
    val secondaryValue: String = "Google Weather",
    val prompt: String = "Погодный виджет в стиле Liquid Glass с данными Google Погода",
    val stylePreset: String = WidgetStylePreset.LIQUID_GLASS.name,
    val category: String = WidgetCategory.WEATHER.name,
    val sizePreset: String = WidgetSizePreset.WIDE_4X2.name,
    val primaryColorHex: String = "#38BDF8",
    val secondaryColorHex: String = "#A855F7",
    val surfaceColorHex: String = "#0F172A",
    val glassOpacity: Double = 0.35,
    val blurRadiusDp: Double = 24.0,
    val specularIntensity: Double = 0.85,
    val cornerRadiusDp: Double = 28.0,
    val iconName: String = "WbSunny",
    val customIconBase64: String? = null,
    val dataSourceType: String = WidgetDataSourceType.GOOGLE_WEATHER.name,
    val sourcePackageHint: String = WidgetDataSourceType.GOOGLE_WEATHER.defaultPackage,
    val tapBehavior: String = WidgetTapBehavior.REFRESH_WIDGET.name,
    val isAnimated: Boolean = true,
    val animationStyle: String = WidgetAnimationStyle.LIQUID_SHIMMER.name,
    val fontFamily: String = WidgetFontFamily.GOOGLE_SANS.name,
    val showForecast3Day: Boolean = false,
    val locationName: String = "Москва",
    val forecastJson: String? = null,
    val clockVariation: String = WidgetClockVariation.DIGITAL_HM.name,
    val autoDarkAdapt: Boolean = true,
    val isPinned: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreatePayload(uid: String): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "userId" to uid,
            "title" to title.take(80).ifBlank { "Виджет" },
            "subtitle" to subtitle.take(120),
            "primaryValue" to primaryValue.take(60).ifBlank { "100%" },
            "secondaryValue" to secondaryValue.take(80),
            "prompt" to prompt.take(500).ifBlank { "AI Widget" },
            "stylePreset" to WidgetStylePreset.fromString(stylePreset).name,
            "category" to WidgetCategory.fromString(category).name,
            "sizePreset" to WidgetSizePreset.fromString(sizePreset).name,
            "primaryColorHex" to sanitizeHex(primaryColorHex, "#38BDF8"),
            "secondaryColorHex" to sanitizeHex(secondaryColorHex, "#A855F7"),
            "surfaceColorHex" to sanitizeHex(surfaceColorHex, "#0F172A"),
            "glassOpacity" to glassOpacity.coerceIn(0.05, 1.0),
            "blurRadiusDp" to blurRadiusDp.coerceIn(0.0, 40.0),
            "specularIntensity" to specularIntensity.coerceIn(0.0, 1.0),
            "cornerRadiusDp" to cornerRadiusDp.coerceIn(8.0, 48.0),
            "iconName" to iconName.take(60).ifBlank { "AutoAwesome" },
            "dataSourceType" to WidgetDataSourceType.fromString(dataSourceType).name,
            "sourcePackageHint" to sourcePackageHint.take(120),
            "tapBehavior" to WidgetTapBehavior.fromString(tapBehavior).name,
            "isAnimated" to isAnimated,
            "animationStyle" to WidgetAnimationStyle.fromString(animationStyle).name,
            "fontFamily" to WidgetFontFamily.fromString(fontFamily).name,
            "showForecast3Day" to showForecast3Day,
            "locationName" to locationName.take(80),
            "clockVariation" to WidgetClockVariation.fromString(clockVariation).name,
            "autoDarkAdapt" to autoDarkAdapt,
            "isPinned" to isPinned,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!customIconBase64.isNullOrBlank()) {
            base["customIconBase64"] = customIconBase64.take(150000)
        }
        if (!forecastJson.isNullOrBlank()) {
            base["forecastJson"] = forecastJson.take(5000)
        }
        return base
    }

    fun toUpdatePayload(): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "title" to title.take(80).ifBlank { "Виджет" },
            "subtitle" to subtitle.take(120),
            "primaryValue" to primaryValue.take(60).ifBlank { "100%" },
            "secondaryValue" to secondaryValue.take(80),
            "prompt" to prompt.take(500).ifBlank { "AI Widget" },
            "stylePreset" to WidgetStylePreset.fromString(stylePreset).name,
            "category" to WidgetCategory.fromString(category).name,
            "sizePreset" to WidgetSizePreset.fromString(sizePreset).name,
            "primaryColorHex" to sanitizeHex(primaryColorHex, "#38BDF8"),
            "secondaryColorHex" to sanitizeHex(secondaryColorHex, "#A855F7"),
            "surfaceColorHex" to sanitizeHex(surfaceColorHex, "#0F172A"),
            "glassOpacity" to glassOpacity.coerceIn(0.05, 1.0),
            "blurRadiusDp" to blurRadiusDp.coerceIn(0.0, 40.0),
            "specularIntensity" to specularIntensity.coerceIn(0.0, 1.0),
            "cornerRadiusDp" to cornerRadiusDp.coerceIn(8.0, 48.0),
            "iconName" to iconName.take(60).ifBlank { "AutoAwesome" },
            "dataSourceType" to WidgetDataSourceType.fromString(dataSourceType).name,
            "sourcePackageHint" to sourcePackageHint.take(120),
            "tapBehavior" to WidgetTapBehavior.fromString(tapBehavior).name,
            "isAnimated" to isAnimated,
            "animationStyle" to WidgetAnimationStyle.fromString(animationStyle).name,
            "fontFamily" to WidgetFontFamily.fromString(fontFamily).name,
            "showForecast3Day" to showForecast3Day,
            "locationName" to locationName.take(80),
            "clockVariation" to WidgetClockVariation.fromString(clockVariation).name,
            "autoDarkAdapt" to autoDarkAdapt,
            "isPinned" to isPinned,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!customIconBase64.isNullOrBlank()) {
            base["customIconBase64"] = customIconBase64.take(150000)
        }
        if (!forecastJson.isNullOrBlank()) {
            base["forecastJson"] = forecastJson.take(5000)
        }
        return base
    }
}

data class GeminiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val attachedWidget: WidgetDesign? = null,
    val isStreaming: Boolean = false
)

@IgnoreExtraProperties
data class UserWorkspace(
    @DocumentId val id: String = "workspace",
    val userId: String = "",
    val activeWallpaperPreset: String = WallpaperPreset.AURORA_BOREALIS.name,
    val extractedPaletteHex: List<String> = WallpaperPreset.AURORA_BOREALIS.defaultPalette,
    val darkThemeMode: String = DarkThemeMode.SYSTEM.name,
    val globalGlassOpacity: Double = 0.35,
    val globalBlurDp: Double = 24.0,
    val syncDeviceName: String = "Android Studio Device",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreatePayload(uid: String): Map<String, Any> {
        val safePalette = extractedPaletteHex
            .map { sanitizeHex(it, "#38BDF8") }
            .take(8)
            .ifEmpty { WallpaperPreset.AURORA_BOREALIS.defaultPalette }
        return mapOf(
            "userId" to uid,
            "activeWallpaperPreset" to WallpaperPreset.fromString(activeWallpaperPreset).name,
            "extractedPaletteHex" to safePalette,
            "darkThemeMode" to DarkThemeMode.fromString(darkThemeMode).name,
            "globalGlassOpacity" to globalGlassOpacity.coerceIn(0.05, 1.0),
            "globalBlurDp" to globalBlurDp.coerceIn(0.0, 40.0),
            "syncDeviceName" to syncDeviceName.take(80).ifBlank { "Android Device" },
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toUpdatePayload(): Map<String, Any> {
        val safePalette = extractedPaletteHex
            .map { sanitizeHex(it, "#38BDF8") }
            .take(8)
            .ifEmpty { WallpaperPreset.AURORA_BOREALIS.defaultPalette }
        return mapOf(
            "activeWallpaperPreset" to WallpaperPreset.fromString(activeWallpaperPreset).name,
            "extractedPaletteHex" to safePalette,
            "darkThemeMode" to DarkThemeMode.fromString(darkThemeMode).name,
            "globalGlassOpacity" to globalGlassOpacity.coerceIn(0.05, 1.0),
            "globalBlurDp" to globalBlurDp.coerceIn(0.0, 40.0),
            "syncDeviceName" to syncDeviceName.take(80).ifBlank { "Android Device" },
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

fun sanitizeHex(hex: String?, fallback: String): String {
    if (hex.isNullOrBlank()) return fallback
    val cleaned = if (hex.startsWith("#")) hex.trim() else "#${hex.trim()}"
    val regex = Regex("^#[0-9A-Fa-f]{6}$")
    return if (regex.matches(cleaned)) cleaned.uppercase() else fallback
}
