package com.example.weatherapp.widget.presentation

import androidx.annotation.StringRes
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.widget.WidgetData
import java.time.LocalDate

/**
 * What a widget knows about its data.
 *
 * The states are separate because they need different sentences: a widget that has never received a
 * forecast says so, a widget whose refresh failed shows cached values and admits they may be old, and
 * a widget without any location has nothing to name.
 */
enum class WidgetState {
    /** Weather values are available. */
    READY,

    /** A request is running and nothing has arrived yet. */
    LOADING,

    /** A location exists but neither the network nor the cache has weather for it. */
    NO_DATA,

    /** The widget cannot resolve a location at all, for example because the app has no home location. */
    NO_LOCATION,
}

/** How the day a widget row belongs to relates to today in the location's own time zone. */
enum class WidgetDayRelation { TODAY, TOMORROW, DAY_AFTER, OTHER }

/** German label of such a day, resolved through string resources; later days keep the weekday. */
@StringRes
fun WidgetDayRelation.labelRes(): Int? = when (this) {
    WidgetDayRelation.TODAY -> R.string.widget_day_today
    WidgetDayRelation.TOMORROW -> R.string.tomorrow
    WidgetDayRelation.DAY_AFTER -> R.string.day_after_tomorrow
    WidgetDayRelation.OTHER -> null
}

/** Relation of [date] to [today], both plain calendar dates of the same location. */
fun dayRelationOf(date: LocalDate, today: LocalDate): WidgetDayRelation = when {
    date == today -> WidgetDayRelation.TODAY
    date == today.plusDays(1) -> WidgetDayRelation.TOMORROW
    date == today.plusDays(2) -> WidgetDayRelation.DAY_AFTER
    else -> WidgetDayRelation.OTHER
}

/**
 * Subtle "last updated" line of a widget, from the fetch time of the snapshot.
 *
 * This is deliberately the moment the app last *retrieved* the forecast, not the moment the launcher
 * redrew the widget, and it is null when no fetch time exists. Whether that age deserves a warning is
 * answered by [WidgetData.isProbablyOutdated] - the same threshold the app itself uses - so a widget
 * and a screen never disagree about the same cached response.
 */
fun WidgetData.updatedAgoOrNull(): String? =
    fetchedAt?.let { WeatherFormatter.relativeTime(it, now) }

/** Placeholder used everywhere a value was never reported; matches the app's own en dash. */
const val WIDGET_MISSING_VALUE = "\u2013"
