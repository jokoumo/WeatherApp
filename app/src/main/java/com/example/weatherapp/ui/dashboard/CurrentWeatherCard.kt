package com.example.weatherapp.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.CurrentWeather
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.ui.components.StackedDetail
import com.example.weatherapp.ui.components.WeatherIcon
import com.example.weatherapp.ui.theme.OnHeader
import com.example.weatherapp.ui.theme.OnHeaderMuted
import com.example.weatherapp.ui.weather.headerGradient
import com.example.weatherapp.ui.weather.shortDescriptionRes
import java.time.Instant
import java.time.ZoneId

/**
 * Hero card with the current conditions. The gradient reflects the weather and whether it is
 * day or night at the selected location; white text keeps contrast in both app themes.
 *
 * [dataInstant] is null when the API did not report a usable observation time. The card then
 * states that instead of showing a time that was never measured.
 */
@Composable
fun CurrentWeatherCard(
    location: SavedLocation,
    current: CurrentWeather?,
    todayMin: Double?,
    todayMax: Double?,
    dataInstant: Instant?,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
) {
    val condition = current?.condition
    val gradient = headerGradient(condition, current?.isDay)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(colors = listOf(gradient.first, gradient.second)))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = OnHeader,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val region = listOfNotNull(location.admin1, location.country)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    if (region.isNotEmpty()) {
                        Text(
                            text = region,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnHeaderMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = dataInstant?.let { WeatherFormatter.fullDateTime(it, zoneId) }
                            ?: stringResource(R.string.observation_time_unknown),
                        style = MaterialTheme.typography.labelMedium,
                        color = OnHeaderMuted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                WeatherIcon(
                    condition = condition,
                    precipitation = current?.precipitationStyle ?: PrecipitationStyle.NONE,
                    windWarning = current?.windWarning ?: WindWarning.NONE,
                    size = 76.dp,
                    backingColor = OnHeader,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = WeatherFormatter.temperature(current?.temperatureCelsius),
                    style = MaterialTheme.typography.displayLarge,
                    color = OnHeader,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            shortDescriptionRes(
                                condition = condition,
                                style = current?.precipitationStyle ?: PrecipitationStyle.NONE,
                            ),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnHeader,
                        maxLines = 2,
                    )
                    Text(
                        text = stringResource(
                            R.string.feels_like_value,
                            WeatherFormatter.temperature(current?.apparentTemperatureCelsius),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnHeaderMuted,
                    )
                    if (todayMin != null || todayMax != null) {
                        Text(
                            text = stringResource(
                                R.string.min_max_today,
                                WeatherFormatter.temperature(todayMin),
                                WeatherFormatter.temperature(todayMax),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnHeaderMuted,
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = OnHeaderMuted.copy(alpha = 0.35f),
            )

            // No clip here on purpose: a rounded clip on this column would cut the first
            // strokes of the leftmost label, which is what made "Luftfeuchtigkeit" look broken.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderDetailRow(
                    firstLabel = stringResource(R.string.detail_humidity),
                    firstValue = WeatherFormatter.percent(current?.relativeHumidityPercent),
                    secondLabel = stringResource(R.string.detail_precipitation),
                    secondValue = WeatherFormatter.millimetres(current?.precipitationMillimetres),
                )
                HeaderDetailRow(
                    firstLabel = stringResource(R.string.detail_wind),
                    firstValue = WeatherFormatter.windSpeed(current?.windSpeedKilometresPerHour) + " " +
                        WeatherFormatter.windDirectionLabel(current?.windDirectionDegrees).orEmpty(),
                    secondLabel = stringResource(R.string.detail_wind_gusts),
                    secondValue = WeatherFormatter.windSpeed(current?.windGustsKilometresPerHour),
                )
                HeaderDetailRow(
                    firstLabel = stringResource(R.string.detail_cloud_cover),
                    firstValue = WeatherFormatter.percent(current?.cloudCoverPercent),
                    secondLabel = stringResource(R.string.detail_pressure),
                    secondValue = WeatherFormatter.pressure(current?.pressureHectopascal),
                )
            }
        }
    }
}

/**
 * Two detail pairs side by side. The gap between the columns keeps a long value of the first
 * column from touching the label of the second one.
 */
@Composable
private fun HeaderDetailRow(
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
            labelColor = OnHeaderMuted,
            valueColor = OnHeader,
            modifier = Modifier.weight(1f),
        )
        StackedDetail(
            label = secondLabel,
            value = secondValue,
            labelColor = OnHeaderMuted,
            valueColor = OnHeader,
            modifier = Modifier.weight(1f),
        )
    }
}
