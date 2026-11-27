package com.example.weatherapp.widget.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.ui.weather.shortDescriptionRes
import com.example.weatherapp.ui.weather.weatherIconRes
import com.example.weatherapp.widget.WidgetData
import java.time.LocalDate

/** Today, tomorrow and the day after tomorrow, ready to be drawn. */
data class ThreeDayWidgetContent(
    val state: WidgetState,
    val locationName: String,
    val updatedAgo: String? = null,
    val days: List<DayRow> = emptyList(),
)

/**
 * One forecast day.
 *
 * The values are the ones the API reports for that day: the daily minimum and maximum, the maximum
 * hourly precipitation probability of the day and the sum of its precipitation. Nothing is
 * re-aggregated from the hourly series here, because the daily block already carries those numbers
 * and a second aggregation would produce a different answer in a time zone with a daylight saving
 * transition.
 */
data class DayRow(
    val date: LocalDate,
    val dayRelation: WidgetDayRelation,
    val weekdayLabel: String,
    val shortDate: String,
    @get:DrawableRes val iconRes: Int,
    @get:StringRes val conditionLabelRes: Int,
    val temperatureRange: String,
    val minTemperatureCelsius: Double?,
    val maxTemperatureCelsius: Double?,
    val precipitationProbabilityPercent: Int?,
    val precipitationMillimetres: Double?,
    val windWarning: WindWarning,
    val hasData: Boolean,
)

/** Number of days the three-day widget always lays out. */
const val THREE_DAY_COUNT = 3

/**
 * Precipitation of the day as one short line: probability and amount, whatever the response reported.
 *
 * A day with neither value returns null and the layout leaves the line out instead of printing a pair
 * of dashes.
 */
fun DayRow.precipitationSummary(): String? =
    listOfNotNull(
        precipitationProbabilityPercent?.let { WeatherFormatter.percent(it) },
        precipitationMillimetres?.let { WeatherFormatter.millimetres(it) },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")


/**
 * Builds the three-day content of [data].
 *
 * "Today" is the calendar date **of the location**: the widget asks the snapshot's zone, never the
 * phone's, so a widget showing a city on the other side of the planet rolls over to the next three
 * days when that city has midnight, not when the phone does. A day the response does not cover keeps
 * its row and reports [DayRow.hasData] false, so the layout does not silently lose a day.
 */
fun buildThreeDayWidgetContent(data: WidgetData?): ThreeDayWidgetContent {
    val location = data?.location
        ?: return ThreeDayWidgetContent(state = WidgetState.NO_LOCATION, locationName = "")

    val snapshot = data.snapshot
    if (snapshot == null || snapshot.daily.isEmpty()) {
        return ThreeDayWidgetContent(
            state = WidgetState.NO_DATA,
            locationName = location.name,
            updatedAgo = data.updatedAgoOrNull(),
        )
    }

    val today = data.now.atZone(data.timeZone).toLocalDate()
    val byDate = snapshot.daily
        .distinctBy { it.date }
        .associateBy { it.date }

    return ThreeDayWidgetContent(
        state = WidgetState.READY,
        locationName = location.name,
        updatedAgo = data.updatedAgoOrNull(),
        days = (0 until THREE_DAY_COUNT).map { offset ->
            toDayRow(byDate[today.plusDays(offset.toLong())], today.plusDays(offset.toLong()), today)
        },
    )
}

private fun toDayRow(daily: DailyWeather?, date: LocalDate, today: LocalDate): DayRow {
    val condition = daily?.condition
    val style = daily?.precipitationStyle ?: PrecipitationStyle.NONE
    return DayRow(
        date = date,
        dayRelation = dayRelationOf(date, today),
        weekdayLabel = WeatherFormatter.weekday(date),
        shortDate = WeatherFormatter.shortDate(date),
        iconRes = weatherIconRes(condition, style),
        conditionLabelRes = shortDescriptionRes(condition, style),
        temperatureRange = WeatherFormatter.temperatureRange(
            daily?.minTemperatureCelsius,
            daily?.maxTemperatureCelsius,
        ),
        minTemperatureCelsius = daily?.minTemperatureCelsius,
        maxTemperatureCelsius = daily?.maxTemperatureCelsius,
        precipitationProbabilityPercent = daily?.precipitationProbabilityPercent,
        precipitationMillimetres = daily?.precipitationMillimetres,
        windWarning = daily?.windWarning ?: WindWarning.NONE,
        hasData = daily != null,
    )
}
