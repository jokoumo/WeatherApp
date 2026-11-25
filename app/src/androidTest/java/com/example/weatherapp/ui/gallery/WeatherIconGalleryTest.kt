package com.example.weatherapp.ui.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the development gallery itself. A redesign is only as good as the review behind it, so
 * the gallery has to contain every condition of the family and really render each symbol at each
 * comparison size instead of silently dropping rows.
 */
@RunWith(AndroidJUnit4::class)
class WeatherIconGalleryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun galleryCoversEveryWeatherCondition() {
        assertEquals(
            "the gallery must show each condition at least once",
            WeatherCondition.entries.toSet(),
            galleryEntries.map { it.condition }.toSet(),
        )
    }

    @Test
    fun galleryRendersEverySymbolAtEveryComparisonSize() {
        composeRule.setContent { WeatherAppTheme { WeatherIconGallery() } }
        composeRule.onNodeWithText("Wettersymbole").assertIsDisplayed()
        composeRule.onNodeWithText("Helle Karte").assertIsDisplayed()
        val symbols = composeRule
            .onAllNodesWithContentDescription(
                context.getString(R.string.content_description_weather_icon),
            )
            .fetchSemanticsNodes(true)
        assertEquals(
            galleryEntries.size * gallerySizes.size * gallerySections.size,
            symbols.size,
        )
    }
}
