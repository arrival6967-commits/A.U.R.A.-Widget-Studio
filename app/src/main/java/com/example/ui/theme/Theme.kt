package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.data.DarkThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = CyanGlowDark,
    onPrimary = OnCyanGlowDark,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = OnCyanContainerDark,
    secondary = VioletExpressiveDark,
    onSecondary = OnVioletExpressiveDark,
    secondaryContainer = VioletContainerDark,
    onSecondaryContainer = OnVioletContainerDark,
    tertiary = RoseAccentDark,
    onTertiary = OnRoseAccentDark,
    tertiaryContainer = RoseContainerDark,
    onTertiaryContainer = OnRoseContainerDark,
    background = ObsidianBackground,
    onBackground = OnObsidianText,
    surface = ObsidianSurface,
    onSurface = OnObsidianText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = OnObsidianVariant
)

private val LightColorScheme = lightColorScheme(
    primary = CyanGlowLight,
    onPrimary = OnCyanGlowLight,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = OnCyanContainerLight,
    secondary = VioletExpressiveLight,
    onSecondary = OnVioletExpressiveLight,
    secondaryContainer = VioletContainerLight,
    onSecondaryContainer = OnVioletContainerLight,
    tertiary = RoseAccentLight,
    onTertiary = OnRoseAccentLight,
    tertiaryContainer = RoseContainerLight,
    onTertiaryContainer = OnRoseContainerLight,
    background = AlabasterBackground,
    onBackground = OnAlabasterText,
    surface = AlabasterSurface,
    onSurface = OnAlabasterText,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = OnAlabasterVariant
)

@Composable
fun AuraWidgetTheme(
    darkThemeMode: DarkThemeMode = DarkThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (darkThemeMode) {
        DarkThemeMode.SYSTEM -> systemDark
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
