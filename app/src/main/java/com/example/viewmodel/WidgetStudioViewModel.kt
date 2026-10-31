package com.example.viewmodel

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DarkThemeMode
import com.example.data.GeminiChatMessage
import com.example.data.UserWorkspace
import com.example.data.WallpaperPreset
import com.example.data.WidgetAnimationStyle
import com.example.data.WidgetCategory
import com.example.data.WidgetClockVariation
import com.example.data.WidgetDataSourceType
import com.example.data.WidgetDesign
import com.example.data.WidgetFontFamily
import com.example.data.WidgetRepository
import com.example.data.WidgetSizePreset
import com.example.data.WidgetStylePreset
import com.example.data.WidgetTapBehavior
import com.example.data.sanitizeHex
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "WidgetStudioVM"

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface AiGenerationState {
    data object Idle : AiGenerationState
    data class Generating(val statusText: String) : AiGenerationState
    data class Success(val summary: String) : AiGenerationState
    data class Error(val message: String) : AiGenerationState
}

class WidgetStudioViewModel(
    private val repository: WidgetRepository,
    private val currentUserId: String
) : ViewModel() {

    private val widgetSchema = Schema.obj(
        properties = mapOf(
            "conversationalReply" to Schema.string(description = "Helpful natural Russian response in Gemini style explaining the widget or answering the user's message"),
            "title" to Schema.string(description = "Short Russian widget title (2 to 4 words)"),
            "subtitle" to Schema.string(description = "Descriptive Russian subtitle or context metric"),
            "primaryValue" to Schema.string(description = "Bold main display metric like +23°C, 14:45:12, Заметка Keep, 94%"),
            "secondaryValue" to Schema.string(description = "Secondary pill badge text in Russian (e.g. Google Погода, Google Keep, OneNote, Xiaomi Notes)"),
            "stylePreset" to Schema.enumeration(
                values = listOf("LIQUID_GLASS", "M3_EXPRESSIVE", "CYBER_NEON", "MINIMAL_FROST"),
                description = "Visual widget design language"
            ),
            "category" to Schema.enumeration(
                values = listOf("WEATHER", "CLOCK", "NOTES", "FITNESS", "MUSIC", "BATTERY", "CALENDAR", "FINANCE", "CUSTOM"),
                description = "Widget functional category"
            ),
            "sizePreset" to Schema.enumeration(
                values = listOf("SMALL_2X2", "WIDE_4X2", "LARGE_4X4"),
                description = "Recommended grid size"
            ),
            "dataSourceType" to Schema.enumeration(
                values = listOf(
                    "LIVE_CLOCK", "GOOGLE_WEATHER", "GOOGLE_KEEP", "MICROSOFT_ONENOTE",
                    "SAMSUNG_NOTES", "XIAOMI_NOTES", "OPPO_NOTES", "SYSTEM_BATTERY",
                    "MEDIA_SESSION", "CALENDAR_EVENTS", "STATIC_CUSTOM"
                ),
                description = "App or system data provider feeding this widget"
            ),
            "isAnimated" to Schema.boolean(description = "Whether widget has live animation enabled"),
            "animationStyle" to Schema.enumeration(
                values = listOf("LIQUID_SHIMMER", "PULSE_GLOW", "TICKING_SWEEP", "ORBITAL_WAVE", "NONE"),
                description = "Animation style for the widget"
            ),
            "fontFamily" to Schema.enumeration(
                values = listOf(
                    "GOOGLE_SANS", "MONTSERRAT", "PLAYFAIR_DISPLAY", "JETBRAINS_MONO",
                    "COMFORTAA", "RUBIK", "SPACE_GROTESK", "PLUS_JAKARTA"
                ),
                description = "Typography font family for the widget"
            ),
            "primaryColorHex" to Schema.string(description = "Vibrant hex color #RRGGBB harmonized with wallpaper"),
            "secondaryColorHex" to Schema.string(description = "Secondary accent hex color #RRGGBB"),
            "surfaceColorHex" to Schema.string(description = "Deep or tinted surface hex color #RRGGBB"),
            "glassOpacity" to Schema.double(description = "Element background opacity between 0.15 and 0.85"),
            "blurRadiusDp" to Schema.double(description = "Backdrop blur radius between 12.0 and 36.0"),
            "specularIntensity" to Schema.double(description = "Liquid Glass rim light reflection intensity 0.2 to 1.0"),
            "cornerRadiusDp" to Schema.double(description = "Shape corner radius between 16.0 and 42.0"),
            "showForecast3Day" to Schema.boolean(description = "Whether to show extended 3-day weather forecast (true for 3-day weather forecast requests)"),
            "locationName" to Schema.string(description = "City or location name for weather or calendar"),
            "iconName" to Schema.enumeration(
                values = listOf(
                    "WbSunny", "AccessTime", "EditNote", "DirectionsRun", "MusicNote",
                    "BatteryChargingFull", "CalendarMonth", "TrendingUp", "AutoAwesome"
                ),
                description = "Best matching Material icon name"
            )
        )
    )

    private val aiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = widgetSchema
            }
        )
    }

    val savedWidgetsState: StateFlow<DataUiState<List<WidgetDesign>>> =
        repository.observeWidgets(currentUserId)
            .map<List<WidgetDesign>, DataUiState<List<WidgetDesign>>> { list ->
                DataUiState.Success(list)
            }
            .catch { error ->
                Log.w(TAG, "Error observing saved widgets", error)
                emit(DataUiState.Error(error.localizedMessage ?: "Не удалось загрузить виджеты из облака"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DataUiState.Loading
            )

    private val _workspace = MutableStateFlow(
        UserWorkspace(
            userId = currentUserId,
            syncDeviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        )
    )
    val workspace: StateFlow<UserWorkspace> = _workspace.asStateFlow()

    private val _selectedChatFont = MutableStateFlow(WidgetFontFamily.GOOGLE_SANS)
    val selectedChatFont: StateFlow<WidgetFontFamily> = _selectedChatFont.asStateFlow()

    private val _activeDraft = MutableStateFlow(
        WidgetDesign(
            userId = currentUserId,
            title = "Google Погода • Москва",
            subtitle = "Ощущается как +20°C • УФ-индекс 3",
            primaryValue = "+22°C",
            secondaryValue = "Google Погода",
            prompt = "Погодный виджет в стиле Apple Liquid Glass с данными из Google Погода",
            stylePreset = WidgetStylePreset.LIQUID_GLASS.name,
            category = WidgetCategory.WEATHER.name,
            sizePreset = WidgetSizePreset.WIDE_4X2.name,
            primaryColorHex = "#38BDF8",
            secondaryColorHex = "#A855F7",
            surfaceColorHex = "#0F172A",
            glassOpacity = 0.34,
            blurRadiusDp = 26.0,
            specularIntensity = 0.88,
            cornerRadiusDp = 30.0,
            iconName = "WbSunny",
            dataSourceType = WidgetDataSourceType.GOOGLE_WEATHER.name,
            sourcePackageHint = WidgetDataSourceType.GOOGLE_WEATHER.defaultPackage,
            tapBehavior = WidgetTapBehavior.REFRESH_WIDGET.name,
            isAnimated = true,
            animationStyle = WidgetAnimationStyle.LIQUID_SHIMMER.name,
            fontFamily = WidgetFontFamily.GOOGLE_SANS.name,
            autoDarkAdapt = true,
            isPinned = true
        )
    )
    val activeDraft: StateFlow<WidgetDesign> = _activeDraft.asStateFlow()

    private val _aiState = MutableStateFlow<AiGenerationState>(AiGenerationState.Idle)
    val aiState: StateFlow<AiGenerationState> = _aiState.asStateFlow()

    // Dedicated clean Gemini Chat messages (starts completely clean without suggestion chips)
    private val _chatMessages = MutableStateFlow<List<GeminiChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<GeminiChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatGenerating = MutableStateFlow(false)
    val isChatGenerating: StateFlow<Boolean> = _isChatGenerating.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        observeCloudWorkspace()
    }

    private fun observeCloudWorkspace() {
        viewModelScope.launch {
            repository.observeWorkspace(currentUserId)
                .catch { e ->
                    Log.w(TAG, "Failed to observe workspace settings", e)
                }
                .collect { remoteWorkspace ->
                    if (remoteWorkspace != null) {
                        _workspace.value = remoteWorkspace
                    } else {
                        val initial = _workspace.value
                        repository.saveWorkspace(initial)
                    }
                }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearGeminiChat() {
        _chatMessages.value = emptyList()
    }

    fun selectChatFont(font: WidgetFontFamily) {
        _selectedChatFont.value = font
        _activeDraft.update { it.copy(fontFamily = font.name) }
        // Also update the latest generated widget in the chat so the user sees the new font immediately
        _chatMessages.update { list ->
            val lastWidgetIndex = list.indexOfLast { it.attachedWidget != null }
            if (lastWidgetIndex >= 0) {
                list.mapIndexed { index, msg ->
                    if (index == lastWidgetIndex && msg.attachedWidget != null) {
                        msg.copy(attachedWidget = msg.attachedWidget.copy(fontFamily = font.name))
                    } else {
                        msg
                    }
                }
            } else {
                list
            }
        }
    }

    fun updateChatMessageWidgetFont(messageId: String, font: WidgetFontFamily) {
        _selectedChatFont.value = font
        _chatMessages.update { list ->
            list.map { msg ->
                if (msg.id == messageId && msg.attachedWidget != null) {
                    val updatedWidget = msg.attachedWidget.copy(fontFamily = font.name)
                    _activeDraft.value = updatedWidget
                    msg.copy(attachedWidget = updatedWidget)
                } else {
                    msg
                }
            }
        }
    }

    fun sendGeminiChatMessage(userMessage: String) {
        val cleanText = userMessage.trim()
        if (cleanText.isBlank() || _isChatGenerating.value) return

        val chosenFont = _selectedChatFont.value
        val userMsgObj = GeminiChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = cleanText
        )
        val placeholderId = UUID.randomUUID().toString()
        val thinkingMsg = GeminiChatMessage(
            id = placeholderId,
            isUser = false,
            text = "Gemini проектирует виджет со шрифтом ${chosenFont.label}…",
            isStreaming = true
        )

        _chatMessages.update { it + userMsgObj + thinkingMsg }
        _isChatGenerating.value = true

        viewModelScope.launch {
            try {
                val currentPalette = _workspace.value.extractedPaletteHex.joinToString(", ")
                val historyContext = _chatMessages.value
                    .dropLast(1)
                    .takeLast(6)
                    .joinToString("\n") { msg ->
                        if (msg.isUser) "Пользователь: ${msg.text}" else "Gemini: ${msg.text}"
                    }

                val fullInstruction = """
                    Ты — Gemini, интеллектуальный архитектор живых Android-виджетов.
                    История диалога:
                    $historyContext
                    
                    Запрос пользователя: "$cleanText"
                    Выбранный пользователем шрифт виджета (fontFamily): ${chosenFont.name} (${chosenFont.label}).
                    Палитра обоев пользователя: [$currentPalette].
                    
                    Правила выбора источника данных (dataSourceType):
                    - Если просят погоду / Google Погоду -> GOOGLE_WEATHER (iconName = WbSunny)
                    - Если просят расширенный прогноз погоды на 3 дня -> GOOGLE_WEATHER (iconName = WbSunny, showForecast3Day = true, sizePreset = WIDE_4X2 или LARGE_4X4)
                    - Если просят календарь / сетку месяца 2 на 2 -> CALENDAR_EVENTS (category = CALENDAR, iconName = CalendarMonth, title = "Календарь", sizePreset = SMALL_2X2)
                    - Если просят часы / время -> LIVE_CLOCK (category = CLOCK, iconName = AccessTime)
                    - Если просят вариации часов: clockVariation ("DIGITAL_HM" - только часы и минуты, "DIGITAL_HMS" - с секундами, "TIMER" - таймер обратного отсчета, "STOPWATCH" - секундомер)
                    - Если просят заметки -> NOTES (category = NOTES, iconName = EditNote, title = "Заметки Google Keep", sourcePackageHint = "com.google.android.keep")
                    - Если просят батарею / заряд -> SYSTEM_BATTERY (iconName = BatteryChargingFull)
                    - Если просят музыку / плеер -> MEDIA_SESSION (iconName = MusicNote)
                    
                    Правила размера сетки (sizePreset):
                    - Если просят виджет 2 на 2, маленький или компактный -> SMALL_2X2
                    - Если просят прогноз на 3 дня -> WIDE_4X2 или LARGE_4X4
                    - По умолчанию: WIDE_4X2
                    
                    Правила выбора шрифта (fontFamily):
                    - По умолчанию используй выбранный пользователем шрифт: ${chosenFont.name}.
                    - Если пользователь явно попросил в тексте другой шрифт (например Montserrat, Playfair Display, JetBrains Mono, Comfortaa, Rubik, Space Grotesk, Plus Jakarta Sans, Google Sans), установи соответствующий fontFamily.
                    
                    Сформируй JSON с дружелюбным ответом в поле conversationalReply и параметрами живого анимированного виджета.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    aiModel.generateContent(fullInstruction)
                }

                val jsonString = response.text ?: throw IllegalStateException("Пустой ответ от Gemini")
                val json = JSONObject(jsonString)

                val generatedWidget = parseWidgetFromJson(
                    json = json,
                    promptText = cleanText,
                    styleHint = null,
                    preferredFont = chosenFont
                )
                _activeDraft.value = generatedWidget
                _selectedChatFont.value = WidgetFontFamily.fromString(generatedWidget.fontFamily)

                val replyText = json.optString(
                    "conversationalReply",
                    "Готово! Я создал виджет «${generatedWidget.title}» со шрифтом ${WidgetFontFamily.fromString(generatedWidget.fontFamily).label}."
                )

                _chatMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == placeholderId) {
                            GeminiChatMessage(
                                id = placeholderId,
                                isUser = false,
                                text = replyText,
                                attachedWidget = generatedWidget,
                                isStreaming = false
                            )
                        } else {
                            msg
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini chat timed out", e)
                _chatMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == placeholderId) {
                            msg.copy(
                                text = "Время ожидания ответа Gemini истекло (60 сек). Попробуйте отправить запрос еще раз.",
                                isStreaming = false
                            )
                        } else msg
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat generation failed", e)
                _chatMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == placeholderId) {
                            msg.copy(
                                text = "Ошибка обращения к Gemini: ${e.localizedMessage ?: "Неизвестная ошибка"}",
                                isStreaming = false
                            )
                        } else msg
                    }
                }
            } finally {
                _isChatGenerating.value = false
            }
        }
    }

    fun generateWidgetWithAi(userPrompt: String, preferredStyle: WidgetStylePreset? = null) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isBlank()) {
            _aiState.value = AiGenerationState.Error("Введите описание виджета для нейросети")
            return
        }

        viewModelScope.launch {
            _aiState.value = AiGenerationState.Generating("Gemini создает архитектуру и шейдеры виджета…")
            try {
                val currentPalette = _workspace.value.extractedPaletteHex.joinToString(", ")
                val styleHint = preferredStyle?.name ?: _activeDraft.value.stylePreset
                val currentFont = WidgetFontFamily.fromString(_activeDraft.value.fontFamily)
                val fullInstruction = """
                    Ты — ведущий дизайнер интерфейсов Android 16 (Material 3 Expressive) и Apple iOS 26 (Liquid Glass).
                    Создай конфигурацию живого виджета по запросу пользователя: "$cleanPrompt".
                    Предпочтительный визуальный стиль: $styleHint.
                    Выбранный шрифт: ${currentFont.name}.
                    Текущая цветовая палитра обоев пользователя: [$currentPalette].
                    Определи подходящий источник данных dataSourceType (GOOGLE_WEATHER, LIVE_CLOCK, GOOGLE_KEEP, MICROSOFT_ONENOTE, SAMSUNG_NOTES, XIAOMI_NOTES, OPPO_NOTES, SYSTEM_BATTERY, MEDIA_SESSION, CALENDAR_EVENTS).
                    Если просят прогноз на 3 дня, установи showForecast3Day = true.
                    Если просят календарь, установи category = CALENDAR, dataSourceType = CALENDAR_EVENTS, iconName = CalendarMonth.
                    Если просят размер 2х2 или компактный, установи sizePreset = SMALL_2X2.
                    Все тексты должны быть на русском языке.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    aiModel.generateContent(fullInstruction)
                }

                val jsonString = response.text ?: throw IllegalStateException("Пустой ответ от модели Gemini")
                val json = JSONObject(jsonString)

                val updatedDraft = parseWidgetFromJson(
                    json = json,
                    promptText = cleanPrompt,
                    styleHint = styleHint,
                    preferredFont = currentFont
                )
                _activeDraft.value = updatedDraft

                val rationale = json.optString(
                    "conversationalReply",
                    "Виджет успешно создан и адаптирован под палитру обоев."
                )
                _aiState.value = AiGenerationState.Success(rationale)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI widget generation timed out", e)
                _aiState.value = AiGenerationState.Error("Время ожидания нейросети истекло (60 сек). Попробуйте снова.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI widget generation failed", e)
                _aiState.value = AiGenerationState.Error(
                    e.localizedMessage ?: "Ошибка генерации виджета через Firebase AI Logic"
                )
            }
        }
    }

    private fun parseWidgetFromJson(
        json: JSONObject,
        promptText: String,
        styleHint: String?,
        preferredFont: WidgetFontFamily? = null
    ): WidgetDesign {
        val current = _activeDraft.value
        val resolvedSource = WidgetDataSourceType.fromString(
            json.optString("dataSourceType", current.dataSourceType)
        )
        val resolvedAnimStyle = WidgetAnimationStyle.fromString(
            json.optString("animationStyle", current.animationStyle)
        )
        val lowerPrompt = promptText.lowercase()
        val promptExplicitlyMentionsFont = WidgetFontFamily.entries.any {
            lowerPrompt.contains(it.label.lowercase()) || lowerPrompt.contains(it.name.lowercase())
        }
        val resolvedFont = if (promptExplicitlyMentionsFont) {
            WidgetFontFamily.fromString(json.optString("fontFamily", preferredFont?.name ?: current.fontFamily))
        } else {
            preferredFont ?: WidgetFontFamily.fromString(json.optString("fontFamily", current.fontFamily))
        }

        val resolvedClock = when {
            lowerPrompt.contains("секундомер") -> WidgetClockVariation.STOPWATCH
            lowerPrompt.contains("таймер") || lowerPrompt.contains("обратный") -> WidgetClockVariation.TIMER
            lowerPrompt.contains("секунд") -> WidgetClockVariation.DIGITAL_HMS
            lowerPrompt.contains("только часы") || lowerPrompt.contains("без секунд") -> WidgetClockVariation.DIGITAL_HM
            else -> WidgetClockVariation.fromString(json.optString("clockVariation", current.clockVariation))
        }

        return current.copy(
            id = "",
            userId = currentUserId,
            title = json.optString("title", current.title).take(80),
            subtitle = json.optString("subtitle", current.subtitle).take(120),
            primaryValue = json.optString("primaryValue", current.primaryValue).take(60),
            secondaryValue = json.optString("secondaryValue", resolvedSource.label).take(80),
            prompt = promptText,
            stylePreset = json.optString("stylePreset", styleHint ?: current.stylePreset),
            category = json.optString("category", current.category),
            sizePreset = json.optString("sizePreset", current.sizePreset),
            primaryColorHex = sanitizeHex(json.optString("primaryColorHex"), current.primaryColorHex),
            secondaryColorHex = sanitizeHex(json.optString("secondaryColorHex"), current.secondaryColorHex),
            surfaceColorHex = sanitizeHex(json.optString("surfaceColorHex"), current.surfaceColorHex),
            glassOpacity = json.optDouble("glassOpacity", current.glassOpacity).coerceIn(0.05, 1.0),
            blurRadiusDp = json.optDouble("blurRadiusDp", current.blurRadiusDp).coerceIn(0.0, 40.0),
            specularIntensity = json.optDouble("specularIntensity", current.specularIntensity).coerceIn(0.0, 1.0),
            cornerRadiusDp = json.optDouble("cornerRadiusDp", current.cornerRadiusDp).coerceIn(8.0, 48.0),
            iconName = json.optString("iconName", resolvedSource.iconHint),
            dataSourceType = resolvedSource.name,
            sourcePackageHint = resolvedSource.defaultPackage,
            tapBehavior = current.tapBehavior,
            isAnimated = json.optBoolean("isAnimated", true),
            animationStyle = resolvedAnimStyle.name,
            fontFamily = resolvedFont.name,
            clockVariation = resolvedClock.name,
            showForecast3Day = json.optBoolean("showForecast3Day", current.showForecast3Day),
            locationName = json.optString("locationName", current.locationName).take(80)
        )
    }

    fun setClockVariation(variation: WidgetClockVariation) {
        _activeDraft.update { it.copy(clockVariation = variation.name) }
    }

    fun applyStylePresetInstant(preset: WidgetStylePreset) {
        _activeDraft.update { draft ->
            when (preset) {
                WidgetStylePreset.LIQUID_GLASS -> draft.copy(
                    stylePreset = preset.name,
                    glassOpacity = 0.30,
                    blurRadiusDp = 28.0,
                    specularIntensity = 0.92,
                    cornerRadiusDp = 30.0
                )
                WidgetStylePreset.M3_EXPRESSIVE -> draft.copy(
                    stylePreset = preset.name,
                    glassOpacity = 0.78,
                    blurRadiusDp = 14.0,
                    specularIntensity = 0.35,
                    cornerRadiusDp = 38.0
                )
                WidgetStylePreset.CYBER_NEON -> draft.copy(
                    stylePreset = preset.name,
                    glassOpacity = 0.42,
                    blurRadiusDp = 22.0,
                    specularIntensity = 0.95,
                    cornerRadiusDp = 20.0
                )
                WidgetStylePreset.MINIMAL_FROST -> draft.copy(
                    stylePreset = preset.name,
                    glassOpacity = 0.22,
                    blurRadiusDp = 34.0,
                    specularIntensity = 0.55,
                    cornerRadiusDp = 24.0
                )
            }
        }
    }

    fun selectDataSource(source: WidgetDataSourceType) {
        _activeDraft.update { draft ->
            when (source) {
                WidgetDataSourceType.LIVE_CLOCK -> draft.copy(
                    dataSourceType = source.name,
                    sourcePackageHint = source.defaultPackage,
                    category = WidgetCategory.CLOCK.name,
                    title = "Живые Часы • Точный ход",
                    secondaryValue = "LIVE CLOCK",
                    iconName = "AccessTime",
                    isAnimated = true,
                    animationStyle = WidgetAnimationStyle.TICKING_SWEEP.name
                )
                WidgetDataSourceType.GOOGLE_WEATHER -> draft.copy(
                    dataSourceType = source.name,
                    sourcePackageHint = source.defaultPackage,
                    category = WidgetCategory.WEATHER.name,
                    title = "Google Погода",
                    primaryValue = "+22°C",
                    subtitle = "Ощущается как +20°C • Ясно • УФ 2",
                    secondaryValue = "Google Weather",
                    iconName = "WbSunny"
                )
                WidgetDataSourceType.NOTES -> draft.copy(
                    dataSourceType = source.name,
                    sourcePackageHint = source.defaultPackage,
                    category = WidgetCategory.NOTES.name,
                    title = "Заметки Google Keep",
                    primaryValue = "Купить кофе и созвон в 16:00",
                    subtitle = "Синхронизировано с Google Keep",
                    secondaryValue = "Google Keep",
                    iconName = "EditNote"
                )
                WidgetDataSourceType.SYSTEM_BATTERY -> draft.copy(
                    dataSourceType = source.name,
                    sourcePackageHint = source.defaultPackage,
                    category = WidgetCategory.BATTERY.name,
                    title = "Заряд и Система",
                    secondaryValue = "BATTERY LIVE",
                    iconName = "BatteryChargingFull",
                    isAnimated = true,
                    animationStyle = WidgetAnimationStyle.PULSE_GLOW.name
                )
                WidgetDataSourceType.MEDIA_SESSION -> draft.copy(
                    dataSourceType = source.name,
                    sourcePackageHint = source.defaultPackage,
                    category = WidgetCategory.MUSIC.name,
                    title = "Сейчас играет • Spatial Audio",
                    primaryValue = "Midnight Aurora",
                    subtitle = "Lossless 24-bit • 02:44 / 04:12",
                    secondaryValue = "Media Session",
                    iconName = "MusicNote",
                    isAnimated = true,
                    animationStyle = WidgetAnimationStyle.ORBITAL_WAVE.name
                )
                WidgetDataSourceType.CALENDAR_EVENTS -> draft.copy(
                    dataSourceType = source.name,
                    sourcePackageHint = source.defaultPackage,
                    category = WidgetCategory.CALENDAR.name,
                    title = "Ближайшая встреча",
                    primaryValue = "15:30 • Дизайн-ревью",
                    subtitle = "Google Meet • Зал Аврора",
                    secondaryValue = "Календарь",
                    iconName = "CalendarMonth"
                )
                WidgetDataSourceType.STATIC_CUSTOM -> draft.copy(
                    dataSourceType = source.name,
                    sourcePackageHint = "",
                    secondaryValue = "Custom AI"
                )
            }
        }
    }

    fun updateAnimationAndTapBehavior(
        isAnimated: Boolean? = null,
        animationStyle: WidgetAnimationStyle? = null,
        tapBehavior: WidgetTapBehavior? = null
    ) {
        _activeDraft.update { draft ->
            draft.copy(
                isAnimated = isAnimated ?: draft.isAnimated,
                animationStyle = animationStyle?.name ?: draft.animationStyle,
                tapBehavior = tapBehavior?.name ?: draft.tapBehavior
            )
        }
    }

    fun toggle3DayForecast(enabled: Boolean) {
        _activeDraft.update { it.copy(showForecast3Day = enabled) }
    }

    fun selectSizePreset(preset: WidgetSizePreset) {
        _activeDraft.update { it.copy(sizePreset = preset.name) }
    }

    fun refreshRealLocationWeather(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val real = com.example.util.RealWeatherManager.fetchRealWeather(context, force = true)
                _activeDraft.update { draft ->
                    draft.copy(
                        title = "Погода • ${real.locationName}",
                        primaryValue = real.temperature,
                        subtitle = "${real.conditionText} • Ощущается ${real.apparentTemperature}",
                        locationName = real.locationName,
                        secondaryValue = real.locationName
                    )
                }
                _statusBanner.value = "Погода обновлена для ${real.locationName} (${real.temperature})"
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh real location weather", e)
            }
        }
    }

    fun updateDraftColors(
        primaryHex: String? = null,
        secondaryHex: String? = null,
        surfaceHex: String? = null
    ) {
        _activeDraft.update { draft ->
            draft.copy(
                primaryColorHex = primaryHex?.let { sanitizeHex(it, draft.primaryColorHex) } ?: draft.primaryColorHex,
                secondaryColorHex = secondaryHex?.let { sanitizeHex(it, draft.secondaryColorHex) } ?: draft.secondaryColorHex,
                surfaceColorHex = surfaceHex?.let { sanitizeHex(it, draft.surfaceColorHex) } ?: draft.surfaceColorHex
            )
        }
    }

    fun updateDraftGlassParameters(
        opacity: Double? = null,
        blurDp: Double? = null,
        specular: Double? = null,
        cornerDp: Double? = null
    ) {
        _activeDraft.update { draft ->
            draft.copy(
                glassOpacity = opacity?.coerceIn(0.05, 1.0) ?: draft.glassOpacity,
                blurRadiusDp = blurDp?.coerceIn(0.0, 40.0) ?: draft.blurRadiusDp,
                specularIntensity = specular?.coerceIn(0.0, 1.0) ?: draft.specularIntensity,
                cornerRadiusDp = cornerDp?.coerceIn(8.0, 48.0) ?: draft.cornerRadiusDp
            )
        }
    }

    fun updateDraftSizeAndIcon(
        sizePreset: WidgetSizePreset? = null,
        iconName: String? = null,
        customIconBase64: String? = _activeDraft.value.customIconBase64,
        autoDarkAdapt: Boolean? = null
    ) {
        _activeDraft.update { draft ->
            draft.copy(
                sizePreset = sizePreset?.name ?: draft.sizePreset,
                iconName = iconName ?: draft.iconName,
                customIconBase64 = customIconBase64,
                autoDarkAdapt = autoDarkAdapt ?: draft.autoDarkAdapt
            )
        }
    }

    fun updateDraftTexts(
        title: String? = null,
        subtitle: String? = null,
        primaryValue: String? = null,
        secondaryValue: String? = null
    ) {
        _activeDraft.update { draft ->
            draft.copy(
                title = title ?: draft.title,
                subtitle = subtitle ?: draft.subtitle,
                primaryValue = primaryValue ?: draft.primaryValue,
                secondaryValue = secondaryValue ?: draft.secondaryValue
            )
        }
    }

    fun showStatusMessage(message: String) {
        _statusBanner.value = message
    }

    fun selectWallpaperPreset(preset: WallpaperPreset, customExtractedColors: List<String>? = null) {
        val palette = customExtractedColors?.takeIf { it.isNotEmpty() } ?: preset.defaultPalette
        val updatedWorkspace = _workspace.value.copy(
            activeWallpaperPreset = preset.name,
            extractedPaletteHex = palette,
            syncDeviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        )
        _workspace.value = updatedWorkspace
        harmonizeActiveWidgetWithWallpaper(palette)
        viewModelScope.launch {
            repository.saveWorkspace(updatedWorkspace)
        }
    }

    fun harmonizeActiveWidgetWithWallpaper(palette: List<String> = _workspace.value.extractedPaletteHex) {
        if (palette.isEmpty()) return
        val primary = palette.getOrElse(0) { "#38BDF8" }
        val secondary = palette.getOrElse(1) { "#A855F7" }
        val surface = palette.getOrElse(3) { "#0F172A" }
        updateDraftColors(primaryHex = primary, secondaryHex = secondary, surfaceHex = surface)
        _statusBanner.value = "Цветовая палитра виджета синхронизирована с обоями"
    }

    fun setDarkThemeMode(mode: DarkThemeMode) {
        val updated = _workspace.value.copy(
            darkThemeMode = mode.name,
            syncDeviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        )
        _workspace.value = updated
        viewModelScope.launch {
            val res = repository.saveWorkspace(updated)
            if (res.isSuccess) {
                _statusBanner.value = "Тема (${mode.label}) синхронизирована в облаке"
            }
        }
    }

    fun saveCurrentWidgetToCloud() {
        viewModelScope.launch {
            val draft = _activeDraft.value
            val result = if (draft.id.isNotBlank()) {
                repository.updateWidget(draft).map { draft.id }
            } else {
                repository.createWidget(draft)
            }
            result.onSuccess { savedId ->
                _activeDraft.update { it.copy(id = savedId) }
                _statusBanner.value = "Виджет «${draft.title}» сохранен в облако Firestore"
            }.onFailure { err ->
                _statusBanner.value = "Ошибка сохранения: ${err.localizedMessage}"
            }
        }
    }

    fun loadWidgetIntoEditor(widget: WidgetDesign) {
        _activeDraft.value = widget
        _statusBanner.value = "Виджет «${widget.title}» открыт в конструкторе"
    }

    fun deleteWidgetFromCloud(widgetId: String) {
        viewModelScope.launch {
            repository.deleteWidget(widgetId).onSuccess {
                if (_activeDraft.value.id == widgetId) {
                    _activeDraft.update { it.copy(id = "") }
                }
                _statusBanner.value = "Виджет удален из облака"
            }
        }
    }

    fun syncWorkspaceNow() {
        viewModelScope.launch {
            val updated = _workspace.value.copy(
                globalGlassOpacity = _activeDraft.value.glassOpacity,
                globalBlurDp = _activeDraft.value.blurRadiusDp,
                syncDeviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            )
            _workspace.value = updated
            repository.saveWorkspace(updated).onSuccess {
                _statusBanner.value = "Настройки облака синхронизированы для всех устройств"
            }.onFailure { err ->
                _statusBanner.value = "Ошибка синхронизации: ${err.localizedMessage}"
            }
        }
    }

    fun seedStarterShowcaseWidgets() {
        viewModelScope.launch {
            val showcase = listOf(
                WidgetDesign(
                    userId = currentUserId,
                    title = "Погода • Прогноз на 3 дня",
                    subtitle = "Малооблачно • Ощущается +15°C",
                    primaryValue = "+16°C",
                    secondaryValue = "3 ДНЯ",
                    prompt = "Расширенная версия виджета погоды с прогнозом на ближайшие 3 дня в стиле Apple Liquid Glass",
                    stylePreset = WidgetStylePreset.LIQUID_GLASS.name,
                    category = WidgetCategory.WEATHER.name,
                    sizePreset = WidgetSizePreset.WIDE_4X2.name,
                    primaryColorHex = "#38BDF8",
                    secondaryColorHex = "#A855F7",
                    surfaceColorHex = "#0F172A",
                    glassOpacity = 0.32,
                    blurRadiusDp = 28.0,
                    specularIntensity = 0.90,
                    cornerRadiusDp = 30.0,
                    iconName = "WbSunny",
                    dataSourceType = WidgetDataSourceType.GOOGLE_WEATHER.name,
                    sourcePackageHint = WidgetDataSourceType.GOOGLE_WEATHER.defaultPackage,
                    tapBehavior = WidgetTapBehavior.REFRESH_WIDGET.name,
                    isAnimated = true,
                    animationStyle = WidgetAnimationStyle.LIQUID_SHIMMER.name,
                    autoDarkAdapt = true,
                    showForecast3Day = true,
                    isPinned = true
                ),
                WidgetDesign(
                    userId = currentUserId,
                    title = "Календарь 2×2",
                    subtitle = "Сетка месяца и текущий день",
                    primaryValue = "Сетка месяца",
                    secondaryValue = "Календарь",
                    prompt = "Компактный виджет календаря 2 на 2 в стиле Liquid Glass",
                    stylePreset = WidgetStylePreset.LIQUID_GLASS.name,
                    category = WidgetCategory.CALENDAR.name,
                    sizePreset = WidgetSizePreset.SMALL_2X2.name,
                    primaryColorHex = "#A855F7",
                    secondaryColorHex = "#EC4899",
                    surfaceColorHex = "#1E1035",
                    glassOpacity = 0.35,
                    blurRadiusDp = 26.0,
                    specularIntensity = 0.92,
                    cornerRadiusDp = 28.0,
                    iconName = "CalendarMonth",
                    dataSourceType = WidgetDataSourceType.CALENDAR_EVENTS.name,
                    sourcePackageHint = WidgetDataSourceType.CALENDAR_EVENTS.defaultPackage,
                    tapBehavior = WidgetTapBehavior.OPEN_SOURCE_APP.name,
                    isAnimated = true,
                    animationStyle = WidgetAnimationStyle.LIQUID_SHIMMER.name,
                    autoDarkAdapt = true,
                    isPinned = false
                ),
                WidgetDesign(
                    userId = currentUserId,
                    title = "Живые Часы • Только ЧЧ:ММ",
                    subtitle = "Точный ход без секунд",
                    primaryValue = "14:45",
                    secondaryValue = "2x2 CLOCK",
                    prompt = "Компактный виджет часов только часы и минуты в стиле Material 3 Expressive",
                    stylePreset = WidgetStylePreset.M3_EXPRESSIVE.name,
                    category = WidgetCategory.CLOCK.name,
                    sizePreset = WidgetSizePreset.SMALL_2X2.name,
                    primaryColorHex = "#F472B6",
                    secondaryColorHex = "#FB923C",
                    surfaceColorHex = "#1E1B4B",
                    glassOpacity = 0.78,
                    blurRadiusDp = 16.0,
                    specularIntensity = 0.40,
                    cornerRadiusDp = 36.0,
                    iconName = "AccessTime",
                    dataSourceType = WidgetDataSourceType.LIVE_CLOCK.name,
                    sourcePackageHint = WidgetDataSourceType.LIVE_CLOCK.defaultPackage,
                    clockVariation = WidgetClockVariation.DIGITAL_HM.name,
                    tapBehavior = WidgetTapBehavior.REFRESH_WIDGET.name,
                    isAnimated = true,
                    animationStyle = WidgetAnimationStyle.LIQUID_SHIMMER.name,
                    autoDarkAdapt = true,
                    isPinned = true
                ),
                WidgetDesign(
                    userId = currentUserId,
                    title = "Заметки Google Keep",
                    subtitle = "Синхронизировано со смартфоном",
                    primaryValue = "Дизайн-ревью в 16:00 • Зарядка",
                    secondaryValue = "Google Keep",
                    prompt = "Виджет быстрых заметок Google Keep в стиле Cyber Neon",
                    stylePreset = WidgetStylePreset.CYBER_NEON.name,
                    category = WidgetCategory.NOTES.name,
                    sizePreset = WidgetSizePreset.WIDE_4X2.name,
                    primaryColorHex = "#10B981",
                    secondaryColorHex = "#06B6D4",
                    surfaceColorHex = "#090D16",
                    glassOpacity = 0.40,
                    blurRadiusDp = 24.0,
                    specularIntensity = 0.95,
                    cornerRadiusDp = 26.0,
                    iconName = "EditNote",
                    dataSourceType = WidgetDataSourceType.NOTES.name,
                    sourcePackageHint = WidgetDataSourceType.NOTES.defaultPackage,
                    tapBehavior = WidgetTapBehavior.OPEN_SOURCE_APP.name,
                    isAnimated = true,
                    animationStyle = WidgetAnimationStyle.PULSE_GLOW.name,
                    autoDarkAdapt = true,
                    isPinned = false
                )
            )
            showcase.forEach { repository.createWidget(it) }
            _statusBanner.value = "Витрина виджетов (Погода 3 дня, Календарь, 2х2 Часы, Заметки) обновлена в облаке"
        }
    }
}
