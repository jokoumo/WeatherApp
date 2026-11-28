package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The rolling four day periods the day-period widget is built from: which period contains a given
 * instant in the time zone of the location, which four periods follow each other from there, and how
 * those slots are aggregated.
 *
 * Everything works on fixed instants and hand-written hourly points, so neither the machine clock nor
 * a live forecast can influence the result. The boundary hours are tested on both sides because the
 * whole feature is about boundaries: a widget that renders the wrong period for ten minutes around a
 * period change is wrong in a way a screenshot cannot show.
 */
class RollingDayPeriodsTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
    private val kathmandu: ZoneId = ZoneId.of("Asia/Kathmandu")
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    /** Monday, 30 November 2026 - an ordinary week without transitions. */
    private val monday: LocalDate = LocalDate.of(2026, 11, 30)

    /** Tuesday, 1 December 2026. */
    private val tuesday: LocalDate = LocalDate.of(2026, 12, 1)

    private fun localInstant(date: LocalDate, zone: ZoneId, hourOfDay: Int, minute: Int = 0): Instant =
        date.atTime(hourOfDay % 24, minute)
            .plusDays(hourOfDay / 24L)
            .atZone(zone)
            .toInstant()

    private fun hour(
        instant: Instant,
        temperature: Double? = 5.0,
        condition: WeatherCondition? = WeatherCondition.CLEAR_DAY,
        probability: Int? = null,
        millimetres: Double? = null,
        windGusts: Double? = null,
    ): HourlyWeather = HourlyWeather(
        instant = instant,
        timeZone = berlin,
        temperatureCelsius = temperature,
        apparentTemperatureCelsius = null,
        weatherCode = null,
        condition = condition,
        precipitationProbabilityPercent = probability,
        precipitationMillimetres = millimetres,
        relativeHumidityPercent = null,
        cloudCoverPercent = null,
        windSpeedKilometresPerHour = 10.0,
        windDirectionDegrees = null,
        windGustsKilometresPerHour = windGusts,
        pressureHectopascal = null,
        isDay = true,
    )

    /** One point per hour of [date], which is what a full forecast payload delivers. */
    private fun fullDay(date: LocalDate, zone: ZoneId = berlin): List<HourlyWeather> =
        (0 until 24).map { offset ->
            hour(localInstant(date, zone, offset), probability = 10 + offset)
        }

    // region which period contains the instant

    @Test
    fun `boundary hours belong to the period that starts at them`() {
        assertEquals(DayPeriodSlot(monday, DayPeriod.MORNING), currentDayPeriodSlot(localInstant(monday, berlin, 6), berlin))
        assertEquals(DayPeriodSlot(monday, DayPeriod.MORNING), currentDayPeriodSlot(localInstant(monday, berlin, 11, 59), berlin))
        assertEquals(DayPeriodSlot(monday, DayPeriod.AFTERNOON), currentDayPeriodSlot(localInstant(monday, berlin, 12), berlin))
        assertEquals(DayPeriodSlot(monday, DayPeriod.AFTERNOON), currentDayPeriodSlot(localInstant(monday, berlin, 17, 59), berlin))
        assertEquals(DayPeriodSlot(monday, DayPeriod.EVENING), currentDayPeriodSlot(localInstant(monday, berlin, 18), berlin))
        assertEquals(DayPeriodSlot(monday, DayPeriod.EVENING), currentDayPeriodSlot(localInstant(monday, berlin, 21, 59), berlin))
        assertEquals(DayPeriodSlot(monday, DayPeriod.NIGHT), currentDayPeriodSlot(localInstant(monday, berlin, 22), berlin))
        assertEquals(DayPeriodSlot(monday, DayPeriod.NIGHT), currentDayPeriodSlot(localInstant(monday, berlin, 23, 59), berlin))
    }

    @Test
    fun `hours before six in the morning belong to the night that started the day before`() {
        assertEquals(DayPeriodSlot(monday.minusDays(1), DayPeriod.NIGHT), currentDayPeriodSlot(localInstant(monday, berlin, 0), berlin))
        assertEquals(DayPeriodSlot(monday.minusDays(1), DayPeriod.NIGHT), currentDayPeriodSlot(localInstant(monday, berlin, 3), berlin))
        assertEquals(DayPeriodSlot(monday.minusDays(1), DayPeriod.NIGHT), currentDayPeriodSlot(localInstant(monday, berlin, 5, 59), berlin))
    }

    @Test
    fun `the night is never split in two at midnight`() {
        // 23:00 and 03:00 are the same night, so the slot must name the same calendar day.
        val beforeMidnight = currentDayPeriodSlot(localInstant(monday, berlin, 23), berlin)
        val afterMidnight = currentDayPeriodSlot(localInstant(monday, berlin, 27), berlin)
        assertEquals(beforeMidnight, afterMidnight)
    }

    // endregion

    // region the rolling sequence

    @Test
    fun `four periods from the evening run into the next day`() {
        assertEquals(
            listOf(
                DayPeriodSlot(monday, DayPeriod.EVENING),
                DayPeriodSlot(monday, DayPeriod.NIGHT),
                DayPeriodSlot(tuesday, DayPeriod.MORNING),
                DayPeriodSlot(tuesday, DayPeriod.AFTERNOON),
            ),
            rollingDayPeriodSlots(localInstant(monday, berlin, 19), berlin),
        )
    }

    @Test
    fun `four periods from three in the morning start with the night that began yesterday`() {
        assertEquals(
            listOf(
                DayPeriodSlot(monday.minusDays(1), DayPeriod.NIGHT),
                DayPeriodSlot(monday, DayPeriod.MORNING),
                DayPeriodSlot(monday, DayPeriod.AFTERNOON),
                DayPeriodSlot(monday, DayPeriod.EVENING),
            ),
            rollingDayPeriodSlots(localInstant(monday, berlin, 3), berlin),
        )
    }

    @Test
    fun `four periods from late at night wrap into the following day`() {
        assertEquals(
            listOf(
                DayPeriodSlot(monday, DayPeriod.NIGHT),
                DayPeriodSlot(tuesday, DayPeriod.MORNING),
                DayPeriodSlot(tuesday, DayPeriod.AFTERNOON),
                DayPeriodSlot(tuesday, DayPeriod.EVENING),
            ),
            rollingDayPeriodSlots(localInstant(monday, berlin, 23), berlin),
        )
    }

    @Test
    fun `the sequence always runs Vormittag Nachmittag Abend Nacht in that order`() {
        val slots = rollingDayPeriodSlots(localInstant(monday, berlin, 9), berlin, count = 9)
        assertEquals(
            listOf(
                DayPeriod.MORNING, DayPeriod.AFTERNOON, DayPeriod.EVENING, DayPeriod.NIGHT,
                DayPeriod.MORNING, DayPeriod.AFTERNOON, DayPeriod.EVENING, DayPeriod.NIGHT,
                DayPeriod.MORNING,
            ),
            slots.map { it.period },
        )
        // Nine slots from a morning therefore end three days later, and every day appears once.
        assertEquals(
            listOf(monday, monday, monday, monday, monday.plusDays(1), monday.plusDays(1), monday.plusDays(1), monday.plusDays(1), monday.plusDays(2)),
            slots.map { it.date },
        )
    }

    @Test
    fun `a rolling window never skips a period between four consecutive slots`() {
        // For every start hour of a day, consecutive slots have to be neighbours in the period cycle.
        (0 until 24).forEach { startHour ->
            val slots = rollingDayPeriodSlots(localInstant(monday, berlin, startHour), berlin)
            (0 until slots.size - 1).forEach { index ->
                val windows = dayPeriodWindow(slots[index].date, berlin, slots[index].period)
                val next = dayPeriodWindow(slots[index + 1].date, berlin, slots[index + 1].period)
                assertEquals(
                    "hour $startHour: period ${slots[index].period} of ${slots[index].date} must end where ${slots[index + 1].period} starts",
                    next.start,
                    windows.end,
                )
            }
        }
    }

    // endregion

    // region time zones and daylight saving

    @Test
    fun `the slot follows the location time zone, not one shared absolute offset`() {
        val instant = localInstant(monday, berlin, 3)
        // The same instant is 11:00 in Tokyo and 08:30 in Kolkata: three different periods.
        assertEquals(DayPeriodSlot(monday.minusDays(1), DayPeriod.NIGHT), currentDayPeriodSlot(instant, berlin))
        assertEquals(DayPeriodSlot(monday, DayPeriod.MORNING), currentDayPeriodSlot(instant, tokyo))
        assertEquals(DayPeriodSlot(monday, DayPeriod.MORNING), currentDayPeriodSlot(instant, kolkata))
    }

    @Test
    fun `a zone with a fractional offset is cut at its own wall clock hours`() {
        // 05:45 local in Kathmandu is still the night of the day before; 06:00 local is morning.
        val stillNight = localInstant(monday, kathmandu, 5, 45)
        val morning = localInstant(monday, kathmandu, 6)
        assertEquals(DayPeriodSlot(monday.minusDays(1), DayPeriod.NIGHT), currentDayPeriodSlot(stillNight, kathmandu))
        assertEquals(DayPeriodSlot(monday, DayPeriod.MORNING), currentDayPeriodSlot(morning, kathmandu))
        // Kolkata's half hour must not bend the window either: the night ends at 06:00 local time.
        assertEquals(
            localInstant(monday, kolkata, 30),
            dayPeriodWindow(monday, kolkata, DayPeriod.NIGHT).end,
        )
    }

    @Test
    fun `the spring transition keeps the night and the morning adjacent`() {
        // Europe/Berlin, 29 March 2026: 02:00 standard time becomes 03:00 daylight time.
        val transitionZone = berlin
        val transitionDay = LocalDate.of(2026, 3, 29)
        val beforeTransition = Instant.parse("2026-03-29T00:59:00Z") // 01:59 local standard time
        val afterTransition = Instant.parse("2026-03-29T01:00:00Z") // 03:00 local daylight time

        // The hour that the clock jumps over is inside the night, so the night is neither split nor
        // shifted: both sides of the transition belong to the night that started on the 28th.
        assertEquals(
            DayPeriodSlot(transitionDay.minusDays(1), DayPeriod.NIGHT),
            currentDayPeriodSlot(beforeTransition, transitionZone),
        )
        assertEquals(
            DayPeriodSlot(transitionDay.minusDays(1), DayPeriod.NIGHT),
            currentDayPeriodSlot(afterTransition, transitionZone),
        )
        // The morning of the transition day begins at 06:00 daylight time, one hour earlier in UTC.
        assertEquals(
            DayPeriodSlot(transitionDay, DayPeriod.MORNING),
            currentDayPeriodSlot(Instant.parse("2026-03-29T04:00:00Z"), transitionZone),
        )
        // The night that contains the missing hour is one hour shorter, and the rolling window still
        // hands over exactly where the morning begins.
        val night = dayPeriodWindow(transitionDay.minusDays(1), transitionZone, DayPeriod.NIGHT)
        assertEquals(7, night.expectedHourCount)
        assertEquals(dayPeriodWindow(transitionDay, transitionZone, DayPeriod.MORNING).start, night.end)
    }

    @Test
    fun `the autumn transition names the repeated hour by the night it belongs to`() {
        // Europe/Berlin, 25 October 2026: 03:00 daylight time becomes 02:00 standard time, so 02:30
        // happens twice. Both occurrences belong to the night that started on the 24th.
        val transitionDay = LocalDate.of(2026, 10, 25)
        val firstOccurrence = Instant.parse("2026-10-25T00:30:00Z") // 02:30 daylight time
        val secondOccurrence = Instant.parse("2026-10-25T01:30:00Z") // 02:30 standard time

        assertEquals(
            DayPeriodSlot(transitionDay.minusDays(1), DayPeriod.NIGHT),
            currentDayPeriodSlot(firstOccurrence, berlin),
        )
        assertEquals(
            DayPeriodSlot(transitionDay.minusDays(1), DayPeriod.NIGHT),
            currentDayPeriodSlot(secondOccurrence, berlin),
        )
        // The night that spans the fall-back has one hour more than a normal night.
        assertEquals(
            9,
            dayPeriodWindow(transitionDay.minusDays(1), berlin, DayPeriod.NIGHT).expectedHourCount,
        )
    }

    @Test
    fun `a rolling window across the spring transition has no gap`() {
        val transitionDay = LocalDate.of(2026, 3, 29)
        val slots = rollingDayPeriodSlots(Instant.parse("2026-03-29T00:30:00Z"), berlin)
        assertEquals(4, slots.size)
        (0 until slots.size - 1).forEach { index ->
            assertEquals(
                dayPeriodWindow(slots[index + 1].date, berlin, slots[index + 1].period).start,
                dayPeriodWindow(slots[index].date, berlin, slots[index].period).end,
            )
        }
    }

    // endregion

    // region aggregation through the existing day period builder

    @Test
    fun `every rolling period is aggregated by the same code as the daily detail sheet`() {
        val hours = fullDay(monday, berlin) + fullDay(monday.plusDays(1), berlin)
        val instant = localInstant(monday, berlin, 19)
        val rolling = buildRollingDayPeriods(hours, instant, berlin)

        rolling.forEach { period ->
            assertEquals(
                "slot ${period.slot} has to be aggregated exactly like the detail sheet does",
                buildDayPeriodForecast(hours, period.slot.date, berlin, period.slot.period),
                period.forecast,
            )
        }
    }

    @Test
    fun `the precipitation probability of a period stays the highest hourly value`() {
        // 5 % early in the morning, 80 % at 10:00. A daily value would be neither of those, and the
        // widget must never present a maximum of hourly values as something else.
        val hours = (0 until 24).map { offset ->
            hour(localInstant(monday, berlin, offset), probability = if (offset == 10) 80 else 5)
        }
        val morning = buildRollingDayPeriodForecasts(hours, localInstant(monday, berlin, 9), berlin).first()

        assertEquals(DayPeriod.MORNING, morning.period)
        assertEquals(80, morning.maxPrecipitationProbabilityPercent)
        assertEquals(6, morning.availableHourCount)
        assertTrue(morning.isComplete)
    }

    @Test
    fun `a period beyond the forecast keeps its slot and reports that it has no data`() {
        // Only Monday is covered: from Monday evening the window runs into a day without hours.
        val rolling = buildRollingDayPeriods(fullDay(monday, berlin), localInstant(monday, berlin, 19), berlin)

        assertEquals(4, rolling.size)
        assertTrue(rolling[0].forecast.hasData) // evening of Monday: hours 18 to 23 exist
        // The night of Monday reaches until Tuesday 06:00. Half of it is covered, so the period has
        // data - marked as incomplete - instead of pretending the whole night is known.
        assertTrue(rolling[1].forecast.hasData)
        assertFalse(rolling[1].forecast.isComplete)
        assertFalse(rolling[2].forecast.hasData)
        assertFalse(rolling[3].forecast.hasData)
        // The slot is still the right one, which is what lets the layout say which period is missing.
        assertEquals(DayPeriodSlot(tuesday, DayPeriod.MORNING), rolling[2].slot)
    }

    @Test
    fun `hours given in any order are aggregated the same way`() {
        val hours = fullDay(monday, berlin)
        val forward = buildRollingDayPeriodForecasts(hours, localInstant(monday, berlin, 7), berlin)
        val shuffled = buildRollingDayPeriodForecasts(hours.reversed(), localInstant(monday, berlin, 7), berlin)

        assertEquals(forward, shuffled)
    }

    // endregion
}
