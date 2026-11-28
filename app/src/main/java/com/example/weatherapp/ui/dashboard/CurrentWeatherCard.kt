package com.example.weatherapp.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
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
import com.example.weatherapp.ui.theme.FavoriteStarGold
import com.example.weatherapp.ui.theme.HeaderWindIndicatorColors
import com.example.weatherapp.ui.theme.OnHeader
import com.example.weatherapp.ui.theme.OnHeaderMuted
import com.example.weatherapp.ui.weather.headerGradient
import com.example.weatherapp.ui.weather.shortDescriptionRes
import java.time.Instant
import java.time.ZoneId

/**
 * What the hero card may show. The card belongs to a location, and a location is identified before
 * its weather has arrived, so the header never waits for a response.
 */
enum class WeatherCardStatus {
    /** Weather values are available and are rendered below the header. */
    READY,

    /** No values yet and a request is running: a compact indicator instead of any numbers. */
    LOADING,

    /** No values and the request failed: only the header is shown, the error belongs below. */
    FAILED,
}

/**
 * Hero card with the current conditions. The gradient reflects the weather and whether it is
 * day or night at the selected location; white text keeps contrast in both app themes.
 *
 * [dataInstant] is null when the API did not report a usable observation time. The card then
 * states that instead of showing a time that was never measured.
 *
 * The favorite star sits in the same line as the location name, so it clearly belongs to the
 * place and not to the weather symbol next to it.
 *
 * While [status] is not [WeatherCardStatus.READY] the card keeps the location header - name,
 * region and star - and leaves out every weather value, because showing a temperature or a symbol
 * that was never measured would be a fabrication. [WeatherCardStatus.LOADING] replaces the weather
 * symbol with a small spinner and labels the line; the error message of a failed load is shown
 * outside the card, where the retry button is.
 */
@Composable
fun CurrentWeatherCard(
    location: SavedLocation,
    current: CurrentWeather?,
    todayMin: Double?,
    todayMax: Double?,
    dataInstant: Instant?,
    zoneId: ZoneId,
    isFavorite: Boolean,
    isHomeLocation: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    status: WeatherCardStatus = WeatherCardStatus.READY,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = location.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = OnHeader,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(4.dp))
                        FavoriteStarButton(
                            isFavorite = isFavorite || isHomeLocation,
                            isHomeLocation = isHomeLocation,
                            onToggleFavorite = onToggleFavorite,
                        )
                    }
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
                    when (status) {
                        WeatherCardStatus.READY -> Text(
                            text = dataInstant?.let { WeatherFormatter.fullDateTime(it, zoneId) }
                                ?: stringResource(R.string.observation_time_unknown),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnHeaderMuted,
                        )

                        // No observation time exists yet, so the line says what is happening
                        // instead of leaving the user without any clue about the spinner.
                        WeatherCardStatus.LOADING -> Text(
                            text = stringResource(R.string.loading_weather),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnHeaderMuted,
                        )

                        // The error message and the retry button live below the card.
                        WeatherCardStatus.FAILED -> Unit
                    }
                }
                when (status) {
                    WeatherCardStatus.READY -> {
                        Spacer(Modifier.width(12.dp))
                        WeatherIcon(
                            condition = condition,
                            precipitation = current?.precipitationStyle ?: PrecipitationStyle.NONE,
                            windWarning = current?.windWarning ?: WindWarning.NONE,
                            size = 76.dp,
                            windIndicatorColors = HeaderWindIndicatorColors,
                        )
                    }

                    WeatherCardStatus.LOADING -> {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = OnHeader,
                        )
                    }

                    WeatherCardStatus.FAILED -> Unit
                }
            }

            if (status != WeatherCardStatus.READY) return@Column

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
                                WeatherFormatter.temperatureRange(todayMin, todayMax),
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
 * Star of the location currently shown.
 *
 * A filled star in the warm accent colour means "saved", an outlined one means "not saved yet".
 * The home location is always saved, so its star is filled too; tapping it does not remove
 * anything, and the accessibility label says so instead of promising an action that never happens.
 */
@Composable
private fun FavoriteStarButton(
    isFavorite: Boolean,
    isHomeLocation: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val label = when {
        isHomeLocation -> stringResource(R.string.content_description_star_home)
        isFavorite -> stringResource(R.string.action_remove_favorite)
        else -> stringResource(R.string.action_add_favorite)
    }
    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = label,
                tint = FavoriteStarGold,
                modifier = Modifier.size(26.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_star_outline),
                contentDescription = label,
                tint = OnHeader,
                modifier = Modifier.size(26.dp),
            )
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
