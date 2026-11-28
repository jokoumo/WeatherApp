package com.example.weatherapp.widget.presentation

import com.example.weatherapp.domain.model.CurrentWeather
import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.widget.WidgetData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which information tiles the current weather widget shows, in which order, and where a value comes
 * from.
 *
 * The two rules this file exists for are the ones a screenshot cannot prove: a metric the response did
 * not report is absent rather than replaced by a zero, and the precipitation probability is the value
 * of the **hour that contains the moment**, never the daily maximum of the same day.
 */
class CurrentWidgetContentTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    /** Thursday, 3 December 2026, 10:30 local time in Berlin. */
    private val now: Instant = Instant.parse("2026-12-03T09:30:00Z")

    /** Non-breaking space, as the formatter puts one between a number and its unit. */
    private val nb = "\u00A0"

    private fun current(
        temperature: Double = 7.0,
        apparent: Double? = 4.0,
        condition: WeatherCondition = WeatherCondition.LIGHT_RAIN,
        humidity: Int? = 80,
        precipitationMillimetres: Double? = 0.6,
        cloudCover: Int? = 90,
        windSpeed: Double? = 18.0,
        windDirection: Double? = 225.0,
        windGusts: Double? = 42.0,
        pressure: Double? = 1013.0,
    ): CurrentWeather = CurrentWeather(
        instant = now,
        temperatureCelsius = temperature,
        apparentTemperatureCelsius = apparent,
        weatherCode = null,
        condition = condition,
        relativeHumidityPercent = humidity,
        precipitationMillimetres = precipitationMillimetres,
        cloudCoverPercent = cloudCover,
        windSpeedKilometresPerHour = windSpeed,
        windDirectionDegrees = windDirection,
        windGustsKilometresPerHour = windGusts,
        pressureHectopascal = pressure,
        isDay = true,
    )

    private fun hour(
        localHour: Int,
        zone: ZoneId = berlin,
        date: LocalDate = LocalDate.of(2026, 12, 3),
        temperature: Double? = 6.0,
        probability: Int? = 30,
    ): HourlyWeather = HourlyWeather(
        instant = date.atTime(localHour % 24, 0)
            .plusDays(localHour / 24L)
            .atZone(zone)
            .toInstant(),
        timeZone = zone,
        temperatureCelsius = temperature,
        apparentTemperatureCelsius = null,
        weatherCode = null,
        condition = WeatherCondition.CLEAR_DAY,
        precipitationProbabilityPercent = probability,
        precipitationMillimetres = null,
        relativeHumidityPercent = null,
        cloudCoverPercent = null,
        windSpeedKilometresPerHour = null,
        windDirectionDegrees = null,
        windGustsKilometresPerHour = null,
        pressureHectopascal = null,
        isDay = true,
    )

    private fun daily(
        date: LocalDate,
        min: Double?,
        max: Double?,
        probability: Int? = 60,
        millimetres: Double? = 3.0,
    ): DailyWeather = DailyWeather(
        date = date,
        weatherCode = null,
        condition = WeatherCondition.OVERCAST,
        minTemperatureCelsius = min,
        maxTemperatureCelsius = max,
        precipitationProbabilityPercent = probability,
        precipitationMillimetres = millimetres,
        maxWindSpeedKilometresPerHour = 20.0,
        maxWindGustsKilometresPerHour = 40.0,
        sunshineSeconds = null,
        daylightSeconds = null,
    )

    private fun data(
        current: CurrentWeather? = current(),
        hourly: List<HourlyWeather> = (6..23).map { hour(it) },
        daily: List<DailyWeather> = listOf(
            daily(LocalDate.of(2026, 12, 3), 2.0, 9.0),
            daily(LocalDate.of(2026, 12, 4), 1.0, 7.0),
        ),
        zone: ZoneId = berlin,
        fetchedAt: Instant = now.minus(12, ChronoUnit.MINUTES),
    ): WidgetData = WidgetData(
        location = hamburg,
        snapshot = WeatherSnapshot(
            locationId = hamburg.id,
            fetchedAt = fetchedAt,
            timeZone = zone,
            current = current,
            hourly = hourly,
            daily = daily,
        ),
        now = now,
    )

    private fun metrics(content: CurrentWidgetContent): List<WidgetMetric> =
        content.tiles.map { it.metric }

    // region states

    @Test
    fun `a widget without a location says so instead of naming a place`() {
        val content = buildCurrentWidgetContent(null, WidgetSizeClass.EXPANDED)

        assertEquals(WidgetState.NO_LOCATION, content.state)
        assertEquals("", content.locationName)
        assertTrue(content.tiles.isEmpty())
    }

    @Test
    fun `a location whose snapshot is empty has no data`() {
        val noSnapshot = WidgetData(location = hamburg, snapshot = null, now = now)
        val emptySnapshot = WidgetData(
            location = hamburg,
            snapshot = WeatherSnapshot(
                locationId = hamburg.id,
                fetchedAt = now,
                timeZone = berlin,
                current = null,
                hourly = emptyList(),
                daily = emptyList(),
            ),
            now = now,
        )

        assertEquals(WidgetState.NO_DATA, buildCurrentWidgetContent(noSnapshot, WidgetSizeClass.EXPANDED).state)
        assertEquals(WidgetState.NO_DATA, buildCurrentWidgetContent(emptySnapshot, WidgetSizeClass.EXPANDED).state)
        // The place is still named, because the widget belongs to it even with nothing to show.
        assertEquals(hamburg.name, buildCurrentWidgetContent(noSnapshot, WidgetSizeClass.EXPANDED).locationName)
    }

    @Test
    fun `the age of the response is shown from the fetch time`() {
        val content = buildCurrentWidgetContent(data(), WidgetSizeClass.EXPANDED)

        assertEquals("vor 12 Minuten", content.updatedAgo)
    }

    @Test
    fun `a snapshot without a fetch time has no age to show`() {
        val fetchedAtEpoch = Instant.EPOCH
        val fresh = WidgetData(
            location = hamburg,
            snapshot = WeatherSnapshot(
                locationId = hamburg.id,
                fetchedAt = fetchedAtEpoch,
                timeZone = berlin,
                current = current(),
                hourly = emptyList(),
                daily = emptyList(),
            ),
            now = fetchedAtEpoch,
        )

        assertEquals("gerade eben", buildCurrentWidgetContent(fresh, WidgetSizeClass.EXPANDED).updatedAgo)
    }

    // endregion

    // region which tiles, in which size

    @Test
    fun `the expanded layout shows four prioritized metrics`() {
        val content = buildCurrentWidgetContent(data(), WidgetSizeClass.EXPANDED)

        assertEquals(
            listOf(WidgetMetric.HUMIDITY, WidgetMetric.WIND, WidgetMetric.TODAY_RANGE, WidgetMetric.PRECIPITATION_PROBABILITY),
            metrics(content),
        )
    }

    @Test
    fun `the compact layout prioritizes humidity and wind`() {
        val content = buildCurrentWidgetContent(data(), WidgetSizeClass.COMPACT)

        assertEquals(listOf(WidgetMetric.HUMIDITY, WidgetMetric.WIND), metrics(content))
        assertEquals("80$nb%", content.tiles.first().value)
    }

    @Test
    fun `a metric the response did not report is absent instead of a placeholder`() {
        val sparse = data(
            current = current(humidity = null, pressure = null, windDirection = null, cloudCover = null),
            hourly = emptyList(),
            daily = emptyList(),
        )
        val content = buildCurrentWidgetContent(sparse, WidgetSizeClass.EXPANDED)

        assertEquals(
            listOf(
                WidgetMetric.WIND,
                WidgetMetric.PRECIPITATION,
                WidgetMetric.WIND_GUSTS,
            ),
            metrics(content),
        )
        // No "heute min / max" tile without hourly or daily values, and no humidity tile without one.
        assertFalse(metrics(content).contains(WidgetMetric.TODAY_RANGE))
        assertFalse(metrics(content).contains(WidgetMetric.HUMIDITY))
    }

    // endregion

    // region where the values come from

    @Test
    fun `the precipitation probability is the value of the current hour, not the daily maximum`() {
        // The current hour (10:00-11:00) reports 20 %, the rest of the day 70 % and the daily row 90 %.
        val hours = (6..23).map { hour(it, probability = if (it == 10) 20 else 70) }
        val content = buildCurrentWidgetContent(
            data(hourly = hours, daily = listOf(daily(LocalDate.of(2026, 12, 3), 2.0, 9.0, probability = 90))),
            WidgetSizeClass.EXPANDED,
        )
        val tile = content.tiles.first { it.metric == WidgetMetric.PRECIPITATION_PROBABILITY }

        assertEquals("20$nb%", tile.value)
    }

    @Test
    fun `an hour that does not cover the moment is never used as the current one`() {
        // The series jumps from 09:00 to 11:00, so at 10:30 no interval contains the moment.
        val hours = listOf(hour(9, probability = 40), hour(11, probability = 80))
        val content = buildCurrentWidgetContent(
            data(hourly = hours, daily = emptyList()),
            WidgetSizeClass.EXPANDED,
        )

        assertFalse(metrics(content).contains(WidgetMetric.PRECIPITATION_PROBABILITY))
    }

    @Test
    fun `the range of today is the daily pair of the location, not a re-aggregation`() {
        val content = buildCurrentWidgetContent(data(), WidgetSizeClass.EXPANDED)
        val tile = content.tiles.first { it.metric == WidgetMetric.TODAY_RANGE }

        assertEquals("2/9$nb\u00B0C", tile.value)
    }

    @Test
    fun `the range falls back to the hours of the day when no daily row exists`() {
        val hours = (6..23).map { hour(it, temperature = 6.0 + it) }
        val content = buildCurrentWidgetContent(
            data(hourly = hours, daily = emptyList()),
            WidgetSizeClass.EXPANDED,
        )
        val tile = content.tiles.first { it.metric == WidgetMetric.TODAY_RANGE }

        // Hours 06 to 23 of the same local day: 12 °C to 29 °C.
        assertEquals("12/29$nb\u00B0C", tile.value)
    }

    @Test
    fun `today is the calendar day of the location, not the day of the device`() {
        // 09:30 UTC is already 18:30 in Tokyo: the widget of a Japanese city has to pick the Japanese
        // day, whose daily row is the one for 3 December in Tokyo's date sequence.
        val tokyoData = WidgetData(
            location = hamburg.copy(id = "tokyo-widget", name = "Tokio", timeZoneId = tokyo.id),
            snapshot = WeatherSnapshot(
                locationId = "tokyo-widget",
                fetchedAt = now,
                timeZone = tokyo,
                current = current(),
                hourly = (6..23).map { hour(it, zone = tokyo, temperature = 20.0) },
                daily = listOf(
                    daily(LocalDate.of(2026, 12, 3), 11.0, 18.0),
                    daily(LocalDate.of(2026, 12, 4), 10.0, 17.0),
                ),
            ),
            now = now,
        )
        val content = buildCurrentWidgetContent(tokyoData, WidgetSizeClass.EXPANDED)

        assertEquals("11/18$nb\u00B0C", content.tiles.first { it.metric == WidgetMetric.TODAY_RANGE }.value)
    }

    @Test
    fun `feels like stays null when the response never reported an apparent temperature`() {
        val content = buildCurrentWidgetContent(
            data(current = current(apparent = null)),
            WidgetSizeClass.EXPANDED,
        )

        assertNull(content.feelsLike)
    }

    @Test
    fun `the condition label and the icon follow the current condition including its intensity`() {
        val drizzle = buildCurrentWidgetContent(
            data(current = current(condition = WeatherCondition.LIGHT_DRIZZLE)),
            WidgetSizeClass.EXPANDED,
        )
        val clear = buildCurrentWidgetContent(
            data(current = current(condition = WeatherCondition.CLEAR_DAY)),
            WidgetSizeClass.EXPANDED,
        )

        assertTrue(drizzle.conditionLabelRes != clear.conditionLabelRes)
        assertTrue(drizzle.iconRes != clear.iconRes)
    }

    // endregion
}
