package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.domain.model.WindWarningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The four day periods of the daily detail sheet: their absolute boundaries in the time zone of
 * the location, the aggregation of the hourly values inside them, and the representative condition
 * of a period. Every case works on fixed instants and hand-written hourly points, so neither the
 * system clock nor a live forecast can influence the result.
 */
class DayPeriodForecastTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
    private val kathmandu: ZoneId = ZoneId.of("Asia/Kathmandu")

    /** Monday, 30 November 2026 — the day used by the boundary tests. */
    private val monday: LocalDate = LocalDate.of(2026, 11, 30)

    private fun hour(
        instant: Instant,
        zone: ZoneId = berlin,
        temperature: Double? = 5.0,
        condition: WeatherCondition? = null,
        probability: Int? = null,
        millimetres: Double? = null,
        windSpeed: Double? = null,
        windGusts: Double? = null,
        isDay: Boolean? = true,
    ): HourlyWeather = HourlyWeather(
        instant = instant,
        timeZone = zone,
        temperatureCelsius = temperature,
        apparentTemperatureCelsius = null,
        weatherCode = null,
        condition = condition,
        precipitationProbabilityPercent = probability,
        precipitationMillimetres = millimetres,
        relativeHumidityPercent = null,
        cloudCoverPercent = null,
        windSpeedKilometresPerHour = windSpeed,
        windDirectionDegrees = null,
        windGustsKilometresPerHour = windGusts,
        pressureHectopascal = null,
        isDay = isDay,
    )

    /**
     * Instant of a wall clock hour of [date]; values at or above 24 continue into the following
     * calendar day, which is how the night of a day is written down.
     */
    private fun localInstant(date: LocalDate, zone: ZoneId, hourOfDay: Int): Instant =
        date.atTime(hourOfDay % 24, 0)
            .plusDays(hourOfDay / 24L)
            .atZone(zone)
            .toInstant()

    /** One point per hour from [fromHour] of [date], [count] hours long. */
    private fun series(
        date: LocalDate,
        zone: ZoneId = berlin,
        fromHour: Int = 0,
        count: Int = 24,
        build: (Instant) -> HourlyWeather = { hour(it, zone) },
    ): List<HourlyWeather> =
        (0 until count).map { offset -> build(localInstant(date, zone, fromHour + offset)) }

    /** Points at every absolute hour of [window], which is what a forecast payload delivers. */
    private fun pointsOf(window: DayPeriodWindow): List<HourlyWeather> =
        (0 until window.expectedHourCount.toLong())
            .map { hour(window.start.plus(Duration.ofHours(it))) }

    private fun period(
        periods: List<DayPeriodForecast>,
        dayPeriod: DayPeriod,
    ): DayPeriodForecast = periods.first { it.period == dayPeriod }

    private fun periods(
        hours: List<HourlyWeather>,
        date: LocalDate = monday,
        zone: ZoneId = berlin,
    ): List<DayPeriodForecast> = buildDayPeriodForecasts(hours, date, zone)

    // region period boundaries

    @Test
    fun `morning runs from 06_00 to 11_59 local time`() {
        val morning = period(periods(series(monday)), DayPeriod.MORNING)

        assertEquals(Instant.parse("2026-11-30T05:00:00Z"), morning.start) // 06:00 CET
        assertEquals(Instant.parse("2026-11-30T11:00:00Z"), morning.end) // exclusive: 12:00 CET
        assertEquals(6, morning.availableHourCount)
        assertEquals(6, morning.expectedHourCount)
        assertTrue(morning.isComplete)
    }

    @Test
    fun `afternoon runs from 12_00 to 17_59 local time`() {
        val afternoon = period(periods(series(monday)), DayPeriod.AFTERNOON)

        assertEquals(Instant.parse("2026-11-30T11:00:00Z"), afternoon.start)
        assertEquals(Instant.parse("2026-11-30T17:00:00Z"), afternoon.end)
        assertEquals(6, afternoon.availableHourCount)
    }

    @Test
    fun `evening runs from 18_00 to 21_59 local time`() {
        val evening = period(periods(series(monday)), DayPeriod.EVENING)

        assertEquals(Instant.parse("2026-11-30T17:00:00Z"), evening.start)
        assertEquals(Instant.parse("2026-11-30T21:00:00Z"), evening.end)
        assertEquals(4, evening.availableHourCount)
    }

    @Test
    fun `night runs from 22_00 of the selected day to 05_59 of the next one`() {
        val night = period(periods(series(monday, count = 48)), DayPeriod.NIGHT)

        assertEquals(Instant.parse("2026-11-30T21:00:00Z"), night.start) // 22:00 CET Monday
        assertEquals(Instant.parse("2026-12-01T05:00:00Z"), night.end) // exclusive: 06:00 CET Tuesday
        assertEquals(8, night.availableHourCount)
        assertEquals(8, night.expectedHourCount)
    }

    /** The night belongs to the day it starts in, so Tuesday's early hours are Monday's night. */
    @Test
    fun `night includes the early hours of the following calendar day`() {
        val hours = series(monday, count = 48)
        val night = period(periods(hours), DayPeriod.NIGHT)
        val inNight = hours.filter { !it.instant.isBefore(night.start) && it.instant.isBefore(night.end) }

        assertEquals(
            listOf("22:00", "23:00", "00:00", "01:00", "02:00", "03:00", "04:00", "05:00"),
            inNight.map { WeatherFormatter.clockTime(it.instant, berlin) },
        )
        assertEquals(
            "six of the eight hours are already Tuesday",
            6,
            inNight.count { it.localDate == monday.plusDays(1) },
        )
    }

    /** Monday 03:00 belongs to the night that started on Sunday, never to Monday's own night. */
    @Test
    fun `night never includes the early hours of the selected day itself`() {
        val night = period(periods(series(monday, count = 48)), DayPeriod.NIGHT)
        val earlyMonday = listOf(0, 3, 5).map { localInstant(monday, berlin, it) }

        earlyMonday.forEach { instant ->
            assertTrue("$instant must lie before Monday's night", instant.isBefore(night.start))
        }
        // Sunday's night holds them instead, which is where they belong.
        val sunday = monday.minusDays(1)
        val sundayNight = period(periods(series(sunday, count = 48), sunday), DayPeriod.NIGHT)
        assertTrue(
            earlyMonday.all { !it.isBefore(sundayNight.start) && it.isBefore(sundayNight.end) },
        )
    }

    @Test
    fun `periods are returned in reading order`() {
        assertEquals(
            listOf("MORNING", "AFTERNOON", "EVENING", "NIGHT"),
            periods(series(monday, count = 48)).map { it.period.name },
        )
    }

    // endregion

    // region aggregation

    @Test
    fun `temperature range is the smallest and the largest hourly value of the period`() {
        val temperatures = listOf(9.0, 7.0, 8.5, 12.0, 11.0, 10.0)
        val hours = temperatures.mapIndexed { index, value ->
            hour(localInstant(monday, berlin, 6 + index), temperature = value)
        } + hour(localInstant(monday, berlin, 12), temperature = 3.5)
        val all = periods(hours)

        val morning = period(all, DayPeriod.MORNING)
        assertEquals(7.0, morning.minTemperatureCelsius!!, 0.001)
        assertEquals(12.0, morning.maxTemperatureCelsius!!, 0.001)
        // The 12:00 point lies outside the morning and must not widen its range.
        assertEquals(3.5, period(all, DayPeriod.AFTERNOON).minTemperatureCelsius!!, 0.001)
    }

    @Test
    fun `precipitation probability is the highest hourly value and never a combined formula`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), probability = 20),
            hour(localInstant(monday, berlin, 7), probability = 70),
            hour(localInstant(monday, berlin, 8), probability = 40),
            hour(localInstant(monday, berlin, 9), probability = null),
        )

        // 1 - (0.8 * 0.3 * 0.6) would be 85.6 %; the honest statement is the maximum.
        assertEquals(70, period(periods(hours), DayPeriod.MORNING).maxPrecipitationProbabilityPercent)
    }

    @Test
    fun `precipitation amount is the sum of the hourly amounts inside the period`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), millimetres = 0.2),
            hour(localInstant(monday, berlin, 7), millimetres = 1.8),
            hour(localInstant(monday, berlin, 8), millimetres = 0.0),
            hour(localInstant(monday, berlin, 9), millimetres = null),
            // Outside the period: must not be counted.
            hour(localInstant(monday, berlin, 12), millimetres = 9.0),
        )

        assertEquals(2.0, period(periods(hours), DayPeriod.MORNING).precipitationMillimetres!!, 0.0001)
    }

    @Test
    fun `a period of dry hours reports zero instead of missing data`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), millimetres = 0.0),
            hour(localInstant(monday, berlin, 7), millimetres = 0.0),
            hour(localInstant(monday, berlin, 8), millimetres = 0.0),
        )
        val morning = period(periods(hours), DayPeriod.MORNING)

        // A reported zero is a measurement; only a genuinely missing amount renders as an en dash.
        assertEquals(0.0, morning.precipitationMillimetres!!, 0.0001)
    }

    @Test
    fun `wind speed is the mean of the sustained hourly values`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), windSpeed = 10.0, windGusts = 30.0),
            hour(localInstant(monday, berlin, 7), windSpeed = 20.0, windGusts = 45.0),
            hour(localInstant(monday, berlin, 8), windSpeed = null, windGusts = null),
            hour(localInstant(monday, berlin, 9), windSpeed = Double.NaN, windGusts = -4.0),
        )
        val morning = period(periods(hours), DayPeriod.MORNING)

        assertEquals(15.0, morning.averageWindSpeedKilometresPerHour!!, 0.001)
        assertEquals(20.0, morning.maximumWindSpeedKilometresPerHour!!, 0.001)
        assertEquals(45.0, morning.maximumWindGustsKilometresPerHour!!, 0.001)
    }

    @Test
    fun `gusts stay separate from sustained wind in the warning`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), windSpeed = 20.0, windGusts = 60.0),
            hour(localInstant(monday, berlin, 7), windSpeed = 22.0, windGusts = 55.0),
        )
        val morning = period(periods(hours), DayPeriod.MORNING)

        assertEquals(WindWarningLevel.STRONG, morning.windWarning.level)
        assertTrue("the warning must be attributed to the gusts", morning.windWarning.causedByGusts)
    }

    @Test
    fun `missing temperatures stay missing instead of becoming zero`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), temperature = null),
            hour(localInstant(monday, berlin, 7), temperature = null),
        )
        val morning = period(periods(hours), DayPeriod.MORNING)

        assertNull(morning.minTemperatureCelsius)
        assertNull(morning.maxTemperatureCelsius)
        assertTrue("the period itself is still available", morning.hasData)
    }

    @Test
    fun `a range with one known temperature is not invented as a spread`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), temperature = null),
            hour(localInstant(monday, berlin, 7), temperature = 7.4),
        )
        val morning = period(periods(hours), DayPeriod.MORNING)

        assertEquals(7.4, morning.minTemperatureCelsius!!, 0.001)
        assertEquals(7.4, morning.maxTemperatureCelsius!!, 0.001)
        assertEquals("7\u00A0\u00B0C", WeatherFormatter.temperatureRange(7.4, 7.4))
        assertEquals("7\u201312\u00A0\u00B0C", WeatherFormatter.temperatureRange(7.4, 11.6))
        assertEquals("\u2013", WeatherFormatter.temperatureRange(null, null))
        assertEquals("7\u00A0\u00B0C", WeatherFormatter.temperatureRange(null, 7.4))
    }

    @Test
    fun `missing precipitation stays unknown instead of reading as no precipitation`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), millimetres = null, probability = null),
            hour(localInstant(monday, berlin, 7), millimetres = null, probability = null),
        )
        val morning = period(periods(hours), DayPeriod.MORNING)

        assertNull(morning.precipitationMillimetres)
        assertNull(morning.maxPrecipitationProbabilityPercent)
    }

    @Test
    fun `missing weather conditions leave the condition unknown`() {
        val hours = series(monday, fromHour = 6, count = 6, build = { hour(it, condition = null) })
        val morning = period(periods(hours), DayPeriod.MORNING)

        assertNull(morning.representativeCondition)
        assertNull(morning.severeCondition)
        assertEquals(6, morning.availableHourCount)
        assertEquals(5.0, morning.minTemperatureCelsius!!, 0.001)
    }

    @Test
    fun `a period without any hourly point reports itself as unavailable`() {
        val morning = period(periods(series(monday, fromHour = 12, count = 12)), DayPeriod.MORNING)

        assertFalse(morning.hasData)
        assertEquals(0, morning.availableHourCount)
        assertEquals(6, morning.expectedHourCount)
        assertNull(morning.minTemperatureCelsius)
        assertNull(morning.precipitationMillimetres)
        assertNull(morning.averageWindSpeedKilometresPerHour)
        assertNull(morning.representativeCondition)
    }

    @Test
    fun `incomplete hourly data is marked so a partial sum is not read as a total`() {
        val hours = listOf(
            hour(localInstant(monday, berlin, 6), millimetres = 1.0),
            hour(localInstant(monday, berlin, 7), millimetres = 1.0),
        )
        val morning = period(periods(hours), DayPeriod.MORNING)

        assertEquals(2, morning.availableHourCount)
        assertEquals(6, morning.expectedHourCount)
        assertFalse(morning.isComplete)
        assertEquals(2.0, morning.precipitationMillimetres!!, 0.0001)
    }

    @Test
    fun `the result does not depend on the order of the hourly payload`() {
        val hours = series(
            monday,
            fromHour = 6,
            count = 6,
            build = { instant ->
                hour(
                    instant,
                    temperature = 2.0,
                    condition = WeatherCondition.OVERCAST,
                    millimetres = 0.5,
                    windSpeed = 12.0,
                    windGusts = 30.0,
                )
            },
        )

        assertEquals(periods(hours), periods(hours.reversed()))
    }

    // endregion

    // region time zones and daylight saving

    /**
     * Spring 2026 in Berlin: the night that runs into 29 March is one hour short, because the
     * clock jumps from 02:00 to 03:00 while it is already underway.
     */
    @Test
    fun `night across the spring transition holds seven hours and stays complete`() {
        val nightOf = LocalDate.of(2026, 3, 28) // Saturday 22:00 into Sunday morning
        val window = dayPeriodWindow(nightOf, berlin, DayPeriod.NIGHT)

        assertEquals(Instant.parse("2026-03-28T21:00:00Z"), window.start) // 22:00 CET Saturday
        assertEquals(Instant.parse("2026-03-29T04:00:00Z"), window.end) // 06:00 CEST Sunday
        assertEquals(7L, Duration.between(window.start, window.end).toHours())
        assertEquals(7, window.expectedHourCount)

        val night = period(periods(pointsOf(window), nightOf), DayPeriod.NIGHT)
        assertEquals(7, night.availableHourCount)
        assertTrue(night.isComplete)
    }

    /**
     * Autumn 2026 in Berlin: the night that runs into 25 October repeats 02:00 and therefore
     * holds nine hourly points, both of them on the same calendar day.
     */
    @Test
    fun `night across the autumn transition counts both occurrences of the repeated hour`() {
        val nightOf = LocalDate.of(2026, 10, 24) // Saturday 22:00 into Sunday morning
        val window = dayPeriodWindow(nightOf, berlin, DayPeriod.NIGHT)

        assertEquals(Instant.parse("2026-10-24T20:00:00Z"), window.start) // 22:00 CEST Saturday
        assertEquals(Instant.parse("2026-10-25T05:00:00Z"), window.end) // 06:00 CET Sunday
        assertEquals(9L, Duration.between(window.start, window.end).toHours())
        assertEquals(9, window.expectedHourCount)

        val night = period(periods(pointsOf(window), nightOf), DayPeriod.NIGHT)
        val clockTimes = (0 until 9L).map {
            WeatherFormatter.clockTime(window.start.plus(Duration.ofHours(it)), berlin)
        }

        assertEquals(9, night.availableHourCount)
        assertTrue(night.isComplete)
        assertEquals(2, clockTimes.count { it == "02:00" })
    }

    @Test
    fun `boundaries follow a half hour offset zone`() {
        val day = LocalDate.of(2026, 11, 30)
        val morning = dayPeriodWindow(day, kolkata, DayPeriod.MORNING)
        val night = dayPeriodWindow(day, kolkata, DayPeriod.NIGHT)

        assertEquals(Instant.parse("2026-11-30T00:30:00Z"), morning.start) // 06:00 +05:30
        assertEquals(Instant.parse("2026-11-30T06:30:00Z"), morning.end)
        assertEquals(Instant.parse("2026-11-30T16:30:00Z"), night.start) // 22:00 +05:30
        assertEquals(Instant.parse("2026-12-01T00:30:00Z"), night.end) // 06:00 +05:30 the next day
    }

    @Test
    fun `boundaries follow a quarter hour offset zone`() {
        val evening = dayPeriodWindow(LocalDate.of(2026, 11, 30), kathmandu, DayPeriod.EVENING)

        assertEquals(Instant.parse("2026-11-30T12:15:00Z"), evening.start) // 18:00 +05:45
        assertEquals(Instant.parse("2026-11-30T16:15:00Z"), evening.end)
    }

    /** A location on the other side of the planet keeps its own calendar day. */
    @Test
    fun `periods are calendar periods of the location and not of the device`() {
        val morning = dayPeriodWindow(monday, ZoneId.of("Asia/Tokyo"), DayPeriod.MORNING)

        assertEquals(Instant.parse("2026-11-29T21:00:00Z"), morning.start) // 06:00 JST on the 30th
        assertEquals(Instant.parse("2026-11-30T03:00:00Z"), morning.end)
    }

    // endregion

    // region representative condition

    private fun withConditions(vararg conditions: WeatherCondition): List<HourlyWeather> =
        conditions.mapIndexed { index, condition ->
            hour(localInstant(monday, berlin, 6 + index), condition = condition)
        }

    @Test
    fun `the condition that fills the period represents it`() {
        val mostlyCloudy = withConditions(
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
            WeatherCondition.LIGHT_RAIN,
            WeatherCondition.LIGHT_RAIN,
        )
        assertEquals(WeatherCondition.OVERCAST, representativeCondition(mostlyCloudy))

        val mostlyRain = withConditions(
            WeatherCondition.LIGHT_RAIN,
            WeatherCondition.LIGHT_RAIN,
            WeatherCondition.LIGHT_RAIN,
            WeatherCondition.LIGHT_RAIN,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
        )
        assertEquals(WeatherCondition.LIGHT_RAIN, representativeCondition(mostlyRain))
    }

    @Test
    fun `related conditions add up instead of splitting the count`() {
        val mixedRain = withConditions(
            WeatherCondition.LIGHT_RAIN,
            WeatherCondition.RAIN,
            WeatherCondition.RAIN_SHOWERS,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
        )
        val representative = representativeCondition(mixedRain)

        assertTrue(
            "expected a rain condition, got $representative",
            representative == WeatherCondition.LIGHT_RAIN ||
                representative == WeatherCondition.RAIN ||
                representative == WeatherCondition.RAIN_SHOWERS,
        )
    }

    @Test
    fun `heavy precipitation counts heavier than an equal number of quiet hours`() {
        val twoHeavyAgainstThreeClear = withConditions(
            WeatherCondition.HEAVY_RAIN,
            WeatherCondition.HEAVY_RAIN,
            WeatherCondition.CLEAR_DAY,
            WeatherCondition.CLEAR_DAY,
            WeatherCondition.CLEAR_DAY,
        )

        assertEquals(WeatherCondition.HEAVY_RAIN, representativeCondition(twoHeavyAgainstThreeClear))
    }

    @Test
    fun `a brief thunderstorm is named without taking over the whole period`() {
        val mostlyCloudyWithOneStorm = withConditions(
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
            WeatherCondition.THUNDERSTORM,
        )
        val morning = period(periods(mostlyCloudyWithOneStorm), DayPeriod.MORNING)

        assertEquals(WeatherCondition.OVERCAST, morning.representativeCondition)
        assertEquals(WeatherCondition.THUNDERSTORM, morning.severeCondition)
        assertTrue(morning.hasSeparateSevereCondition)
    }

    @Test
    fun `a period that is mostly thunderstorm represents thunderstorm`() {
        val stormy = withConditions(
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.OVERCAST,
            WeatherCondition.OVERCAST,
        )
        val morning = period(periods(stormy), DayPeriod.MORNING)

        assertEquals(WeatherCondition.THUNDERSTORM, morning.representativeCondition)
        assertEquals(WeatherCondition.THUNDERSTORM, morning.severeCondition)
        assertFalse(
            "no separate hint is needed when the symbol already shows it",
            morning.hasSeparateSevereCondition,
        )
    }

    @Test
    fun `the strongest hail storm is the one that is reported`() {
        val hours = withConditions(
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.THUNDERSTORM_WITH_HAIL,
            WeatherCondition.SEVERE_THUNDERSTORM_WITH_HAIL,
        )

        assertEquals(
            WeatherCondition.SEVERE_THUNDERSTORM_WITH_HAIL,
            severeCondition(hours),
        )
    }

    @Test
    fun `the night period shows the night variant of a clear sky`() {
        val hours = series(
            monday,
            fromHour = 22,
            count = 8,
            build = { instant -> hour(instant, condition = WeatherCondition.CLEAR_NIGHT, isDay = false) },
        )
        val night = period(periods(hours), DayPeriod.NIGHT)

        assertEquals(WeatherCondition.CLEAR_NIGHT, night.representativeCondition)
    }

    @Test
    fun `a clear day period keeps the sun instead of the moon`() {
        val hours = withConditions(
            WeatherCondition.CLEAR_NIGHT,
            WeatherCondition.CLEAR_DAY,
            WeatherCondition.CLEAR_DAY,
            WeatherCondition.CLEAR_DAY,
        )

        assertEquals(WeatherCondition.CLEAR_DAY, representativeCondition(hours))
    }

    @Test
    fun `unknown conditions carry no vote`() {
        val hours = withConditions(
            WeatherCondition.UNKNOWN,
            WeatherCondition.UNKNOWN,
            WeatherCondition.UNKNOWN,
        )

        assertNull(representativeCondition(hours))
        assertNull(severeCondition(hours))
    }

    // endregion

    // region complete hourly data

    /**
     * The sheet must work on the whole downloaded series: the rolling strip only holds 24 hours,
     * so using it would leave the day after tomorrow empty even though the response has its hours.
     */
    @Test
    fun `the day after tomorrow is complete when the full hourly series is used`() {
        val start = LocalDate.of(2026, 11, 22)
        val full = series(start, count = 96) // four days
        val dayAfterTomorrow = start.plusDays(2)
        val allPeriods = periods(full, dayAfterTomorrow)

        assertEquals(listOf(6, 6, 4, 8), allPeriods.map { it.availableHourCount })
        assertTrue(allPeriods.all { it.hasData && it.isComplete })

        // The same aggregation on a 24 hour rolling window would leave the day empty.
        val rollingOnly = full.take(24)
        assertTrue(
            "a rolling window must not be used for the day after tomorrow",
            periods(rollingOnly, dayAfterTomorrow).none { it.hasData },
        )
    }

    @Test
    fun `forecast sections carry the complete hourly series`() {
        val reference = Instant.parse("2026-11-22T11:00:00Z")
        val snapshot = WeatherSnapshot(
            locationId = "loc-1",
            fetchedAt = reference,
            timeZone = berlin,
            current = null,
            hourly = series(LocalDate.of(2026, 11, 22), count = 72),
            daily = emptyList(),
        )
        val sections = buildForecastSections(snapshot = snapshot, referenceInstant = reference)

        assertEquals(72, sections.allHours.size)
        assertEquals(24, sections.hourlyEntries.size)
        assertEquals(
            sections.allHours.map { it.instant }.sorted(),
            sections.allHours.map { it.instant },
        )
    }

    // endregion
}
