package com.example.weatherapp.widget.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.domain.sortedDistinctHours
import com.example.weatherapp.ui.weather.shortDescriptionRes
import com.example.weatherapp.ui.weather.weatherIconRes
import com.example.weatherapp.widget.WidgetData
import java.time.Duration
import java.time.Instant

/** The nine metrics the current weather widget can show, in the order they are preferred. */
enum class WidgetMetric {
    TODAY_RANGE,
    HUMIDITY,
    PRECIPITATION_PROBABILITY,
    PRECIPITATION,
    WIND,
    WIND_GUSTS,
    PRESSURE,
    CLOUD_COVER,
    WIND_DIRECTION,
}

/**
 * Detailed current weather, ready to be drawn.
 *
 * [tiles] holds the metrics the data can actually support, already reduced to what the size class
 * offers room for; an empty list means there is nothing to add, not that a placeholder is missing.
 * [headlineValues] is what the compact layout shows next to the temperature, so the smallest widget
 * still carries two real statements.
 */
data class CurrentWidgetContent(
    val state: WidgetState,
    val locationName: String,
    val temperature: String = WIDGET_MISSING_VALUE,
    @get:StringRes val conditionLabelRes: Int = R.string.weather_unknown,
    @get:DrawableRes val iconRes: Int = R.drawable.weather_cloudy,
    val windWarning: WindWarning = WindWarning.NONE,
    val feelsLike: String? = null,
    val updatedAgo: String? = null,
    val tiles: List<WidgetMetricTile> = emptyList(),
    val headlineValues: List<String> = emptyList(),
)

/** One information tile: a label from resources and the formatted value of the actual data. */
data class WidgetMetricTile(
    val metric: WidgetMetric,
    @get:StringRes val labelRes: Int,
    val value: String,
)

/**
 * Number of information tiles a size class has room for.
 *
 * Three columns of three rows in the expanded layout, two columns of two rows in the medium one, and
 * no grid at all when the widget is compact - which is what keeps nine metrics from being squeezed
 * into an unreadable space.
 */
fun tileCountFor(sizeClass: WidgetSizeClass): Int = when (sizeClass) {
    WidgetSizeClass.COMPACT -> 0
    WidgetSizeClass.MEDIUM -> 4
    WidgetSizeClass.EXPANDED -> 9
}

/**
 * Builds the current weather content of [data] for one [sizeClass].
 *
 * Which metric is shown where is decided here, in a plain function, so the answer to "does the
 * medium layout really prefer temperature, humidity, precipitation and wind?" is a test rather than a
 * screenshot.
 */
fun buildCurrentWidgetContent(
    data: WidgetData?,
    sizeClass: WidgetSizeClass,
): CurrentWidgetContent {
    val location = data?.location
        ?: return CurrentWidgetContent(state = WidgetState.NO_LOCATION, locationName = "")

    val snapshot = data.snapshot
    val current = snapshot?.current
    if (snapshot == null || (current == null && snapshot.hourly.isEmpty() && snapshot.daily.isEmpty())) {
        return CurrentWidgetContent(
            state = WidgetState.NO_DATA,
            locationName = location.name,
            updatedAgo = data.updatedAgoOrNull(),
        )
    }

    val condition = current?.condition
    val style = current?.precipitationStyle ?: PrecipitationStyle.NONE
    val available = availableTiles(snapshot, data.now)

    return CurrentWidgetContent(
        state = WidgetState.READY,
        locationName = location.name,
        temperature = WeatherFormatter.temperature(current?.temperatureCelsius),
        conditionLabelRes = shortDescriptionRes(condition, style),
        iconRes = weatherIconRes(condition, style),
        windWarning = current?.windWarning ?: WindWarning.NONE,
        feelsLike = current?.apparentTemperatureCelsius?.let {
            WeatherFormatter.temperature(it)
        },
        updatedAgo = data.updatedAgoOrNull(),
        tiles = available.take(tileCountFor(sizeClass)),
        headlineValues = available.take(COMPACT_HEADLINE_VALUES).map { it.value },
    )
}

/**
 * The metrics that have a real value, in the order the layouts prefer them.
 *
 * A metric the API did not report is simply absent: no zero is substituted for a missing humidity,
 * and the current hour's precipitation probability is looked up in the hourly series instead of
 * being borrowed from the daily maximum, which would say something different.
 */
