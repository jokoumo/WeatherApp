package com.example.weatherapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.ui.theme.LocalWindIndicatorColors
import com.example.weatherapp.ui.theme.WindIndicatorColors
import com.example.weatherapp.ui.weather.indicatorColor
import com.example.weatherapp.ui.weather.labelRes
import com.example.weatherapp.ui.weather.weatherIconRes

/**
 * Share of the weather symbol's footprint the wind glyph may occupy. The Flat Color icons keep
 * their upper end corner free, so this stays well below the old half-size overlay disc.
 */
private const val WIND_INDICATOR_RATIO = 0.34f

/** Smallest wind glyph that still reads as wind inside the compact hourly and day rows. */
private val minWindIndicatorSize = 9.dp

/**
 * Weather symbol of a condition, optionally with the supplementary wind indicator.
 *
 * The indicator is a small untinted-by-default wind glyph in the free upper end corner of the
 * same footprint: no backing disc, no extra padding and no layout change, so the cloud, sun,
 * moon, rain or snow underneath stay fully visible. Every weather drawable reserves that corner,
 * which is why the glyph never covers the sun, the moon or a precipitation symbol.
 */
@Composable
fun WeatherIcon(
    condition: WeatherCondition?,
    precipitation: PrecipitationStyle,
    windWarning: WindWarning,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    windIndicatorColors: WindIndicatorColors = LocalWindIndicatorColors.current,
) {
    Box(modifier = modifier.size(size)) {
        Image(
            painter = painterResource(id = weatherIconRes(condition, precipitation)),
            contentDescription = stringResource(R.string.content_description_weather_icon),
            modifier = Modifier.size(size),
        )
        if (windWarning.isWarning) {
            val indicatorSize = (size.value * WIND_INDICATOR_RATIO).coerceAtLeast(
                minWindIndicatorSize.value,
            ).dp
            Icon(
                painter = painterResource(id = R.drawable.weather_wind),
                contentDescription = stringResource(windWarning.labelRes()),
                tint = windWarning.indicatorColor(windIndicatorColors),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(indicatorSize),
            )
        }
    }
}

/** Section heading with an optional trailing note. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Label and value pair as used in the current weather grid and in the details sheet. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasizeValue: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = if (emphasizeValue) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Label above value, used on the coloured header where a compact grid is needed.
 *
 * The label may wrap instead of being cut off, which keeps long German labels such as
 * "Luftfeuchtigkeit" complete at large system font scales and on narrow screens.
 */
@Composable
fun StackedDetail(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            maxLines = 2,
        )
    }
}

/** Neutral note strip used for cache, offline and hint messages. */
@Composable
fun InfoNote(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = accentColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading_weather),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
    }
}
