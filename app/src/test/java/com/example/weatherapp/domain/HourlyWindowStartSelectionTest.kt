package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Regression tests for the start of the rolling hourly window. The window must begin at the
 * hourly interval that contains the reference instant and must never fall back to an interval
 * that has already elapsed just because the payload is missing the current hour.
 */
class HourlyWindowStartSelectionTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun hour(instant: Instant, zone: ZoneId = berlin): HourlyWeather = HourlyWeather(
        instant = instant,
        timeZone = zone,
        temperatureCelsius = 5.0,
        apparentTemperatureCelsius = null,
        weatherCode = 1,
        condition = null,
        precipitationProbabilityPercent = null,
        precipitationMillimetres = null,
        relativeHumidityPercent = null,
        cloudCoverPercent = null,
        windSpeedKilometresPerHour = null,
        windDirectionDegrees = null,
        windGustsKilometresPerHour = null,
        pressureHectopascal = null,
        isDay = true,
    )

    /** Payload built from explicit instants, so gaps are easy to describe. */
    private fun payload(instants: List<Instant>, zone: ZoneId = berlin): WeatherSnapshot =
        WeatherSnapshot(
            locationId = "loc-1",
            fetchedAt = instants.firstOrNull() ?: Instant.EPOCH,
            timeZone = zone,
            current = null,
            hourly = instants.map { hour(it, zone) },
            daily = emptyList(),
        )

    private fun windowOf(instants: List<Instant>, reference: Instant, zone: ZoneId = berlin) =
        buildForecastSections(payload(instants, zone), reference).hourlyEntries

    private fun localTime(text: String): Instant = LocalDateTime.parse(text).atZone(berlin).toInstant()

    private fun localBerlin(hoursOfDay: List<Int>, day: Int = 22): List<Instant> = hoursOfDay.map {
        LocalDate.of(2026, 11, day).atTime(it, 0).atZone(berlin).toInstant()
    }

    private fun hourlyInstants(start: Instant, count: Int, stepMinutes: Long = 60): List<Instant> =
        (0 until count).map { start.plus(Duration.ofMinutes(stepMinutes * it)) }

    private fun clockTimes(entries: List<HourlyWindowEntry>, zone: ZoneId = berlin): List<String> =
        entries.map { WeatherFormatter.clockTime(it.hour.instant.atZone(zone).toLocalDateTime()) }

    @Test
    fun `the current interval is the first entry when the payload contains it`() {
        val reference = localTime("2026-11-22T14:30")
        val window = windowOf(localBerlin(listOf(13, 14, 15, 16)), reference)

        assertEquals(listOf("14:00", "15:00", "16:00"), clockTimes(window))
        assertTrue(window.first().isCurrent)
    }

    @Test
    fun `a missing current hour starts the window at the next available hour`() {
        val reference = localTime("2026-11-22T14:30")
        val window = windowOf(localBerlin(listOf(13, 15, 16, 17)), reference)

        // The 13:00 interval ended at 14:00 and must not be presented as the current forecast.
        assertEquals(listOf("15:00", "16:00", "17:00"), clockTimes(window))
        assertFalse(window.any { it.isCurrent })
    }

    @Test
    fun `a payload that only holds elapsed hours shows no hourly entries`() {
        val reference = localTime("2026-11-22T14:30")

        assertTrue(windowOf(localBerlin(listOf(11, 12, 13)), reference).isEmpty())
    }

    @Test
    fun `a payload that only starts later is shown from its first point`() {
        val reference = localTime("2026-11-22T14:30")
        val window = windowOf(localBerlin(listOf(18, 19, 20)), reference)

        assertEquals(listOf("18:00", "19:00", "20:00"), clockTimes(window))
        assertFalse(window.any { it.isCurrent })
    }

    @Test
    fun `a reference exactly on the hour selects that hour`() {
        val reference = localTime("2026-11-22T14:00")
        val window = windowOf(localBerlin(listOf(13, 14, 15)), reference)

        assertEquals(listOf("14:00", "15:00"), clockTimes(window))
        assertTrue(window.first().isCurrent)
    }

    @Test
    fun `one second before the hour keeps the current interval`() {
        val reference = localTime("2026-11-22T13:59").plus(Duration.ofSeconds(59))
        val window = windowOf(localBerlin(listOf(13, 14, 15)), reference)

        assertEquals(listOf("13:00", "14:00", "15:00"), clockTimes(window))
        assertTrue(window.first().isCurrent)
    }

    @Test
    fun `exactly on the hour an expired interval is not reused when the new hour is missing`() {
        val reference = localTime("2026-11-22T14:00")
        val window = windowOf(localBerlin(listOf(13, 15, 16)), reference)

        assertEquals(listOf("15:00", "16:00"), clockTimes(window))
    }

    @Test
    fun `an empty payload produces an empty window`() {
        val reference = localTime("2026-11-22T14:30")

        assertTrue(windowOf(emptyList(), reference).isEmpty())
        assertEquals(0, hourlyWindowStartIndex(emptyList(), reference))
    }

    @Test
    fun `unsorted payloads are sorted before the window is selected`() {
        val reference = localTime("2026-11-22T14:30")
        val window = windowOf(localBerlin(listOf(17, 13, 16, 14, 15)), reference)

        assertEquals(listOf("14:00", "15:00", "16:00", "17:00"), clockTimes(window))
        assertEquals(
            window.map { it.hour.instant }.sorted(),
            window.map { it.hour.instant },
        )
    }

    @Test
    fun `no entry of the window is an interval that has already elapsed`() {
        val reference = localTime("2026-11-22T14:30")
        val window = windowOf(localBerlin(listOf(8, 9, 10, 15, 16, 20)), reference)
        val currentHourStart = reference.atZone(berlin).truncatedTo(ChronoUnit.HOURS).toInstant()

        assertEquals(listOf("15:00", "16:00", "20:00"), clockTimes(window))
        assertTrue(window.all { !it.hour.instant.isBefore(currentHourStart) })
    }

    @Test
    fun `a large gap shortens the window instead of stretching it beyond 24 hours`() {
        val reference = localTime("2026-11-22T14:30")
        val instants = localBerlin(listOf(14)) +
            localBerlin((20..23).toList()) +
            localBerlin((0..20).toList(), day = 23)
        val window = windowOf(instants, reference)

        // 14:00 is current, and the horizon 24 hours later ends at 13:00 on the 23rd: the five
        // missing hours stay missing and the strip does not reach further ahead to fill up.
        assertEquals(19, window.size)
        assertEquals(listOf("14:00", "20:00"), clockTimes(window).take(2))
        assertEquals("13:00", clockTimes(window).last())
        assertEquals(1, window.count { it.isCurrent })
        assertTrue(
            Duration.between(window.first().hour.instant, window.last().hour.instant)
                < Duration.ofHours(HOURLY_WINDOW_SIZE.toLong()),
        )
    }

    @Test
    fun `the window still covers 24 consecutive hours across midnight`() {
        val reference = localTime("2026-11-22T23:45")
        val start = LocalDate.of(2026, 11, 22).atStartOfDay(berlin).toInstant()
        val window = windowOf(hourlyInstants(start, 48), reference)

        assertEquals(24, window.size)
        assertEquals("23:00", clockTimes(window).first())
        assertEquals("22:00", clockTimes(window).last())
        assertTrue(window.first().isCurrent)
        assertTrue(window[1].startsNewDay)
    }

    @Test
    fun `a spring day without the current local hour still yields actual hourly intervals`() {
        // 2027-03-28: Berlin jumps from 01:59 CET to 03:00 CEST, so local 02:00 never happens.
        val reference = Instant.parse("2027-03-28T00:30:00Z") // 01:30 local
        val start = Instant.parse("2027-03-27T23:00:00Z")
        val window = windowOf(hourlyInstants(start, 30), reference)

        assertEquals(Instant.parse("2027-03-28T00:00:00Z"), window.first().hour.instant)
        assertEquals(listOf("01:00", "03:00", "04:00"), clockTimes(window).take(3))
        assertTrue(window.first().isCurrent)
        assertEquals(24, window.size)
    }

    @Test
    fun `an autumn day keeps both occurrences of the repeated hour`() {
        // 2026-10-25: local 02:00 happens twice, at 00:00Z (+02) and at 01:00Z (+01).
        val reference = Instant.parse("2026-10-25T00:30:00Z") // 02:30 first occurrence
        val start = Instant.parse("2026-10-24T23:00:00Z")
        val window = windowOf(hourlyInstants(start, 30), reference)

        assertEquals(Instant.parse("2026-10-25T00:00:00Z"), window.first().hour.instant)
        assertTrue(window.first().isCurrent)
        assertTrue(window.any { it.hour.instant == Instant.parse("2026-10-25T01:00:00Z") })
        // Only the repeated local hour is flagged as ambiguous, the rest of the day is not.
        assertEquals(
            listOf(Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-10-25T01:00:00Z")),
            window.filter { it.ambiguousClockTime }.map { it.hour.instant },
        )
    }

    @Test
    fun `the second occurrence of a repeated local hour becomes current later on`() {
        val reference = Instant.parse("2026-10-25T01:30:00Z") // 02:30 second occurrence (+01)
        val start = Instant.parse("2026-10-24T23:00:00Z")
        val window = windowOf(hourlyInstants(start, 30), reference)

        assertEquals(Instant.parse("2026-10-25T01:00:00Z"), window.first().hour.instant)
        assertTrue(window.first().isCurrent)
    }

    @Test
    fun `a zone with a half hour offset selects the interval that contains the instant`() {
        // Asia/Kolkata hourly points land on :30 UTC, so truncating the reference to a whole UTC
        // hour would skip the interval that is actually running.
        val reference = Instant.parse("2026-11-22T04:47:00Z") // 10:17 local
        val start = Instant.parse("2026-11-22T04:30:00Z") // 10:00 local
        val window = windowOf(hourlyInstants(start, 12), reference, kolkata)

        assertEquals(start, window.first().hour.instant)
        assertTrue(window.first().isCurrent)
        assertEquals("10:00", clockTimes(window, kolkata).first())
    }

    @Test
    fun `start index counts whole intervals instead of the nearest earlier point`() {
        val reference = localTime("2026-11-22T14:30")
        val sorted = sortedDistinctHours(localBerlin(listOf(13, 15, 16)).map { hour(it) })

        // 13:00 has already elapsed, so the window starts at index 1 and skips it.
        assertEquals(1, hourlyWindowStartIndex(sorted, reference))
        assertEquals(
            localBerlin(listOf(15, 16)),
            hourlyWindow(sorted, reference).map { it.instant },
        )
    }
}
