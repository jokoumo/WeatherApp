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
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Rolling-window behaviour around daylight saving transitions and in zones with fractional UTC
 * offsets. The payloads below are built from absolute instants, exactly like the `unixtime`
 * response format the app requests, so the window is judged on real hours rather than on labels.
 */
class HourlyWindowDaylightSavingTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun hour(instant: Instant, zone: ZoneId): HourlyWeather = HourlyWeather(
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

    /** Hourly points from [firstInstant] upwards, i.e. 24 actual hourly intervals per day. */
    private fun snapshot(
        zone: ZoneId,
        firstInstant: Instant,
        points: Int,
    ): WeatherSnapshot {
        val hours = (0 until points).map { hour(firstInstant.plus(Duration.ofHours(it.toLong())), zone) }
        return WeatherSnapshot(
            locationId = "loc-1",
            fetchedAt = firstInstant,
            timeZone = zone,
            current = null,
            hourly = hours,
            daily = emptyList(),
        )
    }

    private fun assertConsecutiveHours(entries: List<HourlyWindowEntry>) {
        assertEquals(entries.map { it.hour.instant }.distinct().size, entries.size)
        for (index in 1 until entries.size) {
            assertEquals(
                Duration.ofHours(1),
                Duration.between(entries[index - 1].hour.instant, entries[index].hour.instant),
            )
        }
    }

    private fun label(entry: HourlyWindowEntry): String =
        WeatherFormatter.clockTime(entry.hour.localDateTime)

    @Test
    fun `the autumn transition keeps both occurrences of a repeated clock time apart`() {
        // Europe/Berlin: 2026-10-25 runs from 2026-10-24T22:00Z to 2026-10-25T23:00Z, 25 real hours.
        val zone = berlin
        val first = Instant.parse("2026-10-24T22:00:00Z")
        val reference = Instant.parse("2026-10-25T00:30:00Z") // 02:30 CEST
        val entries = buildForecastSections(snapshot(zone, first, 72), reference).hourlyEntries

        assertEquals(24, entries.size)
        assertConsecutiveHours(entries)

        val twoHourEntries = entries.filter { label(it) == "02:00" }
        assertEquals(
            listOf(Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-10-25T01:00:00Z")),
            twoHourEntries.map { it.hour.instant },
        )
        assertTrue(twoHourEntries.all { it.ambiguousClockTime })
        assertEquals(
            listOf("02:00 +02", "02:00 +01"),
            twoHourEntries.map { WeatherFormatter.clockTimeWithOffset(it.hour.instant, zone) },
        )
        assertEquals(LocalDate.of(2026, 10, 25), twoHourEntries[0].hour.localDate)
        assertEquals(LocalDate.of(2026, 10, 25), twoHourEntries[1].hour.localDate)
    }

    @Test
    fun `a window that contains the repeated hour shows 24 consecutive hours of the 25 hour day`() {
        val zone = berlin
        val first = Instant.parse("2026-10-24T22:00:00Z")
        val reference = Instant.parse("2026-10-24T22:45:00Z") // 00:45 local on 25 Oct
        val entries = buildForecastSections(snapshot(zone, first, 72), reference).hourlyEntries

        assertEquals(24, entries.size)
        assertConsecutiveHours(entries)
        assertEquals(
            Duration.ofHours(23),
            Duration.between(entries.first().hour.instant, entries.last().hour.instant),
        )
        assertEquals(
            LocalDate.of(2026, 10, 25),
            entries.last { it.dayRelation == HourlyDayRelation.TODAY }.hour.localDate,
        )
    }

    @Test
    fun `the spring transition does not invent the skipped hour`() {
        // Europe/Berlin: 2027-03-28 runs from 2027-03-27T23:00Z to 2027-03-28T22:00Z, 23 real hours.
        val zone = berlin
        val first = Instant.parse("2027-03-27T23:00:00Z")
        val reference = Instant.parse("2027-03-28T00:30:00Z") // 01:30 CET
        val entries = buildForecastSections(snapshot(zone, first, 72), reference).hourlyEntries

        assertEquals(24, entries.size)
        assertConsecutiveHours(entries)
        assertFalse(entries.any { it.hour.localDateTime.toLocalDate() == LocalDate.of(2027, 3, 28) && it.hour.localDateTime.hour == 2 })

        val labels = entries.map { label(it) }
        assertEquals("01:00", labels[0])
        assertEquals("03:00", labels[1]) // the local 02:00 hour does not exist that day
        assertFalse(labels.contains("02:00"))
        assertEquals(LocalDate.of(2027, 3, 28), entries.first().hour.localDate)
        assertEquals(LocalDate.of(2027, 3, 29), entries.last().hour.localDate)
    }

    @Test
    fun `the window advances over the transition exactly one hour at a time`() {
        val zone = berlin
        val first = Instant.parse("2026-10-24T22:00:00Z")
        val data = snapshot(zone, first, 72)

        val before = buildForecastSections(data, Instant.parse("2026-10-25T00:59:00Z")).hourlyEntries
        val after = buildForecastSections(data, Instant.parse("2026-10-25T01:00:00Z")).hourlyEntries

        assertEquals(Instant.parse("2026-10-25T00:00:00Z"), before.first().hour.instant)
        assertEquals(Instant.parse("2026-10-25T01:00:00Z"), after.first().hour.instant)
        assertEquals(before.drop(1).map { it.hour.instant }, after.dropLast(1).map { it.hour.instant })
    }

    @Test
    fun `a location west of the utc offset repeats its clock hour the same way`() {
        // America/New_York falls back on 2026-11-01 at 06:00 local (05:00Z).
        val zone = ZoneId.of("America/New_York")
        val first = Instant.parse("2026-10-31T04:00:00Z") // 00:00 local on 31 Oct, EDT
        val reference = Instant.parse("2026-11-01T04:30:00Z") // 01:30 EDT
        val entries = buildForecastSections(snapshot(zone, first, 72), reference).hourlyEntries

        assertEquals(24, entries.size)
        assertConsecutiveHours(entries)
        val repeated = entries.filter { label(it) == "01:00" }
        assertEquals(
            listOf("01:00 -04", "01:00 -05"),
            repeated.map { WeatherFormatter.clockTimeWithOffset(it.hour.instant, zone) },
        )
    }

    @Test
    fun `a half hour offset selects the local hour and labels the next local day correctly`() {
        val zone = ZoneId.of("Asia/Kolkata") // +05:30, no daylight saving
        val first = Instant.parse("2026-11-21T18:30:00Z") // 00:00 local on 22 Nov
        val reference = Instant.parse("2026-11-22T05:00:00Z") // 10:30 local
        val entries = buildForecastSections(snapshot(zone, first, 72), reference).hourlyEntries

        assertEquals(24, entries.size)
        assertConsecutiveHours(entries)
        // 10:30 local falls into the interval that started at 10:00 local.
        assertEquals(Instant.parse("2026-11-22T04:30:00Z"), entries.first().hour.instant)
        assertEquals("10:00", label(entries.first()))
        assertTrue(entries.first().isCurrent)

        val nextDay = entries.first { it.dayRelation == HourlyDayRelation.TOMORROW }
        assertEquals(Instant.parse("2026-11-22T18:30:00Z"), nextDay.hour.instant)
        assertEquals(LocalDate.of(2026, 11, 23), nextDay.hour.localDate)
        assertEquals("00:00", label(nextDay))
        assertTrue(nextDay.startsNewDay)
    }

    @Test
    fun `a forty five minute offset is handled like any other zone`() {
        val zone = ZoneId.of("Asia/Kathmandu") // +05:45
        val first = Instant.parse("2026-11-21T18:15:00Z") // 00:00 local on 22 Nov
        val reference = Instant.parse("2026-11-22T05:00:00Z") // 10:45 local
        val entries = buildForecastSections(snapshot(zone, first, 72), reference).hourlyEntries

        assertEquals(24, entries.size)
        assertConsecutiveHours(entries)
        assertEquals(Instant.parse("2026-11-22T04:15:00Z"), entries.first().hour.instant)
        assertEquals("10:00", label(entries.first()))
        assertEquals(
            "00:00",
            label(entries.first { it.dayRelation == HourlyDayRelation.TOMORROW }),
        )
    }

    @Test
    fun `a half hour daylight saving shift keeps 24 consecutive hours without repeated labels`() {
        // Australia/Lord Howe shifts by 30 minutes (+11:00 -> +10:30) in early April 2027.
        val zone = ZoneId.of("Australia/Lord_Howe")
        val first = Instant.parse("2027-04-01T13:00:00Z") // 00:00 local on 2 Apr
        val reference = Instant.parse("2027-04-03T14:30:00Z") // just before the shift
        val entries = buildForecastSections(snapshot(zone, first, 96), reference).hourlyEntries

        assertEquals(24, entries.size)
        assertConsecutiveHours(entries)
        // The shift happens inside the window, and both offsets are represented.
        assertEquals(
            setOf(ZoneOffset.ofHoursMinutes(11, 0), ZoneOffset.ofHoursMinutes(10, 30)),
            entries.map { it.hour.instant.atZone(zone).offset }.toSet(),
        )
        // A 30 minute fall-back never repeats a clock time, so the labels stay unique.
        assertEquals(entries.size, entries.map { label(it) }.distinct().size)
        assertEquals(1, entries.count { it.isCurrent })
    }
}
