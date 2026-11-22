package com.example.weatherapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * German formatting rules used across the UI: units, decimal comma, non breaking spaces,
 * thousands separator, 24-hour clock, German weekday names and the en dash placeholder for
 * missing values.
 */
class WeatherFormatterTest {

    private val formatter = WeatherFormatter
    private val berlin = ZoneId.of("Europe/Berlin")

    /** Non breaking space between number and unit, as the formatter inserts it. */
    private val nb: String = "\u00A0"

    @Test
    fun `temperature is rounded and keeps the unit`() {
        assertEquals("6$nb°C", formatter.temperature(6.4))
        // Exact halves go to the even value, matching the German number format.
        assertEquals("6$nb°C", formatter.temperature(6.5))
        assertEquals("7$nb°C", formatter.temperature(6.6))
        assertEquals("-3$nb°C", formatter.temperature(-3.4))
    }

    @Test
    fun `temperatures close to zero never render as negative zero`() {
        assertEquals("0$nb°C", formatter.temperature(-0.4))
        assertEquals("0", formatter.temperatureValue(-0.2))
        assertEquals("-1$nb°C", formatter.temperature(-0.6))
    }

    @Test
    fun `missing values render as en dash instead of a placeholder number`() {
        assertEquals("–", formatter.temperature(null))
        assertEquals("–", formatter.temperatureValue(null))
        assertEquals("–", formatter.millimetres(null))
        assertEquals("–", formatter.percent(null as Int?))
        assertEquals("–", formatter.windSpeed(null))
        assertEquals("–", formatter.pressure(null))
    }

    @Test
    fun `precipitation uses one decimal only for small amounts`() {
        assertEquals("0,4${nb}mm", formatter.millimetres(0.4))
        assertEquals("0${nb}mm", formatter.millimetres(0.0))
        assertEquals("12${nb}mm", formatter.millimetres(12.4))
    }

    @Test
    fun `percent wind speed and pressure use the expected units`() {
        assertEquals("45$nb%", formatter.percent(45))
        assertEquals("11${nb}km/h", formatter.windSpeed(11.2))
        // German grouping separator is a full stop.
        assertEquals("1.013${nb}hPa", formatter.pressure(1013.4))
    }

    @Test
    fun `clock uses the 24-hour format`() {
        assertEquals("14:05", formatter.clockTime(LocalDateTime.of(2026, 11, 22, 14, 5)))
        assertEquals("00:00", formatter.clockTime(LocalDateTime.of(2026, 11, 22, 0, 0)))
    }

    @Test
    fun `instants are rendered in the location time zone including daylight saving`() {
        // Winter time in Europe/Berlin is UTC+1, summer time is UTC+2.
        assertEquals("14:00", formatter.clockTime(Instant.parse("2026-11-22T13:00:00Z"), berlin))
        assertEquals("15:00", formatter.clockTime(Instant.parse("2026-06-22T13:00:00Z"), berlin))
        assertEquals(
            "Sonntag, 22. November 2026, 12:15",
            formatter.fullDateTime(Instant.parse("2026-11-22T11:15:00Z"), berlin),
        )
    }

    @Test
    fun `dates are formatted in German`() {
        val date = LocalDate.of(2026, 11, 22)
        assertEquals("So., 22. November", formatter.weekdayAndDate(date))
        assertEquals("So., 22. Nov.", formatter.weekdayAndShortDate(date))
        assertEquals("22.11.", formatter.shortDate(date))
        assertEquals("Sonntag, 22. November 2026", formatter.fullDate(date))
        assertEquals("So.", formatter.weekday(date))
    }

    @Test
    fun `durations use hours and minutes`() {
        assertEquals("6 h 12 min", formatter.duration(22_320))
        assertEquals("1 h", formatter.duration(3_600))
        assertEquals("20 min", formatter.duration(1_200))
        assertEquals("–", formatter.duration(null))
        assertEquals("–", formatter.duration(0))
    }

    @Test
    fun `relative age of data is phrased in German`() {
        val now = Instant.parse("2026-11-22T12:00:00Z")
        assertEquals("gerade eben", formatter.relativeTime(now.minusSeconds(30), now))
        assertEquals("vor 12 Minuten", formatter.relativeTime(now.minusSeconds(12 * 60), now))
        assertEquals("vor 3 Stunden", formatter.relativeTime(now.minusSeconds(3 * 3600), now))
        assertEquals("gestern", formatter.relativeTime(now.minusSeconds(26 * 3600), now))
        assertEquals("vor 4 Tagen", formatter.relativeTime(now.minusSeconds(4 * 24 * 3600), now))
    }

    @Test
    fun `wind direction uses a sixteen point compass`() {
        assertEquals("N", formatter.windDirectionLabel(0.0))
        assertEquals("N", formatter.windDirectionLabel(350.0))
        assertEquals("NNO", formatter.windDirectionLabel(22.0))
        assertEquals("O", formatter.windDirectionLabel(90.0))
        assertEquals("S", formatter.windDirectionLabel(180.0))
        assertEquals("W", formatter.windDirectionLabel(270.0))
        assertEquals("NNW", formatter.windDirectionLabel(337.0))
        // Negative and over-360 values are normalised.
        assertEquals("W", formatter.windDirectionLabel(-90.0))
        assertEquals("S", formatter.windDirectionLabel(540.0))
        assertEquals(null, formatter.windDirectionLabel(null))
        assertEquals(null, formatter.windDirectionLabel(Double.NaN))
    }

    @Test
    fun `wind direction combines label and degrees`() {
        assertEquals("N (350°)", formatter.windDirection(350.0))
        assertEquals("S (180°)", formatter.windDirection(180.4))
        assertEquals("–", formatter.windDirection(null))
    }
}
