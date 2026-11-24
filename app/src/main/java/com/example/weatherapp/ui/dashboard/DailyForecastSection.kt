package com.example.weatherapp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.ui.components.StackedDetail
import com.example.weatherapp.ui.components.WeatherIcon
import com.example.weatherapp.ui.weather.shortDescriptionRes

/** Large card for one of the next two days, with the full set of daily values. */
@Composable
fun DetailedDayCard(
    day: DailyWeather,
    modifier: Modifier = Modifier,
    overline: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (overline != null) {
                        Text(
                            text = overline,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = WeatherFormatter.weekdayAndDate(day.date),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            shortDescriptionRes(
                                condition = day.condition,
                                style = day.precipitationStyle,
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(10.dp))
                WeatherIcon(
                    condition = day.condition,
                    precipitation = day.precipitationStyle,
                    windWarning = day.windWarning,
                    size = 44.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = WeatherFormatter.temperature(day.maxTemperatureCelsius),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = WeatherFormatter.temperature(day.minTemperatureCelsius),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DayDetailRow(
                    firstLabel = stringResource(R.string.detail_precipitation_probability),
                    firstValue = WeatherFormatter.percent(day.precipitationProbabilityPercent),
                    secondLabel = stringResource(R.string.detail_precipitation),
                    secondValue = WeatherFormatter.millimetres(day.precipitationMillimetres),
                )
                DayDetailRow(
                    firstLabel = stringResource(R.string.detail_wind),
                    firstValue = WeatherFormatter.windSpeed(day.maxWindSpeedKilometresPerHour),
                    secondLabel = stringResource(R.string.detail_wind_gusts),
                    secondValue = WeatherFormatter.windSpeed(day.maxWindGustsKilometresPerHour),
                )
                DayDetailRow(
                    firstLabel = stringResource(R.string.detail_sunshine),
                    firstValue = WeatherFormatter.duration(day.sunshineSeconds),
                    secondLabel = stringResource(R.string.detail_day_length),
                    secondValue = WeatherFormatter.duration(day.daylightSeconds),
                )
            }
        }
    }
}

@Composable
private fun DayDetailRow(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StackedDetail(
            label = firstLabel,
            value = firstValue,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StackedDetail(
            label = secondLabel,
            value = secondValue,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Compact list for forecast day 4 and beyond.
 *
 * Forecasts that far out are not reliable in detail, so a row carries only what stays useful:
 * the day, the weather symbol with its wind warning when one applies, the precipitation
 * information and the minimum and maximum temperature. Written condition labels and a
 * temperature range bar were removed because they repeated what the symbol and the two numbers
 * already say, and the values are set in a size that reads comfortably on a phone.
 */
@Composable
fun ExtendedDayList(
    days: List<DailyWeather>,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) {
        Text(
            text = stringResource(R.string.no_daily_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            days.forEachIndexed { index, day ->
                ExtendedDayRow(day = day)
                if (index != days.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtendedDayRow(day: DailyWeather) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(84.dp)) {
            Text(
                text = WeatherFormatter.weekday(day.date),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = WeatherFormatter.shortDate(day.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        WeatherIcon(
            condition = day.condition,
            precipitation = day.precipitationStyle,
            windWarning = day.windWarning,
            size = 30.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = extendedPrecipitationText(day),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = WeatherFormatter.temperatureValue(day.minTemperatureCelsius) + "°",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = WeatherFormatter.temperatureValue(day.maxTemperatureCelsius) + "°",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp),
        )
    }
}

/**
 * Precipitation of a compact forecast row: the probability first, because it is what people act
 * on, and the expected amount next to it when the API reported one. Missing values render as an
 * en dash instead of being guessed.
 */
private fun extendedPrecipitationText(day: DailyWeather): String =
    listOfNotNull(
        day.precipitationProbabilityPercent?.let { WeatherFormatter.percent(it) },
        day.precipitationMillimetres?.let { WeatherFormatter.millimetres(it) },
    ).joinToString(" · ").ifEmpty {
        WeatherFormatter.millimetres(null)
    }
