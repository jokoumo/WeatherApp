package com.example.weatherapp.ui

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.testing.EmptyGeocodingApi
import com.example.weatherapp.testing.FakeForecastApi
import com.example.weatherapp.testing.InMemorySettingsStore
import com.example.weatherapp.testing.InMemoryThemeStore
import com.example.weatherapp.testing.NoForecastCache
import com.example.weatherapp.ui.settings.AppearanceViewModel
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The daily detail sheet driven through the real root composable and a real [MainViewModel].
 *
 * The forecast comes from [FakeForecastApi], which answers once with a complete multi-day payload.
 * Everything the sheet shows has to come out of that already loaded response: opening a day must
 * never ask the weather service again, and the day after tomorrow must be as complete as tomorrow
 * even though the rolling strip only displays 24 hours.
 */
@RunWith(AndroidJUnit4::class)
class DayForecastSheetNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val hamburg = SavedLocation(
        id = "2911298",
        name = "Hamburg",
        latitude = 53.55,
        longitude = 9.99,
        admin1 = "Freie und Hansestadt Hamburg",
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    private lateinit var forecastApi: FakeForecastApi
    private lateinit var mainViewModel: MainViewModel
    private lateinit var appearanceViewModel: AppearanceViewModel

    @Before
    fun setUp() {
        forecastApi = FakeForecastApi()
        mainViewModel = MainViewModel(
            locationRepository = LocationRepository(
                settingsStore = InMemorySettingsStore(
                    LocationSettings(homeLocation = hamburg, selectedLocationId = hamburg.id),
                ),
                geocodingApi = EmptyGeocodingApi(),
            ),
            weatherRepository = WeatherRepository(forecastApi, NoForecastCache(), ForecastMapper()),
        )
        appearanceViewModel = AppearanceViewModel(InMemoryThemeStore())
    }

    private fun renderRoot() {
        composeRule.setContent {
            WeatherAppTheme {
                WeatherRoot(
                    viewModel = mainViewModel,
                    appearanceViewModel = appearanceViewModel,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            mainViewModel.uiState.value.weather.sections.detailedDays.size == 2
        }
        composeRule.waitForIdle()
    }

    /** The selected day as the application sees it, so the test never guesses a date. */
    private fun today() = mainViewModel.uiState.value.weather.today
        ?: error("the dashboard has no current date")

    private fun openDailySheet(date: java.time.LocalDate) {
        composeRule.onNodeWithText(WeatherFormatter.weekdayAndDate(date))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
    }

    private fun closeSheet() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_close)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun bothDetailedDayCardsOpenTheirOwnSheetWithoutANewRequest() {
        renderRoot()
        val today = today()
        val requestsAfterLoad = forecastApi.callCount

        openDailySheet(today.plusDays(1))
        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertIsDisplayed()
        composeRule.onNodeWithText(WeatherFormatter.fullWeekdayAndDate(today.plusDays(1)))
            .assertIsDisplayed()
        closeSheet()

        openDailySheet(today.plusDays(2))
        composeRule.onNodeWithText(WeatherFormatter.fullWeekdayAndDate(today.plusDays(2)))
            .assertIsDisplayed()
        composeRule.onNodeWithText(WeatherFormatter.fullWeekdayAndDate(today.plusDays(1)))
            .assertDoesNotExist()
        closeSheet()

        assertEquals(
            "opening or closing a daily detail sheet sent a forecast request",
            requestsAfterLoad,
            forecastApi.callCount,
        )
        assertEquals(hamburg.id, mainViewModel.uiState.value.weather.location?.id)
    }

    /**
     * The day after tomorrow is fully described from the same response: the rolling strip only
     * holds 24 hours, so a sheet built from it would show empty periods here.
     */
    @Test
    fun dayAfterTomorrowIsAggregatedFromTheAlreadyLoadedHourlyData() {
        renderRoot()
        val today = today()

        openDailySheet(today.plusDays(2))

        listOf(
            R.string.day_period_morning,
            R.string.day_period_afternoon,
            R.string.day_period_evening,
            R.string.day_period_night,
        ).forEach { label ->
            composeRule.onNodeWithText(context.getString(label)).assertExists()
        }
        composeRule
            .onAllNodesWithText(context.getString(R.string.day_period_no_data))
            .assertCountEquals(0)
        // Four aggregated period blocks, each with its own probability, amount, wind and gusts.
        // "Max. Regenw." only exists inside the sheet, so the count is unambiguous.
        composeRule
            .onAllNodesWithText(context.getString(R.string.day_period_precipitation_probability))
            .assertCountEquals(4)
    }

    @Test
    fun systemBackClosesTheSheetAndLeavesTheDashboardUsable() {
        renderRoot()
        val today = today()

        openDailySheet(today.plusDays(1))
        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertIsDisplayed()

        // The sheet is a window of its own, so the system back key is injected the way the
        // framework delivers it; the dashboard below never sees that key.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.section_daily)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_settings))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.settings_screen_title)).assertIsDisplayed()
    }
}
