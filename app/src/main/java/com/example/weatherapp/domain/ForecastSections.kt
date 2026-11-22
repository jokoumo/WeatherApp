package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Size of the rolling hourly strip: the current hourly interval plus the following
 * [HOURLY_WINDOW_SIZE] - 1 intervals, so 24 consecutive hours in total.
 */
const val HOURLY_WINDOW_SIZE = 24

/** Number of daily forecasts that are rendered as large cards. */
const val DETAILED_DAY_COUNT = 2

/** Relation of an hourly entry's local date to the day the dashboard was built for. */
enum class HourlyDayRelation { TODAY, TOMORROW, LATER }

/**
 * One hourly forecast point together with the flags the hourly strip needs: which entry is the
 * current hour, where a new local calendar day starts, and whether the clock time repeats
 * itself inside the window because a daylight saving transition made a local hour occur twice.
 */
data class HourlyWindowEntry(
    val hour: HourlyWeather,
    val isCurrent: Boolean,
    /** True for the first entry of a local calendar day, used for the subtle day label. */
    val startsNewDay: Boolean,
    val dayRelation: HourlyDayRelation,
    /**
     * True when another entry in the window has the same local clock time. The strip then adds
     * the UTC offset so the two occurrences of, say, 02:00 stay distinguishable.
     */
    val ambiguousClockTime: Boolean,
)

/**
 * Splits a snapshot into the sections shown on the dashboard: the rolling 24 hour window,
 * today's own hours (used for the min/max summary), the next two days in detail and all
 * remaining days compact.
 *
 * The hourly window is selected by absolute timestamp, not by local hour number, so it may
 * cross midnight and it never depends on the device time zone.
 */
