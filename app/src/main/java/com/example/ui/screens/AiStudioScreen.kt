package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHomeWork
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DarkThemeMode
import com.example.data.UserWorkspace
import com.example.data.WallpaperPreset
import com.example.data.WidgetAnimationStyle
import com.example.data.WidgetClockVariation
import com.example.data.WidgetDataSourceType
import com.example.data.WidgetDesign
import com.example.data.WidgetFontFamily
import com.example.data.WidgetSizePreset
import com.example.data.WidgetStylePreset
import com.example.data.WidgetTapBehavior
import com.example.ui.components.InteractiveWallpaperStage
import com.example.ui.components.parseComposeColor
import com.example.ui.theme.toComposeFontFamily
import com.example.viewmodel.AiGenerationState
import com.example.widget.AuraAppWidgetProvider

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiStudioScreen(
    activeDraft: WidgetDesign,
    workspace: UserWorkspace,
    aiState: AiGenerationState,
    customWallpaperUri: Uri?,
    onGenerateAi: (String, WidgetStylePreset) -> Unit,
    onSelectStylePreset: (WidgetStylePreset) -> Unit,
    onSelectFont: (WidgetFontFamily) -> Unit,
    onSelectSizePreset: (WidgetSizePreset) -> Unit,
    onSelectDataSource: (WidgetDataSourceType) -> Unit,
    onUpdateAnimationAndTap: (Boolean?, WidgetAnimationStyle?, WidgetTapBehavior?) -> Unit,
    onSelectQuickColor: (String) -> Unit,
    onSaveToCloud: () -> Unit,
    onOpenCustomizer: () -> Unit,
    onPinResultMessage: (String) -> Unit,
    onToggleForecast3Day: (Boolean) -> Unit = {},
    onRefreshLocationWeather: () -> Unit = {},
    onSelectClockVariation: (WidgetClockVariation) -> Unit = {}
) {
    val context = LocalContext.current
    var promptText by rememberSaveable { mutableStateOf("") }
    val selectedStyle = WidgetStylePreset.fromString(activeDraft.stylePreset)
    val selectedFont = WidgetFontFamily.fromString(activeDraft.fontFamily)
    val selectedSize = WidgetSizePreset.fromString(activeDraft.sizePreset)
    val selectedSource = WidgetDataSourceType.fromString(activeDraft.dataSourceType)
    val selectedAnimStyle = WidgetAnimationStyle.fromString(activeDraft.animationStyle)
    val selectedTapBehavior = WidgetTapBehavior.fromString(activeDraft.tapBehavior)
    val selectedClockVariation = WidgetClockVariation.fromString(activeDraft.clockVariation)
    val wallpaperPreset = WallpaperPreset.fromString(workspace.activeWallpaperPreset)
    val darkMode = DarkThemeMode.fromString(workspace.darkThemeMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("ai_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Column {
            Text(
                text = stringResource(R.string.studio_header_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.studio_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Interactive 1:1 Live Preview Stage over User's Wallpaper
        InteractiveWallpaperStage(
            widget = activeDraft,
            wallpaperPreset = wallpaperPreset,
            customWallpaperUri = customWallpaperUri,
            darkThemeMode = darkMode
        )

        // Instant Wallpaper Palette Swatch Strip + Size Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                workspace.extractedPaletteHex.take(5).forEachIndexed { idx, hex ->
                    val swatchColor = parseComposeColor(hex)
                    val isSelected = activeDraft.primaryColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { onSelectQuickColor(hex) }
                            .testTag("quick_swatch_$idx")
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WidgetSizePreset.entries.forEach { size ->
                    FilterChip(
                        selected = selectedSize == size,
                        onClick = { onSelectSizePreset(size) },
                        label = { Text(size.gridLabel) },
                        modifier = Modifier.testTag("size_chip_${size.name}")
                    )
                }
            }
        }

        // 1. Style Preset Selector (Liquid Glass vs Material 3 Expressive vs Cyber vs Frost)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.style_preset_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WidgetStylePreset.entries.forEach { preset ->
                    val isSelected = selectedStyle == preset
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectStylePreset(preset) }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .testTag("style_preset_${preset.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = preset.badge,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 1b. Widget Font Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Шрифт виджета (${selectedFont.label})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WidgetFontFamily.entries.forEach { font ->
                    val isSelected = selectedFont == font
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFont(font) },
                        label = {
                            Text(
                                text = font.label,
                                fontFamily = font.toComposeFontFamily(),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.testTag("studio_font_chip_${font.name.lowercase()}")
                    )
                }
            }
        }

        // 2. App Data Source Integration Card (Google Weather, Keep, OneNote, Samsung/Xiaomi/Oppo Notes, Live Clock)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.data_source_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${selectedSource.label}: ${selectedSource.providerSubtitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetDataSourceType.entries.forEach { source ->
                        FilterChip(
                            selected = selectedSource == source,
                            onClick = { onSelectDataSource(source) },
                            label = { Text(source.label) },
                            modifier = Modifier.testTag("data_source_chip_${source.name}")
                        )
                    }
                }

                if (selectedSource == WidgetDataSourceType.GOOGLE_WEATHER) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Прогноз на 3 дня",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Расширенная версия виджета с прогнозом на 3 ближайших дня",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = activeDraft.showForecast3Day,
                                    onCheckedChange = { onToggleForecast3Day(it) },
                                    modifier = Modifier.testTag("switch_3day_forecast")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Локация: ${activeDraft.locationName.ifBlank { "Авто (Москва)" }}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(
                                    onClick = onRefreshLocationWeather,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("btn_refresh_location_weather")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Обновить GPS/Город", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (selectedSource == WidgetDataSourceType.LIVE_CLOCK || activeDraft.category.equals("CLOCK", ignoreCase = true)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Вариация часов и времени",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                WidgetClockVariation.entries.forEach { variation ->
                                    val isSelected = selectedClockVariation == variation
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectClockVariation(variation) },
                                        label = {
                                            Text(
                                                text = variation.label,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.testTag("clock_variation_${variation.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Live Widget Animation & Non-Intrusive Tap Behavior Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Animation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Живая анимация виджета",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = activeDraft.isAnimated,
                        onCheckedChange = { enabled ->
                            onUpdateAnimationAndTap(enabled, null, null)
                        },
                        modifier = Modifier.testTag("switch_widget_animated")
                    )
                }

                if (activeDraft.isAnimated) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetAnimationStyle.entries
                            .filter { it != WidgetAnimationStyle.NONE }
                            .forEach { anim ->
                                FilterChip(
                                    selected = selectedAnimStyle == anim,
                                    onClick = { onUpdateAnimationAndTap(true, anim, null) },
                                    label = { Text(anim.label) },
                                    modifier = Modifier.testTag("anim_style_chip_${anim.name}")
                                )
                            }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Поведение при нажатии на виджет (без запуска A.U.R.A. Widgets)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WidgetTapBehavior.entries.forEach { behavior ->
                        val isSelected = selectedTapBehavior == behavior
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onUpdateAnimationAndTap(null, null, behavior) }
                                .testTag("tap_behavior_${behavior.name}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = behavior.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = behavior.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Optional Direct AI Prompt Refinement
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text(stringResource(R.string.prompt_label)) },
                    placeholder = { Text(stringResource(R.string.prompt_placeholder)) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_prompt_input")
                )

                val isGenerating = aiState is AiGenerationState.Generating
                Button(
                    onClick = { onGenerateAi(promptText, selectedStyle) },
                    enabled = !isGenerating && promptText.isNotBlank(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_ai_widget_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.action_generating))
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_generate_ai),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Action Bar: Save to Cloud + Pin 1:1 Widget to Launcher + Fine-tune Glass
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    AuraAppWidgetProvider.saveActiveWidgetToLocalCache(context, activeDraft)
                    onSaveToCloud()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("save_cloud_button")
            ) {
                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_save_cloud),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onOpenCustomizer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("open_glass_customizer_button")
            ) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.nav_customizer),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Button(
            onClick = {
                val pinned = AuraAppWidgetProvider.requestPinWidgetToLauncher(context, activeDraft)
                if (pinned) {
                    onPinResultMessage("1:1 виджет отправлен на рабочий стол Android (без открытия приложения при касании)")
                } else {
                    onPinResultMessage("1:1 виджет обновлен! Добавьте «A.U.R.A. Widgets» на домашний экран.")
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pin_to_launcher_button")
        ) {
            Icon(imageVector = Icons.Default.AddHomeWork, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_pin_home),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
