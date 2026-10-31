package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.Base64
import com.example.data.WidgetAnimationStyle
import com.example.data.WidgetClockVariation
import com.example.data.WidgetDataSourceType
import com.example.data.WidgetDesign
import com.example.data.WidgetFontFamily
import com.example.data.WidgetStylePreset
import com.example.ui.theme.loadAndroidTypeface
import com.example.util.DailyForecastItem
import com.example.util.LiveResolvedWidgetTelemetry
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * High-DPI adaptive renderer that produces pixel-identical Liquid Glass, M3 Expressive,
 * Cyber Neon, and Minimal Frost widget bitmaps for BOTH the in-app Compose preview AND
 * the real Android Home Screen RemoteViews widget.
 */
object WidgetBitmapPainter {

    fun TextPaint.applyUniversalShadow(scale: Float, radius: Float = 8f, dy: Float = 3f, alpha: Int = 0xCC) {
        this.setShadowLayer(radius * scale, 0f, dy * scale, (alpha shl 24) or 0x000000)
    }

    fun renderHighResWidgetBitmap(
        widget: WidgetDesign,
        telemetry: LiveResolvedWidgetTelemetry,
        effectiveDark: Boolean,
        animationPhase: Float,
        widthPx: Int = 960,
        heightPx: Int = 460,
        context: Context? = null,
        skipCenterText: Boolean = false
    ): Bitmap {
        val safeW = widthPx.coerceAtLeast(320)
        val safeH = heightPx.coerceAtLeast(220)
        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val aspectRatio = safeW.toFloat() / safeH.toFloat()
        val isSquareOrCompact = aspectRatio < 1.35f
        val is3DayForecast = widget.showForecast3Day && telemetry.forecast3Day.isNotEmpty()
        val isCalendar = telemetry.isCalendar || widget.category.equals("CALENDAR", ignoreCase = true)

        val minDim = min(safeW, safeH).toFloat()
        val scale = when {
            isSquareOrCompact -> (minDim / 380f).coerceIn(0.55f, 2.5f)
            is3DayForecast -> (minDim / 400f).coerceIn(0.55f, 2.5f)
            else -> (min(safeW / 760f, safeH / 380f)).coerceIn(0.55f, 2.5f)
        }

        val style = WidgetStylePreset.fromString(widget.stylePreset)
        val fontEnum = WidgetFontFamily.fromString(widget.fontFamily)
        val boldTypeface = if (context != null) {
            fontEnum.loadAndroidTypeface(context, Typeface.BOLD)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val regularTypeface = if (context != null) {
            fontEnum.loadAndroidTypeface(context, Typeface.NORMAL)
        } else {
            Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val animStyle = if (widget.isAnimated) {
            WidgetAnimationStyle.fromString(widget.animationStyle)
        } else {
            WidgetAnimationStyle.NONE
        }

        val primaryColor = parseAndroidColor(widget.primaryColorHex, Color.parseColor("#38BDF8"))
        val secondaryColor = parseAndroidColor(widget.secondaryColorHex, Color.parseColor("#A855F7"))
        val rawSurface = parseAndroidColor(widget.surfaceColorHex, Color.parseColor("#0F172A"))

        val adaptedSurface = if (widget.autoDarkAdapt) {
            if (effectiveDark) {
                blendColor(rawSurface, Color.parseColor("#060A14"), 0.42f)
            } else {
                blendColor(rawSurface, Color.parseColor("#F8FAFC"), 0.58f)
            }
        } else {
            rawSurface
        }

        val isLightHighOpacity = widget.autoDarkAdapt && !effectiveDark && widget.glassOpacity > 0.58
        val textPrimaryColor = if (isLightHighOpacity) Color.parseColor("#0F172A") else Color.WHITE
        val textSecondaryColor = withAlpha(textPrimaryColor, if (isLightHighOpacity) 195 else 205)

        val cornerRadiusPx = (widget.cornerRadiusDp.toFloat().coerceIn(8f, 48f) * 2.3f * scale)
            .coerceAtMost(min(safeW, safeH) * 0.44f)
        val pad = 2f * scale
        val cardRect = RectF(pad, pad, safeW - pad, safeH - pad)

        // Clip to rounded card
        val clipPath = Path().apply {
            addRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        }
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        val opacity = widget.glassOpacity.toFloat().coerceIn(0.08f, 0.98f)
        val specular = widget.specularIntensity.toFloat().coerceIn(0.0f, 1.0f)
        val phaseAngle = (animationPhase * Math.PI * 2.0).toFloat()

        // 1. Background Surface & Style Shaders
        when (style) {
            WidgetStylePreset.LIQUID_GLASS, WidgetStylePreset.MINIMAL_FROST -> {
                val baseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        cardRect.left,
                        cardRect.top,
                        cardRect.right,
                        cardRect.bottom,
                        intArrayOf(
                            withAlpha(adaptedSurface, (opacity * 225).toInt()),
                            withAlpha(primaryColor, (opacity * 165).toInt()),
                            withAlpha(secondaryColor, (opacity * 145).toInt())
                        ),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(cardRect, baseFillPaint)

                // Animated Caustic Specular Orb
                val shiftX = if (animStyle != WidgetAnimationStyle.NONE) cos(phaseAngle) * 0.18f else 0f
                val shiftY = if (animStyle != WidgetAnimationStyle.NONE) sin(phaseAngle) * 0.14f else 0f
                val causticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cardRect.width() * (0.24f + shiftX),
                        cardRect.height() * (0.18f + shiftY),
                        cardRect.width() * 0.72f,
                        intArrayOf(
                            withAlpha(Color.WHITE, (125 * specular).toInt()),
                            withAlpha(primaryColor, (75 * specular).toInt()),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(cardRect, causticPaint)
            }

            WidgetStylePreset.M3_EXPRESSIVE -> {
                val m3FillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        cardRect.left,
                        cardRect.top,
                        cardRect.right,
                        cardRect.bottom,
                        intArrayOf(
                            withAlpha(adaptedSurface, (opacity * 242).toInt().coerceAtLeast(90)),
                            withAlpha(primaryColor, (opacity * 205).toInt().coerceAtLeast(75))
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(cardRect, m3FillPaint)

                val blobShift = if (animStyle != WidgetAnimationStyle.NONE) sin(phaseAngle) * 24f * scale else 0f
                val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(secondaryColor, 95)
                }
                canvas.drawCircle(
                    cardRect.right - 90f * scale + blobShift,
                    cardRect.top + 80f * scale,
                    165f * scale,
                    blobPaint
                )
                val accentBlobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(primaryColor, 70)
                }
                canvas.drawCircle(
                    cardRect.right - 220f * scale - blobShift * 0.6f,
                    cardRect.bottom - 30f * scale,
                    115f * scale,
                    accentBlobPaint
                )
            }

            WidgetStylePreset.CYBER_NEON -> {
                val cyberBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(Color.parseColor("#070A14"), (opacity * 245).toInt().coerceAtLeast(160))
                }
                canvas.drawRect(cardRect, cyberBgPaint)

                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(primaryColor, 34)
                    strokeWidth = 1.5f * scale
                }
                val step = 64f * scale
                var gx = cardRect.left
                while (gx < cardRect.right) {
                    canvas.drawLine(gx, cardRect.top, gx, cardRect.bottom, gridPaint)
                    gx += step
                }
            }
        }

        // 2. Animated Overlay Effects
        when (animStyle) {
            WidgetAnimationStyle.LIQUID_SHIMMER -> {
                val shimmerCenter = cardRect.left + cardRect.width() * animationPhase
                val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        shimmerCenter - 180f * scale,
                        cardRect.top,
                        shimmerCenter + 180f * scale,
                        cardRect.bottom,
                        intArrayOf(
                            Color.TRANSPARENT,
                            withAlpha(Color.WHITE, (68 * specular).toInt().coerceAtLeast(28)),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(cardRect, shimmerPaint)
            }

            WidgetAnimationStyle.PULSE_GLOW -> {
                val pulseAlpha = ((0.5f + 0.5f * sin(phaseAngle)) * 85f).toInt().coerceIn(20, 100)
                val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        cardRect.centerX(),
                        cardRect.centerY(),
                        cardRect.width() * 0.65f,
                        withAlpha(secondaryColor, pulseAlpha),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(cardRect, pulsePaint)
            }

            WidgetAnimationStyle.TICKING_SWEEP -> {
                // Sleek linear accent pulse along the top edge — no circular dial on the right!
                val sweepW = cardRect.width() * 0.45f
                val sweepX = cardRect.left + (cardRect.width() - sweepW) * animationPhase
                val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 3.5f * scale
                    shader = LinearGradient(
                        sweepX, cardRect.top + 2f * scale,
                        sweepX + sweepW, cardRect.top + 2f * scale,
                        intArrayOf(Color.TRANSPARENT, primaryColor, Color.TRANSPARENT),
                        null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawLine(sweepX, cardRect.top + 2f * scale, sweepX + sweepW, cardRect.top + 2f * scale, sweepPaint)
            }

            WidgetAnimationStyle.ORBITAL_WAVE -> {
                val wavePath = Path()
                val baseY = cardRect.bottom - 48f * scale
                wavePath.moveTo(cardRect.left, cardRect.bottom)
                wavePath.lineTo(cardRect.left, baseY)
                var wx = cardRect.left
                while (wx <= cardRect.right) {
                    val normX = (wx - cardRect.left) / cardRect.width()
                    val wy = baseY + sin(normX * Math.PI * 2.5 + phaseAngle).toFloat() * 16f * scale
                    wavePath.lineTo(wx, wy)
                    wx += 24f * scale
                }
                wavePath.lineTo(cardRect.right, cardRect.bottom)
                wavePath.close()

                val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(primaryColor, 58)
                }
                canvas.drawPath(wavePath, wavePaint)
            }

            WidgetAnimationStyle.NONE -> {}
        }

        canvas.restoreToCount(saveCount)

        // 3. Specular Rim Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 4f * scale
            shader = if (style == WidgetStylePreset.CYBER_NEON) {
                SweepGradient(
                    cardRect.centerX(),
                    cardRect.centerY(),
                    intArrayOf(primaryColor, secondaryColor, primaryColor),
                    null
                )
            } else {
                LinearGradient(
                    cardRect.left,
                    cardRect.top,
                    cardRect.right,
                    cardRect.bottom,
                    intArrayOf(
                        withAlpha(Color.WHITE, (220 * specular).toInt().coerceAtLeast(60)),
                        withAlpha(primaryColor, (175 * specular).toInt().coerceAtLeast(50)),
                        withAlpha(secondaryColor, (140 * specular).toInt().coerceAtLeast(40))
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, borderPaint)

        // 4. Adaptive Foreground Layouts
        when {
            // Case A: 3-Day Real Weather Forecast Layout
            is3DayForecast -> {
                render3DayWeatherLayout(
                    canvas = canvas,
                    cardRect = cardRect,
                    widget = widget,
                    telemetry = telemetry,
                    scale = scale,
                    boldTypeface = boldTypeface,
                    regularTypeface = regularTypeface,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor
                )
            }

            // Case B: Calendar Widget Layout (Wide or 2x2)
            isCalendar -> {
                if (isSquareOrCompact) {
                    renderCalendarCompact2x2(
                        canvas = canvas,
                        cardRect = cardRect,
                        widget = widget,
                        telemetry = telemetry,
                        scale = scale,
                        boldTypeface = boldTypeface,
                        regularTypeface = regularTypeface,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                } else {
                    renderCalendarWideLayout(
                        canvas = canvas,
                        cardRect = cardRect,
                        widget = widget,
                        telemetry = telemetry,
                        scale = scale,
                        boldTypeface = boldTypeface,
                        regularTypeface = regularTypeface,
                        textPrimaryColor = textPrimaryColor,
                        textSecondaryColor = textSecondaryColor,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor
                    )
                }
            }

            // Case C: 2x2 Compact Square Grid (Weather, Clock, Battery, Notes)
            isSquareOrCompact -> {
                renderCompactSquareLayout(
                    canvas = canvas,
                    cardRect = cardRect,
                    widget = widget,
                    telemetry = telemetry,
                    scale = scale,
                    boldTypeface = boldTypeface,
                    regularTypeface = regularTypeface,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    skipCenterText = skipCenterText
                )
            }

            // Case D: Standard Wide 4x2 Layout
            else -> {
                renderStandardWideLayout(
                    canvas = canvas,
                    cardRect = cardRect,
                    widget = widget,
                    telemetry = telemetry,
                    scale = scale,
                    boldTypeface = boldTypeface,
                    regularTypeface = regularTypeface,
                    textPrimaryColor = textPrimaryColor,
                    textSecondaryColor = textSecondaryColor,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    style = style,
                    fontEnum = fontEnum,
                    skipCenterText = skipCenterText
                )
            }
        }

        return bitmap
    }

    /**
     * Compact 2x2 Square Layout: Designed specifically so text & fonts never stretch or squish.
     * Clean layout without distracting floating right circles/pills.
     */
    private fun renderCompactSquareLayout(
        canvas: Canvas,
        cardRect: RectF,
        widget: WidgetDesign,
        telemetry: LiveResolvedWidgetTelemetry,
        scale: Float,
        boldTypeface: Typeface,
        regularTypeface: Typeface,
        textPrimaryColor: Int,
        textSecondaryColor: Int,
        primaryColor: Int,
        secondaryColor: Int,
        skipCenterText: Boolean = false
    ) {
        val innerPad = 26f * scale
        val isClock = widget.category.equals("CLOCK", ignoreCase = true) ||
            widget.dataSourceType == WidgetDataSourceType.LIVE_CLOCK.name
        val clockVar = WidgetClockVariation.fromString(widget.clockVariation)

        if (isClock) {
            val maxAllowedW = cardRect.width() - innerPad * 2.2f

            if (!skipCenterText) {
                val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textPrimaryColor
                    textSize = when (clockVar) {
                        WidgetClockVariation.DIGITAL_HM -> (cardRect.height() * 0.36f).coerceIn(36f * scale, 86f * scale)
                        WidgetClockVariation.DIGITAL_HMS -> (cardRect.height() * 0.22f).coerceIn(22f * scale, 52f * scale)
                        WidgetClockVariation.TIMER -> (cardRect.height() * 0.30f).coerceIn(28f * scale, 68f * scale)
                        WidgetClockVariation.STOPWATCH -> (cardRect.height() * 0.25f).coerceIn(25f * scale, 58f * scale)
                    }
                    typeface = boldTypeface
                    isFakeBoldText = true
                    applyUniversalShadow(scale)
                }

                val timeVal = when (clockVar) {
                    WidgetClockVariation.DIGITAL_HM -> telemetry.primaryValue.take(5).ifBlank { "14:45" }
                    WidgetClockVariation.TIMER -> "04:59"
                    WidgetClockVariation.STOPWATCH -> "00:14.35"
                    WidgetClockVariation.DIGITAL_HMS -> telemetry.primaryValue.ifBlank { "14:45:32" }
                }

                // Auto-fit so time with seconds ("14:45:32") and wide fonts (JetBrains Mono/Montserrat) NEVER overflow
                var tw = timePaint.measureText(timeVal)
                if (tw > maxAllowedW && tw > 0f) {
                    timePaint.textSize *= (maxAllowedW / tw) * 0.94f
                    tw = timePaint.measureText(timeVal)
                }

                val timeY = cardRect.centerY() - 6f * scale
                canvas.drawText(timeVal, cardRect.centerX() - tw / 2f, timeY, timePaint)

                // Date placed directly UNDER the clock using the exact chosen font!
                val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textSecondaryColor
                    textSize = (cardRect.height() * 0.095f).coerceIn(12f * scale, 22f * scale)
                    typeface = boldTypeface // Exact same chosen font as the clock
                    isFakeBoldText = true
                    applyUniversalShadow(scale)
                }
                val dateText = telemetry.subtitle.ifBlank { "Пятница, 30 октября" }
                var dw = datePaint.measureText(dateText)
                if (dw > maxAllowedW && dw > 0f) {
                    datePaint.textSize *= (maxAllowedW / dw) * 0.94f
                    dw = datePaint.measureText(dateText)
                }
                val dateY = timeY + (datePaint.textSize * 1.55f) + 8f * scale
                canvas.drawText(dateText, cardRect.centerX() - dw / 2f, dateY, datePaint)
            }
            return
        }

        val iconSize = 64f * scale
        val iconRect = RectF(
            cardRect.left + innerPad,
            cardRect.top + innerPad,
            cardRect.left + innerPad + iconSize,
            cardRect.top + innerPad + iconSize
        )
        val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(primaryColor, 76)
        }
        canvas.drawRoundRect(iconRect, 20f * scale, 20f * scale, iconBgPaint)

        val customBitmap = decodeCustomIcon(widget.customIconBase64)
        if (customBitmap != null) {
            val inset = 10f * scale
            val dst = RectF(iconRect.left + inset, iconRect.top + inset, iconRect.right - inset, iconRect.bottom - inset)
            canvas.drawBitmap(customBitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG))
        } else {
            drawVectorSymbol(
                canvas = canvas,
                iconName = widget.iconName,
                centerX = iconRect.centerX(),
                centerY = iconRect.centerY(),
                radius = iconSize * 0.30f,
                color = textPrimaryColor,
                scale = scale
            )
        }

        // Top category/source label next to icon (clean, no right circle!)
        val sourceLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondaryColor
            textSize = 21f * scale
            typeface = boldTypeface
            applyUniversalShadow(scale)
        }
        val safeSource = TextUtils.ellipsize(
            telemetry.title,
            sourceLabelPaint,
            cardRect.right - innerPad - (iconRect.right + 14f * scale),
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(safeSource, iconRect.right + 14f * scale, iconRect.centerY() + 7f * scale, sourceLabelPaint)

        // Center / Main Metric
        if (!skipCenterText) {
            val primaryValPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimaryColor
                textSize = (cardRect.height() * 0.22f).coerceIn(44f * scale, 76f * scale)
                typeface = boldTypeface
                applyUniversalShadow(scale)
            }
            val maxContentW = cardRect.width() - innerPad * 2
            val safeVal = TextUtils.ellipsize(telemetry.primaryValue, primaryValPaint, maxContentW, TextUtils.TruncateAt.END).toString()
            val primaryValY = cardRect.centerY() + 18f * scale
            canvas.drawText(safeVal, cardRect.left + innerPad, primaryValY, primaryValPaint)
        }

