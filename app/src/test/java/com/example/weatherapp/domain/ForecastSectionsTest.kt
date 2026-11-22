package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.DailyWeather
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

/**
 * Day and hour bucketing for the dashboard, including time zone and daylight saving
 * behaviour, plus the cache freshness rules.
 */
class ForecastSectionsTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")
    private val reference: Instant = Instant.parse("2026-11-22T11:00:00Z")

    private fun hour(localDateTime: LocalDateTime, zone: ZoneId): HourlyWeather = HourlyWeather(
        instant = localDateTime.atZone(zone).toInstant(),
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

    private fun day(date: LocalDate): DailyWeather = DailyWeather(
        date = date,
        weatherCode = 2,
        condition = null,
        minTemperatureCelsius = 1.0,
        maxTemperatureCelsius = 7.0,
        precipitationProbabilityPercent = 20,
        precipitationMillimetres = 0.5,
        maxWindSpeedKilometresPerHour = 12.0,
        maxWindGustsKilometresPerHour = 25.0,
        sunshineSeconds = 3600,
        daylightSeconds = 28_800,
    )

    private fun snapshot(
        zone: ZoneId,
        hourly: List<HourlyWeather> = emptyList(),
        daily: List<DailyWeather> = emptyList(),
    ): WeatherSnapshot = WeatherSnapshot(
        locationId = "loc-1",
        fetchedAt = reference,
        timeZone = zone,
        current = null,
        hourly = hourly,
        daily = daily,
    )

    private fun fullHours(zone: ZoneId, date: LocalDate): List<HourlyWeather> =
        (0..23).map { hour(date.atTime(it, 0), zone) }

    @Test
    fun `today is the calendar day in the location time zone, not the device zone`() {
        // 2026-11-21T16:30Z is already 2026-11-22, 01:30 in Tokyo.
        val hours = fullHours(tokyo, LocalDate.of(2026, 11, 21)) +
            fullHours(tokyo, LocalDate.of(2026, 11, 22))
        val sections = buildForecastSections(
            snapshot = snapshot(tokyo, hours),
            referenceInstant = Instant.parse("2026-11-21T16:30:00Z"),
        )
        assertEquals(24, sections.todayHours.size)
        assertTrue(sections.todayHours.all { it.localDate == LocalDate.of(2026, 11, 22) })
    }

    @Test
    fun `hours of other days never appear in the hourly strip`() {
        val today = LocalDate.of(2026, 11, 22)
        val hours = fullHours(berlin, today.minusDays(1)) +
            fullHours(berlin, today) +
            fullHours(berlin, today.plusDays(1))
        val sections = buildForecastSections(snapshot(berlin, hours), reference)
        assertEquals(24, sections.todayHours.size)
        assertTrue(sections.todayHours.all { it.localDate == today })
    }

    @Test
    fun `hourly entries are deduplicated and sorted by instant`() {
        val today = LocalDate.of(2026, 11, 22)
        val hours = fullHours(berlin, today).shuffled() + hour(today.atTime(9, 0), berlin)
        val sections = buildForecastSections(snapshot(berlin, hours), reference)
        assertEquals(24, sections.todayHours.size)
        val instants = sections.todayHours.map { it.instant }
        assertEquals(instants.sorted(), instants)
        assertEquals(instants.distinct().size, instants.size)
    }

    @Test
    fun `the next two days are detailed and the remaining days are compact`() {
        val today = LocalDate.of(2026, 11, 22)
        val daily = (0..15).map { day(today.plusDays(it.toLong())) }
        val sections = buildForecastSections(snapshot(berlin, daily = daily), reference)

        assertEquals(
            listOf(today.plusDays(1), today.plusDays(2)),
            sections.detailedDays.map { it.date },
        )
        assertEquals(13, sections.extendedDays.size)
        assertEquals(today.plusDays(3), sections.extendedDays.first().date)
        assertEquals(today.plusDays(15), sections.extendedDays.last().date)
    }

    @Test
    fun `days before today are ignored, so a stale snapshot does not show yesterday`() {
        val today = LocalDate.of(2026, 11, 22)
        val daily = (0..15).map { day(today.minusDays(2).plusDays(it.toLong())) }
        val sections = buildForecastSections(snapshot(berlin, daily = daily), reference)
        assertTrue(sections.detailedDays.all { it.date.isAfter(today) })
        assertTrue(sections.extendedDays.all { it.date.isAfter(today) })
        assertEquals(13, sections.detailedDays.size + sections.extendedDays.size)
    }

    @Test
    fun `a shorter forecast simply produces shorter lists and duplicate dates collapse`() {
        val today = LocalDate.of(2026, 11, 22)
        val daily = listOf(
            day(today),
            day(today.plusDays(1)),
            day(today.plusDays(1)),
            day(today.plusDays(2)),
            day(today.plusDays(3)),
        )
        val sections = buildForecastSections(snapshot(berlin, daily = daily), reference)
        assertEquals(listOf(today.plusDays(1), today.plusDays(2)), sections.detailedDays.map { it.date })
        assertEquals(listOf(today.plusDays(3)), sections.extendedDays.map { it.date })
    }

    @Test
    fun `hourly data stays correct on the autumn daylight saving change`() {
        // 2026-10-25 has 25 real hours in Europe/Berlin because the clock goes back at 03:00.
        val dstDay = LocalDate.of(2026, 10, 25)
        val hours = fullHours(berlin, dstDay)
        val snapshot = snapshot(berlin, hours)
        val sections = buildForecastSections(snapshot, Instant.parse("2026-10-25T12:00:00Z"))

        assertEquals(24, sections.todayHours.size)
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), sections.todayHours.first().instant)
        assertEquals(Instant.parse("2026-10-25T22:00:00Z"), sections.todayHours.last().instant)
        assertEquals(
            Duration.ofHours(24),
            Duration.between(
                sections.todayHours.first().instant,
                sections.todayHours.last().instant,
            ),
        )
    }

    @Test
    fun `hourly data stays correct on the spring daylight saving change`() {
        // 2026-03-29 skips 02:00, so the local day only has 23 hourly stamps.
        val dstDay = LocalDate.of(2026, 3, 29)
        val localTimes = (0..1).map { dstDay.atTime(it, 0) } + (3..23).map { dstDay.atTime(it, 0) }
        val hours = localTimes.map { hour(it, berlin) }
        val sections = buildForecastSections(snapshot(berlin, hours), Instant.parse("2026-03-29T12:00:00Z"))

        assertEquals(23, sections.todayHours.size)
        assertEquals(
            Duration.ofHours(22),
            Duration.between(
                sections.todayHours.first().instant,
                sections.todayHours.last().instant,
            ),
        )
    }

    @Test
    fun `current hour index points at the last hour that already started`() {
        val today = LocalDate.of(2026, 11, 22)
        val hours = fullHours(berlin, today)
        // 11:00Z is 12:00 in Berlin, so the current bucket is the one starting at 12:00.
        assertEquals(12, currentHourIndex(hours, reference))
        assertEquals(0, currentHourIndex(emptyList(), reference))
        // A list that starts in the future anchors at the first entry.
        val tomorrowHours = fullHours(berlin, today.plusDays(1))
        assertEquals(0, currentHourIndex(tomorrowHours, reference))
    }

    @Test
    fun `cached data is fresh for half an hour`() {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        assertTrue(CachePolicy.isFresh(fetchedAt, fetchedAt.plus(Duration.ofMinutes(29))))
        assertTrue(CachePolicy.isFresh(fetchedAt, fetchedAt.plus(Duration.ofMinutes(30))))
        assertFalse(CachePolicy.isFresh(fetchedAt, fetchedAt.plus(Duration.ofMinutes(31))))
        assertTrue(CachePolicy.isStale(fetchedAt, fetchedAt.plus(Duration.ofMinutes(31))))
    }

    @Test
    fun `cache from the future is not treated as fresh and old cache is flagged as outdated`() {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        assertFalse(CachePolicy.isFresh(fetchedAt, fetchedAt.minus(Duration.ofMinutes(5))))
        assertTrue(CachePolicy.isProbablyOutdated(fetchedAt, fetchedAt.plus(Duration.ofHours(4))))
        assertFalse(CachePolicy.isProbablyOutdated(fetchedAt, fetchedAt.plus(Duration.ofHours(1))))
    }

    @Test
    fun `time zone resolution falls back in a safe order`() {
        assertEquals(berlin, zoneIdOrDefault("Europe/Berlin", "UTC"))
        assertEquals(tokyo, zoneIdOrDefault(null, "Asia/Tokyo"))
        assertEquals(berlin, zoneIdOrDefault("Not/AZone", "Europe/Berlin"))
        assertEquals(ZoneId.of("UTC"), zoneIdOrDefault("Not/AZone", "Also/NotAAZone"))
    }
}
