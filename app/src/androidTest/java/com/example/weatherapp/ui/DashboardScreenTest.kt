package com.example.weatherapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
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
import com.example.weatherapp.domain.buildForecastSections
import com.example.weatherapp.domain.model.CurrentWeather
import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WeatherSnapshot
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

    private fun hour(date: LocalDate, hourOfDay: Int): HourlyWeather = HourlyWeather(
        instant = date.atTime(hourOfDay, 0).atZone(zone).toInstant(),
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

    /**
     * The instant the hourly window is built for: the same instant the current conditions were
     * observed at, which is 12:00 local in Berlin.
     */
    private val reference: Instant = fetchedAt

    /** Hourly data for today and tomorrow plus a full 16 day outlook. */
    private fun snapshot(): WeatherSnapshot = WeatherSnapshot(
        locationId = hamburg.id,
        fetchedAt = fetchedAt,
        timeZone = zone,
        current = null,
        hourly = (0..23).map { hour(today, it) } + (0..23).map { hour(today.plusDays(1), it) },
        daily = (0..15).map { day(it) },
    )

    private fun sections(): ForecastSections = buildForecastSections(snapshot(), reference)

    private fun stateWith(
        failure: DataLoadFailure? = null,
        withData: Boolean = true,
        observationInstant: Instant? = fetchedAt,
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
                    instant = observationInstant,
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
    fun hourlyStripStartsAtTheCurrentHourAndLabelsItsDay() {
        render(stateWith())

        // The window begins at the current hour, so the first chip shows 12:00 with its day label.
        composeRule.onNodeWithText(WeatherFormatter.clockTime(today.atTime(12, 0))).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.hourly_day_today)).assertIsDisplayed()
    }

    @Test
    fun tappingAnHourOpensTheDetailSheet() {
        render(stateWith())

        composeRule.onNodeWithText(WeatherFormatter.clockTime(today.atTime(12, 0))).performClick()

        composeRule.onNodeWithText(context.getString(R.string.hourly_details_title)).assertIsDisplayed()
        // "Windrichtung" only exists in the sheet, while labels like "Luftdruck" are also on the card.
        composeRule.onNodeWithText(context.getString(R.string.detail_wind_direction)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.detail_temperature)).assertIsDisplayed()
        composeRule.onNodeWithText(
            WeatherFormatter.weekdayAndDate(today) + " · " + WeatherFormatter.clockTime(today.atTime(12, 0)),
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun aCurrentWeatherWithoutTimestampIsLabelledAndNotSilentlyTimed() {
        render(stateWith(observationInstant = null))

        composeRule.onNodeWithText(context.getString(R.string.observation_time_unknown)).assertIsDisplayed()
        // No timestamp may be implied by the download time on the card.
        composeRule.onNodeWithText(WeatherFormatter.fullDateTime(fetchedAt, zone)).assertDoesNotExist()
        // The rest of the dashboard keeps working.
        composeRule.onNodeWithText(WeatherFormatter.clockTime(today.atTime(12, 0))).assertIsDisplayed()
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
    fun toolbarOpensTheFullScreenLocationManager() {
        render(stateWith())

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_locations)).performClick()

        // The manager fills the screen: its own title, the saved places, and a fixed search field.
        composeRule.onNodeWithText(context.getString(R.string.locations_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.locations_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.locations_home)).assertExists()
        composeRule.onNodeWithText(berlin.name).assertExists()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back_to_weather))
            .assertIsDisplayed()
    }

    /**
     * Cached data is presented exactly like live data: the dashboard shows the snapshot and its
     * update line, and the status strip that used to label the source stays away. The test keeps
     * asserting the label is absent so the removed card cannot creep back in.
     */
    @Test
    fun cachedDataIsShownWithoutAStatusStrip() {
        val base = stateWith()
        val cached = base.copy(
            weather = base.weather.copy(
                snapshotSource = com.example.weatherapp.data.repository.SnapshotSource.CACHED,
            ),
        )
        render(cached)

        composeRule.onNodeWithText(context.getString(R.string.section_hourly)).assertIsDisplayed()
        val updateLine = context.getString(R.string.last_updated).substringBefore(":")
        composeRule.onNodeWithText(updateLine, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.status_cached)).assertDoesNotExist()
    }
}
