package com.example.weatherapp.widget.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.weatherapp.R
import com.example.weatherapp.domain.DayPeriod
import com.example.weatherapp.domain.RollingDayPeriod
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.buildRollingDayPeriods
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.ui.weather.shortDescriptionRes
import com.example.weatherapp.ui.weather.weatherIconRes
import com.example.weatherapp.widget.WidgetData
import java.time.LocalDate
import java.time.ZoneId

/**
 * Weather of the next four day periods, ready to be drawn.
 *
 * The periods come from [buildRollingDayPeriods], which aggregates with the existing
 * `buildDayPeriodForecast`, so the widget and the daily detail sheet of the app describe the same
 * hours the same way: same windows, same maximum hourly precipitation probability, same
 * representative condition. Nothing here computes weather a second time.
 */
data class PeriodWidgetContent(
    val state: WidgetState,
    val locationName: String,
    val updatedAgo: String? = null,
    val periods: List<PeriodCard> = emptyList(),
)

/**
 * One period card.
 *
 * The German wording stays in resources ([period] and [conditionLabelRes] are keys, not text) and
 * the day a card belongs to is expressed as [dayRelation] plus the formatted weekday. That is what
 * lets a test assert "at 03:00 the first card is the night that began yesterday" without comparing
 * translated sentences. A value the forecast never reported stays null and renders as the en dash.
 */
data class PeriodCard(
    val period: DayPeriod,
    val dayRelation: WidgetDayRelation,
    val weekdayLabel: String,
    val startHour: String,
    val endHour: String,
    @get:DrawableRes val iconRes: Int,
    @get:StringRes val conditionLabelRes: Int,
    val temperatureRange: String,
    val precipitationProbabilityPercent: Int?,
    val precipitationMillimetres: Double?,
    val averageWindSpeedKilometresPerHour: Double?,
    val maximumWindGustsKilometresPerHour: Double?,
    val windWarning: WindWarning,
    val hasData: Boolean,
    val isComplete: Boolean,
    @get:StringRes val severeConditionLabelRes: Int? = null,
)

/**
 * Builds the rolling day-period content of [data].
 *
 * "Now" is [WidgetData.now] - the instant the data was loaded - so one render answers one question
 * and the four periods cannot disagree with the values shown next to them.
 */
fun buildPeriodWidgetContent(data: WidgetData?): PeriodWidgetContent {
    val location = data?.location
        ?: return PeriodWidgetContent(state = WidgetState.NO_LOCATION, locationName = "")

    val snapshot = data.snapshot
    if (snapshot == null || snapshot.hourly.isEmpty()) {
        return PeriodWidgetContent(
            state = if (data.awaitingFirstForecast) WidgetState.LOADING else WidgetState.NO_DATA,
            locationName = location.name,
            updatedAgo = data.updatedAgoOrNull(),
        )
    }

    val zone = data.timeZone
    val today = data.now.atZone(zone).toLocalDate()
    val periods = buildRollingDayPeriods(snapshot.hourly, data.now, zone)

    return PeriodWidgetContent(
        state = WidgetState.READY,
        locationName = location.name,
        updatedAgo = data.updatedAgoOrNull(),
        periods = periods.map { rolling -> toPeriodCard(rolling, today, zone) },
    )
}

/**
 * One aggregated period as a card.
 *
 * [PeriodCard.precipitationProbabilityPercent] is the **maximum** hourly probability inside the
 * period, which is exactly what [com.example.weatherapp.domain.DayPeriodForecast] reports; the layout
 * labels it "Max. Regenw." and never presents it as the chance of rain at some point during the
 * whole period. A period the hourly series does not cover keeps [PeriodCard.hasData] false so the
 * card can say so instead of drawing zeros.
 */
private fun toPeriodCard(
    rolling: RollingDayPeriod,
    today: LocalDate,
    zone: ZoneId,
): PeriodCard {
    val forecast = rolling.forecast
    val condition = forecast.representativeCondition
    val style = forecast.precipitationStyle
    return PeriodCard(
        period = rolling.slot.period,
        dayRelation = dayRelationOf(rolling.slot.date, today),
        weekdayLabel = WeatherFormatter.weekday(rolling.slot.date),
        startHour = WeatherFormatter.hour(forecast.start, zone),
        endHour = WeatherFormatter.hour(forecast.end, zone),
        iconRes = weatherIconRes(condition, style),
        conditionLabelRes = shortDescriptionRes(condition, style),
        temperatureRange = WeatherFormatter.temperatureRange(
            forecast.minTemperatureCelsius,
            forecast.maxTemperatureCelsius,
        ),
        precipitationProbabilityPercent = forecast.maxPrecipitationProbabilityPercent,
        precipitationMillimetres = forecast.precipitationMillimetres,
        averageWindSpeedKilometresPerHour = forecast.averageWindSpeedKilometresPerHour,
        maximumWindGustsKilometresPerHour = forecast.maximumWindGustsKilometresPerHour,
        windWarning = forecast.windWarning,
        hasData = forecast.hasData,
        isComplete = forecast.isComplete,
        severeConditionLabelRes = if (forecast.hasSeparateSevereCondition) {
            shortDescriptionRes(forecast.severeCondition, PrecipitationStyle.NONE)
        } else {
            null
        },
    )
}

/** Resource label of a day period, kept next to the card that shows it. */
@StringRes
fun DayPeriod.labelRes(): Int = when (this) {
    DayPeriod.MORNING -> R.string.day_period_morning
    DayPeriod.AFTERNOON -> R.string.day_period_afternoon
    DayPeriod.EVENING -> R.string.day_period_evening
    DayPeriod.NIGHT -> R.string.day_period_night
}

/**
 * Precipitation of the period as one short line: the highest hourly probability and the sum of the
 * hourly amounts, whatever the response reported.
 *
 * The probability keeps the word of the aggregation - it is a maximum of hourly values, not the
 * chance of rain at some point during the period - and the layout labels it that way. A period with
 * neither value returns null, and the line is left out instead of printing two dashes.
 */
fun PeriodCard.precipitationSummary(): String? =
    listOfNotNull(
        precipitationProbabilityPercent?.let { WeatherFormatter.percent(it) },
        precipitationMillimetres?.let { WeatherFormatter.millimetres(it) },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
