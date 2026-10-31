package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddHomeWork
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DarkThemeMode
import com.example.data.UserWorkspace
import com.example.data.WidgetDesign
import com.example.ui.components.LiveWidgetCard
import com.example.viewmodel.DataUiState
import com.example.widget.AuraAppWidgetProvider
import kotlin.math.roundToInt

@Composable
fun WidgetCollectionScreen(
    widgetsState: DataUiState<List<WidgetDesign>>,
    workspace: UserWorkspace,
    onSeedShowcase: () -> Unit,
    onSelectWidgetForEdit: (WidgetDesign) -> Unit,
    onDeleteWidget: (String) -> Unit,
    onPinMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (DarkThemeMode.fromString(workspace.darkThemeMode)) {
        DarkThemeMode.SYSTEM -> systemDark
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("widget_collection_screen")
    ) {
        Text(
            text = stringResource(R.string.collection_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.collection_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (widgetsState) {
            is DataUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.testTag("collection_loading"))
                }
            }

            is DataUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = widgetsState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is DataUiState.Success -> {
                val widgets = widgetsState.data
                if (widgets.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .testTag("empty_collection_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_widgets_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.empty_widgets_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onSeedShowcase,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("seed_presets_button")
                            ) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.action_seed_presets))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(widgets, key = { it.id.ifBlank { it.title } }) { item ->
                            Card(
                                shape = RoundedCornerShape(26.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("saved_widget_card_${item.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    LiveWidgetCard(
                                        widget = item,
                                        effectiveDark = effectiveDark,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(164.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Прозрачность ${(item.glassOpacity * 100).roundToInt()}% • Blur ${item.blurRadiusDp.roundToInt()}dp",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    val pinned = AuraAppWidgetProvider.requestPinWidgetToLauncher(context, item)
                                                    if (pinned) {
                                                        onPinMessage("Виджет «${item.title}» отправлен на главный экран")
                                                    } else {
                                                        onPinMessage("Виджет «${item.title}» активирован для главного экрана")
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("pin_saved_widget_${item.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddHomeWork,
                                                    contentDescription = stringResource(R.string.action_pin_home),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Button(
                                                onClick = { onSelectWidgetForEdit(item) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("edit_saved_widget_${item.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = stringResource(R.string.action_edit_widget),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.action_edit_widget))
                                            }

                                            if (item.id.isNotBlank()) {
                                                IconButton(
                                                    onClick = { onDeleteWidget(item.id) },
                                                    modifier = Modifier.testTag("delete_saved_widget_${item.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = stringResource(R.string.action_delete_widget),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
