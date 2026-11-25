package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.PrecipitationClassifier
import com.example.weatherapp.domain.model.PrecipitationIntensity
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.domain.model.WindWarningClassifier
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * The four parts of a forecast day the daily detail sheet shows.
 *
 * The night belongs to the day it starts in: for Monday it runs from Monday 22:00 to Tuesday
 * 06:00, and the early hours of Monday itself belong to the night of Sunday.
 */
enum class DayPeriod { MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * Aggregated forecast of one [DayPeriod].
 *
 * Every value is derived from the hourly forecast points that lie inside [start, end). A value
 * stays null when the underlying hours did not report it, so the UI can show a placeholder
 * instead of a zero. [availableHourCount] and [expectedHourCount] say how much of the period the
 * hourly series actually covers, which is what lets the sheet mark a sum as partial rather than
 * presenting it as the complete total of the period.
 */
data class DayPeriodForecast(
    val period: DayPeriod,
    val start: Instant,
    val end: Instant,
    val minTemperatureCelsius: Double?,
    val maxTemperatureCelsius: Double?,
    val maxPrecipitationProbabilityPercent: Int?,
    val precipitationMillimetres: Double?,
    val averageWindSpeedKilometresPerHour: Double?,
    val maximumWindSpeedKilometresPerHour: Double?,
    val maximumWindGustsKilometresPerHour: Double?,
    val representativeCondition: WeatherCondition?,
    /**
     * Strongest thunderstorm or hail reported in the period, whether or not it shaped the
     * representative condition. A brief but significant event is therefore never hidden.
     */
    val severeCondition: WeatherCondition?,
    val windWarning: WindWarning,
    val availableHourCount: Int,
    val expectedHourCount: Int,
) {
    /** False when the hourly series holds no point at all inside this period. */
    val hasData: Boolean get() = availableHourCount > 0

    /** False when the hourly series is shorter than the period, so sums are partial. */
    val isComplete: Boolean get() = availableHourCount >= expectedHourCount

    /** Intensity of the representative condition, using the shared three-level classification. */
    val precipitationStyle: PrecipitationStyle
        get() = PrecipitationClassifier.classify(representativeCondition)

    /** True when a severe event is worth naming next to the representative condition. */
    val hasSeparateSevereCondition: Boolean
        get() = severeCondition != null && severeCondition != representativeCondition
}

/** Start and end of a period as absolute instants in the time zone of a location. */
data class DayPeriodWindow(val start: Instant, val end: Instant) {
    /** Hourly forecast points the period should contain, allowing for 23 h and 25 h days. */
    val expectedHourCount: Int
        get() = ceil(Duration.between(start, end).toMinutes() / 60.0 - COMPLETENESS_TOLERANCE_MINUTES).toInt()
}

/**
 * Hour of the day at which a period starts, counted from midnight of the selected day, so the
 * night reaches into the following calendar day (22 → 30 means 22:00 to 06:00 the next day).
 */
private fun periodStartHour(period: DayPeriod): Int = when (period) {
    DayPeriod.MORNING -> 6
    DayPeriod.AFTERNOON -> 12
    DayPeriod.EVENING -> 18
    DayPeriod.NIGHT -> 22
}

private fun periodEndHour(period: DayPeriod): Int = when (period) {
    DayPeriod.MORNING -> 12
    DayPeriod.AFTERNOON -> 18
    DayPeriod.EVENING -> 22
    DayPeriod.NIGHT -> 30
}

/** A period shorter than this whole number of hours is still treated as fully covered. */
private const val COMPLETENESS_TOLERANCE_MINUTES = 0.5

/**
 * Resolves one local wall clock hour of [date] into an absolute instant.
 *
 * Hours at or above 24 belong to the following calendar day, which is how the night period of a
 * day ends on the next one. A wall clock time that a daylight saving gap removes (a zone where
 * 06:00 for example does not exist on one particular day) is resolved by [ZoneId] to the first
 * instant after the gap, so the boundary is never silently placed on the previous day.
 */
private fun localHourInstant(date: LocalDate, zone: ZoneId, hourOfDay: Int): Instant =
    date.atTime(hourOfDay % 24, 0)
        .plusDays(hourOfDay / 24L)
        .atZone(zone)
        .toInstant()

/** Absolute window of [period] for [date] in [zone]. */
fun dayPeriodWindow(date: LocalDate, zone: ZoneId, period: DayPeriod): DayPeriodWindow =
    DayPeriodWindow(
        start = localHourInstant(date, zone, periodStartHour(period)),
        end = localHourInstant(date, zone, periodEndHour(period)),
    )

/**
 * Builds the four period forecasts of one day from the **complete** hourly series of a location.
 *
 * The rolling 24 hour strip only shows a slice of the downloaded data; the daily detail needs the
 * whole series, otherwise the day after tomorrow would look empty even though the response holds
 * its hours. [hours] may be in any order and may contain duplicates; both are normalised through
 * [sortedDistinctHours] so the result never depends on the order of the payload.
 */
fun buildDayPeriodForecasts(
    hours: List<HourlyWeather>,
    date: LocalDate,
    zone: ZoneId,
): List<DayPeriodForecast> {
    val sorted = sortedDistinctHours(hours)
    return DayPeriod.entries.map { period -> buildDayPeriodForecast(sorted, date, zone, period) }
}

/** Aggregates a single period; [hours] is expected to be sorted by instant. */
fun buildDayPeriodForecast(
    hours: List<HourlyWeather>,
    date: LocalDate,
    zone: ZoneId,
    period: DayPeriod,
): DayPeriodForecast {
    val window = dayPeriodWindow(date, zone, period)
    val inPeriod = hours.filter { !it.instant.isBefore(window.start) && it.instant.isBefore(window.end) }
    val temperatures = inPeriod.mapNotNull { it.temperatureCelsius?.takeIf { value -> value.isFinite() } }
    val probabilities = inPeriod.mapNotNull { it.precipitationProbabilityPercent }
    val precipitationAmounts = inPeriod.mapNotNull {
        it.precipitationMillimetres?.takeIf { value -> value.isFinite() && value >= 0.0 }
    }
    val sustainedWind = inPeriod.mapNotNull {
        it.windSpeedKilometresPerHour?.takeIf { value -> value.isFinite() && value >= 0.0 }
    }
    val gusts = inPeriod.mapNotNull {
        it.windGustsKilometresPerHour?.takeIf { value -> value.isFinite() && value >= 0.0 }
    }
    val representative = representativeCondition(inPeriod)
    return DayPeriodForecast(
        period = period,
        start = window.start,
        end = window.end,
        minTemperatureCelsius = temperatures.minOrNull(),
        maxTemperatureCelsius = temperatures.maxOrNull(),
        maxPrecipitationProbabilityPercent = probabilities.maxOrNull(),
        // A reported zero is a measurement ("no precipitation in this hour"), so it contributes to
        // the sum; only hours whose amount is missing are skipped. A period without a single
        // reported amount stays null: "no data" and "0 mm" are not the same statement, and a period
        // that only partly covers its hours still shows what it has.
        precipitationMillimetres = precipitationAmounts.takeIf { it.isNotEmpty() }?.sum(),
        averageWindSpeedKilometresPerHour = sustainedWind.takeIf { it.isNotEmpty() }?.average(),
        maximumWindSpeedKilometresPerHour = sustainedWind.maxOrNull(),
        maximumWindGustsKilometresPerHour = gusts.maxOrNull(),
        representativeCondition = representative,
        severeCondition = severeCondition(inPeriod),
        windWarning = WindWarningClassifier.classify(
            sustainedKilometresPerHour = sustainedWind.maxOrNull(),
            gustsKilometresPerHour = gusts.maxOrNull(),
        ),
        availableHourCount = inPeriod.size,
        expectedHourCount = window.expectedHourCount,
    )
}

/**
 * Coarse weather family a period can be described by.
 *
 * The concrete WMO conditions are grouped here so that "light rain", "rain" and "rain showers"
 * add up to one rainy period instead of splitting the count three ways. The severity rank decides
 * ties: rain beats cloudiness, a thunderstorm beats everything else.
 */
private enum class ConditionGroup(val severity: Int) {
    CLEAR(0),
    PARTLY_CLOUDY(1),
    OVERCAST(2),
    FOG(3),
    DRIZZLE(4),
    RAIN(5),
    SNOW_GRAINS(6),
    SNOW(7),
    FREEZING_PRECIPITATION(8),
    THUNDERSTORM(9),
    HAIL(10),
}

private fun groupOf(condition: WeatherCondition): ConditionGroup? = when (condition) {
    WeatherCondition.CLEAR_DAY, WeatherCondition.CLEAR_NIGHT,
    WeatherCondition.MAINLY_CLEAR_DAY, WeatherCondition.MAINLY_CLEAR_NIGHT,
    -> ConditionGroup.CLEAR

    WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> ConditionGroup.PARTLY_CLOUDY
    WeatherCondition.OVERCAST -> ConditionGroup.OVERCAST
    WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> ConditionGroup.FOG
    WeatherCondition.LIGHT_DRIZZLE, WeatherCondition.DRIZZLE, WeatherCondition.DENSE_DRIZZLE ->
        ConditionGroup.DRIZZLE

    WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.DENSE_FREEZING_DRIZZLE,
    WeatherCondition.FREEZING_RAIN, WeatherCondition.HEAVY_FREEZING_RAIN,
    -> ConditionGroup.FREEZING_PRECIPITATION

    WeatherCondition.LIGHT_RAIN, WeatherCondition.RAIN, WeatherCondition.HEAVY_RAIN,
    WeatherCondition.LIGHT_RAIN_SHOWERS, WeatherCondition.RAIN_SHOWERS,
    WeatherCondition.VIOLENT_RAIN_SHOWERS,
    -> ConditionGroup.RAIN

    WeatherCondition.LIGHT_SNOW, WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW,
    WeatherCondition.LIGHT_SNOW_SHOWERS, WeatherCondition.SNOW_SHOWERS,
    -> ConditionGroup.SNOW

    WeatherCondition.SNOW_GRAINS -> ConditionGroup.SNOW_GRAINS
    WeatherCondition.THUNDERSTORM -> ConditionGroup.THUNDERSTORM
    WeatherCondition.THUNDERSTORM_WITH_HAIL, WeatherCondition.SEVERE_THUNDERSTORM_WITH_HAIL ->
        ConditionGroup.HAIL

    WeatherCondition.UNKNOWN -> null
}

/** Weight of one hour inside its group: heavier precipitation counts for longer weather. */
private fun hourWeight(hour: HourlyWeather): Double =
    when (hour.precipitationStyle.intensity) {
        PrecipitationIntensity.HEAVY -> 2.0
        PrecipitationIntensity.MODERATE -> 1.5
        else -> 1.0
    }

/**
 * The condition that stands for the whole period.
 *
 * Algorithm, deliberately simple and deterministic:
 * 1. Hours without a condition, and unknown conditions, carry no information and are skipped.
 * 2. Each remaining hour votes for its [ConditionGroup] with a weight of 1, 1.5 or 2 depending on
 *    how heavy its reported precipitation is, so a period of steady rain outweighs an equal number
 *    of cloudy hours. Cloud cover enters through the groups themselves: clear, partly cloudy and
 *    overcast are separate groups, so the sky state that dominates the period is the one that wins.
 * 3. The group with the highest total weight wins; ties go to the more significant group, which
 *    keeps the result independent of the order of the hours.
 * 4. Inside the winning group the most frequent concrete condition wins (ties go to the heavier
 *    one), so a brief extreme never speaks for the period.
 * 5. The day/night variants follow the period rather than a single hour: the majority of the
 *    hours that report a sun state decides, which is what makes the night period show a moon.
 *
 * A single thunderstorm hour in a long period therefore does not become the representative
 * condition; [severeCondition] still reports it so the sheet can name it next to the icon.
 */
fun representativeCondition(hours: List<HourlyWeather>): WeatherCondition? {
    val voting = hours.mapNotNull { hour ->
        hour.condition?.let { condition -> groupOf(condition)?.let { group -> hour to group } }
    }
    if (voting.isEmpty()) return null

    val scores = voting.groupBy { it.second }
        .mapValues { entry -> entry.value.sumOf { hourWeight(it.first) } }
    val winner = scores.entries
        .maxWithOrNull(
            compareBy<Map.Entry<ConditionGroup, Double>> { it.value }
                .thenBy { it.key.severity },
        )?.key ?: return null

    val members = voting.filter { it.second == winner }.map { it.first }
    val concrete = members.mapNotNull { it.condition }
        .groupingBy { it }
        .eachCount()
        .entries
        .maxWithOrNull(
            compareBy<Map.Entry<WeatherCondition, Int>> { it.value }
                .thenBy { it.key.ordinal },
        )?.key ?: return null

    return withPeriodDaylight(concrete, members)
}

/**
 * Strongest thunderstorm or hail condition reported in the period, or null when there is none.
 * Hail with a thunderstorm outranks a plain thunderstorm, and a severe one outranks both.
 */
fun severeCondition(hours: List<HourlyWeather>): WeatherCondition? =
    hours.mapNotNull { it.condition }
        .filter { groupOf(it) == ConditionGroup.THUNDERSTORM || groupOf(it) == ConditionGroup.HAIL }
        .maxByOrNull { it.ordinal }

/**
 * Re-draws the day/night variant of a sky condition from the sun state of the period instead of
 * the state of whichever hour happened to be first. Conditions without a day/night variant are
 * returned unchanged.
 */
private fun withPeriodDaylight(condition: WeatherCondition, hours: List<HourlyWeather>): WeatherCondition {
    val base = when (condition) {
        WeatherCondition.CLEAR_DAY, WeatherCondition.CLEAR_NIGHT -> WeatherCondition.CLEAR_DAY
        WeatherCondition.MAINLY_CLEAR_DAY, WeatherCondition.MAINLY_CLEAR_NIGHT ->
            WeatherCondition.MAINLY_CLEAR_DAY

        WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT ->
            WeatherCondition.PARTLY_CLOUDY_DAY

        else -> return condition
    }
    val dayHours = hours.count { it.isDay == true }
    val nightHours = hours.count { it.isDay == false }
    val isDay = dayHours >= nightHours
    return when {
        isDay -> base
        base == WeatherCondition.CLEAR_DAY -> WeatherCondition.CLEAR_NIGHT
        base == WeatherCondition.MAINLY_CLEAR_DAY -> WeatherCondition.MAINLY_CLEAR_NIGHT
        else -> WeatherCondition.PARTLY_CLOUDY_NIGHT
    }
}
