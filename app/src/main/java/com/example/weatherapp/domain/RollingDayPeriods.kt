package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.HourlyWeather
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One [DayPeriod] of one calendar day, addressed by the day the period *starts* in.
 *
 * [DayPeriod.NIGHT] belongs to [date] even though it reaches into the following morning, which is
 * the rule [buildDayPeriodForecast] already uses for the daily detail sheet. Keeping day and period
 * together in one value is what makes the sequence of periods unambiguous across midnight.
 */
data class DayPeriodSlot(val date: LocalDate, val period: DayPeriod)

/** Order of the periods inside one calendar day, as defined by [DayPeriodWindow]. */
private val periodOrder = listOf(DayPeriod.MORNING, DayPeriod.AFTERNOON, DayPeriod.EVENING, DayPeriod.NIGHT)

/**
 * The period that contains [instant] in [zone], together with the calendar day it belongs to.
 *
 * Local clock hours decide, and the local clock is the one of the *location*, never the one of the
 * phone:
 *
 * | Local hour | Period | Belongs to |
 * | --- | --- | --- |
 * | 06:00–11:59 | [DayPeriod.MORNING] | that day |
 * | 12:00–17:59 | [DayPeriod.AFTERNOON] | that day |
 * | 18:00–21:59 | [DayPeriod.EVENING] | that day |
 * | 22:00–23:59 | [DayPeriod.NIGHT] | that day |
 * | 00:00–05:59 | [DayPeriod.NIGHT] | the **previous** day |
 *
 * The last row is the reason a night is never skipped and never split: at 03:00 the ongoing night
 * started on the evening before, so the answer is the night of that evening.
 */
fun currentDayPeriodSlot(instant: Instant, zone: ZoneId): DayPeriodSlot {
    val local = instant.atZone(zone).toLocalDateTime()
    val hour = local.hour
    return when (hour) {
        in 6..11 -> DayPeriodSlot(local.toLocalDate(), DayPeriod.MORNING)
        in 12..17 -> DayPeriodSlot(local.toLocalDate(), DayPeriod.AFTERNOON)
        in 18..21 -> DayPeriodSlot(local.toLocalDate(), DayPeriod.EVENING)
        in 22..23 -> DayPeriodSlot(local.toLocalDate(), DayPeriod.NIGHT)
        else -> DayPeriodSlot(local.toLocalDate().minusDays(1), DayPeriod.NIGHT)
    }
}

/**
 * The [count] periods that follow each other from now on, starting with the one that contains
 * [instant].
 *
 * The slots come out in chronological order because periods run in a fixed cycle per local calendar
 * day - Vormittag, Nachmittag, Abend, Nacht - and the night hands over to the next day's morning.
 * Four slots therefore always describe "the rest of today plus the start of tomorrow", whatever the
 * clock says: at 23:00 the first slot is tonight, at 03:00 it is the night that began yesterday.
 *
 * Boundaries themselves are not computed here: [dayPeriodWindow] already resolves local wall clock
 * hours to absolute instants in [zone], including the 23 h and 25 h days a daylight saving
 * transition produces and zones with a fractional offset.
 */
fun rollingDayPeriodSlots(
    instant: Instant,
    zone: ZoneId,
    count: Int = ROLLING_PERIOD_COUNT,
): List<DayPeriodSlot> {
    require(count > 0) { "The number of periods has to be positive" }
    val first = currentDayPeriodSlot(instant, zone)
    val startIndex = periodOrder.indexOf(first.period)
    return (0 until count).map { step ->
        val position = startIndex + step
        DayPeriodSlot(
            date = first.date.plusDays((position / periodOrder.size).toLong()),
            period = periodOrder[position % periodOrder.size],
        )
    }
}

/**
 * The rolling periods rendered as full [DayPeriodForecast] values.
 *
 * Every slot is aggregated by the existing [buildDayPeriodForecast], so the widget cannot disagree
 * with the daily detail sheet: same windows, same maximum hourly precipitation probability, same
 * representative condition. [hours] is the complete hourly series of a snapshot and may be in any
 * order, [buildDayPeriodForecast] normalises it per call.
 *
 * A slot whose hours the forecast does not reach any more comes back with [DayPeriodForecast.hasData]
 * false rather than being dropped, so the layout stays stable and can say that no data exists.
 */
fun buildRollingDayPeriodForecasts(
    hours: List<HourlyWeather>,
    instant: Instant,
    zone: ZoneId,
    count: Int = ROLLING_PERIOD_COUNT,
): List<DayPeriodForecast> =
    buildRollingDayPeriods(hours, instant, zone, count).map { it.forecast }

/**
 * Same as [buildRollingDayPeriodForecasts], but keeps the [DayPeriodSlot] of every period.
 *
 * A presentation layer needs the slot to say which calendar day a card belongs to - "Heute",
 * "Morgen" - without deriving it from an instant, which is exactly the step that would go wrong in a
 * time zone with a daylight saving transition.
 */
fun buildRollingDayPeriods(
    hours: List<HourlyWeather>,
    instant: Instant,
    zone: ZoneId,
    count: Int = ROLLING_PERIOD_COUNT,
): List<RollingDayPeriod> {
    val sorted = sortedDistinctHours(hours)
    return rollingDayPeriodSlots(instant, zone, count).map { slot ->
        RollingDayPeriod(slot, buildDayPeriodForecast(sorted, slot.date, zone, slot.period))
    }
}

/** One rolling period: the calendar slot it occupies plus its aggregated weather. */
data class RollingDayPeriod(val slot: DayPeriodSlot, val forecast: DayPeriodForecast)

/** Number of periods the day-period widget shows. */
const val ROLLING_PERIOD_COUNT = 4