private fun availableTiles(
    snapshot: WeatherSnapshot,
    referenceInstant: Instant,
): List<WidgetMetricTile> {
    val current = snapshot.current
    val hours = sortedDistinctHours(snapshot.hourly)
    val today = referenceInstant.atZone(snapshot.timeZone).toLocalDate()
    val todayHours = hours.filter { it.localDate == today }
    val tiles = mutableListOf<WidgetMetricTile>()

    // The day's minimum and maximum come from the daily entry of the location's own today, which is
    // the same pair the app shows for a day. Only when the response has no daily row for that date do
    // the hourly values of the day stand in - a fallback, never a second definition of the day.
    val todayDaily = snapshot.daily.firstOrNull { it.date == today }
    val todayMin = todayDaily?.minTemperatureCelsius
        ?: todayHours.mapNotNull { it.temperatureCelsius }.minOrNull()
    val todayMax = todayDaily?.maxTemperatureCelsius
        ?: todayHours.mapNotNull { it.temperatureCelsius }.maxOrNull()
    if (todayMin != null || todayMax != null) {
        tiles += WidgetMetricTile(
            metric = WidgetMetric.TODAY_RANGE,
            labelRes = R.string.widget_tile_today_range,
            value = WeatherFormatter.temperatureRange(todayMin, todayMax),
        )
    }
    current?.relativeHumidityPercent?.let {
        tiles += WidgetMetricTile(WidgetMetric.HUMIDITY, R.string.detail_humidity, WeatherFormatter.percent(it))
    }
    currentHour(hours, referenceInstant)?.precipitationProbabilityPercent?.let {
        tiles += WidgetMetricTile(
            metric = WidgetMetric.PRECIPITATION_PROBABILITY,
            labelRes = R.string.widget_tile_precipitation_probability,
            value = WeatherFormatter.percent(it),
        )
    }
    current?.precipitationMillimetres?.let {
        tiles += WidgetMetricTile(
            WidgetMetric.PRECIPITATION,
            R.string.detail_precipitation,
            WeatherFormatter.millimetres(it),
        )
    }
    current?.windSpeedKilometresPerHour?.let {
        tiles += WidgetMetricTile(WidgetMetric.WIND, R.string.detail_wind, WeatherFormatter.windSpeed(it))
    }
    current?.windGustsKilometresPerHour?.let {
        tiles += WidgetMetricTile(
            WidgetMetric.WIND_GUSTS,
            R.string.detail_wind_gusts,
            WeatherFormatter.windSpeed(it),
        )
    }
    current?.pressureHectopascal?.let {
        tiles += WidgetMetricTile(WidgetMetric.PRESSURE, R.string.detail_pressure, WeatherFormatter.pressure(it))
    }
    current?.cloudCoverPercent?.let {
        tiles += WidgetMetricTile(WidgetMetric.CLOUD_COVER, R.string.detail_cloud_cover, WeatherFormatter.percent(it))
    }
    current?.windDirectionDegrees?.let { degrees ->
        WeatherFormatter.windDirectionLabel(degrees)?.let {
            tiles += WidgetMetricTile(
                WidgetMetric.WIND_DIRECTION,
                R.string.detail_wind_direction,
                WeatherFormatter.windDirection(degrees),
            )
        }
    }
    return tiles
}

/**
 * The hourly interval that contains [referenceInstant], or null when the series has no point for the
 * current hour.
 *
 * Only an interval that really covers the moment is accepted: an already elapsed hour would report a
 * precipitation probability of the past, and the next later hour would report one of the future, so
 * neither is a "current" value and the tile is left out instead.
 */
private fun currentHour(hours: List<HourlyWeather>, referenceInstant: Instant): HourlyWeather? =
    hours.firstOrNull { hour ->
        !hour.instant.isAfter(referenceInstant) &&
            hour.instant.plus(HOURLY_INTERVAL).isAfter(referenceInstant)
    }

/** Length of one hourly forecast interval, matching the API's hourly step. */
private val HOURLY_INTERVAL: Duration = Duration.ofHours(1)

/** How many values the compact layout shows next to the temperature. */
private const val COMPACT_HEADLINE_VALUES = 2
