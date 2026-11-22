package com.example.weatherapp.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.ui.weather.iconRes

/** Weather symbol for a condition. Missing conditions fall back to a neutral cloud. */
@Composable
fun WeatherIcon(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Image(
        painter = painterResource(id = (condition ?: WeatherCondition.UNKNOWN).iconRes()),
        contentDescription = stringResource(R.string.content_description_weather_icon),
        modifier = modifier.size(size),
    )
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

/** Label above value, used on the coloured header where a compact grid is needed. */
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
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            maxLines = 2,
        )
    }
}

/**
 * Small bar showing where a day's temperature range sits inside the range of the whole list.
 * Renders nothing meaningful when the values are missing.
 */
@Composable
fun TemperatureRangeBar(
    minTemperature: Double?,
    maxTemperature: Double?,
    overallMin: Double?,
    overallMax: Double?,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 56.dp,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val cool = Color(0xFF5B9BD5)
    val warm = Color(0xFFE08A4B)
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val thickness = 5.dp.toPx()
            val centerY = size.height / 2f
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(thickness / 2f),
                size = androidx.compose.ui.geometry.Size(size.width, thickness),
                topLeft = Offset(0f, centerY - thickness / 2f),
            )
            if (minTemperature != null && maxTemperature != null &&
                overallMin != null && overallMax != null && overallMax > overallMin
            ) {
                val span = (overallMax - overallMin).toFloat()
                val start = ((minTemperature - overallMin) / span).toFloat().coerceIn(0f, 1f)
                val end = ((maxTemperature - overallMin) / span).toFloat().coerceIn(0f, 1f)
                val x0 = start * size.width
                val x1 = (end * size.width).coerceAtLeast(x0 + thickness / 2f)
                drawLine(
                    brush = Brush.horizontalGradient(listOf(cool, warm)),
                    start = Offset(x0, centerY),
                    end = Offset(x1, centerY),
                    strokeWidth = thickness,
                    cap = StrokeCap.Round,
                )
            }
        }
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
