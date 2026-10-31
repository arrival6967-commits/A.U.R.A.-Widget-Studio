package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.DarkThemeMode
import com.example.data.WallpaperPreset
import com.example.data.WidgetDataSourceType
import com.example.data.WidgetDesign
import com.example.data.WidgetSizePreset
import com.example.data.WidgetStylePreset
import com.example.util.AppDataSourceManager
import kotlinx.coroutines.delay

fun parseComposeColor(hex: String, fallback: Color = Color(0xFF38BDF8)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

fun resolveMaterialIcon(iconName: String): ImageVector {
    return when (iconName) {
        "WbSunny" -> Icons.Default.WbSunny
        "AccessTime" -> Icons.Default.AccessTime
        "EditNote" -> Icons.Default.EditNote
        "DirectionsRun" -> Icons.AutoMirrored.Filled.DirectionsRun
        "MusicNote" -> Icons.Default.MusicNote
        "BatteryChargingFull" -> Icons.Default.BatteryChargingFull
        "CalendarMonth" -> Icons.Default.CalendarMonth
        "TrendingUp" -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Default.AutoAwesome
    }
}

@Composable
fun InteractiveWallpaperStage(
    widget: WidgetDesign,
    wallpaperPreset: WallpaperPreset,
    customWallpaperUri: Uri?,
    darkThemeMode: DarkThemeMode,
    modifier: Modifier = Modifier
) {
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (darkThemeMode) {
        DarkThemeMode.SYSTEM -> systemDark
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wallpaper_orb_motion")
    val orbShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_shift"
    )

    val paletteColors = remember(wallpaperPreset) {
        wallpaperPreset.defaultPalette.map { parseComposeColor(it) }
    }

    val sizePreset = WidgetSizePreset.fromString(widget.sizePreset)
    val stageHeight = when {
        sizePreset == WidgetSizePreset.SMALL_2X2 -> 250.dp
        widget.showForecast3Day || sizePreset == WidgetSizePreset.LARGE_4X4 -> 300.dp
        else -> 240.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stageHeight)
            .clip(RoundedCornerShape(32.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .testTag("interactive_wallpaper_stage"),
        contentAlignment = Alignment.Center
    ) {
        if (customWallpaperUri != null && wallpaperPreset == WallpaperPreset.CUSTOM_PHOTO) {
            AsyncImage(
                model = customWallpaperUri,
                contentDescription = stringResource(R.string.cd_widget_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val baseBg = if (effectiveDark) Color(0xFF060913) else Color(0xFFE2E8F0)
                drawRect(color = baseBg)

                val c0 = paletteColors.getOrElse(0) { Color(0xFF38BDF8) }
                val c1 = paletteColors.getOrElse(1) { Color(0xFFA855F7) }
                val c2 = paletteColors.getOrElse(2) { Color(0xFFF472B6) }
                val c3 = paletteColors.getOrElse(4) { Color(0xFF22D3EE) }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c0.copy(alpha = 0.72f), Color.Transparent),
                        center = Offset(
                            x = size.width * (0.2f + 0.25f * orbShift),
                            y = size.height * (0.28f + 0.15f * orbShift)
                        ),
                        radius = size.maxDimension * 0.62f
                    ),
                    radius = size.maxDimension * 0.62f
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c1.copy(alpha = 0.68f), Color.Transparent),
                        center = Offset(
                            x = size.width * (0.82f - 0.22f * orbShift),
                            y = size.height * (0.72f - 0.18f * orbShift)
                        ),
                        radius = size.maxDimension * 0.58f
                    ),
                    radius = size.maxDimension * 0.58f
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c2.copy(alpha = 0.52f), Color.Transparent),
                        center = Offset(
                            x = size.width * 0.55f,
                            y = size.height * (0.2f + 0.4f * orbShift)
                        ),
                        radius = size.maxDimension * 0.46f
                    ),
                    radius = size.maxDimension * 0.46f
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c3.copy(alpha = 0.40f), Color.Transparent),
                        center = Offset(
                            x = size.width * 0.18f,
                            y = size.height * 0.85f
                        ),
                        radius = size.maxDimension * 0.42f
                    ),
                    radius = size.maxDimension * 0.42f
                )
            }
        }

        // Top-left status indicator showing active wallpaper & data source
        Surface(
            color = Color.Black.copy(alpha = 0.42f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(parseComposeColor(widget.primaryColorHex))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${WidgetStylePreset.fromString(widget.stylePreset).label} • ${WidgetDataSourceType.fromString(widget.dataSourceType).label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }

        // Exact 1:1 Live Widget Card rendered by WidgetBitmapPainter
        LiveWidgetCard(
            widget = widget,
            effectiveDark = effectiveDark,
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .then(
                    when (sizePreset) {
                        WidgetSizePreset.SMALL_2X2 -> Modifier
                            .size(190.dp)
                        WidgetSizePreset.WIDE_4X2 -> Modifier
                            .fillMaxWidth()
                            .height(if (widget.showForecast3Day) 220.dp else 178.dp)
                        WidgetSizePreset.LARGE_4X4 -> Modifier
                            .fillMaxWidth()
                            .height(232.dp)
                    }
                )
        )
    }
}

@Composable
fun LiveWidgetCard(
    widget: WidgetDesign,
    effectiveDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Live 1-second ticker so clocks and battery telemetry genuinely tick in real time
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(widget.dataSourceType, widget.isAnimated) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "widget_live_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val effectivePhase = if (widget.isAnimated) {
        // Quantize slightly to 25fps for smooth rendering without excessive bitmap churn
        ((animPhase * 25f).toInt() / 25f)
    } else {
        0.25f
    }

    val sizePreset = WidgetSizePreset.fromString(widget.sizePreset)
    val is3DayForecast = widget.showForecast3Day
    val targetWidthPx = when (sizePreset) {
        WidgetSizePreset.SMALL_2X2 -> 600
        WidgetSizePreset.WIDE_4X2 -> 960
        WidgetSizePreset.LARGE_4X4 -> 960
    }
    val targetHeightPx = when {
        sizePreset == WidgetSizePreset.SMALL_2X2 -> 600
        is3DayForecast || sizePreset == WidgetSizePreset.LARGE_4X4 -> 540
        else -> 460
    }

    val telemetry = remember(widget, nowMillis / 1000L) {
        AppDataSourceManager.resolveLiveTelemetry(context, widget, nowMillis)
    }

    val renderedBitmap = remember(
        widget,
        telemetry,
        effectiveDark,
        effectivePhase,
        targetWidthPx,
        targetHeightPx
    ) {
        WidgetBitmapPainter.renderHighResWidgetBitmap(
            widget = widget,
            telemetry = telemetry,
            effectiveDark = effectiveDark,
            animationPhase = effectivePhase,
            widthPx = targetWidthPx,
            heightPx = targetHeightPx,
            context = context
        ).asImageBitmap()
    }

    Box(
        modifier = modifier.testTag("live_widget_card"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = renderedBitmap,
            contentDescription = widget.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
