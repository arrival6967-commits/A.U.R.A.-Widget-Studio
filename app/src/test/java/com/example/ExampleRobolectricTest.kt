package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.fontResId
import com.example.ui.theme.loadAndroidTypeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("A.U.R.A. Widgets", appName)
  }

  @Test
  fun `verify font loading`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    for (font in com.example.data.WidgetFontFamily.entries) {
      val resId = font.fontResId()
      val tf = font.loadAndroidTypeface(context)
      assertNotNull("Font ${font.name} must load", tf)
      println("Font ${font.name} resId=$resId loaded=$tf")
    }
  }

  @Test
  fun `verify multi-instance widget isolation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val weatherWidget = com.example.data.WidgetDesign(
      title = "Погода",
      category = com.example.data.WidgetCategory.WEATHER.name,
      primaryValue = "+21°C"
    )
    val clockWidget = com.example.data.WidgetDesign(
      title = "Часы",
      category = com.example.data.WidgetCategory.CLOCK.name,
      clockVariation = com.example.data.WidgetClockVariation.DIGITAL_HMS.name,
      primaryValue = "14:45:32"
    )

    // Assign weather to widget ID 101
    com.example.widget.AuraAppWidgetProvider.saveWidgetForAppWidgetId(context, 101, weatherWidget)

    // Now save a new active clock draft to local cache
    com.example.widget.AuraAppWidgetProvider.saveActiveWidgetToLocalCache(context, clockWidget)

    // Verify widget 101 still has WEATHER, not overwritten by clock
    val loaded = com.example.widget.AuraAppWidgetProvider.loadWidgetForAppWidgetId(context, 101)
    assertEquals(com.example.data.WidgetCategory.WEATHER.name, loaded.category)
    assertEquals("Погода", loaded.title)
  }

  @Test
  fun `verify compact 2x2 clock with seconds bitmap rendering`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val clockWidget = com.example.data.WidgetDesign(
      title = "Живые Часы",
      category = com.example.data.WidgetCategory.CLOCK.name,
      clockVariation = com.example.data.WidgetClockVariation.DIGITAL_HMS.name,
      sizePreset = com.example.data.WidgetSizePreset.SMALL_2X2.name,
      fontFamily = com.example.data.WidgetFontFamily.MONTSERRAT.name,
      primaryValue = "14:45:32",
      subtitle = "Пятница, 30 октября"
    )
    val telemetry = com.example.util.AppDataSourceManager.resolveLiveTelemetry(context, clockWidget)
    val bmp = com.example.ui.components.WidgetBitmapPainter.renderHighResWidgetBitmap(
      widget = clockWidget,
      telemetry = telemetry,
      effectiveDark = true,
      animationPhase = 0.5f,
      widthPx = 480,
      heightPx = 480,
      context = context
    )
    assertNotNull(bmp)
    assertEquals(480, bmp.width)
    assertEquals(480, bmp.height)
  }
}

