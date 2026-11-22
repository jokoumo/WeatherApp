package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Splits a snapshot into the sections shown on the dashboard:
 * today with hourly detail, the next two days in detail and all remaining days compact.
 *
 * The Open-Meteo standard forecast covers up to 16 calendar days, so the extended list
 * is normally 13 entries; when the API returns fewer days the list is simply shorter.
 */
data class ForecastSections(
    val todayHours: List<HourlyWeather>,
    val detailedDays: List<DailyWeather>,
    val extendedDays: List<DailyWeather>,
) {
    companion object {
        val EMPTY = ForecastSections(emptyList(), emptyList(), emptyList())
    }
}

/** Number of daily forecasts that are rendered as large cards. */
const val DETAILED_DAY_COUNT = 2

/**
 * Builds the dashboard sections for [snapshot]. [referenceInstant] is the current point in
 * time; the "today" bucket is defined in the location time zone, so a location on the other
 * side of the planet uses its own calendar day.
 */
fun buildForecastSections(
    snapshot: WeatherSnapshot,
    referenceInstant: Instant,
): ForecastSections {
    val zone = snapshot.timeZone
    val today = referenceInstant.atZone(zone).toLocalDate()

    val hours = snapshot.hourly
        .distinctBy { it.instant }
        .sortedBy { it.instant }
        .filter { it.instant.atZone(zone).toLocalDate() == today }

    val futureDays = snapshot.daily
        .distinctBy { it.date }
        .sortedBy { it.date }
        .filter { it.date.isAfter(today) }

    return ForecastSections(
        todayHours = hours,
        detailedDays = futureDays.take(DETAILED_DAY_COUNT),
        extendedDays = futureDays.drop(DETAILED_DAY_COUNT),
    )
}

/**
 * Index of the hourly entry that covers [referenceInstant]. Returns 0 when the list is
 * empty or starts in the future, which keeps scrolling predictable.
 */
fun currentHourIndex(hours: List<HourlyWeather>, referenceInstant: Instant): Int {
    if (hours.isEmpty()) return 0
    val index = hours.indexOfLast { !it.instant.isAfter(referenceInstant) }
    return if (index < 0) 0 else index
}

/** Decides whether cached forecast data should be refreshed. */
object CachePolicy {
    /** Cached forecasts younger than this are reused without a network request. */
    val freshDuration: Duration = Duration.ofMinutes(30)

    /** Cached forecasts older than this are presented as possibly outdated. */
    val outdatedDuration: Duration = Duration.ofHours(3)

    fun age(fetchedAt: Instant, now: Instant): Duration = Duration.between(fetchedAt, now)

    fun isFresh(fetchedAt: Instant, now: Instant): Boolean {
        val currentAge = age(fetchedAt, now)
        return !currentAge.isNegative && currentAge <= freshDuration
    }

    fun isStale(fetchedAt: Instant, now: Instant): Boolean = !isFresh(fetchedAt, now)

    fun isProbablyOutdated(fetchedAt: Instant, now: Instant): Boolean =
        age(fetchedAt, now) > outdatedDuration
}

fun zoneIdOrDefault(zoneId: String?, fallback: String): ZoneId =
    runCatching { ZoneId.of(zoneId) }
        .recoverCatching { ZoneId.of(fallback) }
        .getOrElse { ZoneId.of("UTC") }
