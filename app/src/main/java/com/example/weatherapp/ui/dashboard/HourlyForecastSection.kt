package com.example.weatherapp.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.ui.components.DetailRow
import com.example.weatherapp.ui.components.WeatherIcon
import com.example.weatherapp.ui.weather.descriptionRes
import com.example.weatherapp.ui.weather.iconRes

/**
 * Horizontal strip with the hours of the current day. The strip is scrolled so that the
 * current hour is visible; tapping an hour opens the details sheet.
 */
@Composable
fun HourlyForecast(
    hours: List<HourlyWeather>,
    currentHourIndex: Int,
    onItemClick: (HourlyWeather) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hours.isEmpty()) {
        Text(
            text = stringResource(R.string.no_hourly_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(key1 = hours.first().instant, key2 = currentHourIndex) {
        if (currentHourIndex > 0) {
            listState.scrollToItem((currentHourIndex - 1).coerceAtLeast(0))
        }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
    ) {
        itemsIndexed(items = hours, key = { _, hour -> hour.instant }) { index, hour ->
            HourChip(
                hour = hour,
                isCurrent = index == currentHourIndex,
                onClick = { onItemClick(hour) },
            )
        }
    }
}

@Composable
private fun HourChip(
    hour: HourlyWeather,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .width(92.dp)
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
            Text(
                text = WeatherFormatter.clockTime(hour.localDateTime),
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(6.dp))
            WeatherIcon(condition = hour.condition, size = 30.dp)
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Bottom sheet with every available value of a single forecast hour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyDetailSheet(
    hour: HourlyWeather,
    onDismiss: () -> Unit,
) {
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
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        id = (hour.condition ?: WeatherCondition.UNKNOWN).iconRes(),
                    ),
                    contentDescription = stringResource(R.string.content_description_weather_icon),
                    modifier = Modifier.width(46.dp).height(46.dp),
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
                            WeatherFormatter.clockTime(hour.localDateTime),
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
        }
    }
}
