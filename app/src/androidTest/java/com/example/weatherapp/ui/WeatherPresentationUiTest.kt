package com.example.weatherapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.domain.HourlyDayRelation
import com.example.weatherapp.domain.HourlyWindowEntry
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.CurrentWeather
import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.PrecipitationClassifier
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.domain.model.WindWarningLevel
import com.example.weatherapp.ui.components.WeatherIcon
import com.example.weatherapp.ui.dashboard.CurrentWeatherCard
import com.example.weatherapp.ui.dashboard.ExtendedDayList
import com.example.weatherapp.ui.dashboard.HourlyForecast
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Layout tests for the parts of the dashboard that were refined: the detail labels of the hero
 * card, the uniform hourly chips, the compact outlook rows and the wind indicator.
 *
 * Heights are compared against each other instead of against fixed pixel values, so the tests
 * stay meaningful when the density, the font scale or the exact spacing change.
 */
@RunWith(AndroidJUnit4::class)
class WeatherPresentationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val today: LocalDate = LocalDate.of(2026, 11, 22)

    private val hamburg = SavedLocation(
        id = "2911298",
        name = "Hamburg",
        latitude = 53.55,
        longitude = 9.99,
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    private fun current(
        windSpeed: Double? = 12.0,
        windGusts: Double? = 25.0,
    ): CurrentWeather = CurrentWeather(
        instant = Instant.parse("2026-11-22T11:00:00Z"),
        temperatureCelsius = 4.0,
        apparentTemperatureCelsius = 1.0,
        weatherCode = 61,
        condition = WeatherCondition.LIGHT_RAIN,
        relativeHumidityPercent = 97,
        precipitationMillimetres = 0.4,
        cloudCoverPercent = 90,
        windSpeedKilometresPerHour = windSpeed,
        windDirectionDegrees = 270.0,
        windGustsKilometresPerHour = windGusts,
        pressureHectopascal = 1012.0,
        isDay = true,
    )

    /**
     * Three consecutive hours that cross midnight: the first carries "Heute", the third "Morgen",
     * the middle one no day label. All three have to look identical apart from that label.
     */
    private fun midnightHours(
        probabilityAt: Int? = null,
        stormyIndex: Int? = null,
    ): List<HourlyWeather> = (0 until 3).map { index ->
        val instant = Instant.parse("2026-11-22T22:00:00Z").plusSeconds(index * 3600L)
        HourlyWeather(
            instant = instant,
            timeZone = zone,
            temperatureCelsius = 4.0 + index * 0.1,
            apparentTemperatureCelsius = 2.0,
            weatherCode = 61,
            condition = WeatherCondition.LIGHT_RAIN,
            precipitationProbabilityPercent = if (index == probabilityAt) 100 else 40,
            precipitationMillimetres = 0.3,
            relativeHumidityPercent = 80,
            cloudCoverPercent = 90,
            windSpeedKilometresPerHour = if (index == stormyIndex) 90.0 else 12.0,
            windDirectionDegrees = 270.0,
            windGustsKilometresPerHour = 25.0,
            pressureHectopascal = 1012.0,
            isDay = false,
        )
    }

    /** Mirrors what the dashboard builds: the first hour and the first hour of a new day are labelled. */
    private fun windowEntries(hours: List<HourlyWeather>): List<HourlyWindowEntry> {
        val firstDate = hours.first().localDate
        return hours.mapIndexed { index, hourly ->
            HourlyWindowEntry(
                hour = hourly,
                isCurrent = index == 0,
                startsNewDay = index == 0 || hourly.localDate != hours[index - 1].localDate,
                dayRelation = when (hourly.localDate) {
                    firstDate -> HourlyDayRelation.TODAY
                    firstDate.plusDays(1) -> HourlyDayRelation.TOMORROW
                    else -> HourlyDayRelation.LATER
                },
                ambiguousClockTime = false,
            )
        }
    }

    private fun day(daysAfterToday: Int): DailyWeather = DailyWeather(
        date = today.plusDays(daysAfterToday.toLong()),
        weatherCode = 3,
        condition = WeatherCondition.OVERCAST,
        minTemperatureCelsius = -1.0,
        maxTemperatureCelsius = 6.0,
        precipitationProbabilityPercent = 30,
        precipitationMillimetres = 1.2,
        maxWindSpeedKilometresPerHour = 45.0,
        maxWindGustsKilometresPerHour = 60.0,
        sunshineSeconds = 3600,
        daylightSeconds = 9000,
    )

    @Test
    fun everyDetailLabelOfTheHeroCardIsVisibleAndInsideTheCard() {
        composeRule.setContent {
            WeatherAppTheme {
                CurrentWeatherCard(
                    location = hamburg,
                    current = current(),
                    todayMin = 1.0,
                    todayMax = 7.0,
                    dataInstant = Instant.parse("2026-11-22T11:00:00Z"),
                    zoneId = zone,
                    modifier = Modifier.testTag("hero"),
                )
            }
        }
        val cardBounds = composeRule.onNodeWithTag("hero").fetchSemanticsNode().boundsInWindow
        val labels = listOf(
            R.string.detail_humidity,
            R.string.detail_precipitation,
            R.string.detail_wind,
            R.string.detail_wind_gusts,
            R.string.detail_cloud_cover,
            R.string.detail_pressure,
        ).map { context.getString(it) }

        labels.forEach { label ->
            val node = composeRule.onNodeWithText(label)
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInWindow
            assertTrue(
                "$label must stay inside the card horizontally",
                bounds.left >= cardBounds.left && bounds.right <= cardBounds.right,
            )
        }

        // The first column must not run into the second one.
        val humidity = composeRule.onNodeWithText(context.getString(R.string.detail_humidity))
            .fetchSemanticsNode().boundsInWindow
        val precipitation = composeRule.onNodeWithText(context.getString(R.string.detail_precipitation))
            .fetchSemanticsNode().boundsInWindow
        assertTrue(
            "the humidity label overlaps the precipitation column",
            humidity.right <= precipitation.left,
        )
    }

    @Test
    fun hourlyChipsKeepTheSameHeightAndIconPositionWithAndWithoutDayLabels() {
        composeRule.setContent {
            WeatherAppTheme {
                HourlyForecast(entries = windowEntries(midnightHours()), onItemClick = {})
            }
        }
        val chips = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(3, chips.size)

        val heights = chips.map { it.boundsInWindow.height }
        val tops = chips.map { it.boundsInWindow.top }
        assertEquals("chips differ in height: $heights", 1, heights.distinctBy(::quantise).size)
        assertEquals("chips differ in their top edge: $tops", 1, tops.distinctBy(::quantise).size)

        // Both day labels are really shown, so the reserved line is used and not just empty.
        composeRule.onNodeWithText(context.getString(R.string.hourly_day_today)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tomorrow)).assertIsDisplayed()

        // Every weather symbol sits at the same height inside its chip.
        val icons = composeRule.onAllNodesWithContentDescription(
            context.getString(R.string.content_description_weather_icon),
        ).fetchSemanticsNodes()
        assertEquals(3, icons.size)
        val iconTops = icons.map { it.boundsInWindow.top }
        assertEquals("icons differ in their top edge: $iconTops", 1, iconTops.distinctBy(::quantise).size)
    }

    @Test
    fun aFullPrecipitationProbabilityIsShownAndFitsItsChip() {
        composeRule.setContent {
            WeatherAppTheme {
                HourlyForecast(entries = windowEntries(midnightHours(probabilityAt = 1)), onItemClick = {})
            }
        }
        val full = WeatherFormatter.percent(100)
        composeRule.onAllNodesWithText(full).assertCountEquals(1)
        val percentBounds = composeRule.onAllNodesWithText(full).onFirst()
            .fetchSemanticsNode().boundsInWindow
        val chipBounds = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()[1].boundsInWindow
        assertTrue(
            "'$full' does not fit inside its chip: text $percentBounds, chip $chipBounds",
            percentBounds.left >= chipBounds.left && percentBounds.right <= chipBounds.right,
        )
        composeRule.onAllNodesWithText(WeatherFormatter.percent(40)).assertCountEquals(2)
    }

    @Test
    fun compactForecastRowsShowNumbersAndIconsButNoWrittenConditions() {
        val days = listOf(day(4), day(5))
        composeRule.setContent {
            WeatherAppTheme {
                ExtendedDayList(days = days)
            }
        }
        // Probability and amount share one line per row, in the same formatting the app uses.
        val precipitation = WeatherFormatter.percent(30) + " · " + WeatherFormatter.millimetres(1.2)
        composeRule.onAllNodesWithText(precipitation).assertCountEquals(days.size)
        composeRule.onAllNodesWithText(precipitation).onFirst().assertIsDisplayed()

        // Both temperatures stay as numbers, and they are bigger than a label.
        composeRule.onAllNodesWithText(WeatherFormatter.temperatureValue(-1.0) + "°")
            .assertCountEquals(days.size)
        composeRule.onAllNodesWithText(WeatherFormatter.temperatureValue(6.0) + "°")
            .assertCountEquals(days.size)
        composeRule.onNodeWithText(WeatherFormatter.weekday(days.first().date)).assertIsDisplayed()
        composeRule.onNodeWithText(WeatherFormatter.shortDate(days.first().date)).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(
            context.getString(R.string.content_description_weather_icon),
        ).assertCountEquals(days.size)

        // The written condition label and the temperature range bar are gone from these rows.
        composeRule.onNodeWithText(context.getString(R.string.weather_overcast)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.weather_rain_light)).assertDoesNotExist()
    }

    @Test
    fun compactForecastRowsCarryTheWindWarningOfTheDay() {
        composeRule.setContent {
            WeatherAppTheme {
                ExtendedDayList(days = listOf(day(4)))
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.wind_warning_strong))
            .assertIsDisplayed()
    }

    @Test
    fun windIndicatorIsOnlyShownAboveTheThresholds() {
        var weather by mutableStateOf(current(windSpeed = 12.0, windGusts = 25.0))
        composeRule.setContent {
            WeatherAppTheme {
                Box {
                    CurrentWeatherCard(
                        location = hamburg,
                        current = weather,
                        todayMin = null,
                        todayMax = null,
                        dataInstant = null,
                        zoneId = zone,
                    )
                }
            }
        }
        val strong = context.getString(R.string.wind_warning_strong)
        val gusts = context.getString(R.string.wind_warning_gusts)
        val storm = context.getString(R.string.wind_warning_storm)

        // Ordinary wind shows nothing at all.
        composeRule.onAllNodesWithContentDescription(strong).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(storm).assertCountEquals(0)

        weather = current(windSpeed = 39.0, windGusts = 25.0)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(strong).assertIsDisplayed()

        weather = current(windSpeed = 20.0, windGusts = 55.0)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(gusts).assertIsDisplayed()

        weather = current(windSpeed = 80.0, windGusts = 90.0)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(storm).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(strong).assertCountEquals(0)
    }

    @Test
    fun hourlyChipsShowTheWindWarningOnlyForHoursThatExceedTheThreshold() {
        composeRule.setContent {
            WeatherAppTheme {
                HourlyForecast(
                    entries = windowEntries(midnightHours(stormyIndex = 1)),
                    onItemClick = {},
                )
            }
        }
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.wind_warning_storm))
            .assertCountEquals(1)
    }

    @Test
    fun newSymbolsAndWindIndicatorsRenderInBothThemes() {
        val conditions = listOf(
            WeatherCondition.LIGHT_RAIN,
            WeatherCondition.RAIN,
            WeatherCondition.HEAVY_RAIN,
            WeatherCondition.LIGHT_SNOW,
            WeatherCondition.SNOW,
            WeatherCondition.HEAVY_SNOW,
            WeatherCondition.SNOW_GRAINS,
        )
        var darkTheme by mutableStateOf(false)
        composeRule.setContent {
            WeatherAppTheme(darkTheme = darkTheme) {
                Row {
                    conditions.forEach { condition ->
                        WeatherIcon(
                            condition = condition,
                            precipitation = PrecipitationClassifier.classify(condition),
                            windWarning = WindWarning(WindWarningLevel.STORM),
                            size = 40.dp,
                        )
                    }
                }
            }
        }
        listOf(false, true).forEach { dark ->
            darkTheme = dark
            composeRule.waitForIdle()
            composeRule.onAllNodesWithContentDescription(
                context.getString(R.string.content_description_weather_icon),
            ).assertCountEquals(conditions.size)
            composeRule.onAllNodesWithContentDescription(
                context.getString(R.string.wind_warning_storm),
            ).assertCountEquals(conditions.size)
        }
    }

    /**
     * The intensity of a symbol is the number of precipitation marks it draws. Reading the shipped
     * vector keeps this test honest about what the user actually sees: one, two or three drops and
     * one, two or three flakes, always on the same cloud.
     */
    @Test
    fun rainAndSnowSymbolsDrawOneTwoOrThreePrecipitationMarks() {
        listOf(
            R.drawable.weather_rain_light,
            R.drawable.weather_rain_moderate,
            R.drawable.weather_rain_heavy,
        ).forEachIndexed { index, id ->
            assertEquals(index + 2, pathDataOf(id).size)
        }
        listOf(
            R.drawable.weather_snow_light,
            R.drawable.weather_snow_moderate,
            R.drawable.weather_snow_heavy,
        ).forEachIndexed { index, id ->
            assertEquals(index + 2, pathDataOf(id).size)
        }
        // The cloud behind them is the same shape, so only the marks differ.
        assertEquals(firstPath(R.drawable.weather_rain_light), firstPath(R.drawable.weather_rain_heavy))
        assertEquals(firstPath(R.drawable.weather_snow_light), firstPath(R.drawable.weather_snow_heavy))
    }

    private fun pathDataOf(id: Int): List<String> {
        val parser = context.resources.getXml(id)
        val paths = mutableListOf<String>()
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "path") {
                paths += parser.getAttributeValue(null, "pathData").orEmpty()
            }
            event = parser.next()
        }
        return paths
    }

    private fun firstPath(id: Int): String = pathDataOf(id).first()

    /** Rounds a pixel bound so that sub pixel differences do not fail the comparison. */
    private fun quantise(value: Float): Long = (value * 10).toLong()
}
