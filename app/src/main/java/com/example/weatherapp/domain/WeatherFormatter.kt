package com.example.weatherapp.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * German formatting for all weather values: Celsius, km/h, millimetres, 24-hour time and
 * German dates. The locale is fixed so that the app looks the same on every device and so
 * that the formatting can be verified in unit tests.
 *
 * The few German literals used here (labels such as "vor 5 Minuten") belong to formatting
 * logic and are kept next to the logic that produces them.
 */
object WeatherFormatter {

    private const val MISSING = "\u2013" // en dash
    private const val NBSP = "\u00A0"

    private val GERMAN_LOCALE: Locale = Locale.GERMANY

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", GERMAN_LOCALE)
    private val weekdayDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d. MMMM", GERMAN_LOCALE)
    private val weekdayShortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d. MMM", GERMAN_LOCALE)
    private val shortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd.MM.", GERMAN_LOCALE)
    private val fullDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN_LOCALE)
    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy, HH:mm", GERMAN_LOCALE)

    private val integerFormat =
        java.text.NumberFormat.getInstance(GERMAN_LOCALE).apply { maximumFractionDigits = 0 }
    private val oneDecimalFormat =
        java.text.NumberFormat.getInstance(GERMAN_LOCALE).apply { maximumFractionDigits = 1 }

    /** Temperature without decimals, for example "12 °C". */
    fun temperature(celsius: Double?): String =
        if (celsius == null) MISSING else integerFormat.format(round(celsius)) + NBSP + "\u00B0C"

    /** Temperature value without unit, for example "-3". */
    fun temperatureValue(celsius: Double?): String =
        if (celsius == null) MISSING else integerFormat.format(round(celsius))

    /** Precipitation amount, one decimal for small amounts, for example "1,4 mm". */
    fun millimetres(value: Double?): String {
        if (value == null) return MISSING
        val formatted = if (kotlin.math.abs(value) < 10) oneDecimalFormat.format(value)
        else integerFormat.format(value)
        return formatted + NBSP + "mm"
    }

    fun percent(value: Int?): String =
        if (value == null) MISSING else integerFormat.format(value) + NBSP + "%"

    fun percent(value: Double?): String =
        if (value == null) MISSING else integerFormat.format(round(value)) + NBSP + "%"

    fun windSpeed(kilometresPerHour: Double?): String =
        if (kilometresPerHour == null) MISSING
        else integerFormat.format(round(kilometresPerHour)) + NBSP + "km/h"

    fun pressure(hectopascal: Double?): String =
        if (hectopascal == null) MISSING
        else integerFormat.format(round(hectopascal)) + NBSP + "hPa"

    /** Clock time of an instant in the given zone, 24-hour format. */
    fun clockTime(instant: Instant, zoneId: ZoneId): String =
        timeFormatter.format(instant.atZone(zoneId))

    fun clockTime(localDateTime: LocalDateTime): String = timeFormatter.format(localDateTime)

    /**
     * Clock time with a compact UTC offset, for example "02:00 +02". Used when one clock time
     * occurs twice in the hourly window because a daylight saving transition repeated a local
     * hour, so both occurrences stay distinguishable. Offsets that are not whole hours keep
     * their minutes, for example "+05:30".
     */
    fun clockTimeWithOffset(instant: Instant, zoneId: ZoneId): String {
        val minutes = zoneId.rules.getOffset(instant).totalSeconds / 60
        val sign = if (minutes >= 0) "+" else "-"
        val absolute = kotlin.math.abs(minutes)
        val hours = (absolute / 60).toString().padStart(2, '0')
        val offset = if (absolute % 60 == 0) {
            "$sign$hours"
        } else {
            "$sign$hours:${(absolute % 60).toString().padStart(2, '0')}"
        }
        return "${timeFormatter.format(instant.atZone(zoneId))} $offset"
    }

    /** "Donnerstag, 22. November" */
    fun weekdayAndDate(date: LocalDate): String = weekdayDateFormatter.format(date)

    /** "Do., 22. Nov." */
    fun weekdayAndShortDate(date: LocalDate): String = weekdayShortDateFormatter.format(date)

    /** "22.11." */
    fun shortDate(date: LocalDate): String = shortDateFormatter.format(date)

    /** "Donnerstag, 22. November 2026" */
    fun fullDate(date: LocalDate): String = fullDateFormatter.format(date)

    /** "Donnerstag, 22. November 2026, 14:35" */
    fun fullDateTime(instant: Instant, zoneId: ZoneId): String =
        dateTimeFormatter.format(instant.atZone(zoneId))

    /** Short localized weekday name, for example "Do." */
    fun weekday(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, GERMAN_LOCALE)

    /** Duration in seconds rendered as "6 h 12 min"; null stays empty. */
    fun duration(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return MISSING
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "$hours h $minutes min"
            hours > 0 -> "$hours h"
            else -> "$minutes min"
        }
    }

    /** Human readable age of data, for example "vor 12 Minuten". */
    fun relativeTime(fetchedAt: Instant, now: Instant): String {
        val age = Duration.between(fetchedAt, now).abs()
        val minutes = age.toMinutes()
        return when {
            age.seconds < 60 -> "gerade eben"
            minutes < 60 -> "vor $minutes Minuten"
            age.toHours() < 24 -> "vor ${age.toHours()} Stunden"
            age.toDays() == 1L -> "gestern"
            else -> "vor ${age.toDays()} Tagen"
        }
    }

    private val compassLabels = listOf(
        "N", "NNO", "NO", "ONO", "O", "OSO", "SO", "SSO",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )

    /** Sixteen point compass label for a wind direction in degrees. */
    fun windDirectionLabel(degrees: Double?): String? {
        if (degrees == null || degrees.isNaN()) return null
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        val index = (((normalized + 11.25) % 360.0) / 22.5).toInt() % compassLabels.size
        return compassLabels[index]
    }

    /** Wind direction as label and degrees, for example "SW (225\u00B0)". */
    fun windDirection(degrees: Double?): String {
        val label = windDirectionLabel(degrees) ?: return MISSING
        return "$label (${integerFormat.format(round(degrees!!))}\u00B0)"
    }

    /**
     * Rounds to the nearest integer, with exact halves going to the even value, which is what
     * [integerFormat] does as well. Negative zero is normalised so that -0,4 °C is shown as
     * "0 °C" instead of "-0 °C".
     */
    private fun round(value: Double): Double {
        val rounded = kotlin.math.round(value)
        return if (rounded == 0.0) 0.0 else rounded
    }
}
