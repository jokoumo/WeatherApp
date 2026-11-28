package com.example.weatherapp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetProviderMetadataTest {
    @Test
    fun providersOfferTwoCellHeightHorizontalResizeAndDedicatedPreviews() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providers = AppWidgetManager.getInstance(context).installedProviders.associateBy { it.provider }
        val expectedPreviews = mapOf(
            CurrentWeatherWidgetReceiver::class.java to R.layout.widget_preview_current_weather,
            DayPeriodWidgetReceiver::class.java to R.layout.widget_preview_day_period,
            ThreeDayWidgetReceiver::class.java to R.layout.widget_preview_three_day,
        )
        expectedPreviews.forEach { (receiver, preview) ->
            val provider = requireNotNull(providers[ComponentName(context, receiver)])
            assertEquals(3, provider.targetCellWidth)
            assertEquals(2, provider.targetCellHeight)
            assertEquals(AppWidgetProviderInfo.RESIZE_HORIZONTAL, provider.resizeMode)
            assertTrue(provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0)
            assertEquals(preview, provider.previewLayout)
            assertNotNull(LayoutInflater.from(context).inflate(preview, null))
        }
    }
}
