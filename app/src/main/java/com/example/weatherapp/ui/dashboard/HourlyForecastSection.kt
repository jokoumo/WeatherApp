package com.example.weatherapp.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.HourlyDayRelation
import com.example.weatherapp.domain.HourlyWindowEntry
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.ui.components.DetailRow
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.components.WeatherIcon
import com.example.weatherapp.ui.weather.descriptionRes
import com.example.weatherapp.ui.weather.explanationRes
import com.example.weatherapp.ui.weather.labelRes
import java.time.LocalDate

/**
 * Horizontal strip with the rolling hourly forecast: the current hour followed by the next
 * 23 hourly forecast points. The strip starts at the current hour, highlights it and labels
 * the day each group of hours belongs to, which keeps a window that crosses midnight readable.
 * Tapping an hour opens the details sheet.
 */
@Composable
fun HourlyForecast(
    entries: List<HourlyWindowEntry>,
    onItemClick: (HourlyWindowEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Text(
            text = stringResource(R.string.no_hourly_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        itemsIndexed(items = entries, key = { _, entry -> entry.hour.instant }) { _, entry ->
            HourChip(
                entry = entry,
                onClick = { onItemClick(entry) },
            )
        }
    }
}

/**
 * One hour of the strip.
 *
 * Every chip has the same vertical structure: a day label line that is reserved even when the
 * hour does not start a new day, the clock time, the weather symbol, the temperature and the
 * precipitation line. Because no element is ever added or removed, all chips have the same outer
 * height and the same positions inside them, whether or not they carry "Heute" or "Morgen".
 */
@Composable
private fun HourChip(
    entry: HourlyWindowEntry,
    onClick: () -> Unit,
) {
    val hour = entry.hour
    val isCurrent = entry.isCurrent
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .width(hourlyChipWidth)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isCurrent) 0.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DayLabelLine(entry = entry, hour = hour)
            Spacer(Modifier.height(2.dp))
            Text(
                // The UTC offset is only shown for clock times that occur twice in the window,
                // which happens when a daylight saving transition repeats a local hour.
                text = if (entry.ambiguousClockTime) {
                    WeatherFormatter.clockTimeWithOffset(hour.instant, hour.timeZone)
                } else {
                    WeatherFormatter.clockTime(hour.localDateTime)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            WeatherIcon(
                condition = hour.condition,
                precipitation = hour.precipitationStyle,
                windWarning = hour.windWarning,
                size = 30.dp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = WeatherFormatter.temperature(hour.temperatureCelsius),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = hour.precipitationProbabilityPercent
                    ?.let { WeatherFormatter.percent(it) }
                    ?: WeatherFormatter.millimetres(hour.precipitationMillimetres),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** Width of one hourly chip; wide enough for "100 %" and an ambiguous clock time. */
private val hourlyChipWidth = 92.dp

/**
 * The day label line of a chip.
 *
 * The line is always laid out with the exact line height of the label style, so a chip without a
 * label reserves precisely the same space as one that shows "Heute" or "Morgen". The reserved
 * height follows the system font scale instead of being a fixed number of dp.
 */
@Composable
private fun DayLabelLine(
    entry: HourlyWindowEntry,
    hour: HourlyWeather,
) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val reservedHeight = with(LocalDensity.current) { labelStyle.lineHeight.toDp() }
    Box(modifier = Modifier.height(reservedHeight), contentAlignment = Alignment.Center) {
        if (entry.startsNewDay) {
            Text(
                text = dayLabel(entry.dayRelation, hour.localDate),
                style = labelStyle,
                color = if (entry.dayRelation == HourlyDayRelation.TODAY) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
            )
        }
    }
}

/** "Heute", "Morgen" or the weekday of a day that starts inside the hourly window. */
@Composable
private fun dayLabel(relation: HourlyDayRelation, date: LocalDate): String = when (relation) {
    HourlyDayRelation.TODAY -> stringResource(R.string.hourly_day_today)
    HourlyDayRelation.TOMORROW -> stringResource(R.string.tomorrow)
    HourlyDayRelation.LATER -> WeatherFormatter.weekday(date)
}

/** Bottom sheet with every available value of a single forecast hour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyDetailSheet(
    entry: HourlyWindowEntry,
    onDismiss: () -> Unit,
) {
    val hour = entry.hour
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(
                    condition = hour.condition,
                    precipitation = hour.precipitationStyle,
                    windWarning = hour.windWarning,
                    size = 46.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hourly_details_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = WeatherFormatter.weekdayAndDate(hour.localDate) + " · " +
                            if (entry.ambiguousClockTime) {
                                WeatherFormatter.clockTimeWithOffset(hour.instant, hour.timeZone)
                            } else {
                                WeatherFormatter.clockTime(hour.localDateTime)
                            },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            (hour.condition ?: WeatherCondition.UNKNOWN).descriptionRes(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            DetailRow(
                label = stringResource(R.string.detail_temperature),
                value = WeatherFormatter.temperature(hour.temperatureCelsius),
                emphasizeValue = true,
            )
            DetailRow(
                label = stringResource(R.string.detail_feels_like),
                value = WeatherFormatter.temperature(hour.apparentTemperatureCelsius),
            )
            DetailRow(
                label = stringResource(R.string.detail_precipitation_probability),
                value = WeatherFormatter.percent(hour.precipitationProbabilityPercent),
            )
            DetailRow(
                label = stringResource(R.string.detail_precipitation),
                value = WeatherFormatter.millimetres(hour.precipitationMillimetres),
            )
            DetailRow(
                label = stringResource(R.string.detail_humidity),
                value = WeatherFormatter.percent(hour.relativeHumidityPercent),
            )
            DetailRow(
                label = stringResource(R.string.detail_cloud_cover),
                value = WeatherFormatter.percent(hour.cloudCoverPercent),
            )
            DetailRow(
                label = stringResource(R.string.detail_wind),
                value = WeatherFormatter.windSpeed(hour.windSpeedKilometresPerHour),
            )
            DetailRow(
                label = stringResource(R.string.detail_wind_direction),
                value = WeatherFormatter.windDirection(hour.windDirectionDegrees),
            )
            DetailRow(
                label = stringResource(R.string.detail_wind_gusts),
                value = WeatherFormatter.windSpeed(hour.windGustsKilometresPerHour),
            )
            DetailRow(
                label = stringResource(R.string.detail_pressure),
                value = WeatherFormatter.pressure(hour.pressureHectopascal),
            )
            // Names the measured value the warning is based on, so gusts are never read as
            // sustained wind and the warning never relies on its colour alone.
            if (hour.windWarning.isWarning) {
                Spacer(Modifier.height(10.dp))
                InfoNote(
                    text = stringResource(
                        hour.windWarning.explanationRes(),
                        WeatherFormatter.windSpeed(hour.windSpeedKilometresPerHour),
                        WeatherFormatter.windSpeed(hour.windGustsKilometresPerHour),
                    ),
                    accentColor = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(hour.windWarning.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
