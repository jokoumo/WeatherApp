package com.example.weatherapp.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.widget.presentation.DayRow
import com.example.weatherapp.widget.presentation.WidgetHeader
import com.example.weatherapp.widget.presentation.WidgetPalette
import com.example.weatherapp.widget.presentation.WidgetSizeClass
import com.example.weatherapp.widget.presentation.WidgetState
import com.example.weatherapp.widget.presentation.WidgetStateMessage
import com.example.weatherapp.widget.presentation.WidgetWeatherSymbol
import com.example.weatherapp.widget.presentation.buildThreeDayWidgetContent
import com.example.weatherapp.widget.presentation.labelRes
import com.example.weatherapp.widget.presentation.precipitationSummary
import com.example.weatherapp.widget.presentation.widgetString

class ThreeDayWidget : GlanceWeatherWidget() {
    @Composable
    override fun WeatherContent(data: WidgetData?, sizeClass: WidgetSizeClass) {
        val content = buildThreeDayWidgetContent(data)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeader(
                locationName = content.locationName,
                updatedAgo = content.updatedAgo,
                sizeClass = sizeClass,
                stale = data?.let { it.isShowingStaleData || it.isProbablyOutdated } == true,
            )
            if (content.state != WidgetState.READY) {
                WidgetStateMessage(content.state, sizeClass, GlanceModifier.defaultWeight())
            } else {
                content.days.forEach { day ->
                    Spacer(GlanceModifier.height(4.dp))
                    ForecastRow(day, sizeClass, GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

class ThreeDayWidgetReceiver : WeatherWidgetReceiver() {
    override val glanceAppWidget = ThreeDayWidget()
}

@Composable
private fun ForecastRow(row: DayRow, sizeClass: WidgetSizeClass, modifier: GlanceModifier) {
    val expanded = sizeClass == WidgetSizeClass.EXPANDED
    val secondaryStyle = TextStyle(color = WidgetPalette.muted, fontSize = WidgetPalette.secondarySizeFor(sizeClass))
    Row(
        modifier = modifier.fillMaxWidth()
            .background(WidgetPalette.tile)
            .cornerRadius(8.dp)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.dayRelation.labelRes()?.let { widgetString(it) } ?: row.weekdayLabel,
                maxLines = 1,
                style = TextStyle(
                    color = WidgetPalette.onSurface,
                    fontSize = WidgetPalette.bodySizeFor(sizeClass),
                    fontWeight = FontWeight.Bold,
                ),
            )
            val details = if (expanded) row.shortDate else {
                row.precipitationProbabilityPercent?.let { WeatherFormatter.percent(it) }
            }
            details?.let { Text(it, maxLines = 1, style = secondaryStyle) }
        }
        WidgetWeatherSymbol(
            iconRes = row.iconRes,
            windWarning = row.windWarning,
            size = WidgetPalette.rowIconSize(sizeClass),
            contentDescription = widgetString(row.conditionLabelRes),
        )
        Spacer(GlanceModifier.width(4.dp))
        if (expanded) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(widgetString(row.conditionLabelRes), maxLines = 2, style = secondaryStyle)
                row.precipitationSummary()?.let { Text(it, maxLines = 1, style = secondaryStyle) }
            }
            Spacer(GlanceModifier.width(4.dp))
        }
        val temperatureStyle = TextStyle(
            color = WidgetPalette.onSurface,
            fontSize = WidgetPalette.bodySizeFor(sizeClass),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
        val description = widgetString(R.string.temperature_range_accessibility, row.temperatureRange)
        Text(
            row.temperatureRange,
            modifier = GlanceModifier.semantics { contentDescription = description },
            maxLines = 2,
            style = temperatureStyle,
        )
    }
}
