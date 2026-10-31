package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.data.DarkThemeMode
import com.example.data.WallpaperPreset
import com.example.data.WidgetRepository
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.GeminiChatScreen
import com.example.ui.screens.GlassCustomizerScreen
import com.example.ui.screens.WidgetCollectionScreen
import com.example.ui.theme.AuraWidgetTheme
import com.example.viewmodel.DataUiState
import com.example.viewmodel.WidgetStudioViewModel
import com.example.widget.AuraAppWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class MainDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    GEMINI_CHAT("gemini_chat", R.string.nav_gemini_chat, Icons.Default.ChatBubbleOutline),
    AI_STUDIO("ai_studio", R.string.nav_ai_studio, Icons.Default.AutoAwesome),
    CUSTOMIZER("customizer", R.string.nav_customizer, Icons.Default.Tune),
    COLLECTION("collection", R.string.nav_my_widgets, Icons.Default.Widgets),
    CLOUD_SYNC("cloud_sync", R.string.nav_cloud_sync, Icons.Default.CloudSync)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            AppRootNavigation()
        }
    }
}

@Composable
fun AppRootNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuraWidgetTheme(darkThemeMode = DarkThemeMode.DARK) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        AuthenticatedWorkspaceHost(currentUserId = user.uid)
    }
}

