package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddHomeWork
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DarkThemeMode
import com.example.data.GeminiChatMessage
import com.example.data.UserWorkspace
import com.example.data.WidgetDesign
import com.example.data.WidgetFontFamily
import com.example.ui.components.LiveWidgetCard
import com.example.ui.theme.GoogleSansFontFamily
import com.example.ui.theme.toComposeFontFamily
import com.example.widget.AuraAppWidgetProvider

/**
 * Dedicated Gemini App-style conversational chat screen WITHOUT any prompt suggestion chips,
 * featuring interactive font selection for widget creation.
 */
@Composable
fun GeminiChatScreen(
    messages: List<GeminiChatMessage>,
    isGenerating: Boolean,
    workspace: UserWorkspace,
    selectedFont: WidgetFontFamily,
    onSelectFont: (WidgetFontFamily) -> Unit,
    onUpdateMessageWidgetFont: (String, WidgetFontFamily) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onOpenWidgetInStudio: (WidgetDesign) -> Unit,
    onSaveWidgetToCloud: (WidgetDesign) -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var inputText by rememberSaveable { mutableStateOf("") }
    var isFontPickerExpanded by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (DarkThemeMode.fromString(workspace.darkThemeMode)) {
        DarkThemeMode.SYSTEM -> systemDark
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("gemini_chat_screen")
    ) {
        // Minimal Gemini Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GeminiSparkleBadge()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.gemini_chat_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Active Widget Font Pill Toggle in Top Bar
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { isFontPickerExpanded = !isFontPickerExpanded }
                        .testTag("toggle_chat_font_picker_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedFont.label,
                            fontFamily = selectedFont.toComposeFontFamily(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (messages.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier.testTag("clear_gemini_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.gemini_chat_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Main Chat Area (Zero suggestion chips when empty — pure Gemini greeting)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GeminiSparkleBadge(sizeDp = 54)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.gemini_chat_empty_greeting),
                        style = TextStyle(
                            fontFamily = GoogleSansFontFamily,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFFC084FC),
                                    Color(0xFFF472B6)
                                )
                            ),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        GeminiMessageBubble(
                            message = msg,
                            effectiveDark = effectiveDark,
                            onSelectWidgetFont = { font ->
                                onUpdateMessageWidgetFont(msg.id, font)
                                onStatusMessage("Шрифт виджета изменен на ${font.label}")
                            },
                            onOpenInStudio = { widget ->
                                onOpenWidgetInStudio(widget)
                            },
                            onSaveToCloud = { widget ->
                                onSaveWidgetToCloud(widget)
                            },
                            onPinToHome = { widget ->
                                val pinned = AuraAppWidgetProvider.requestPinWidgetToLauncher(context, widget)
                                if (pinned) {
                                    onStatusMessage("Виджет «${widget.title}» (${WidgetFontFamily.fromString(widget.fontFamily).label}) отправлен на главный экран")
                                } else {
                                    onStatusMessage("Виджет «${widget.title}» обновлен для главного экрана")
                                }
                            }
                        )
                    }
                }
            }
        }

        // Font Selector Strip for Widget Creation in Chat
        AnimatedVisibility(visible = isFontPickerExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Шрифт создаваемого виджета:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${selectedFont.label} • ${selectedFont.styleBadge}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                        .testTag("chat_font_selector_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetFontFamily.entries.forEach { font ->
                        val isSelected = font == selectedFont
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
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("chat_font_chip_${font.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Bottom Gemini Floating Pill Input Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.45f),
                            Color(0xFFC084FC).copy(alpha = 0.45f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isFontPickerExpanded = !isFontPickerExpanded },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("input_bar_font_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = "Выбрать шрифт виджета",
                        tint = if (isFontPickerExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.gemini_chat_input_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("gemini_chat_input")
                )

                val canSend = inputText.isNotBlank() && !isGenerating
                IconButton(
                    onClick = {
                        val toSend = inputText
                        inputText = ""
                        onSendMessage(toSend)
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF38BDF8), Color(0xFFA855F7))
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(Color.Transparent, Color.Transparent)
                                )
                            }
                        )
                        .testTag("gemini_chat_send_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiSparkleBadge(sizeDp: Int = 32) {
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_star_shimmer")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = pulse),
                        Color(0xFFC084FC).copy(alpha = pulse),
                        Color(0xFFF472B6).copy(alpha = pulse)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((sizeDp * 0.56f).dp)
        )
    }
}

@Composable
private fun GeminiMessageBubble(
    message: GeminiChatMessage,
    effectiveDark: Boolean,
    onSelectWidgetFont: (WidgetFontFamily) -> Unit,
    onOpenInStudio: (WidgetDesign) -> Unit,
    onSaveToCloud: (WidgetDesign) -> Unit,
    onPinToHome: (WidgetDesign) -> Unit
) {
    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 6.dp
                ),
                modifier = Modifier.fillMaxWidth(0.84f)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GeminiSparkleBadge(sizeDp = 28)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val widget = message.attachedWidget
                    if (widget != null) {
                        val activeWidgetFont = WidgetFontFamily.fromString(widget.fontFamily)

                        val is3Day = widget.showForecast3Day
                        val is2x2 = widget.sizePreset == com.example.data.WidgetSizePreset.SMALL_2X2.name

                        LiveWidgetCard(
                            widget = widget,
                            effectiveDark = effectiveDark,
                            modifier = Modifier
                                .then(
                                    if (is2x2) Modifier.size(200.dp)
                                    else Modifier.fillMaxWidth().height(if (is3Day) 220.dp else 176.dp)
                                )
                        )

                        // Inline Font Switcher right on the generated widget card in Chat
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Шрифт этого виджета:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WidgetFontFamily.entries.forEach { fontOption ->
                                    val isSelected = fontOption == activeWidgetFont
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectWidgetFont(fontOption) },
                                        label = {
                                            Text(
                                                text = fontOption.label,
                                                fontFamily = fontOption.toComposeFontFamily(),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPinToHome(widget) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddHomeWork,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.gemini_chat_pin_widget))
                            }

                            OutlinedButton(
                                onClick = { onOpenInStudio(widget) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = { onSaveToCloud(widget) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
