package com.example.weatherapp.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
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
import com.example.weatherapp.ui.components.SecondaryScreenHeader
import com.example.weatherapp.ui.dashboard.DashboardScreen
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
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
        loading: Boolean = false,
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
            isRefreshing = loading,
            failure = failure,
        ),
    )

    private fun render(
        state: MainUiState,
        onRefresh: () -> Unit = {},
        onOpenLocations: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onToggleFavorite: () -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherAppTheme {
                DashboardScreen(
                    state = state,
                    onRefresh = onRefresh,
                    onOpenLocations = onOpenLocations,
                    onOpenSettings = onOpenSettings,
                    onToggleFavorite = onToggleFavorite,
                    onMessageShown = {},
                )
            }
        }
    }

    @Test
    fun dashboardShowsCurrentConditionsAndAllForecastSections() {
        render(stateWith())

        // The location name appears exactly once now: inside the current weather card.
        composeRule.onAllNodesWithText(hamburg.name).assertCountEquals(1)
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

    /**
     * The star of the hero card always speaks for the place that is on screen: here the home
     * location, which is saved by definition and says so instead of offering to be removed.
     */
    @Test
    fun dashboardStarMarksTheHomeLocationAndReportsTaps() {
        var taps = 0
        render(stateWith(), onToggleFavorite = { taps++ })

        val label = context.getString(R.string.content_description_star_home)
        composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(label).performClick()
        composeRule.waitForIdle()

        assertEquals(1, taps)
    }

    /** A displayed favourite offers removing it. */
    @Test
    fun dashboardStarOffersRemovingADisplayedFavourite() {
        val asFavourite = stateWith().copy(
            settings = LocationSettings(
                homeLocation = berlin,
                favorites = listOf(hamburg),
                selectedLocationId = hamburg.id,
            ),
        )

        render(asFavourite)

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_remove_favorite))
            .assertIsDisplayed()
    }

    /** A place that was only looked at offers being saved, and nothing more. */
    @Test
    fun dashboardStarOffersSavingAPlaceThatWasOnlyViewed() {
        val asHistoryEntry = stateWith().copy(
            settings = LocationSettings(
                homeLocation = berlin,
                recentLocations = listOf(hamburg),
                selectedLocationId = hamburg.id,
            ),
        )

        render(asHistoryEntry)

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_add_favorite))
            .assertIsDisplayed()
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
        render(stateWith(failure = DataLoadFailure.NETWORK, withData = false), onRefresh = { refreshed = true })

        composeRule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        assertTrue(refreshed)
    }

    /**
     * While the weather of the selected place is still on its way, the card already identifies that
     * place - name and the star that belongs to it - and shows a loading label instead of any value.
     * Nothing is invented and nothing of a previous place survives.
     */
    @Test
    fun aLoadingLocationIsNamedAndShowsNoInventedValues() {
        render(stateWith(withData = false, loading = true))

        composeRule.onNodeWithText(hamburg.name).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.content_description_star_home))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.loading_weather)).assertIsDisplayed()

        // No temperature, no "feels like", no forecast section: there is nothing measured yet.
        composeRule.onNodeWithText(WeatherFormatter.temperature(6.4)).assertDoesNotExist()
        composeRule
            .onNodeWithText(context.getString(R.string.feels_like_value, WeatherFormatter.temperature(3.9)))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.section_hourly)).assertDoesNotExist()
    }

    /** A failed load keeps the place identified and offers the retry below its name. */
    @Test
    fun aFailedLoadKeepsTheLocationNamedAndOffersRetry() {
        var refreshed = false
        render(
            stateWith(failure = DataLoadFailure.NETWORK, withData = false),
            onRefresh = { refreshed = true },
        )

        composeRule.onNodeWithText(hamburg.name).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.content_description_star_home))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.error_network)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        assertTrue(refreshed)
    }

    /**
     * The toolbar carries the application name and the three actions; the selected location is
     * only shown inside the current weather card. Opening a screen from here is verified by
     * `WeatherRootNavigationTest`, which drives the real navigation.
     */
    @Test
    fun toolbarShowsApplicationNameAndItsThreeActions() {
        render(stateWith())

        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_refresh))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_locations))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_settings))
            .assertIsDisplayed()
        // No location management screen yet, and no location name in the toolbar.
        composeRule.onNodeWithText(context.getString(R.string.locations_screen_title))
            .assertDoesNotExist()
        composeRule.onAllNodesWithText(hamburg.name).assertCountEquals(1)
    }

    @Test
    fun toolbarActionsAreWiredToLocationsAndSettings() {
        var locationsOpened = 0
        var settingsOpened = 0
        render(
            state = stateWith(),
            onOpenLocations = { locationsOpened++ },
            onOpenSettings = { settingsOpened++ },
        )

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_locations)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_settings)).performClick()

        assertTrue("the location action did not fire", locationsOpened == 1)
        assertTrue("the settings action did not fire", settingsOpened == 1)
    }

    /** Every toolbar action keeps at least the 48 dp touch target, side by side on one line. */
    @Test
    fun toolbarActionsKeepReachableTouchTargetsOnOneLine() {
        render(stateWith())

        val labels = listOf(
            context.getString(R.string.action_refresh),
            context.getString(R.string.action_locations),
            context.getString(R.string.action_settings),
        )
        val density = context.resources.displayMetrics.density
        val minimumTouchTarget = 48f * density
        val nodes = labels.map { label ->
            composeRule.onNodeWithContentDescription(label).fetchSemanticsNode()
        }
        nodes.forEach { node ->
            val bounds = node.boundsInWindow
            assertTrue("touch target ${bounds.height} dp", bounds.height >= minimumTouchTarget)
            assertTrue("touch target ${bounds.width} dp", bounds.width >= minimumTouchTarget)
        }
        // Same row, no overlap: the icons sit next to each other instead of stacking.
        val tops = nodes.map { it.boundsInWindow.top }
        assertTrue("toolbar icons are not aligned on one row", tops.distinct().size == 1)
        val lefts = nodes.map { it.boundsInWindow.left }.sorted()
        assertTrue("toolbar icons overlap", lefts[0] < lefts[1] && lefts[1] < lefts[2])
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

    // region toolbar title

    /**
     * The application name in the toolbar is the most prominent heading of the app: it must never
     * render smaller than the title of a secondary screen. Both heights are measured in the same
     * composition, so the comparison also holds when the device uses a large system font.
     */
    @Test
    fun dashboardTitleIsAtLeastAsLargeAsASecondaryScreenTitle() {
        val secondaryTitle = context.getString(R.string.settings_screen_title)
        composeRule.setContent {
            WeatherAppTheme {
                Box {
                    DashboardScreen(
                        state = stateWith(),
                        onRefresh = {},
                        onOpenLocations = {},
                        onOpenSettings = {},
                        onToggleFavorite = {},
                        onMessageShown = {},
                    )
                    // The shared header of every secondary screen, measured with the same
                    // typography and the same font scale as the toolbar above.
                    SecondaryScreenHeader(title = secondaryTitle, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()

        val titleHeight = composeRule.onNodeWithText(context.getString(R.string.app_name))
            .fetchSemanticsNode()
            .boundsInWindow
            .height
        val secondaryHeight = composeRule.onNodeWithText(secondaryTitle)
            .fetchSemanticsNode()
            .boundsInWindow
            .height

        assertTrue(
            "toolbar title $titleHeight px is smaller than a secondary title $secondaryHeight px",
            titleHeight >= secondaryHeight,
        )
    }

    /** The larger title stays on its own side of the toolbar and never reaches the actions. */
    @Test
    fun toolbarTitleStaysClearOfTheActionButtons() {
        render(stateWith())

        val title = composeRule.onNodeWithText(context.getString(R.string.app_name))
            .fetchSemanticsNode()
            .boundsInWindow
        val refresh = composeRule.onNodeWithContentDescription(context.getString(R.string.action_refresh))
            .fetchSemanticsNode()
            .boundsInWindow

        assertTrue("the title overlaps the actions", title.right <= refresh.left)
        assertTrue("the title is cut off at the left edge", title.left >= 0f)
    }

    /**
     * A doubled system font scale is the worst case for the toolbar: the title may shrink to an
     * ellipsis, but it must stay on screen and the three actions must stay reachable next to it.
     */
    @Test
    fun toolbarStaysUsableWithADoubledFontScale() {
        val density = context.resources.displayMetrics.density
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = density, fontScale = 2.0f),
            ) {
                WeatherAppTheme {
                    DashboardScreen(
                        state = stateWith(),
                        onRefresh = {},
                        onOpenLocations = {},
                        onOpenSettings = {},
                        onToggleFavorite = {},
                        onMessageShown = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        val actions = listOf(
            context.getString(R.string.action_refresh),
            context.getString(R.string.action_locations),
            context.getString(R.string.action_settings),
        ).map { label -> composeRule.onNodeWithContentDescription(label) }
        val actionBounds = actions.map { node ->
            node.performClick()
            composeRule.waitForIdle()
            node.fetchSemanticsNode().boundsInWindow
        }
        val title = composeRule.onNodeWithText(context.getString(R.string.app_name))
            .fetchSemanticsNode()
            .boundsInWindow
        assertTrue(
            "the title reaches into the actions at a large font scale",
            title.right <= actionBounds.minOf { it.left },
        )
    }

    // endregion

    // region daily detail sheet

    private fun openDailySheet(daysAfterToday: Int) {
        val date = today.plusDays(daysAfterToday.toLong())
        composeRule.onNodeWithText(WeatherFormatter.weekdayAndDate(date))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
    }

    /** Both detailed day cards say what tapping them does. */
    @Test
    fun detailedDayCardsOfferTheDetailAction() {
        render(stateWith())

        composeRule
            .onAllNodesWithText(context.getString(R.string.day_card_details_hint))
            .assertCountEquals(2)
    }

    @Test
    fun tappingTomorrowOpensTheDailySheetWithItsDateAndFourPeriods() {
        render(stateWith())

        openDailySheet(1)

        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertIsDisplayed()
        composeRule.onNodeWithText(WeatherFormatter.fullWeekdayAndDate(today.plusDays(1)))
            .assertIsDisplayed()
        listOf(
            R.string.day_period_morning,
            R.string.day_period_afternoon,
            R.string.day_period_evening,
            R.string.day_period_night,
        ).forEach { label ->
            composeRule.onNodeWithText(context.getString(label)).assertExists()
        }
        // The night reaches into the next morning, which the printed range makes visible.
        composeRule.onNodeWithText("22–06 Uhr").assertExists()
        composeRule.onNodeWithText("06–12 Uhr").assertExists()
        // Four periods, each with its own set of aggregated values.
        composeRule
            .onAllNodesWithText(context.getString(R.string.day_period_precipitation_probability))
            .assertCountEquals(4)
    }

    @Test
    fun changingTheLocationClosesThePreviousLocationsForecastSheet() {
        val state = mutableStateOf(stateWith())
        composeRule.setContent {
            WeatherAppTheme {
                DashboardScreen(state.value, {}, {}, {}, {}, {})
            }
        }
        openDailySheet(1)
        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertIsDisplayed()
        composeRule.runOnIdle {
            state.value = state.value.copy(weather = state.value.weather.copy(location = berlin))
        }
        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertDoesNotExist()
    }

    @Test
    fun tappingOvermorgenOpensTheSheetForTheDayAfterTomorrow() {
        render(stateWith())

        openDailySheet(2)

        composeRule.onNodeWithText(WeatherFormatter.fullWeekdayAndDate(today.plusDays(2)))
            .assertIsDisplayed()
        composeRule.onNodeWithText(WeatherFormatter.fullWeekdayAndDate(today.plusDays(1)))
            .assertDoesNotExist()
    }

    /**
     * The test payload has hourly data for today and tomorrow only, so the day after tomorrow is
     * the honest "no data" case: every period says so instead of showing invented values.
     */
    @Test
    fun dailySheetHandlesMissingHourlyDataGracefully() {
        render(stateWith())

        openDailySheet(2)

        composeRule
            .onAllNodesWithText(context.getString(R.string.day_period_no_data))
            .assertCountEquals(4)
        composeRule.onNodeWithText(context.getString(R.string.day_period_morning)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.day_period_night)).assertExists()
    }

    @Test
    fun dailySheetClosesThroughItsCloseActionAndKeepsTheDashboard() {
        render(stateWith())

        openDailySheet(1)
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_close)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.section_hourly)).assertExists()
        composeRule.onNodeWithText(WeatherFormatter.clockTime(today.atTime(12, 0))).assertIsDisplayed()
    }

    @Test
    fun dailySheetClosesWithSystemBack() {
        render(stateWith())

        openDailySheet(1)
        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertIsDisplayed()

        // A modal bottom sheet lives in its own window, so the system back key is injected the way
        // the framework delivers it instead of being routed through the activity's dispatcher.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.section_hourly)).assertExists()
    }

    /** The hourly sheet and the daily sheet stay separate: one hour chip still opens the hour. */
    @Test
    fun hourlySheetStillWorksNextToTheDailySheet() {
        render(stateWith())

        composeRule.onNodeWithText(WeatherFormatter.clockTime(today.atTime(12, 0))).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.hourly_details_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.day_details_title)).assertDoesNotExist()
    }

    // endregion
}
