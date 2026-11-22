package com.example.weatherapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.ForecastSections
import com.example.weatherapp.domain.model.CurrentWeather
import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.ui.dashboard.DashboardScreen
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Interaction tests for the dashboard: rendered content, the hourly detail sheet, the error
 * state with its retry action and the saved locations sheet.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val today: LocalDate = LocalDate.of(2026, 11, 22)
    private val fetchedAt: Instant = Instant.parse("2026-11-22T11:00:00Z")

    private val hamburg = SavedLocation(
        id = "2911298",
        name = "Hamburg",
        latitude = 53.55,
        longitude = 9.99,
        admin1 = "Freie und Hansestadt Hamburg",
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    private val berlin = SavedLocation(
        id = "2950280",
        name = "Berlin",
        latitude = 52.52,
        longitude = 13.4,
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    private fun hour(hourOfDay: Int): HourlyWeather = HourlyWeather(
        instant = today.atTime(hourOfDay, 0).atZone(zone).toInstant(),
        timeZone = zone,
        temperatureCelsius = 4.0 + hourOfDay * 0.1,
        apparentTemperatureCelsius = 2.0 + hourOfDay * 0.1,
        weatherCode = 61,
        condition = WeatherCondition.LIGHT_RAIN,
        precipitationProbabilityPercent = 40,
        precipitationMillimetres = 0.3,
        relativeHumidityPercent = 80,
        cloudCoverPercent = 90,
        windSpeedKilometresPerHour = 12.0,
        windDirectionDegrees = 270.0,
        windGustsKilometresPerHour = 25.0,
        pressureHectopascal = 1012.0,
        isDay = hourOfDay in 8..16,
    )

    private fun day(daysAfterToday: Int): DailyWeather = DailyWeather(
        date = today.plusDays(daysAfterToday.toLong()),
        weatherCode = 3,
        condition = WeatherCondition.OVERCAST,
        minTemperatureCelsius = -1.0,
        maxTemperatureCelsius = 6.0,
        precipitationProbabilityPercent = 30,
        precipitationMillimetres = 1.2,
        maxWindSpeedKilometresPerHour = 18.0,
        maxWindGustsKilometresPerHour = 35.0,
        sunshineSeconds = 7_200,
        daylightSeconds = 29_400,
    )

    private fun sections(): ForecastSections = ForecastSections(
        todayHours = (6..21).map { hour(it) },
        detailedDays = listOf(day(1), day(2)),
        extendedDays = (3..15).map { day(it) },
    )

    private fun stateWith(
        failure: DataLoadFailure? = null,
        withData: Boolean = true,
    ): MainUiState = MainUiState(
        settingsLoaded = true,
        settings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            selectedLocationId = hamburg.id,
        ),
        weather = WeatherUiState(
            location = hamburg,
            current = if (withData) {
                CurrentWeather(
                    instant = fetchedAt,
                    temperatureCelsius = 6.4,
                    apparentTemperatureCelsius = 3.9,
                    weatherCode = 3,
                    condition = WeatherCondition.OVERCAST,
                    relativeHumidityPercent = 78,
                    precipitationMillimetres = 0.0,
                    cloudCoverPercent = 100,
                    windSpeedKilometresPerHour = 11.2,
                    windDirectionDegrees = 300.0,
                    windGustsKilometresPerHour = 25.4,
                    pressureHectopascal = 1013.0,
                    isDay = true,
                )
            } else {
                null
            },
            sections = if (withData) sections() else ForecastSections.EMPTY,
            // Anchor 0 keeps the strip unscrolled, so the first chips are laid out and clickable.
            hourlyAnchorIndex = 0,
            snapshotSource = if (failure == null) {
                com.example.weatherapp.data.repository.SnapshotSource.LIVE
            } else {
                null
            },
            fetchedAt = fetchedAt,
            timeZone = zone,
            today = today,
            hasLoadedOnce = true,
            failure = failure,
        ),
    )

    private fun render(
        state: MainUiState,
        onRefresh: () -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherAppTheme {
                DashboardScreen(
                    state = state,
                    onRefresh = onRefresh,
                    onSelectLocation = {},
                    onSetHomeLocation = {},
                    onAddFavorite = {},
                    onRemoveLocation = {},
                    onMoveFavorite = { _, _ -> },
                    onSearchQueryChange = {},
                    onMessageShown = {},
                )
            }
        }
    }

    @Test
    fun dashboardShowsCurrentConditionsAndAllForecastSections() {
        render(stateWith())

        composeRule.onAllNodesWithText(hamburg.name).assertCountEquals(2)
        composeRule.onNodeWithText(WeatherFormatter.fullDateTime(fetchedAt, zone)).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.feels_like_value, WeatherFormatter.temperature(3.9)),
        ).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.section_hourly)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.section_daily)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.tomorrow)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.section_extended)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.extended_hint)).assertExists()
    }

    @Test
    fun tappingAnHourOpensTheDetailSheet() {
        render(stateWith())

        composeRule.onNodeWithText(WeatherFormatter.clockTime(today.atTime(6, 0))).performClick()

        composeRule.onNodeWithText(context.getString(R.string.hourly_details_title)).assertIsDisplayed()
        // "Windrichtung" only exists in the sheet, while labels like "Luftdruck" are also on the card.
        composeRule.onNodeWithText(context.getString(R.string.detail_wind_direction)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.detail_temperature)).assertIsDisplayed()
    }

    @Test
    fun failedLoadShowsGermanErrorMessageAndRetryIsWired() {
        var refreshed = false
        render(stateWith(failure = DataLoadFailure.NETWORK, withData = false)) { refreshed = true }

        composeRule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        assertTrue(refreshed)
    }

    @Test
    fun toolbarOpensTheSavedLocationsSheet() {
        render(stateWith())

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_locations)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.locations_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.locations_home)).assertExists()
        composeRule.onNodeWithText(berlin.name).assertExists()
    }

    @Test
    fun cachedDataIsLabelledAsSavedData() {
        val base = stateWith()
        val cached = base.copy(
            weather = base.weather.copy(
                snapshotSource = com.example.weatherapp.data.repository.SnapshotSource.CACHED,
            ),
        )
        render(cached)

        composeRule.onNodeWithText(context.getString(R.string.status_cached)).assertExists()
    }
}
