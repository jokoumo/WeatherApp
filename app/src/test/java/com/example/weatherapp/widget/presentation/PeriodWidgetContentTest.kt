package com.example.weatherapp.widget.presentation

import com.example.weatherapp.domain.DayPeriod
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.widget.WidgetData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four cards of the day-period widget: which periods they are for a given moment, how each card
 * names its day, and which aggregated values it carries.
 *
 * The value rule this file guards is the one the brief calls out: a period's precipitation probability
 * is the **highest hourly value inside that period**, and the card labels it as such - the widget never
 * borrows a daily probability and never presents a maximum as a chance for the whole period.
 */
class PeriodWidgetContentTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val nb = "\u00A0"

    /** Thursday, 3 December 2026. */
    private val thursday: LocalDate = LocalDate.of(2026, 12, 3)
    private val wednesday: LocalDate = LocalDate.of(2026, 12, 2)
    private val friday: LocalDate = LocalDate.of(2026, 12, 4)

    private fun localInstant(date: LocalDate, hourOfDay: Int, zone: ZoneId = berlin): Instant =
        date.atTime(hourOfDay % 24, 0)
            .plusDays(hourOfDay / 24L)
            .atZone(zone)
            .toInstant()

    private fun hour(
        instant: Instant,
        temperature: Double? = 5.0,
        probability: Int? = 20,
        millimetres: Double? = 0.0,
        condition: WeatherCondition? = WeatherCondition.CLEAR_DAY,
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
        windSpeedKilometresPerHour = 12.0,
        windDirectionDegrees = null,
        windGustsKilometresPerHour = 30.0,
        pressureHectopascal = null,
        isDay = true,
    )

    /** Hours from Wednesday 00:00 through Saturday 23:00, so every rolling window is covered. */
    private fun hours(
        build: (Instant) -> HourlyWeather = { hour(it) },
    ): List<HourlyWeather> = (0 until 72).map { offset ->
        build(wednesday.atStartOfDay().plusHours(offset.toLong()).atZone(berlin).toInstant())
    }

    private fun data(hourly: List<HourlyWeather>, now: Instant): WidgetData = WidgetData(
        location = hamburg,
        snapshot = WeatherSnapshot(
            locationId = hamburg.id,
            fetchedAt = now,
            timeZone = berlin,
            current = null,
            hourly = hourly,
            daily = emptyList(),
        ),
        now = now,
    )

    // region states

    @Test
    fun `a widget without a location has no cards`() {
        val content = buildPeriodWidgetContent(null)

        assertEquals(WidgetState.NO_LOCATION, content.state)
        assertEquals("", content.locationName)
        assertTrue(content.periods.isEmpty())
    }

    @Test
    fun `a location without hourly values has no cards but keeps its name`() {
        val content = buildPeriodWidgetContent(data(emptyList(), localInstant(thursday, 10)))

        assertEquals(WidgetState.NO_DATA, content.state)
        assertEquals(hamburg.name, content.locationName)
        assertTrue(content.periods.isEmpty())
    }

    // endregion

    // region which periods

    @Test
    fun `in the late morning the cards are the rest of the day`() {
        val content = buildPeriodWidgetContent(data(hours(), localInstant(thursday, 10, berlin)))

        assertEquals(
            listOf(DayPeriod.MORNING, DayPeriod.AFTERNOON, DayPeriod.EVENING, DayPeriod.NIGHT),
            content.periods.map { it.period },
        )
        // The night belongs to the day it starts in, so it is still "today".
        assertEquals(
            listOf(
                WidgetDayRelation.TODAY,
                WidgetDayRelation.TODAY,
                WidgetDayRelation.TODAY,
                WidgetDayRelation.TODAY,
            ),
            content.periods.map { it.dayRelation },
        )
    }

    @Test
    fun `at three in the morning the first card is the night that began yesterday`() {
        val content = buildPeriodWidgetContent(data(hours(), localInstant(thursday, 3, berlin)))

        assertEquals(DayPeriod.NIGHT, content.periods.first().period)
        assertEquals(WidgetDayRelation.OTHER, content.periods.first().dayRelation)
        assertEquals("Mi.", content.periods.first().weekdayLabel)
        assertEquals(
            listOf(DayPeriod.MORNING, DayPeriod.AFTERNOON, DayPeriod.EVENING),
            content.periods.drop(1).map { it.period },
        )
        assertEquals(
            listOf(WidgetDayRelation.TODAY, WidgetDayRelation.TODAY, WidgetDayRelation.TODAY),
            content.periods.drop(1).map { it.dayRelation },
        )
    }

    @Test
    fun `late at night the second card is already tomorrow`() {
        val content = buildPeriodWidgetContent(data(hours(), localInstant(thursday, 23, berlin)))

        assertEquals(DayPeriod.NIGHT, content.periods[0].period)
        assertEquals(WidgetDayRelation.TODAY, content.periods[0].dayRelation)
        assertEquals(DayPeriod.MORNING, content.periods[1].period)
        assertEquals(WidgetDayRelation.TOMORROW, content.periods[1].dayRelation)
        assertEquals(DayPeriod.EVENING, content.periods[3].period)
        assertEquals(WidgetDayRelation.TOMORROW, content.periods[3].dayRelation)
    }

    @Test
    fun `every card names its local time range`() {
        val content = buildPeriodWidgetContent(data(hours(), localInstant(thursday, 10, berlin)))
        val night = content.periods.last()

        assertEquals("18", content.periods[2].startHour)
        assertEquals("22", content.periods[2].endHour)
        assertEquals("22", night.startHour)
        // The night ends at six in the morning of the next day, which is what the card has to say.
        assertEquals("06", night.endHour)
    }

    @Test
    fun `the time ranges follow the local clock of the location`() {
        // Kolkata's half-hour offset must not shift the printed hours of a period.
        val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
        val instant = localInstant(thursday, 10, kolkata)
        val content = buildPeriodWidgetContent(
            WidgetData(
                location = hamburg.copy(timeZoneId = kolkata.id),
                snapshot = WeatherSnapshot(
                    locationId = hamburg.id,
                    fetchedAt = instant,
                    timeZone = kolkata,
                    current = null,
                    hourly = (0 until 72).map { offset ->
                        hour(
                            thursday.minusDays(1).atStartOfDay().plusHours(offset.toLong()).atZone(kolkata).toInstant(),
                        )
                    },
                    daily = emptyList(),
                ),
                now = instant,
            ),
        )

        assertEquals(listOf("06", "12", "18", "22"), content.periods.map { it.startHour })
        assertEquals(listOf("12", "18", "22", "06"), content.periods.map { it.endHour })
    }

    @Test
    fun `the night across a spring transition still reads as night`() {
        // 28/29 March 2026 in Berlin: the night loses one hour, the printed range does not change.
        val instant = localInstant(LocalDate.of(2026, 3, 29), 3)
        val hourly = (0 until 72).map { offset ->
            hour(LocalDate.of(2026, 3, 28).atStartOfDay().plusHours(offset.toLong()).atZone(berlin).toInstant())
        }
        val content = buildPeriodWidgetContent(data(hourly, instant))

        assertEquals(DayPeriod.NIGHT, content.periods.first().period)
        assertEquals("22", content.periods.first().startHour)
        assertEquals("06", content.periods.first().endHour)
        assertTrue(content.periods.first().hasData)
        // The night of the transition has seven hours instead of eight, and the hourly series delivers
        // seven points for it - so the card is complete, even though its length is unusual.
        assertTrue(content.periods.first().isComplete)
    }

    // endregion

    // region values

    @Test
    fun `a period shows the highest hourly probability and the sum of its precipitation`() {
        val hourly = hours { instant ->
            val hourOfDay = instant.atZone(berlin).hour
            hour(
                instant,
                probability = if (hourOfDay == 9) 80 else 10,
                millimetres = if (hourOfDay in 6..11) 1.5 else 0.0,
            )
        }
        val morning = buildPeriodWidgetContent(data(hourly, localInstant(thursday, 10, berlin))).periods.first()

        assertEquals(80, morning.precipitationProbabilityPercent)
        // Six hours of 1.5 mm each: the sum, not a maximum.
        assertEquals(9.0, morning.precipitationMillimetres!!, 0.001)
        assertEquals("80$nb% · 9${nb}mm", morning.precipitationSummary())
    }

    @Test
    fun `a period the forecast does not reach keeps its card and says it has no data`() {
        // Only Thursday is covered, so from the night onward the windows have no hours at all.
        val thursdayOnly = (0 until 24).map { offset ->
            hour(thursday.atStartOfDay().plusHours(offset.toLong()).atZone(berlin).toInstant())
        }
        val content = buildPeriodWidgetContent(data(thursdayOnly, localInstant(thursday, 21, berlin)))

        assertEquals(4, content.periods.size)
        assertTrue(content.periods[0].hasData) // evening of Thursday
        assertTrue(content.periods[1].hasData) // night: 22 and 23 exist
        assertFalse(content.periods[2].hasData) // Friday morning: no hours
        assertFalse(content.periods[3].hasData)
        assertEquals("\u2013", content.periods[2].temperatureRange)
        assertEquals(WidgetDayRelation.TOMORROW, content.periods[2].dayRelation)
    }

    @Test
    fun `a period with data carries a condition and a temperature range`() {
        val row = buildPeriodWidgetContent(data(hours(), localInstant(thursday, 14, berlin))).periods.first()

        assertTrue(row.hasData)
        // One value, printed once - the same rule the app uses when a period does not vary.
        assertEquals("5$nb\u00B0C", row.temperatureRange)
        assertTrue(row.conditionLabelRes != 0)
        assertTrue(row.iconRes != 0)
    }

    @Test
    fun `the weekday of a card names the day the period belongs to`() {
        val content = buildPeriodWidgetContent(data(hours(), localInstant(thursday, 23, berlin)))

        // Thursday night, then Friday morning - the weekday labels have to follow the slot's date.
        assertEquals("Do.", content.periods[0].weekdayLabel)
        assertEquals("Fr.", content.periods[1].weekdayLabel)
    }

    // endregion
}