        // Subtitle at bottom with universal shadow (hidden for clocks to keep design pure and minimal)
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondaryColor
            textSize = 22f * scale
            typeface = regularTypeface
            applyUniversalShadow(scale)
        }
        val safeSub = TextUtils.ellipsize(telemetry.subtitle, subPaint, cardRect.width() - innerPad * 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(safeSub, cardRect.left + innerPad, cardRect.bottom - innerPad, subPaint)
    }

    /**
     * Clean 7-column real month calendar grid without any fake events.
     * Accurately calculates days in current month and highlights today's date.
     */
    private fun renderMonthCalendarTable(
        canvas: Canvas,
        tableRect: RectF,
        scale: Float,
        boldTypeface: Typeface,
        regularTypeface: Typeface,
        textPrimaryColor: Int,
        textSecondaryColor: Int,
        primaryColor: Int,
        secondaryColor: Int,
        showMonthHeader: Boolean = true
    ) {
        val today = LocalDate.now()
        val yearMonth = YearMonth.from(today)
        val daysInMonth = yearMonth.lengthOfMonth()
        val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value // 1 = Mon .. 7 = Sun
        val todayDay = today.dayOfMonth

        var topCursor = tableRect.top

        if (showMonthHeader) {
            val ruLocale = Locale.forLanguageTag("ru")
            val monthYearStr = DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale)
                .format(today)
                .replaceFirstChar { it.uppercase() }

            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimaryColor
                textSize = 22f * scale
                typeface = boldTypeface
                applyUniversalShadow(scale)
            }
            val headerW = headerPaint.measureText(monthYearStr)
            canvas.drawText(monthYearStr, tableRect.centerX() - headerW / 2f, topCursor + 20f * scale, headerPaint)
            topCursor += 32f * scale
        }

        // Weekdays row (Пн Вт Ср Чт Пт Сб Вс)
        val weekdays = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
        val colW = tableRect.width() / 7f

        val weekdayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * scale
            typeface = boldTypeface
            applyUniversalShadow(scale)
        }

        for (i in 0 until 7) {
            val colCenterX = tableRect.left + i * colW + colW / 2f
            val isWeekend = i >= 5
            weekdayPaint.color = if (isWeekend) secondaryColor else textSecondaryColor
            val lbl = weekdays[i]
            val lblW = weekdayPaint.measureText(lbl)
            canvas.drawText(lbl, colCenterX - lblW / 2f, topCursor + 14f * scale, weekdayPaint)
        }
        topCursor += 22f * scale

        // Day numbers grid
        val gridHeight = tableRect.bottom - topCursor
        val totalWeeks = ((firstDayOfWeek - 1 + daysInMonth + 6) / 7).coerceAtLeast(5)
        val rowH = gridHeight / totalWeeks

        val dayNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f * scale
            typeface = regularTypeface
            applyUniversalShadow(scale)
        }
        val todayHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            setShadowLayer(8f * scale, 0f, 3f * scale, 0xCC000000.toInt())
        }

        for (day in 1..daysInMonth) {
            val slot = (firstDayOfWeek - 1) + (day - 1)
            val col = slot % 7
            val row = slot / 7

            val cellCenterX = tableRect.left + col * colW + colW / 2f
            val cellCenterY = topCursor + row * rowH + rowH / 2f

            if (day == todayDay) {
                val radius = min(colW, rowH) * 0.42f
                canvas.drawCircle(cellCenterX, cellCenterY - 2f * scale, radius, todayHighlightPaint)
                dayNumPaint.typeface = boldTypeface
                dayNumPaint.color = Color.WHITE
            } else {
                dayNumPaint.typeface = regularTypeface
                dayNumPaint.color = if (col >= 5) withAlpha(secondaryColor, 230) else textPrimaryColor
            }

            val dayStr = day.toString()
            val textW = dayNumPaint.measureText(dayStr)
            val baseline = cellCenterY - (dayNumPaint.descent() + dayNumPaint.ascent()) / 2f - 2f * scale
            canvas.drawText(dayStr, cellCenterX - textW / 2f, baseline, dayNumPaint)
        }
    }

    /**
     * Calendar 2x2 Compact Square Layout: Pure 2x2 grid calendar, zero fake meetings.
     */
    private fun renderCalendarCompact2x2(
        canvas: Canvas,
        cardRect: RectF,
        widget: WidgetDesign,
        telemetry: LiveResolvedWidgetTelemetry,
        scale: Float,
        boldTypeface: Typeface,
        regularTypeface: Typeface,
        textPrimaryColor: Int,
        textSecondaryColor: Int,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        val innerPad = 18f * scale
        val tableRect = RectF(
            cardRect.left + innerPad,
            cardRect.top + innerPad,
            cardRect.right - innerPad,
            cardRect.bottom - innerPad
        )
        renderMonthCalendarTable(
            canvas = canvas,
            tableRect = tableRect,
            scale = scale,
            boldTypeface = boldTypeface,
            regularTypeface = regularTypeface,
            textPrimaryColor = textPrimaryColor,
            textSecondaryColor = textSecondaryColor,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            showMonthHeader = true
        )
    }

    /**
     * Calendar Wide 4x2 Layout: Hero Date card on left + Clean 7-column Month grid on right.
     * Zero fake meetings!
     */
    private fun renderCalendarWideLayout(
        canvas: Canvas,
        cardRect: RectF,
        widget: WidgetDesign,
        telemetry: LiveResolvedWidgetTelemetry,
        scale: Float,
        boldTypeface: Typeface,
        regularTypeface: Typeface,
        textPrimaryColor: Int,
        textSecondaryColor: Int,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        val innerPad = 26f * scale
        val leftCardWidth = cardRect.width() * 0.34f
        val leftRect = RectF(
            cardRect.left + innerPad,
            cardRect.top + innerPad,
            cardRect.left + innerPad + leftCardWidth,
            cardRect.bottom - innerPad
        )
        val leftBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(primaryColor, 55)
        }
        canvas.drawRoundRect(leftRect, 22f * scale, 22f * scale, leftBgPaint)

        // Weekday in left hero card
        val weekPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            textSize = 24f * scale
            typeface = boldTypeface
            applyUniversalShadow(scale)
        }
        canvas.drawText(telemetry.calendarDayOfWeek.uppercase(), leftRect.left + 18f * scale, leftRect.top + 32f * scale, weekPaint)

        // Big day number in left hero card
        val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimaryColor
            textSize = leftRect.height() * 0.42f
            typeface = boldTypeface
            applyUniversalShadow(scale)
        }
        canvas.drawText(telemetry.calendarDayNumber, leftRect.left + 16f * scale, leftRect.centerY() + 20f * scale, dayPaint)

        // Month & Year in left hero card
        val monthPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondaryColor
            textSize = 22f * scale
            typeface = regularTypeface
            applyUniversalShadow(scale)
        }
        canvas.drawText(telemetry.calendarMonthYear, leftRect.left + 18f * scale, leftRect.bottom - 20f * scale, monthPaint)

        // Right side: Real 7-column Month Calendar Table (NO FAKE EVENTS!)
        val rightStartX = leftRect.right + 22f * scale
        val tableRect = RectF(
            rightStartX,
            cardRect.top + innerPad,
            cardRect.right - innerPad,
            cardRect.bottom - innerPad
        )
        renderMonthCalendarTable(
            canvas = canvas,
            tableRect = tableRect,
            scale = scale,
            boldTypeface = boldTypeface,
            regularTypeface = regularTypeface,
            textPrimaryColor = textPrimaryColor,
            textSecondaryColor = textSecondaryColor,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            showMonthHeader = false
        )
    }

    /**
     * 3-Day Real Weather Forecast Layout: Current conditions on top + 3 daily cards at bottom.
     */
    private fun render3DayWeatherLayout(
        canvas: Canvas,
        cardRect: RectF,
        widget: WidgetDesign,
        telemetry: LiveResolvedWidgetTelemetry,
        scale: Float,
        boldTypeface: Typeface,
        regularTypeface: Typeface,
        textPrimaryColor: Int,
        textSecondaryColor: Int,
        primaryColor: Int,
        secondaryColor: Int
    ) {
        val innerPad = 32f * scale

        // Top Section: City & Condition on left, Big Temp & Source on right
        val iconSize = 78f * scale
        val iconRect = RectF(
            cardRect.left + innerPad,
            cardRect.top + innerPad,
            cardRect.left + innerPad + iconSize,
            cardRect.top + innerPad + iconSize
        )
        val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(primaryColor, 70)
        }
        canvas.drawRoundRect(iconRect, 22f * scale, 22f * scale, iconBgPaint)
        drawVectorSymbol(
            canvas = canvas,
            iconName = widget.iconName,
            centerX = iconRect.centerX(),
            centerY = iconRect.centerY(),
            radius = iconSize * 0.30f,
            color = textPrimaryColor,
            scale = scale
        )

        val cityPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimaryColor
            textSize = 34f * scale
            typeface = boldTypeface
            applyUniversalShadow(scale)
        }
        val cityText = telemetry.locationName.ifBlank { "Москва" }
        canvas.drawText(cityText, iconRect.right + 20f * scale, iconRect.top + 34f * scale, cityPaint)

        val conditionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondaryColor
            textSize = 23f * scale
            typeface = regularTypeface
            applyUniversalShadow(scale)
        }
        val subText = TextUtils.ellipsize(
            telemetry.subtitle,
            conditionPaint,
            cardRect.width() * 0.45f,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(subText, iconRect.right + 20f * scale, iconRect.top + 68f * scale, conditionPaint)

        // Right side: Current Temp
        val currentTempPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimaryColor
            textSize = 62f * scale
            typeface = boldTypeface
            applyUniversalShadow(scale)
        }
        val tempText = telemetry.primaryValue
        val tempW = currentTempPaint.measureText(tempText)
        canvas.drawText(tempText, cardRect.right - innerPad - tempW, iconRect.top + 52f * scale, currentTempPaint)

        // Divider
        val dividerY = cardRect.top + 135f * scale
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 2f * scale
            shader = LinearGradient(
                cardRect.left + innerPad, dividerY, cardRect.right - innerPad, dividerY,
                intArrayOf(Color.TRANSPARENT, withAlpha(Color.WHITE, 80), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawLine(cardRect.left + innerPad, dividerY, cardRect.right - innerPad, dividerY, dividerPaint)

        // Bottom Section: 3 Forecast Columns
        val items = telemetry.forecast3Day.take(3)
        val colGap = 16f * scale
        val totalColsW = cardRect.width() - innerPad * 2
        val colWidth = (totalColsW - colGap * (items.size - 1)) / items.size.coerceAtLeast(1)
        val colTop = dividerY + 16f * scale
        val colBottom = cardRect.bottom - innerPad

        for ((idx, day) in items.withIndex()) {
            val left = cardRect.left + innerPad + idx * (colWidth + colGap)
            val right = left + colWidth
            val colRect = RectF(left, colTop, right, colBottom)

            val colCardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(Color.WHITE, if (idx == 0) 36 else 22)
            }
            canvas.drawRoundRect(colRect, 20f * scale, 20f * scale, colCardBg)

            // Day label
            val dayLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (idx == 0) primaryColor else textPrimaryColor
                textSize = 25f * scale
                typeface = boldTypeface
                applyUniversalShadow(scale)
            }
            val dayLabelText = day.dayName
            val dayLabelW = dayLabelPaint.measureText(dayLabelText)
            canvas.drawText(dayLabelText, colRect.centerX() - dayLabelW / 2f, colRect.top + 34f * scale, dayLabelPaint)

            // Mini Weather Icon
            drawVectorSymbol(
                canvas = canvas,
                iconName = day.iconName,
                centerX = colRect.centerX(),
                centerY = colRect.top + 76f * scale,
                radius = 18f * scale,
                color = textPrimaryColor,
                scale = scale * 0.85f
            )

            // Temp range (e.g. +8°..+16°)
            val tempRangePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimaryColor
                textSize = 26f * scale
                typeface = boldTypeface
                applyUniversalShadow(scale)
            }
            val tempRangeText = "${day.minTemp}..${day.maxTemp}"
            val rangeW = tempRangePaint.measureText(tempRangeText)
            canvas.drawText(tempRangeText, colRect.centerX() - rangeW / 2f, colRect.bottom - 44f * scale, tempRangePaint)

            // Condition short text
            val condPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textSecondaryColor
                textSize = 20f * scale
                typeface = regularTypeface
                applyUniversalShadow(scale)
            }
            val elCond = TextUtils.ellipsize(day.conditionText, condPaint, colWidth - 14f * scale, TextUtils.TruncateAt.END).toString()
            val condW = condPaint.measureText(elCond)
            canvas.drawText(elCond, colRect.centerX() - condW / 2f, colRect.bottom - 16f * scale, condPaint)
        }
    }

    /**
     * Standard Wide 4x2 Layout
     */
    private fun renderStandardWideLayout(
        canvas: Canvas,
        cardRect: RectF,
        widget: WidgetDesign,
        telemetry: LiveResolvedWidgetTelemetry,
        scale: Float,
        boldTypeface: Typeface,
        regularTypeface: Typeface,
        textPrimaryColor: Int,
        textSecondaryColor: Int,
        primaryColor: Int,
        secondaryColor: Int,
        style: WidgetStylePreset,
        fontEnum: WidgetFontFamily,
        skipCenterText: Boolean = false
    ) {
        val innerPad = 36f * scale
        val isClock = widget.category.equals("CLOCK", ignoreCase = true) ||
            widget.dataSourceType == WidgetDataSourceType.LIVE_CLOCK.name

        if (isClock) {
            // Clean, elegant clock layout without cluttered descriptions above or below
            val clockVar = WidgetClockVariation.fromString(widget.clockVariation)
            val maxAllowedW = cardRect.width() - innerPad * 2.2f

            if (!skipCenterText) {
                val primaryValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textPrimaryColor
                    textSize = when (clockVar) {
                        WidgetClockVariation.DIGITAL_HM -> (cardRect.height() * 0.42f).coerceIn(44f * scale, 100f * scale)
                        WidgetClockVariation.DIGITAL_HMS -> (cardRect.height() * 0.28f).coerceIn(28f * scale, 70f * scale)
                        WidgetClockVariation.TIMER -> (cardRect.height() * 0.36f).coerceIn(34f * scale, 84f * scale)
                        else -> (cardRect.height() * 0.32f).coerceIn(32f * scale, 76f * scale)
                    }
                    typeface = boldTypeface
                    isFakeBoldText = true
                    applyUniversalShadow(scale)
                }
                val timeVal = when (clockVar) {
                    WidgetClockVariation.DIGITAL_HM -> telemetry.primaryValue.take(5).ifBlank { "14:45" }
                    WidgetClockVariation.TIMER -> "04:59"
                    WidgetClockVariation.STOPWATCH -> "00:14.35"
                    else -> telemetry.primaryValue.ifBlank { "14:45:32" }
                }

                var tw = primaryValuePaint.measureText(timeVal)
                if (tw > maxAllowedW && tw > 0f) {
                    primaryValuePaint.textSize *= (maxAllowedW / tw) * 0.94f
                    tw = primaryValuePaint.measureText(timeVal)
                }

                val timeY = cardRect.centerY() - 6f * scale
                canvas.drawText(timeVal, cardRect.centerX() - tw / 2f, timeY, primaryValuePaint)

                // Date placed directly UNDER the clock using the exact chosen font!
                val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = textSecondaryColor
                    textSize = (cardRect.height() * 0.11f).coerceIn(14f * scale, 24f * scale)
                    typeface = boldTypeface
                    isFakeBoldText = true
                    applyUniversalShadow(scale)
                }
                val dateText = telemetry.subtitle.ifBlank { "Пятница, 30 октября" }
                var dw = datePaint.measureText(dateText)
                if (dw > maxAllowedW && dw > 0f) {
                    datePaint.textSize *= (maxAllowedW / dw) * 0.94f
                    dw = datePaint.measureText(dateText)
                }
                val dateY = timeY + (datePaint.textSize * 1.55f) + 8f * scale
                canvas.drawText(dateText, cardRect.centerX() - dw / 2f, dateY, datePaint)
            }
            return
        }

        val iconBoxSize = 90f * scale
        val iconRect = RectF(
            cardRect.left + innerPad,
            cardRect.top + innerPad,
            cardRect.left + innerPad + iconBoxSize,
            cardRect.top + innerPad + iconBoxSize
        )
        val iconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(primaryColor, 76)
        }
        canvas.drawRoundRect(iconRect, 28f * scale, 28f * scale, iconBgPaint)

        val customBitmap = decodeCustomIcon(widget.customIconBase64)
        if (customBitmap != null) {
            val inset = 14f * scale
            val dst = RectF(iconRect.left + inset, iconRect.top + inset, iconRect.right - inset, iconRect.bottom - inset)
            canvas.drawBitmap(customBitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG))
        } else {
            drawVectorSymbol(
                canvas = canvas,
                iconName = widget.iconName,
                centerX = iconRect.centerX(),
                centerY = iconRect.centerY(),
                radius = iconBoxSize * 0.28f,
                color = textPrimaryColor,
                scale = scale
            )
        }

        // Secondary Pill Badge (top-right)
        val badgeText = telemetry.secondaryValue.ifBlank {
            WidgetDataSourceType.fromString(widget.dataSourceType).label
        }
        val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimaryColor
            textSize = 24f * scale
            typeface = boldTypeface
            applyUniversalShadow(scale)
        }
        val maxBadgeWidth = cardRect.width() * 0.36f
        val ellipsizedBadge = TextUtils.ellipsize(badgeText, badgeTextPaint, maxBadgeWidth, TextUtils.TruncateAt.END).toString()
        val badgeTextWidth = badgeTextPaint.measureText(ellipsizedBadge)
        val badgePadH = 22f * scale
        val badgeHeight = 52f * scale
        val badgeRect = RectF(
            cardRect.right - innerPad - badgeTextWidth - badgePadH * 2,
            cardRect.top + innerPad + 6f * scale,
            cardRect.right - innerPad,
            cardRect.top + innerPad + 6f * scale + badgeHeight
        )
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(secondaryColor, 88)
        }
        canvas.drawRoundRect(badgeRect, badgeHeight / 2f, badgeHeight / 2f, badgeBgPaint)
        val badgeBaseline = badgeRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
        canvas.drawText(ellipsizedBadge, badgeRect.left + badgePadH, badgeBaseline, badgeTextPaint)

        // Title next to Icon (clean, without internal meta-descriptions like "Liquid Glass • Google Sans")
        val titleStartX = iconRect.right + 24f * scale
        val availableTitleWidth = (badgeRect.left - titleStartX - 18f * scale).coerceAtLeast(140f * scale)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimaryColor
            textSize = 34f * scale
            typeface = boldTypeface
            applyUniversalShadow(scale)
        }
        val safeTitle = TextUtils.ellipsize(telemetry.title, titlePaint, availableTitleWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(safeTitle, titleStartX, iconRect.centerY() + 10f * scale, titlePaint)

        // Bottom Section: Primary Value & Subtitle
        val contentWidth = cardRect.width() - innerPad * 2
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textSecondaryColor
            textSize = 26f * scale
            typeface = regularTypeface
            applyUniversalShadow(scale)
        }
        val safeSubtitle = TextUtils.ellipsize(telemetry.subtitle, subtitlePaint, contentWidth, TextUtils.TruncateAt.END).toString()
        val subtitleY = cardRect.bottom - innerPad
        canvas.drawText(safeSubtitle, cardRect.left + innerPad, subtitleY, subtitlePaint)

        if (!skipCenterText) {
            val primaryValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textPrimaryColor
                textSize = 74f * scale
                typeface = boldTypeface
                applyUniversalShadow(scale)
            }
            val safePrimaryVal = TextUtils.ellipsize(telemetry.primaryValue, primaryValuePaint, contentWidth, TextUtils.TruncateAt.END).toString()
            val primaryValY = subtitleY - 44f * scale
            canvas.drawText(safePrimaryVal, cardRect.left + innerPad, primaryValY, primaryValuePaint)
        }
    }

    private fun drawVectorSymbol(
        canvas: Canvas,
        iconName: String,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
        scale: Float
    ) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.STROKE
            strokeWidth = 5f * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = Paint.Style.FILL
        }

        when (iconName) {
            "CalendarToday", "CalendarMonth", "Event", "DateRange" -> {
                val r = radius * 0.90f
                val calRect = RectF(centerX - r, centerY - r * 0.75f, centerX + r, centerY + r)
                canvas.drawRoundRect(calRect, 8f * scale, 8f * scale, strokePaint)
                // Top binder tabs
                canvas.drawLine(centerX - r * 0.5f, centerY - r, centerX - r * 0.5f, centerY - r * 0.65f, strokePaint)
                canvas.drawLine(centerX + r * 0.5f, centerY - r, centerX + r * 0.5f, centerY - r * 0.65f, strokePaint)
                // Header divider
                canvas.drawLine(centerX - r, centerY - r * 0.25f, centerX + r, centerY - r * 0.25f, strokePaint)
                // Grid dots
                canvas.drawCircle(centerX - r * 0.45f, centerY + r * 0.20f, 3.5f * scale, fillPaint)
                canvas.drawCircle(centerX, centerY + r * 0.20f, 3.5f * scale, fillPaint)
                canvas.drawCircle(centerX + r * 0.45f, centerY + r * 0.20f, 3.5f * scale, fillPaint)
                canvas.drawCircle(centerX - r * 0.45f, centerY + r * 0.65f, 3.5f * scale, fillPaint)
                canvas.drawCircle(centerX, centerY + r * 0.65f, 3.5f * scale, fillPaint)
            }

            "WbSunny" -> {
                canvas.drawCircle(centerX, centerY, radius * 0.52f, fillPaint)
                for (i in 0 until 8) {
                    val angle = i * (Math.PI / 4.0)
                    val x1 = centerX + cos(angle).toFloat() * radius * 0.72f
                    val y1 = centerY + sin(angle).toFloat() * radius * 0.72f
                    val x2 = centerX + cos(angle).toFloat() * radius * 1.02f
                    val y2 = centerY + sin(angle).toFloat() * radius * 1.02f
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
            }

            "Cloud", "WbCloudy" -> {
                val r = radius * 0.85f
                val cloudPath = Path().apply {
                    moveTo(centerX - r * 0.6f, centerY + r * 0.35f)
                    lineTo(centerX + r * 0.6f, centerY + r * 0.35f)
                    quadTo(centerX + r * 0.9f, centerY + r * 0.35f, centerX + r * 0.9f, centerY)
                    quadTo(centerX + r * 0.85f, centerY - r * 0.4f, centerX + r * 0.4f, centerY - r * 0.4f)
                    quadTo(centerX + r * 0.1f, centerY - r * 0.8f, centerX - r * 0.3f, centerY - r * 0.5f)
                    quadTo(centerX - r * 0.9f, centerY - r * 0.4f, centerX - r * 0.9f, centerY + r * 0.1f)
                    quadTo(centerX - r * 0.9f, centerY + r * 0.35f, centerX - r * 0.6f, centerY + r * 0.35f)
                    close()
                }
                canvas.drawPath(cloudPath, fillPaint)
            }

            "WaterDrop", "Thunderstorm" -> {
                val drop = Path().apply {
                    moveTo(centerX, centerY - radius * 0.9f)
                    quadTo(centerX + radius * 0.65f, centerY + radius * 0.1f, centerX + radius * 0.55f, centerY + radius * 0.5f)
                    quadTo(centerX, centerY + radius * 0.95f, centerX - radius * 0.55f, centerY + radius * 0.5f)
                    quadTo(centerX - radius * 0.65f, centerY + radius * 0.1f, centerX, centerY - radius * 0.9f)
                    close()
                }
                canvas.drawPath(drop, fillPaint)
            }

            "AccessTime" -> {
                canvas.drawCircle(centerX, centerY, radius * 0.92f, strokePaint)
                canvas.drawLine(centerX, centerY, centerX, centerY - radius * 0.55f, strokePaint)
                canvas.drawLine(centerX, centerY, centerX + radius * 0.45f, centerY + radius * 0.2f, strokePaint)
            }

            "EditNote" -> {
                val r = radius * 0.85f
                val rect = RectF(centerX - r, centerY - r, centerX + r, centerY + r)
                canvas.drawRoundRect(rect, 8f * scale, 8f * scale, strokePaint)
                canvas.drawLine(centerX - r * 0.55f, centerY - r * 0.3f, centerX + r * 0.55f, centerY - r * 0.3f, strokePaint)
                canvas.drawLine(centerX - r * 0.55f, centerY + r * 0.15f, centerX + r * 0.25f, centerY + r * 0.15f, strokePaint)
            }

            "BatteryChargingFull" -> {
                val r = radius * 0.8f
                val body = RectF(centerX - r * 0.65f, centerY - r * 0.8f, centerX + r * 0.65f, centerY + r * 0.9f)
                canvas.drawRoundRect(body, 6f * scale, 6f * scale, strokePaint)
                val bolt = Path().apply {
                    moveTo(centerX + r * 0.1f, centerY - r * 0.45f)
                    lineTo(centerX - r * 0.28f, centerY + r * 0.05f)
                    lineTo(centerX + r * 0.08f, centerY + r * 0.05f)
                    lineTo(centerX - r * 0.1f, centerY + r * 0.5f)
                }
                canvas.drawPath(bolt, strokePaint)
            }

            "MusicNote" -> {
                canvas.drawCircle(centerX - radius * 0.25f, centerY + radius * 0.45f, radius * 0.32f, fillPaint)
                canvas.drawLine(centerX + radius * 0.05f, centerY + radius * 0.45f, centerX + radius * 0.05f, centerY - radius * 0.65f, strokePaint)
                canvas.drawLine(centerX + radius * 0.05f, centerY - radius * 0.65f, centerX + radius * 0.65f, centerY - radius * 0.45f, strokePaint)
            }

            "DirectionsRun" -> {
                canvas.drawCircle(centerX, centerY - radius * 0.65f, radius * 0.24f, fillPaint)
                canvas.drawLine(centerX, centerY - radius * 0.4f, centerX - radius * 0.15f, centerY + radius * 0.2f, strokePaint)
                canvas.drawLine(centerX - radius * 0.15f, centerY + radius * 0.2f, centerX - radius * 0.6f, centerY + radius * 0.75f, strokePaint)
                canvas.drawLine(centerX - radius * 0.15f, centerY + radius * 0.2f, centerX + radius * 0.45f, centerY + radius * 0.8f, strokePaint)
            }

            "TrendingUp" -> {
                val path = Path().apply {
                    moveTo(centerX - radius * 0.8f, centerY + radius * 0.6f)
                    lineTo(centerX - radius * 0.2f, centerY)
                    lineTo(centerX + radius * 0.2f, centerY + radius * 0.3f)
                    lineTo(centerX + radius * 0.8f, centerY - radius * 0.6f)
                }
                canvas.drawPath(path, strokePaint)
                canvas.drawLine(centerX + radius * 0.4f, centerY - radius * 0.6f, centerX + radius * 0.8f, centerY - radius * 0.6f, strokePaint)
                canvas.drawLine(centerX + radius * 0.8f, centerY - radius * 0.6f, centerX + radius * 0.8f, centerY - radius * 0.2f, strokePaint)
            }

            else -> {
                // 4-point AI Star
                val star = Path().apply {
                    moveTo(centerX, centerY - radius)
                    quadTo(centerX, centerY, centerX + radius, centerY)
                    quadTo(centerX, centerY, centerX, centerY + radius)
                    quadTo(centerX, centerY, centerX - radius, centerY)
                    quadTo(centerX, centerY, centerX, centerY - radius)
                    close()
                }
                canvas.drawPath(star, fillPaint)
            }
        }
    }

    private fun decodeCustomIcon(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAndroidColor(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex)
        } catch (e: Exception) {
            fallback
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val r = (Color.red(c1) * (1f - ratio) + Color.red(c2) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) * (1f - ratio) + Color.green(c2) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * (1f - ratio) + Color.blue(c2) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
