package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rolling hourly window, day bucketing and cache freshness for the dashboard. Every case works
 * on a fixed reference instant, so nothing depends on the time the test happens to run at.
 */
class ForecastSectionsTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")
    private val reference: Instant = Instant.parse("2026-11-22T11:00:00Z")

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

    /** One hourly point per hour of [from] for [hours] consecutive hours. */
    private fun hourlySeries(from: Instant, hours: Int, zone: ZoneId = berlin): List<HourlyWeather> =
        (0 until hours).map { hour(from.plus(Duration.ofHours(it.toLong())), zone) }

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

    /** Snapshot whose hourly data starts at local midnight of [startDay] and runs [days] days. */
    private fun fullSnapshot(
        zone: ZoneId,
        startDay: LocalDate,
        days: Int = 16,
        referenceInstant: Instant = reference,
    ): WeatherSnapshot = snapshot(
        zone = zone,
        hourly = hourlySeries(
            from = startDay.atStartOfDay(zone).toInstant(),
            hours = days * 24,
            zone = zone,
        ),
        daily = (0 until days).map { day(startDay.plusDays(it.toLong())) },
    )

    private fun sectionsFor(
        zone: ZoneId,
        startDay: LocalDate,
        referenceInstant: Instant,
        days: Int = 16,
    ): ForecastSections = buildForecastSections(
        snapshot = fullSnapshot(zone, startDay, days, referenceInstant),
        referenceInstant = referenceInstant,
    )

    private fun clockTimes(entries: List<HourlyWindowEntry>, zone: ZoneId): List<String> =
        entries.map { WeatherFormatter.clockTime(it.hour.instant.atZone(zone).toLocalDateTime()) }

    // region rolling window

    @Test
    fun `13_25 shows 13_00 today through 12_00 tomorrow`() {
        val now = Instant.parse("2026-11-22T12:25:00Z") // 13:25 local, CET
        val sections = sectionsFor(berlin, LocalDate.of(2026, 11, 22), now)
        val window = sections.hourlyEntries

        assertEquals(24, window.size)
        assertEquals(Instant.parse("2026-11-22T12:00:00Z"), window.first().hour.instant)
        assertEquals(Instant.parse("2026-11-23T11:00:00Z"), window.last().hour.instant)
        assertEquals(
            listOf("13:00", "23:00", "00:00", "12:00"),
            listOf(
                WeatherFormatter.clockTime(window.first().hour.localDateTime),
                WeatherFormatter.clockTime(window[10].hour.localDateTime),
                WeatherFormatter.clockTime(window[11].hour.localDateTime),
                WeatherFormatter.clockTime(window.last().hour.localDateTime),
            ),
        )
    }

    @Test
    fun `20_15 shows 20_00 today through 19_00 tomorrow`() {
        val now = Instant.parse("2026-11-22T19:15:00Z") // 20:15 local
        val window = sectionsFor(berlin, LocalDate.of(2026, 11, 22), now).hourlyEntries

        assertEquals(24, window.size)
        assertEquals(Instant.parse("2026-11-22T19:00:00Z"), window.first().hour.instant)
        assertEquals(Instant.parse("2026-11-23T18:00:00Z"), window.last().hour.instant)
    }

    @Test
    fun `23_45 shows 23_00 today through 22_00 tomorrow`() {
        val now = Instant.parse("2026-11-22T22:45:00Z") // 23:45 local
        val window = sectionsFor(berlin, LocalDate.of(2026, 11, 22), now).hourlyEntries

        assertEquals(24, window.size)
        assertEquals(Instant.parse("2026-11-22T22:00:00Z"), window.first().hour.instant)
        assertEquals(Instant.parse("2026-11-23T21:00:00Z"), window.last().hour.instant)
        assertEquals("23:00", WeatherFormatter.clockTime(window.first().hour.localDateTime))
        assertEquals("22:00", WeatherFormatter.clockTime(window.last().hour.localDateTime))
    }

    @Test
    fun `00_10 shows 00_00 through 23_00 of the same local day`() {
        val now = Instant.parse("2026-11-21T23:10:00Z") // 00:10 local on 22 Nov
        val sections = sectionsFor(berlin, LocalDate.of(2026, 11, 22), now)
        val window = sections.hourlyEntries

        assertEquals(24, window.size)
        assertEquals(Instant.parse("2026-11-21T23:00:00Z"), window.first().hour.instant)
        assertEquals(Instant.parse("2026-11-22T22:00:00Z"), window.last().hour.instant)
        assertEquals(
            "00:00",
            WeatherFormatter.clockTime(window.first().hour.localDateTime),
        )
        assertEquals("23:00", WeatherFormatter.clockTime(window.last().hour.localDateTime))
        assertTrue(window.all { it.hour.localDate == LocalDate.of(2026, 11, 22) })
    }

    @Test
    fun `the window starts exactly at the hour when the hour begins and one hour later after it`() {
        val justBefore = Instant.parse("2026-11-22T12:59:59Z")
        val justAfter = Instant.parse("2026-11-22T13:00:00Z")
        val snapshot = fullSnapshot(berlin, LocalDate.of(2026, 11, 22))

        assertEquals(
            Instant.parse("2026-11-22T12:00:00Z"),
            buildForecastSections(snapshot, justBefore).hourlyEntries.first().hour.instant,
        )
        assertEquals(
            Instant.parse("2026-11-22T13:00:00Z"),
            buildForecastSections(snapshot, justAfter).hourlyEntries.first().hour.instant,
        )
    }

    @Test
    fun `elapsed hours are never included and hours of the day before never leak in`() {
        val now = Instant.parse("2026-11-22T11:00:00Z")
        val window = sectionsFor(berlin, LocalDate.of(2026, 11, 20), now).hourlyEntries

        assertEquals(24, window.size)
        assertEquals(Instant.parse("2026-11-22T11:00:00Z"), window.first().hour.instant)
        assertTrue(window.all { !it.hour.instant.isBefore(window.first().hour.instant) })
    }

    @Test
    fun `entries stay sorted by absolute instant and appear only once`() {
        val now = Instant.parse("2026-11-22T11:00:00Z")
        val start = LocalDate.of(2026, 11, 22).atStartOfDay(berlin).toInstant()
        val duplicated = hourlySeries(start, 48) + hourlySeries(start, 48)
        val sections = buildForecastSections(snapshot(berlin, duplicated.reversed()), now)
        val window = sections.hourlyEntries

        assertEquals(24, window.size)
        assertEquals(window.map { it.hour.instant }.distinct().size, window.size)
        assertEquals(
            window.map { it.hour.instant }.sorted(),
            window.map { it.hour.instant },
        )
    }

    @Test
    fun `a gap in the hourly data is skipped instead of filled with invented hours`() {
        val start = LocalDate.of(2026, 11, 22).atStartOfDay(berlin).toInstant()
        // Three hours are missing between local 11:00 and local 15:00.
        val withGap = hourlySeries(start, 12) + hourlySeries(start.plus(Duration.ofHours(15)), 40)
        val now = start.plus(Duration.ofHours(5)) // 05:00 local

        val window = buildForecastSections(snapshot(berlin, withGap), now).hourlyEntries

        // The window is bounded by 24 hours of forecast time, not by 24 records: the gap stays
        // visible as a gap and the strip ends at local 04:00 the next day instead of reaching
        // further ahead just to produce 24 entries.
        assertEquals(21, window.size)
        assertEquals(3, missingHourGaps(window))
        assertEquals(start.plus(Duration.ofHours(5)), window.first().hour.instant)
        assertEquals(start.plus(Duration.ofHours(28)), window.last().hour.instant)
        assertTrue(
            Duration.between(window.first().hour.instant, window.last().hour.instant)
                < Duration.ofHours(HOURLY_WINDOW_SIZE.toLong()),
        )
        // Every displayed entry comes from the payload: nothing is generated for the gap.
        assertTrue(window.all { entry -> withGap.any { it.instant == entry.hour.instant } })
    }

    @Test
    fun `a window that reaches the end of the data shows the remaining hours without padding`() {
        val now = Instant.parse("2026-11-23T21:00:00Z")
        val start = LocalDate.of(2026, 11, 22).atStartOfDay(berlin).toInstant()
        val window = buildForecastSections(
            snapshot = snapshot(berlin, hourlySeries(start, 48)),
            referenceInstant = now,
        ).hourlyEntries

        assertEquals(2, window.size)
        assertEquals(Instant.parse("2026-11-23T21:00:00Z"), window.first().hour.instant)
        assertEquals(Instant.parse("2026-11-23T22:00:00Z"), window.last().hour.instant)
    }

    @Test
    fun `hourly data that has not started yet is shown from its first point without a current hour`() {
        val now = Instant.parse("2026-11-22T11:00:00Z")
        val start = now.plus(Duration.ofHours(3))
        val window = buildForecastSections(
            snapshot = snapshot(berlin, hourlySeries(start, 24)),
            referenceInstant = now,
        ).hourlyEntries

        assertEquals(24, window.size)
        assertEquals(start, window.first().hour.instant)
        assertFalse(window.any { it.isCurrent })
    }

    @Test
    fun `the first entry of the window is marked as the current hour`() {
        val sections = sectionsFor(berlin, LocalDate.of(2026, 11, 22), reference)
        val current = sections.hourlyEntries.filter { it.isCurrent }

        assertEquals(1, current.size)
        assertEquals(0, sections.hourlyEntries.indexOf(current.first()))
        assertEquals(reference, current.first().hour.instant)
    }

    @Test
    fun `the window is built in the location time zone and not in the device zone`() {
        // 2026-11-21T16:30Z is already 2026-11-22, 01:30 in Tokyo.
        val now = Instant.parse("2026-11-21T16:30:00Z")
        val sections = sectionsFor(tokyo, LocalDate.of(2026, 11, 22), now)
        val window = sections.hourlyEntries

        assertEquals(24, window.size)
        assertEquals(Instant.parse("2026-11-21T16:00:00Z"), window.first().hour.instant)
        assertEquals(LocalDate.of(2026, 11, 22), window.first().hour.localDate)
        assertEquals(HourlyDayRelation.TODAY, window.first().dayRelation)
    }

    // endregion

    // region day labels and separators

    @Test
    fun `a window that crosses midnight labels the second day as tomorrow`() {
        val now = Instant.parse("2026-11-22T22:45:00Z") // 23:45 local
        val window = sectionsFor(berlin, LocalDate.of(2026, 11, 22), now).hourlyEntries

        assertEquals(HourlyDayRelation.TODAY, window.first().dayRelation)
        assertTrue(window.first().startsNewDay)
        val firstTomorrow = window.first { it.dayRelation == HourlyDayRelation.TOMORROW }
        assertEquals(LocalDate.of(2026, 11, 23), firstTomorrow.hour.localDate)
        assertTrue(firstTomorrow.startsNewDay)
        assertEquals(
            window.drop(1).filter { it.hour.localDate != window[window.indexOf(it) - 1].hour.localDate }.size,
            window.count { it.startsNewDay } - 1,
        )
        assertEquals(
            listOf("23:00", "00:00"),
            listOf(
                WeatherFormatter.clockTime(window.first().hour.localDateTime),
                WeatherFormatter.clockTime(firstTomorrow.hour.localDateTime),
            ),
        )
    }

    @Test
    fun `a window inside one day starts a new day only at its first entry`() {
        val now = Instant.parse("2026-11-21T23:10:00Z") // 00:10 local
        val window = sectionsFor(berlin, LocalDate.of(2026, 11, 22), now).hourlyEntries

        assertEquals(1, window.count { it.startsNewDay })
        assertTrue(window.all { it.dayRelation == HourlyDayRelation.TODAY })
    }

    @Test
    fun `weekday labels of the entries stay correct across midnight`() {
        val now = Instant.parse("2026-11-22T22:45:00Z") // Sunday 23:45 local
        val window = sectionsFor(berlin, LocalDate.of(2026, 11, 22), now).hourlyEntries

        assertEquals("So.", WeatherFormatter.weekday(window.first().hour.localDate))
        assertEquals("Mo.", WeatherFormatter.weekday(window.last().hour.localDate))
    }

    // endregion

    // region today summary and daily sections

    @Test
    fun `the today summary keeps the whole local day even when the window crosses midnight`() {
        val now = Instant.parse("2026-11-22T22:45:00Z") // 23:45 local
        val sections = sectionsFor(berlin, LocalDate.of(2026, 11, 22), now)

        assertEquals(24, sections.todayHours.size)
        assertTrue(sections.todayHours.all { it.localDate == LocalDate.of(2026, 11, 22) })
        assertTrue(sections.hourlyEntries.any { it.hour.localDate != LocalDate.of(2026, 11, 22) })
    }

    @Test
    fun `detailed days are the two days after today and the rest is extended`() {
        val sections = sectionsFor(berlin, LocalDate.of(2026, 11, 22), reference, days = 16)

        assertEquals(
            listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 24)),
            sections.detailedDays.map { it.date },
        )
        assertEquals(13, sections.extendedDays.size)
        assertEquals(LocalDate.of(2026, 12, 7), sections.extendedDays.last().date)
    }

    @Test
    fun `a snapshot without data produces empty sections`() {
        val sections = buildForecastSections(snapshot(berlin), reference)

        assertTrue(sections.hourlyEntries.isEmpty())
        assertTrue(sections.todayHours.isEmpty())
        assertTrue(sections.detailedDays.isEmpty())
        assertTrue(sections.extendedDays.isEmpty())
    }

    // endregion

    // region clock tick

    /** Safety margin that millisUntilNextHour adds; see ForecastSectionsTest below. */
    private val tickMarginMillis = 50L

    @Test
    fun `the tick waits for the remaining minutes of the current local hour`() {
        val now = Instant.parse("2026-11-22T12:25:00Z") // 13:25 local

        assertEquals(
            Duration.ofMinutes(35).toMillis() + tickMarginMillis,
            millisUntilNextHour(now, berlin),
        )
    }

    @Test
    fun `the tick at an exact hour boundary waits a full hour`() {
        assertEquals(
            Duration.ofHours(1).toMillis() + tickMarginMillis,
            millisUntilNextHour(Instant.parse("2026-11-22T12:00:00Z"), berlin),
        )
    }

    @Test
    fun `the tick follows local hour boundaries across a daylight saving change`() {
        // Europe/Berlin switches to CET at 2026-10-25T01:00Z; local hour labels stay hourly.
        val before = Instant.parse("2026-10-25T00:30:00Z") // 02:30 CEST
        val wait = millisUntilNextHour(before, berlin) - tickMarginMillis
        assertEquals(Instant.parse("2026-10-25T01:00:00Z"), before.plusMillis(wait))

        // Australia/Lord Howe shifts by half an hour, so the wait is not always 60 minutes.
        val lordHowe = ZoneId.of("Australia/Lord_Howe")
        val atHalfHour = Instant.parse("2027-04-03T16:00:00Z") // 02:30 local, +10:30
        assertEquals(
            Duration.ofMinutes(30).toMillis() + tickMarginMillis,
            millisUntilNextHour(atHalfHour, lordHowe),
        )
    }

    @Test
    fun `the tick stays positive just after an hour boundary`() {
        // A device clock that jumps must not turn the wait into a busy loop.
        assertTrue(millisUntilNextHour(Instant.parse("2026-11-22T12:00:00.001Z"), berlin) > 0L)
        assertTrue(millisUntilNextHour(Instant.parse("2026-11-22T11:59:59.999Z"), berlin) > 0L)
    }

    // endregion

    // region cache policy

    @Test
    fun `cache within the ttl and downloaded today is fresh`() {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        assertTrue(CachePolicy.isFresh(fetchedAt, fetchedAt.plus(Duration.ofMinutes(29)), berlin))
        assertTrue(CachePolicy.isFresh(fetchedAt, fetchedAt.plus(Duration.ofMinutes(30)), berlin))
        assertFalse(CachePolicy.isFresh(fetchedAt, fetchedAt.plus(Duration.ofMinutes(31)), berlin))
        assertTrue(CachePolicy.isStale(fetchedAt, fetchedAt.plus(Duration.ofMinutes(31)), berlin))
    }

    @Test
    fun `cache from before local midnight is stale after midnight although it is young`() {
        val fetchedAt = Instant.parse("2026-11-21T22:50:00Z") // 23:50 local on 21 Nov
        val now = fetchedAt.plus(Duration.ofMinutes(15)) // 00:05 local on 22 Nov

        assertTrue(CachePolicy.age(fetchedAt, now) <= CachePolicy.freshDuration)
        assertFalse(CachePolicy.isFresh(fetchedAt, now, berlin))
        assertTrue(CachePolicy.isStale(fetchedAt, now, berlin))
    }

    @Test
    fun `freshness is judged in the location time zone, not in a single fixed zone`() {
        val fetchedAt = Instant.parse("2026-11-21T14:50:00Z")
        val now = fetchedAt.plus(Duration.ofMinutes(15))

        // In Tokyo this instant pair is 23:50 and 00:05 on the next day: stale.
        assertFalse(CachePolicy.isFresh(fetchedAt, now, tokyo))
        // In UTC both are still on 21 November: fresh.
        assertTrue(CachePolicy.isFresh(fetchedAt, now, ZoneId.of("UTC")))
    }

    @Test
    fun `the 25 hour day of an autumn transition does not break the day rule`() {
        // Berlin repeats 02:00 on 2026-10-25, so that local day spans 25 real hours.
        val startOfDay = LocalDate.of(2026, 10, 25).atStartOfDay(berlin).toInstant()
        val startOfNextDay = LocalDate.of(2026, 10, 26).atStartOfDay(berlin).toInstant()
        assertEquals(Duration.ofHours(25), Duration.between(startOfDay, startOfNextDay))

        val fetchedAt = Instant.parse("2026-10-25T22:40:00Z") // 23:40 local on 25 Oct
        val beforeMidnight = Instant.parse("2026-10-25T22:55:00Z") // 23:55 local
        val afterMidnight = Instant.parse("2026-10-25T23:05:00Z") // 00:05 local on 26 Oct

        assertTrue(CachePolicy.isFresh(fetchedAt, beforeMidnight, berlin))
        assertTrue(CachePolicy.age(fetchedAt, afterMidnight) <= CachePolicy.freshDuration)
        assertFalse(CachePolicy.isFresh(fetchedAt, afterMidnight, berlin))
    }

    @Test
    fun `the short day of a spring transition does not break the day rule`() {
        // Berlin skips 02:00 on 2027-03-28, so that local day spans 23 real hours.
        val startOfDay = LocalDate.of(2027, 3, 28).atStartOfDay(berlin).toInstant()
        val startOfNextDay = LocalDate.of(2027, 3, 29).atStartOfDay(berlin).toInstant()
        assertEquals(Duration.ofHours(23), Duration.between(startOfDay, startOfNextDay))

        val fetchedAt = Instant.parse("2027-03-28T21:50:00Z") // 23:50 local on 28 Mar (CEST)
        val afterMidnight = fetchedAt.plus(Duration.ofMinutes(15)) // 00:05 local on 29 Mar

        assertTrue(CachePolicy.isFresh(fetchedAt, fetchedAt.plus(Duration.ofMinutes(5)), berlin))
        assertTrue(CachePolicy.age(fetchedAt, afterMidnight) <= CachePolicy.freshDuration)
        assertFalse(CachePolicy.isFresh(fetchedAt, afterMidnight, berlin))
    }

    @Test
    fun `cache from the future is never fresh and old cache is flagged as outdated`() {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        assertFalse(CachePolicy.isFresh(fetchedAt, fetchedAt.minus(Duration.ofMinutes(5)), berlin))
        assertTrue(CachePolicy.isProbablyOutdated(fetchedAt, fetchedAt.plus(Duration.ofHours(4))))
        assertFalse(CachePolicy.isProbablyOutdated(fetchedAt, fetchedAt.plus(Duration.ofHours(1))))
    }

    @Test
    fun `cache stops being usable when its hourly data no longer reaches the current hour`() {
        val now = Instant.parse("2026-11-22T11:00:00Z")
        val covered = fullSnapshot(berlin, LocalDate.of(2026, 11, 22), days = 3)
        val ended = snapshot(
            berlin,
            hourlySeries(LocalDate.of(2026, 11, 20).atStartOfDay(berlin).toInstant(), 24),
        )

        assertTrue(CachePolicy.hasHourlyCoverage(covered, now))
        assertFalse(CachePolicy.hasHourlyCoverage(ended, now))
        // A snapshot without hourly data at all is not rejected here.
        assertTrue(CachePolicy.hasHourlyCoverage(snapshot(berlin), now))
    }

    // endregion

    @Test
    fun `time zone resolution falls back in a safe order`() {
        assertEquals(berlin, zoneIdOrDefault("Europe/Berlin", "UTC"))
        assertEquals(tokyo, zoneIdOrDefault(null, "Asia/Tokyo"))
        assertEquals(berlin, zoneIdOrDefault("Not/AZone", "Europe/Berlin"))
        assertEquals(ZoneId.of("UTC"), zoneIdOrDefault("Not/AZone", "Also/NotAAZone"))
    }

    /** Counts how many one hour steps are missing between consecutive window entries. */
    private fun missingHourGaps(entries: List<HourlyWindowEntry>): Int {
        var gaps = 0
        for (index in 1 until entries.size) {
            val step = Duration.between(
                entries[index - 1].hour.instant,
                entries[index].hour.instant,
            ).toHours().toInt()
            if (step != 1) gaps += step - 1
        }
        return gaps
    }
}