@Composable
fun AuthenticatedWorkspaceHost(currentUserId: String) {
    val viewModel: WidgetStudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                WidgetStudioViewModel(WidgetRepository(db), currentUserId)
            }
        }
    )

    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val activeDraft by viewModel.activeDraft.collectAsStateWithLifecycle()
    val selectedChatFont by viewModel.selectedChatFont.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatGenerating by viewModel.isChatGenerating.collectAsStateWithLifecycle()
    val savedWidgetsState by viewModel.savedWidgetsState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MainDestination.GEMINI_CHAT) }
    var customWallpaperUri by remember { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Mandatory BackHandler for state-switched secondary tabs
    BackHandler(enabled = currentTab != MainDestination.GEMINI_CHAT) {
        currentTab = MainDestination.GEMINI_CHAT
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val darkThemeMode = DarkThemeMode.fromString(workspace.darkThemeMode)
    val savedCount = (savedWidgetsState as? DataUiState.Success)?.data?.size ?: 0

    AuraWidgetTheme(darkThemeMode = darkThemeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                            MainDestination.entries.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentTab == dest,
                                    onClick = { currentTab = dest },
                                    icon = {
                                        Icon(
                                            imageVector = dest.icon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("nav_tab_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideScreen) {
                        NavigationRail(modifier = Modifier.testTag("side_navigation_rail")) {
                            MainDestination.entries.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentTab == dest,
                                    onClick = { currentTab = dest },
                                    icon = {
                                        Icon(
                                            imageVector = dest.icon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("rail_tab_${dest.route}")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentTab) {
                            MainDestination.GEMINI_CHAT -> {
                                GeminiChatScreen(
                                    messages = chatMessages,
                                    isGenerating = isChatGenerating,
                                    workspace = workspace,
                                    selectedFont = selectedChatFont,
                                    onSelectFont = { font ->
                                        viewModel.selectChatFont(font)
                                        AuraAppWidgetProvider.saveActiveWidgetToLocalCache(
                                            context,
                                            activeDraft.copy(fontFamily = font.name)
                                        )
                                    },
                                    onUpdateMessageWidgetFont = { messageId, font ->
                                        viewModel.updateChatMessageWidgetFont(messageId, font)
                                        val updated = chatMessages.find { it.id == messageId }?.attachedWidget?.copy(fontFamily = font.name)
                                            ?: activeDraft.copy(fontFamily = font.name)
                                        AuraAppWidgetProvider.saveActiveWidgetToLocalCache(context, updated)
                                    },
                                    onSendMessage = { msg ->
                                        viewModel.sendGeminiChatMessage(msg)
                                    },
                                    onClearChat = {
                                        viewModel.clearGeminiChat()
                                    },
                                    onOpenWidgetInStudio = { widget ->
                                        viewModel.loadWidgetIntoEditor(widget)
                                        currentTab = MainDestination.AI_STUDIO
                                    },
                                    onSaveWidgetToCloud = { widget ->
                                        viewModel.loadWidgetIntoEditor(widget)
                                        viewModel.saveCurrentWidgetToCloud()
                                    },
                                    onStatusMessage = { msg ->
                                        viewModel.showStatusMessage(msg)
                                    }
                                )
                            }

                            MainDestination.AI_STUDIO -> {
                                AiStudioScreen(
                                    activeDraft = activeDraft,
                                    workspace = workspace,
                                    aiState = aiState,
                                    customWallpaperUri = customWallpaperUri,
                                    onGenerateAi = { prompt, style ->
                                        viewModel.generateWidgetWithAi(prompt, style)
                                    },
                                    onSelectStylePreset = { preset ->
                                        viewModel.applyStylePresetInstant(preset)
                                    },
                                    onSelectFont = { font ->
                                        viewModel.selectChatFont(font)
                                        AuraAppWidgetProvider.saveActiveWidgetToLocalCache(
                                            context,
                                            activeDraft.copy(fontFamily = font.name)
                                        )
                                    },
                                    onSelectSizePreset = { size ->
                                        viewModel.updateDraftSizeAndIcon(sizePreset = size)
                                    },
                                    onSelectDataSource = { source ->
                                        viewModel.selectDataSource(source)
                                    },
                                    onUpdateAnimationAndTap = { anim, style, tap ->
                                        viewModel.updateAnimationAndTapBehavior(anim, style, tap)
                                    },
                                    onSelectQuickColor = { hex ->
                                        viewModel.updateDraftColors(primaryHex = hex)
                                    },
                                    onSaveToCloud = {
                                        viewModel.saveCurrentWidgetToCloud()
                                    },
                                    onOpenCustomizer = {
                                        currentTab = MainDestination.CUSTOMIZER
                                    },
                                    onPinResultMessage = { msg ->
                                        viewModel.showStatusMessage(msg)
                                    },
                                    onToggleForecast3Day = { enabled ->
                                        viewModel.toggle3DayForecast(enabled)
                                    },
                                    onRefreshLocationWeather = {
                                        viewModel.refreshRealLocationWeather(context)
                                    },
                                    onSelectClockVariation = { variation ->
                                        viewModel.setClockVariation(variation)
                                    }
                                )
                            }

                            MainDestination.CUSTOMIZER -> {
                                GlassCustomizerScreen(
                                    activeDraft = activeDraft,
                                    workspace = workspace,
                                    customWallpaperUri = customWallpaperUri,
                                    onCustomWallpaperSelected = { uri, extractedColors ->
                                        customWallpaperUri = uri
                                        viewModel.selectWallpaperPreset(
                                            WallpaperPreset.CUSTOM_PHOTO,
                                            extractedColors
                                        )
                                    },
                                    onSelectWallpaperPreset = { preset ->
                                        viewModel.selectWallpaperPreset(preset)
                                    },
                                    onHarmonizeWithWallpaper = {
                                        viewModel.harmonizeActiveWidgetWithWallpaper()
                                    },
                                    onUpdateColors = { p, s, surf ->
                                        viewModel.updateDraftColors(p, s, surf)
                                    },
                                    onUpdateGlassParams = { op, bl, sp, cr ->
                                        viewModel.updateDraftGlassParameters(op, bl, sp, cr)
                                    },
                                    onUpdateIconAndDarkAdapt = { iconName, base64, autoDark ->
                                        viewModel.updateDraftSizeAndIcon(
                                            iconName = iconName,
                                            customIconBase64 = base64,
                                            autoDarkAdapt = autoDark
                                        )
                                    },
                                    onUpdateTexts = { t, sub, pv, sv ->
                                        viewModel.updateDraftTexts(t, sub, pv, sv)
                                    },
                                    onSaveToCloud = {
                                        viewModel.saveCurrentWidgetToCloud()
                                    }
                                )
                            }

                            MainDestination.COLLECTION -> {
                                WidgetCollectionScreen(
                                    widgetsState = savedWidgetsState,
                                    workspace = workspace,
                                    onSeedShowcase = {
                                        viewModel.seedStarterShowcaseWidgets()
                                    },
                                    onSelectWidgetForEdit = { widget ->
                                        viewModel.loadWidgetIntoEditor(widget)
                                        currentTab = MainDestination.AI_STUDIO
                                    },
                                    onDeleteWidget = { id ->
                                        viewModel.deleteWidgetFromCloud(id)
                                    },
                                    onPinMessage = { msg ->
                                        viewModel.showStatusMessage(msg)
                                    }
                                )
                            }

                            MainDestination.CLOUD_SYNC -> {
                                CloudSyncScreen(
                                    workspace = workspace,
                                    savedWidgetsCount = savedCount,
                                    onSelectDarkThemeMode = { mode ->
                                        viewModel.setDarkThemeMode(mode)
                                    },
                                    onSyncNow = {
                                        viewModel.syncWorkspaceNow()
                                    },
                                    onSignedOut = {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