data class ForecastSections(
    val hourlyEntries: List<HourlyWindowEntry>,
    val todayHours: List<HourlyWeather>,
    val detailedDays: List<DailyWeather>,
    val extendedDays: List<DailyWeather>,
) {
    companion object {
        val EMPTY = ForecastSections(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * Builds the dashboard sections for [snapshot]. [referenceInstant] is the current point in
 * time; every calendar day and clock time is evaluated in the time zone of the location, so a
 * location on the other side of the planet uses its own day.
 */
fun buildForecastSections(
    snapshot: WeatherSnapshot,
    referenceInstant: Instant,
): ForecastSections {
    val zone = snapshot.timeZone
    val today = referenceInstant.atZone(zone).toLocalDate()
    val hours = sortedDistinctHours(snapshot.hourly)

    val window = hourlyWindow(hours, referenceInstant)

    val futureDays = snapshot.daily
        .distinctBy { it.date }
        .sortedBy { it.date }
        .filter { it.date.isAfter(today) }

    return ForecastSections(
        hourlyEntries = describeHourlyWindow(window, referenceInstant, today),
        todayHours = hours.filter { it.localDate == today },
        detailedDays = futureDays.take(DETAILED_DAY_COUNT),
        extendedDays = futureDays.drop(DETAILED_DAY_COUNT),
    )
}

/** Hourly points sorted by instant, keeping exactly one entry per instant. */
fun sortedDistinctHours(hours: List<HourlyWeather>): List<HourlyWeather> =
    hours.distinctBy { it.instant }.sortedBy { it.instant }

/**
 * Index of the hourly point that starts the rolling window, decided by absolute time only.
 *
 * The current interval is the point that contains [referenceInstant]: it starts at or before the
 * reference instant and ends one hour later. When the payload has no point for the current hour,
 * the window starts at the earliest later point instead of falling back to an interval that has
 * already elapsed — at 14:30 a payload of 13:00, 15:00, 16:00 starts at 15:00, never at 13:00.
 * A payload that ends before the current hour therefore has no start index inside the list, and
 * [hourlyWindow] returns no entries at all rather than showing expired forecast hours.
 *
 * [hours] must be sorted and free of duplicates, as produced by [sortedDistinctHours].
 */
fun hourlyWindowStartIndex(hours: List<HourlyWeather>, referenceInstant: Instant): Int {
    if (hours.isEmpty()) return 0
    val startedIndex = hours.indexOfLast { !it.instant.isAfter(referenceInstant) }
    if (startedIndex < 0) return 0 // the series only begins later: show it from its first point
    val endsCurrentInterval = hours[startedIndex].instant.plus(HOURLY_INTERVAL).isAfter(referenceInstant)
    return if (endsCurrentInterval) startedIndex else startedIndex + 1
}

/**
 * The points shown in the rolling strip: at most [HOURLY_WINDOW_SIZE] entries, starting at the
 * current interval and never reaching further than one window length beyond its first entry.
 *
 * The horizon is a duration and not a record count, so a payload with missing hours shortens the
 * window instead of stretching it over more than 24 hours just to fill 24 chips. Nothing is
 * invented for the missing hours.
 *
 * [hours] must be sorted and free of duplicates, as produced by [sortedDistinctHours].
 */
fun hourlyWindow(hours: List<HourlyWeather>, referenceInstant: Instant): List<HourlyWeather> {
    val startIndex = hourlyWindowStartIndex(hours, referenceInstant).coerceAtMost(hours.size)
    val candidates = hours.subList(startIndex, hours.size)
    val first = candidates.firstOrNull() ?: return emptyList()
    val horizon = first.instant.plus(HOURLY_WINDOW_DURATION)
    return candidates.takeWhile { it.instant.isBefore(horizon) }.take(HOURLY_WINDOW_SIZE)
}

/** Length of one hourly forecast interval. */
private val HOURLY_INTERVAL: Duration = Duration.ofHours(1)

/** Span the rolling strip may cover: [HOURLY_WINDOW_SIZE] hourly intervals. */
private val HOURLY_WINDOW_DURATION: Duration = Duration.ofHours(HOURLY_WINDOW_SIZE.toLong())

/**
 * Adds the presentation flags of the hourly strip. [window] is expected to be sorted by
 * instant, as produced by [buildForecastSections].
 */
fun describeHourlyWindow(
    window: List<HourlyWeather>,
    referenceInstant: Instant,
    today: LocalDate,
): List<HourlyWindowEntry> {
    val clockTimeCounts = window.groupingBy { it.localDateTime }.eachCount()
    val currentIndex = window.indexOfLast { !it.instant.isAfter(referenceInstant) }
    return window.mapIndexed { index, hour ->
        val date = hour.localDate
        HourlyWindowEntry(
            hour = hour,
            isCurrent = index == currentIndex,
            startsNewDay = index == 0 || date != window[index - 1].localDate,
            dayRelation = when {
                date == today -> HourlyDayRelation.TODAY
                date == today.plusDays(1) -> HourlyDayRelation.TOMORROW
                else -> HourlyDayRelation.LATER
            },
            ambiguousClockTime = (clockTimeCounts[hour.localDateTime] ?: 0) > 1,
        )
    }
}

/**
 * Milliseconds until the next local hour begins in [zone], used to advance the hourly window
 * when the clock moves on instead of polling. The result is never below one second, so a clock
 * that was moved backwards cannot turn the wait into a busy loop.
 */
fun millisUntilNextHour(now: Instant, zone: ZoneId): Long {
    val nextHourStart = now.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
    val millis = Duration.between(now, nextHourStart).toMillis()
    return millis.coerceAtLeast(MIN_TICK_DELAY_MILLIS) + TICK_MARGIN_MILLIS
}

private const val MIN_TICK_DELAY_MILLIS = 1_000L

/** Small safety margin so the tick never wakes before the hour has really begun. */
private const val TICK_MARGIN_MILLIS = 50L

/** Decides whether cached forecast data should be refreshed. */
object CachePolicy {

    /** Cached forecasts younger than this are reused without a network request. */
    val freshDuration: Duration = Duration.ofMinutes(30)

    /** Cached forecasts older than this are presented as possibly outdated. */
    val outdatedDuration: Duration = Duration.ofHours(3)

    fun age(fetchedAt: Instant, now: Instant): Duration = Duration.between(fetchedAt, now)

    /**
     * Fresh means: not from the future, within [freshDuration] and downloaded on the current
     * calendar day of [zone].
     *
     * The day rule matters because a forecast covers a whole day: data downloaded at 23:50 is
     * only 15 minutes old at 00:05 but belongs to the day before, so it is served as stale
     * instead of silently presented as current. The rule uses the location time zone and true
     * daylight saving rules, and it survives the 23 h and 25 h days a transition produces.
     */
    fun isFresh(fetchedAt: Instant, now: Instant, zone: ZoneId): Boolean {
        val currentAge = age(fetchedAt, now)
        if (currentAge.isNegative) return false // clock skew: a cache entry from the future
        if (currentAge > freshDuration) return false
        return fetchedAt.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
    }

    fun isStale(fetchedAt: Instant, now: Instant, zone: ZoneId): Boolean =
        !isFresh(fetchedAt, now, zone)

    fun isProbablyOutdated(fetchedAt: Instant, now: Instant): Boolean =
        age(fetchedAt, now) > outdatedDuration

    /**
     * False when the cached hourly series no longer reaches the current hour, which means the
     * rolling window cannot be filled from it. A snapshot without hourly data at all is not
     * judged here; the repository keeps showing whatever else it has.
     */
    fun hasHourlyCoverage(snapshot: WeatherSnapshot, now: Instant): Boolean {
        val last = snapshot.hourly.maxOfOrNull { it.instant } ?: return true
        return !last.isBefore(now.truncatedTo(ChronoUnit.HOURS))
    }
}

fun zoneIdOrDefault(zoneId: String?, fallback: String): ZoneId =
    runCatching { ZoneId.of(zoneId) }
        .recoverCatching { ZoneId.of(fallback) }
        .getOrElse { ZoneId.of("UTC") }
