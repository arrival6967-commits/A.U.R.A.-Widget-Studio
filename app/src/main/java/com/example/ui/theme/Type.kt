package com.example.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.WidgetFontFamily
import java.util.concurrent.ConcurrentHashMap

val GoogleSansFontFamily = FontFamily(
    Font(R.font.google_sans, FontWeight.Normal),
    Font(R.font.google_sans, FontWeight.Medium),
    Font(R.font.google_sans, FontWeight.SemiBold),
    Font(R.font.google_sans, FontWeight.Bold)
)

val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat, FontWeight.Normal),
    Font(R.font.montserrat, FontWeight.Medium),
    Font(R.font.montserrat, FontWeight.SemiBold),
    Font(R.font.montserrat, FontWeight.Bold)
)

val PlayfairDisplayFontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(R.font.playfair_display, FontWeight.Medium),
    Font(R.font.playfair_display, FontWeight.SemiBold),
    Font(R.font.playfair_display, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val ComfortaaFontFamily = FontFamily(
    Font(R.font.comfortaa, FontWeight.Normal),
    Font(R.font.comfortaa, FontWeight.Medium),
    Font(R.font.comfortaa, FontWeight.SemiBold),
    Font(R.font.comfortaa, FontWeight.Bold)
)

val RubikFontFamily = FontFamily(
    Font(R.font.rubik, FontWeight.Normal),
    Font(R.font.rubik, FontWeight.Medium),
    Font(R.font.rubik, FontWeight.SemiBold),
    Font(R.font.rubik, FontWeight.Bold)
)

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

@FontRes
fun WidgetFontFamily.fontResId(): Int = when (this) {
    WidgetFontFamily.GOOGLE_SANS -> R.font.google_sans
    WidgetFontFamily.MONTSERRAT -> R.font.montserrat
    WidgetFontFamily.PLAYFAIR_DISPLAY -> R.font.playfair_display
    WidgetFontFamily.JETBRAINS_MONO -> R.font.jetbrains_mono
    WidgetFontFamily.COMFORTAA -> R.font.comfortaa
    WidgetFontFamily.RUBIK -> R.font.rubik
    WidgetFontFamily.SPACE_GROTESK -> R.font.space_grotesk
    WidgetFontFamily.PLUS_JAKARTA -> R.font.plus_jakarta_sans
}

fun WidgetFontFamily.toComposeFontFamily(): FontFamily = when (this) {
    WidgetFontFamily.GOOGLE_SANS -> GoogleSansFontFamily
    WidgetFontFamily.MONTSERRAT -> MontserratFontFamily
    WidgetFontFamily.PLAYFAIR_DISPLAY -> PlayfairDisplayFontFamily
    WidgetFontFamily.JETBRAINS_MONO -> JetBrainsMonoFontFamily
    WidgetFontFamily.COMFORTAA -> ComfortaaFontFamily
    WidgetFontFamily.RUBIK -> RubikFontFamily
    WidgetFontFamily.SPACE_GROTESK -> SpaceGroteskFontFamily
    WidgetFontFamily.PLUS_JAKARTA -> PlusJakartaSansFontFamily
}

private val typefaceCache = ConcurrentHashMap<String, Typeface>()

fun WidgetFontFamily.loadAndroidTypeface(context: Context, style: Int = Typeface.NORMAL): Typeface {
    val cacheKey = "${this.name}_$style"
    typefaceCache[cacheKey]?.let { return it }
    return try {
        val base = ResourcesCompat.getFont(context, this.fontResId())
        if (base != null) {
            typefaceCache[cacheKey] = base
            base
        } else {
            Typeface.create(Typeface.DEFAULT, style)
        }
    } catch (e: Exception) {
        Typeface.create(Typeface.DEFAULT, style)
    }
}

// Global application typography using Google Sans across all menus, headers, tabs, and controls
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)
