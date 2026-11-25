package com.example.weatherapp.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.DayPeriod
import com.example.weatherapp.domain.DayPeriodForecast
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.buildDayPeriodForecasts
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.ui.components.StackedDetail
import com.example.weatherapp.ui.components.WeatherIcon
import com.example.weatherapp.ui.weather.descriptionRes
import com.example.weatherapp.ui.weather.explanationRes
import com.example.weatherapp.ui.weather.shortDescriptionRes
import java.time.LocalDate
import java.time.ZoneId

/**
 * Detailed outlook for one forecast day, split into Vormittag, Nachmittag, Abend and Nacht.
 *
 * The sheet is the daily counterpart of [HourlyDetailSheet] and deliberately a separate
 * component: an hour is described by a list of values, a period by values that were aggregated
 * from hours, and mixing the two would hide which number is measured and which one is computed.
 *
 * Everything is built from the hourly points the application already holds — [hours] is the
 * complete series of the location, not the rolling 24 hour strip, so the day after tomorrow is as
 * complete as tomorrow. Opening this sheet never sends a request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayForecastDetailSheet(
    date: LocalDate,
    hours: List<HourlyWeather>,
    zone: ZoneId,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val periods = remember(hours, date, zone) { buildDayPeriodForecasts(hours, date, zone) }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.day_details_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = WeatherFormatter.fullWeekdayAndDate(date),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Explicit 48 dp box, the same touch target the rest of the app uses.
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // The four cards are taller than a small screen, so the content scrolls while the
            // sheet itself stays open; every period therefore stays reachable.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                periods.forEach { period -> DayPeriodCard(period = period, zone = zone) }
                Text(
                    text = stringResource(R.string.day_period_footnote),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** One period: name and time range, symbol with condition and temperatures, then the values. */
@Composable
private fun DayPeriodCard(period: DayPeriodForecast, zone: ZoneId) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(period.period.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.day_period_time_range,
                        WeatherFormatter.hour(period.start, zone),
                        WeatherFormatter.hour(period.end, zone),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!period.hasData) {
                Text(
                    text = stringResource(R.string.day_period_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                return@Column
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val condition = period.representativeCondition
                if (condition != null) {
                    WeatherIcon(
                        condition = condition,
                        precipitation = period.precipitationStyle,
                        windWarning = period.windWarning,
                        size = 38.dp,
                        contentDescriptionOverride = stringResource(
                            R.string.content_description_day_period,
                            stringResource(period.period.labelRes()),
                            stringResource(shortDescriptionRes(condition, period.precipitationStyle)),
                            WeatherFormatter.temperatureRange(
                                period.minTemperatureCelsius,
                                period.maxTemperatureCelsius,
                            ),
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            shortDescriptionRes(condition, period.precipitationStyle),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                    // A brief but significant event is named even when it did not shape the
                    // representative symbol of the whole period.
                    if (period.hasSeparateSevereCondition) {
                        Text(
                            text = stringResource(
                                R.string.day_period_severe_hint,
                                stringResource(
                                    (period.severeCondition ?: WeatherCondition.UNKNOWN).descriptionRes(),
                                ),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = WeatherFormatter.temperatureRange(
                        period.minTemperatureCelsius,
                        period.maxTemperatureCelsius,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(12.dp))
            val details = listOf(
                stringResource(R.string.day_period_precipitation_probability) to
                    WeatherFormatter.percent(period.maxPrecipitationProbabilityPercent),
                stringResource(R.string.day_period_precipitation) to
                    WeatherFormatter.millimetres(period.precipitationMillimetres),
                stringResource(R.string.day_period_wind_average) to
                    WeatherFormatter.windSpeed(period.averageWindSpeedKilometresPerHour),
                stringResource(R.string.day_period_wind_gusts) to
                    WeatherFormatter.windSpeed(period.maximumWindGustsKilometresPerHour),
            )
            // Four columns side by side at the normal system font size. With a large system font
            // the same four values are laid out as two rows of two, so a column never becomes so
            // narrow that a unit like "12 km/h" is broken in half.
            val columnsPerRow = if (LocalDensity.current.fontScale >= 1.3f) 2 else 4
            details.chunked(columnsPerRow).forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 0.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { (label, value) ->
                        PeriodDetail(label = label, value = value, modifier = Modifier.weight(1f))
                    }
                    // Keep the columns of a partial row as wide as in a full row.
                    repeat(columnsPerRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // One quiet line instead of a coloured badge: the warning names the measurement it is
            // based on, so gusts are never read as sustained wind.
            if (period.windWarning.isWarning) {
                Text(
                    text = stringResource(
                        period.windWarning.explanationRes(),
                        WeatherFormatter.windSpeed(period.maximumWindSpeedKilometresPerHour),
                        WeatherFormatter.windSpeed(period.maximumWindGustsKilometresPerHour),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (!period.isComplete) {
                Text(
                    text = stringResource(R.string.day_period_incomplete),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** Compact label/value pair inside a period card. */
@Composable
private fun PeriodDetail(label: String, value: String, modifier: Modifier = Modifier) {
    StackedDetail(
        label = label,
        value = value,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        valueColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@StringRes
private fun DayPeriod.labelRes(): Int = when (this) {
    DayPeriod.MORNING -> R.string.day_period_morning
    DayPeriod.AFTERNOON -> R.string.day_period_afternoon
    DayPeriod.EVENING -> R.string.day_period_evening
    DayPeriod.NIGHT -> R.string.day_period_night
}
