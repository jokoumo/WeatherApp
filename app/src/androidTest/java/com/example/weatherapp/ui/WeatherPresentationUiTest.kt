package com.example.weatherapp.ui

import android.content.res.Configuration
import android.content.res.XmlResourceParser
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.test.performClick
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
import com.example.weatherapp.domain.model.PrecipitationFamily
import com.example.weatherapp.domain.model.PrecipitationIntensity
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.domain.model.WindWarningLevel
import com.example.weatherapp.ui.components.WeatherIcon
import com.example.weatherapp.ui.dashboard.CurrentWeatherCard
import com.example.weatherapp.ui.dashboard.ExtendedDayList
import com.example.weatherapp.ui.dashboard.HourlyForecast
import com.example.weatherapp.ui.theme.BackgroundDark
import com.example.weatherapp.ui.theme.BackgroundLight
import com.example.weatherapp.ui.theme.SurfaceDark
import com.example.weatherapp.ui.theme.SurfaceLight
import com.example.weatherapp.ui.theme.WeatherAppTheme
import com.example.weatherapp.ui.weather.weatherIconRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow

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
                    isFavorite = true,
                    isHomeLocation = false,
                    onToggleFavorite = {},
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

    /**
     * The star of the hero card speaks the role of the place it belongs to: an outlined star that
     * offers saving, a filled star that offers removing, and a separate label for the home location,
     * which is always saved.
     */
    @Test
    fun theFavoriteStarDescribesTheRoleOfTheDisplayedLocation() {
        var isFavorite by mutableStateOf(false)
        var isHome by mutableStateOf(false)
        var taps = 0
        composeRule.setContent {
            WeatherAppTheme {
                CurrentWeatherCard(
                    location = hamburg,
                    current = current(),
                    todayMin = null,
                    todayMax = null,
                    dataInstant = null,
                    zoneId = zone,
                    isFavorite = isFavorite,
                    isHomeLocation = isHome,
                    onToggleFavorite = { taps++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_add_favorite))
            .assertIsDisplayed()

        isFavorite = true
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_remove_favorite))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_add_favorite))
            .assertDoesNotExist()

        isHome = true
        composeRule.waitForIdle()
        val homeLabel = context.getString(R.string.content_description_star_home)
        composeRule.onNodeWithContentDescription(homeLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_remove_favorite))
            .assertDoesNotExist()

        composeRule.onNodeWithContentDescription(homeLabel).performClick()
        assertEquals("the star is a button of its own", 1, taps)
    }

    /** The star belongs to the place, so it shares the line of the location name. */
    @Test
    fun theFavoriteStarSharesTheLineOfTheLocationName() {
        composeRule.setContent {
            WeatherAppTheme {
                CurrentWeatherCard(
                    location = hamburg,
                    current = current(),
                    todayMin = null,
                    todayMax = null,
                    dataInstant = null,
                    zoneId = zone,
                    isFavorite = false,
                    isHomeLocation = false,
                    onToggleFavorite = {},
                )
            }
        }

        val name = composeRule.onNodeWithText(hamburg.name).fetchSemanticsNode().boundsInWindow
        val star = composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_add_favorite))
            .fetchSemanticsNode()
            .boundsInWindow

        assertTrue("the star is not to the right of the name", star.left >= name.right - 1f)
        val nameCentre = (name.top + name.bottom) / 2f
        val starCentre = (star.top + star.bottom) / 2f
        assertTrue(
            "the star is on another line: name $nameCentre, star $starCentre",
            kotlin.math.abs(nameCentre - starCentre) < 20f,
        )
        val minTouchTarget = 48f * context.resources.displayMetrics.density
        assertTrue(
            "the star touch target ${star.width}x${star.height} is smaller than 48 dp",
            star.width >= minTouchTarget && star.height >= minTouchTarget,
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
                        isFavorite = false,
                        isHomeLocation = false,
                        onToggleFavorite = {},
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
     * one, two or three flakes, all drawn with one shared geometry.
     */
    @Test
    fun rainAndSnowSymbolsDrawOneTwoOrThreeIdenticalPrecipitationMarks() {
        rainIcons.forEachIndexed { index, id ->
            assertEquals(index + 1, marksOf(id).size)
        }
        snowIcons.forEachIndexed { index, id ->
            assertEquals(index + 1, marksOf(id).size)
        }
        // Only the position changes: every rain icon reuses one drop, every snow icon one flake.
        val drops = rainIcons.flatMap { marksOf(it) }
        val flakes = snowIcons.flatMap { marksOf(it) }
        assertEquals("all rain marks must share one drop geometry", 1, drops.map { shapeSignature(it.pathData) }.toSet().size)
        assertEquals("all snow marks must share one flake geometry", 1, flakes.map { shapeSignature(it.pathData) }.toSet().size)
        assertTrue("drops are filled shapes", drops.all { it.strokeColor == null })
        assertTrue("flakes are stroked lines", flakes.all { it.strokeColor != null })
    }

    /**
     * Consistency of the family: one viewport, and one cloud silhouette that every cloudy icon
     * draws more than once (directly, as the offset shadow and, when the sky is overcast, as the
     * secondary cloud behind). A redesign that moves the cloud in a single icon fails here.
     */
    @Test
    fun everyCloudyIconReusesTheSameViewportAndTheSameCloudSilhouette() {
        val silhouettes = cloudyIcons.map { id ->
            val counts = vectorOf(id).shapes.groupingBy { it.pathData }.eachCount()
            val silhouette = counts.maxByOrNull { it.value }
            assertTrue(
                "the cloud of $id has to be drawn as a silhouette and as a shadow",
                silhouette != null && silhouette.value >= 2,
            )
            checkNotNull(silhouette).key
        }.toSet()
        assertEquals("the cloud must be one and the same silhouette everywhere", 1, silhouettes.size)
        allWeatherIcons.forEach { id ->
            val vector = vectorOf(id)
            assertTrue("icon $id must use the shared 48x48 viewport", vector.width == 48f)
            assertTrue("icon $id must use the shared 48x48 viewport", vector.height == 48f)
        }
    }

    /**
     * No weather state may end up without a symbol. Every condition combined with every intensity
     * the presentation can produce has to resolve to a resource that really loads.
     */
    @Test
    fun everyWeatherStateResolvesToAnIconThatLoads() {
        val styles = PrecipitationFamily.entries.flatMap { family ->
            (listOf<PrecipitationIntensity?>(null) + PrecipitationIntensity.entries)
                .map { intensity -> PrecipitationStyle(family, intensity) }
        }
        WeatherCondition.entries.forEach { condition ->
            val resolved = buildList {
                add(PrecipitationClassifier.classify(condition))
                addAll(styles)
            }
            resolved.forEach { style ->
                val res = weatherIconRes(condition, style)
                assertTrue("no icon for $condition with $style", res != 0)
                assertTrue("icon $res of $condition cannot be loaded", context.getDrawable(res) != null)
            }
        }
        assertTrue(
            "the wind glyph must load on its own",
            context.getDrawable(R.drawable.weather_wind) != null,
        )
    }

    /**
     * The palette belongs to the design system: each colour token of the icon family has to stay
     * visible on the surfaces the application actually paints, in the light and in the dark theme,
     * without the geometry changing.
     */
    @Test
    fun everyIconColourStaysVisibleOnTheShippedLightAndDarkSurfaces() {
        listOf(false, true).forEach { night ->
            val configuration = Configuration(context.resources.configuration)
            configuration.uiMode = if (night) {
                Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
            } else {
                Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
            }
            val themed = context.createConfigurationContext(configuration)
            val surfaces = if (night) {
                listOf(SurfaceDark, BackgroundDark)
            } else {
                listOf(SurfaceLight, BackgroundLight)
            }
            iconColourTokens.forEach { token ->
                val colour = Color(themed.getColor(token))
                val name = themed.resources.getResourceName(token)
                surfaces.forEach { surface ->
                    val ratio = contrastRatio(colour, surface)
                    assertTrue(
                        "$name needs 1.5:1 on $surface, was ${ratio.format()}",
                        ratio >= 1.5,
                    )
                }
            }
        }
    }

    private data class VectorShape(
        val pathData: String,
        val fillColor: String?,
        val strokeColor: String?,
    )

    private data class VectorFile(val width: Float, val height: Float, val shapes: List<VectorShape>)

    private fun vectorOf(id: Int): VectorFile {
        val parser = context.resources.getXml(id)
        val shapes = mutableListOf<VectorShape>()
        var width = 0f
        var height = 0f
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "vector" -> {
                        width = attributeOf(parser, "viewportWidth")?.toFloatOrNull() ?: 0f
                        height = attributeOf(parser, "viewportHeight")?.toFloatOrNull() ?: 0f
                    }
                    "path" -> shapes += VectorShape(
                        pathData = attributeOf(parser, "pathData").orEmpty(),
                        fillColor = attributeOf(parser, "fillColor"),
                        strokeColor = attributeOf(parser, "strokeColor"),
                    )
                }
            }
            event = parser.next()
        }
        check(shapes.isNotEmpty()) { "icon $id does not contain a single path" }
        return VectorFile(width, height, shapes)
    }

    /**
     * Reads an attribute by its plain name. The resources of an installed build are compiled, so
     * the drawable attributes carry the android namespace and cannot be looked up without it.
     */
    private fun attributeOf(parser: XmlResourceParser, name: String): String? {
        for (index in 0 until parser.attributeCount) {
            if (parser.getAttributeName(index) == name) return parser.getAttributeValue(index)
        }
        return null
    }

    /**
     * The precipitation marks of an icon: every path that is not the cloud silhouette, which is
     * the only path an icon draws twice (directly and as the offset shadow).
     */
    private fun marksOf(id: Int): List<VectorShape> {
        val shapes = vectorOf(id).shapes
        val counts = shapes.groupingBy { it.pathData }.eachCount()
        return shapes.filter { counts.getValue(it.pathData) == 1 }
    }

    /**
     * Translation invariant signature of a path: its command letters and the number of numeric
     * arguments. Two drops side by side share it, a differently shaped mark does not.
     */
    private fun shapeSignature(pathData: String): String =
        pathData.filter(Char::isLetter) + "#" + NUMBER_PATTERN.findAll(pathData).count()

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val component = value.toDouble()
            return if (component <= 0.03928) component / 12.92 else ((component + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun Double.format(): String = String.format(java.util.Locale.ROOT, "%.2f", this)

    /** Rounds a pixel bound so that sub pixel differences do not fail the comparison. */
    private fun quantise(value: Float): Long = (value * 10).toLong()

    private companion object {
        val NUMBER_PATTERN = Regex("[-+]?[0-9]*\\.?[0-9]+")

        val rainIcons = listOf(
            R.drawable.weather_rain_light,
            R.drawable.weather_rain_moderate,
            R.drawable.weather_rain_heavy,
        )
        val snowIcons = listOf(
            R.drawable.weather_snow_light,
            R.drawable.weather_snow_moderate,
            R.drawable.weather_snow_heavy,
        )
        val cloudyIcons = listOf(
            R.drawable.weather_cloudy,
            R.drawable.weather_overcast,
            R.drawable.weather_partly_cloudy_day,
            R.drawable.weather_partly_cloudy_night,
            R.drawable.weather_drizzle,
            R.drawable.weather_fog,
            R.drawable.weather_sleet,
            R.drawable.weather_snow_grains,
            R.drawable.weather_thunderstorm,
            R.drawable.weather_hail,
        ) + rainIcons + snowIcons

        val iconColourTokens = listOf(
            R.color.weather_sun,
            R.color.weather_sun_accent,
            R.color.weather_cloud,
            R.color.weather_cloud_shadow,
            R.color.weather_cloud_secondary,
            R.color.weather_rain,
            R.color.weather_snow,
            R.color.weather_hail,
            R.color.weather_moon,
            R.color.weather_lightning,
            R.color.weather_fog,
        )

        val allWeatherIcons = listOf(
            R.drawable.weather_clear_day,
            R.drawable.weather_clear_night,
            R.drawable.weather_partly_cloudy_day,
            R.drawable.weather_partly_cloudy_night,
            R.drawable.weather_cloudy,
            R.drawable.weather_overcast,
            R.drawable.weather_drizzle,
            R.drawable.weather_fog,
            R.drawable.weather_sleet,
            R.drawable.weather_snow_grains,
            R.drawable.weather_thunderstorm,
            R.drawable.weather_hail,
            R.drawable.weather_wind,
        ) + rainIcons + snowIcons
    }
}
