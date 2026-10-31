package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DarkThemeMode
import com.example.data.UserWorkspace
import com.example.data.WallpaperPreset
import com.example.data.WidgetDesign
import com.example.ui.components.InteractiveWallpaperStage
import com.example.ui.components.parseComposeColor
import com.example.ui.components.resolveMaterialIcon
import com.example.util.ImagePaletteUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val builtInIconNames = listOf(
    "WbSunny",
    "AccessTime",
    "DirectionsRun",
    "MusicNote",
    "BatteryChargingFull",
    "CalendarMonth",
    "TrendingUp",
    "AutoAwesome"
)

@Composable
fun GlassCustomizerScreen(
    activeDraft: WidgetDesign,
    workspace: UserWorkspace,
    customWallpaperUri: Uri?,
    onCustomWallpaperSelected: (Uri, List<String>) -> Unit,
    onSelectWallpaperPreset: (WallpaperPreset) -> Unit,
    onHarmonizeWithWallpaper: () -> Unit,
    onUpdateColors: (primary: String?, secondary: String?, surface: String?) -> Unit,
    onUpdateGlassParams: (opacity: Double?, blurDp: Double?, specular: Double?, cornerDp: Double?) -> Unit,
    onUpdateIconAndDarkAdapt: (iconName: String?, customBase64: String?, autoDark: Boolean?) -> Unit,
    onUpdateTexts: (title: String?, subtitle: String?, primaryVal: String?, secondaryVal: String?) -> Unit,
    onSaveToCloud: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallpaperPreset = WallpaperPreset.fromString(workspace.activeWallpaperPreset)
    val darkMode = DarkThemeMode.fromString(workspace.darkThemeMode)

    // Zero-permission Android Photo Picker for Custom Wallpaper
    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val colors = ImagePaletteUtils.extractPaletteFromUri(context, uri)
                onCustomWallpaperSelected(uri, colors)
            }
        }
    }

    // Zero-permission Android Photo Picker for Custom Widget Icon
    val customIconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = ImagePaletteUtils.encodeUriToCompactBase64(context, uri)
                if (!encoded.isNullOrBlank()) {
                    onUpdateIconAndDarkAdapt("CUSTOM", encoded, null)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("glass_customizer_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.customizer_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.customizer_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Live Wallpaper & Widget Stage
        InteractiveWallpaperStage(
            widget = activeDraft,
            wallpaperPreset = wallpaperPreset,
            customWallpaperUri = customWallpaperUri,
            darkThemeMode = darkMode
        )

        // 1. Wallpaper & Palette Adaptation Section
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.wallpaper_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WallpaperPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = wallpaperPreset == preset,
                            onClick = { onSelectWallpaperPreset(preset) },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("wallpaper_preset_${preset.name}")
                        )
                    }
                }

                // Extracted Wallpaper Palette Swatches (Tap for Primary, Secondary, Surface)
                Text(
                    text = "Палитра обоев (нажмите для выбора основного и акцентного цвета):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    workspace.extractedPaletteHex.forEachIndexed { idx, hex ->
                        val color = parseComposeColor(hex)
                        val isPrimary = activeDraft.primaryColorHex.equals(hex, ignoreCase = true)
                        val isSecondary = activeDraft.secondaryColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(color)
                                .border(
                                    width = if (isPrimary || isSecondary) 3.dp else 1.dp,
                                    color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    if (!isPrimary) {
                                        onUpdateColors(hex, null, null)
                                    } else {
                                        onUpdateColors(null, hex, null)
                                    }
                                }
                                .testTag("palette_color_box_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPrimary) {
                                Text("1", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                            } else if (isSecondary) {
                                Text("2", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            wallpaperPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_wallpaper_button")
                    ) {
                        Icon(imageVector = Icons.Default.Wallpaper, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_pick_wallpaper))
                    }

                    Button(
                        onClick = onHarmonizeWithWallpaper,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("apply_wallpaper_palette_button")
                    ) {
                        Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Авто-тон")
                    }
                }
            }
        }

        // 2. Real-time Glass Opacity, Blur, Specular & Shape Sliders
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SliderControlRow(
                    label = stringResource(R.string.slider_opacity_label),
                    valueText = "${(activeDraft.glassOpacity * 100).roundToInt()}%",
                    value = activeDraft.glassOpacity.toFloat(),
                    valueRange = 0.05f..1.0f,
                    onValueChange = { onUpdateGlassParams(it.toDouble(), null, null, null) },
                    testTag = "slider_glass_opacity"
                )

                SliderControlRow(
                    label = stringResource(R.string.slider_blur_label),
                    valueText = "${activeDraft.blurRadiusDp.roundToInt()} dp",
                    value = activeDraft.blurRadiusDp.toFloat(),
                    valueRange = 0f..40f,
                    onValueChange = { onUpdateGlassParams(null, it.toDouble(), null, null) },
                    testTag = "slider_blur_radius"
                )

                SliderControlRow(
                    label = stringResource(R.string.slider_specular_label),
                    valueText = "${(activeDraft.specularIntensity * 100).roundToInt()}%",
                    value = activeDraft.specularIntensity.toFloat(),
                    valueRange = 0f..1f,
                    onValueChange = { onUpdateGlassParams(null, null, it.toDouble(), null) },
                    testTag = "slider_specular_intensity"
                )

                SliderControlRow(
                    label = stringResource(R.string.slider_corner_label),
                    valueText = "${activeDraft.cornerRadiusDp.roundToInt()} dp",
                    value = activeDraft.cornerRadiusDp.toFloat(),
                    valueRange = 8f..48f,
                    onValueChange = { onUpdateGlassParams(null, null, null, it.toDouble()) },
                    testTag = "slider_corner_radius"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.auto_dark_adapt_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = activeDraft.autoDarkAdapt,
                        onCheckedChange = { checked ->
                            onUpdateIconAndDarkAdapt(null, activeDraft.customIconBase64, checked)
                        },
                        modifier = Modifier.testTag("switch_auto_dark_adapt")
                    )
                }
            }
        }

        // 3. Custom Icon Import & Built-in Material Symbols
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
                Text(
                    text = stringResource(R.string.custom_icon_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    builtInIconNames.forEach { name ->
                        val isSelected = activeDraft.customIconBase64 == null && activeDraft.iconName == name
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onUpdateIconAndDarkAdapt(name, null, null)
                                }
                                .testTag("icon_choice_$name")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = resolveMaterialIcon(name),
                                    contentDescription = name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            customIconPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_custom_icon_button")
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_import_custom_icon))
                    }

                    if (!activeDraft.customIconBase64.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onUpdateIconAndDarkAdapt("WbSunny", null, null) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("reset_custom_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.action_reset_custom_icon)
                            )
                        }
                    }
                }
            }
        }

        // 4. Direct Text Customization
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Текст и показатели виджета",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = activeDraft.title,
                    onValueChange = { onUpdateTexts(it, null, null, null) },
                    label = { Text("Заголовок") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_widget_title")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = activeDraft.primaryValue,
                        onValueChange = { onUpdateTexts(null, null, it, null) },
                        label = { Text("Главное значение") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_widget_primary_value")
                    )
                    OutlinedTextField(
                        value = activeDraft.secondaryValue,
                        onValueChange = { onUpdateTexts(null, null, null, it) },
                        label = { Text("Бейдж") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_widget_secondary_value")
                    )
                }
            }
        }

        Button(
            onClick = onSaveToCloud,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("customizer_save_cloud_button")
        ) {
            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.action_save_cloud),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SliderControlRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )
    }
}
