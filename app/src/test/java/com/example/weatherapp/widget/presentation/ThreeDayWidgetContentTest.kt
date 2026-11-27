package com.example.weatherapp.widget.presentation

import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.testing.tokyo
import com.example.weatherapp.widget.WidgetData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three rows of the three-day widget: which calendar days they are, in the time zone that belongs
 * to the location, and what the widget says about a day the response does not cover.
 */
class ThreeDayWidgetContentTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    /** Thursday, 3 December 2026, 09:30 UTC - 10:30 in Berlin, 18:30 in Tokyo. */
    private val now: Instant = Instant.parse("2026-12-03T09:30:00Z")

    private val nb = "\u00A0"

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
        daily: List<DailyWeather>,
        zone: ZoneId = berlin,
        locationZoneId: String = zone.id,
        name: String = "Hamburg",
    ): WidgetData = WidgetData(
        location = com.example.weatherapp.testing.hamburg.copy(timeZoneId = locationZoneId, name = name),
        snapshot = WeatherSnapshot(
            locationId = "widget-three-day",
            fetchedAt = now,
            timeZone = zone,
            current = null,
            hourly = emptyList(),
            daily = daily,
        ),
        now = now,
    )

    /** A complete week of daily rows starting at [firstDate]. */
    private fun week(firstDate: LocalDate = LocalDate.of(2026, 12, 3)): List<DailyWeather> =
        (0 until 7).map { offset ->
            daily(
                date = firstDate.plusDays(offset.toLong()),
                min = 1.0 + offset,
                max = 8.0 + offset,
                probability = 10 + offset,
                millimetres = offset.toDouble(),
            )
        }

    // region states

    @Test
    fun `a widget without a location has no rows and no name`() {
        val content = buildThreeDayWidgetContent(null)

        assertEquals(WidgetState.NO_LOCATION, content.state)
        assertEquals("", content.locationName)
        assertTrue(content.days.isEmpty())
    }

    @Test
    fun `a location without daily rows says so and keeps its name`() {
        val content = buildThreeDayWidgetContent(data(daily = emptyList()))

        assertEquals(WidgetState.NO_DATA, content.state)
        assertEquals("Hamburg", content.locationName)
        assertTrue(content.days.isEmpty())
    }

    // endregion

    // region which days

    @Test
    fun `the rows are today, tomorrow and the day after tomorrow`() {
        val content = buildThreeDayWidgetContent(data(week()))

        assertEquals(3, content.days.size)
        assertEquals(
            listOf(LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 4), LocalDate.of(2026, 12, 5)),
            content.days.map { it.date },
        )
        assertEquals(
            listOf(WidgetDayRelation.TODAY, WidgetDayRelation.TOMORROW, WidgetDayRelation.DAY_AFTER),
            content.days.map { it.dayRelation },
        )
    }

    @Test
    fun `the dates are the calendar days of the location, not of the device`() {
        // The same instant is already 18:30 on 3 December in Tokyo and still 10:30 in Berlin. A widget
        // of a Japanese city therefore has to start at Tokyo's today, and the test makes the snapshot
        // carry a zone whose today differs from Berlin's by choosing an instant late in the day.
        val lateInUtcDay = Instant.parse("2026-12-03T23:30:00Z")
        val tokyoData = WidgetData(
            location = tokyo,
            snapshot = WeatherSnapshot(
                locationId = tokyo.id,
                fetchedAt = lateInUtcDay,
                timeZone = tokyo.timeZoneId.let { ZoneId.of(it) },
                current = null,
                hourly = emptyList(),
                daily = week(LocalDate.of(2026, 12, 4)),
            ),
            now = lateInUtcDay,
        )
        val content = buildThreeDayWidgetContent(tokyoData)

        // 23:30 UTC is 08:30 on 4 December in Tokyo: the first row is the 4th, even though Berlin is
        // still writing the 3rd.
        assertEquals(LocalDate.of(2026, 12, 4), content.days.first().date)
        assertEquals(WidgetDayRelation.TODAY, content.days.first().dayRelation)
    }

    @Test
    fun `the rows roll over at midnight of the location`() {
        val justBeforeMidnight = Instant.parse("2026-12-03T22:59:00Z") // 23:59 Berlin
        val justAfterMidnight = Instant.parse("2026-12-03T23:01:00Z") // 00:01 Berlin on the 4th

        val before = buildThreeDayWidgetContent(
            data(week(), locationZoneId = berlin.id).copy(now = justBeforeMidnight),
        )
        val after = buildThreeDayWidgetContent(
            data(week(), locationZoneId = berlin.id).copy(now = justAfterMidnight),
        )

        assertEquals(LocalDate.of(2026, 12, 3), before.days.first().date)
        assertEquals(LocalDate.of(2026, 12, 4), after.days.first().date)
        assertEquals(WidgetDayRelation.TODAY, after.days.first().dayRelation)
    }

    @Test
    fun `days stay consecutive across a daylight saving transition`() {
        // 29 March 2026 is a 23 hour day in Berlin; the three rows are still three calendar days.
        val springInstant = Instant.parse("2026-03-29T10:00:00Z")
        val content = buildThreeDayWidgetContent(
            data(
                week(LocalDate.of(2026, 3, 29)),
                locationZoneId = berlin.id,
            ).copy(now = springInstant),
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 3, 31),
            ),
            content.days.map { it.date },
        )
        assertTrue(content.days.all { it.hasData })
    }

    @Test
    fun `a day is matched by its date and not by its position in the response`() {
        // The response starts the day after today: today's row exists but has nothing to show.
        val content = buildThreeDayWidgetContent(data(week(LocalDate.of(2026, 12, 4))))

        assertFalse(content.days[0].hasData)
        assertTrue(content.days[1].hasData)
        assertEquals(LocalDate.of(2026, 12, 3), content.days[0].date)
        assertEquals(WidgetDayRelation.TODAY, content.days[0].dayRelation)
    }

    // endregion

    // region values of a row

    @Test
    fun `an uncovered day keeps its row and shows the placeholder of the app`() {
        val content = buildThreeDayWidgetContent(data(daily = week().take(1)))

        assertEquals(3, content.days.size)
        val missing = content.days[1]
        assertFalse(missing.hasData)
        assertEquals("\u2013", missing.temperatureRange)
        assertNull(missing.minTemperatureCelsius)
        assertNull(missing.precipitationSummary())
    }

    @Test
    fun `a row carries the temperature range the app uses and the precipitation of the day`() {
        val row = buildThreeDayWidgetContent(data(week())).days.first()

        assertEquals("1\u20138$nb\u00B0C", row.temperatureRange)
        assertEquals(1.0, row.minTemperatureCelsius!!, 0.001)
        assertEquals(8.0, row.maxTemperatureCelsius!!, 0.001)
        assertEquals("10$nb% · 0${nb}mm", row.precipitationSummary())
    }

    @Test
    fun `the weekday and the short date come from the German formatters of the app`() {
        val row = buildThreeDayWidgetContent(data(week())).days.first()

        assertEquals("Do.", row.weekdayLabel)
        assertEquals("03.12.", row.shortDate)
    }

    @Test
    fun `a day without precipitation values has no precipitation line`() {
        val content = buildThreeDayWidgetContent(
            data(daily = listOf(daily(LocalDate.of(2026, 12, 3), 2.0, 8.0, probability = null, millimetres = null))),
        )

        assertNull(content.days.first().precipitationSummary())
        assertTrue(content.days.first().hasData)
    }

    // endregion
}
