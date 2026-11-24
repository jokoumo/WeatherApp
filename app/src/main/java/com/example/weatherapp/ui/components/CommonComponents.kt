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
import androidx.compose.foundation.shape.CircleShape
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
import com.example.weatherapp.ui.weather.indicatorColor
import com.example.weatherapp.ui.weather.labelRes
import com.example.weatherapp.ui.weather.weatherIconRes

/** Share of the weather symbol an overlaid wind indicator may cover. */
private const val WIND_INDICATOR_RATIO = 0.5f

/** Share of the wind indicator disc taken by the glyph itself. */
private const val WIND_GLYPH_RATIO = 0.78f

/** Smallest wind indicator that is still readable inside the compact hourly and day rows. */
private val minWindIndicatorSize = 14.dp

/**
 * Weather symbol of a condition, optionally with the supplementary wind indicator.
 *
 * The indicator is a small tinted glyph in the upper start corner of the same footprint, so the
 * card layout never changes and clouds, sun, moon, rain or snow stay visible. A solid backing
 * disc keeps the glyph readable over any symbol and over the coloured header.
 */
@Composable
fun WeatherIcon(
    condition: WeatherCondition?,
    precipitation: PrecipitationStyle,
    windWarning: WindWarning,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    backingColor: Color = MaterialTheme.colorScheme.surface,
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(indicatorSize)
                    .background(backingColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.weather_wind),
                    contentDescription = stringResource(windWarning.labelRes()),
                    tint = windWarning.indicatorColor(LocalWindIndicatorColors.current),
                    modifier = Modifier.size((indicatorSize.value * WIND_GLYPH_RATIO).dp),
                )
            }
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
