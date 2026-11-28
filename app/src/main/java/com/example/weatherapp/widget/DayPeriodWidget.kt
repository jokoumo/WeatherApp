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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.weatherapp.R
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.widget.presentation.PeriodCard
import com.example.weatherapp.widget.presentation.WidgetDayRelation
import com.example.weatherapp.widget.presentation.WidgetHeader
import com.example.weatherapp.widget.presentation.WidgetPalette
import com.example.weatherapp.widget.presentation.WidgetSizeClass
import com.example.weatherapp.widget.presentation.WidgetState
import com.example.weatherapp.widget.presentation.WidgetStateMessage
import com.example.weatherapp.widget.presentation.WidgetWeatherSymbol
import com.example.weatherapp.widget.presentation.buildPeriodWidgetContent
import com.example.weatherapp.widget.presentation.labelRes
import com.example.weatherapp.widget.presentation.precipitationSummary
import com.example.weatherapp.widget.presentation.widgetString

class DayPeriodWidget : GlanceWeatherWidget() {
    @Composable
    override fun WeatherContent(data: WidgetData?, sizeClass: WidgetSizeClass) {
        val content = buildPeriodWidgetContent(data)
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
                content.periods.chunked(2).forEach { cards ->
                    Spacer(GlanceModifier.height(4.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        cards.forEachIndexed { index, card ->
                            if (index > 0) Spacer(GlanceModifier.width(4.dp))
                            PeriodCardView(card, sizeClass, GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }
}

class DayPeriodWidgetReceiver : WeatherWidgetReceiver() {
    override val glanceAppWidget = DayPeriodWidget()
}

@Composable
private fun PeriodCardView(card: PeriodCard, sizeClass: WidgetSizeClass, modifier: GlanceModifier) {
    val dayLabel = card.dayRelation.labelRes()?.let { widgetString(it) } ?: card.weekdayLabel
    val expanded = sizeClass == WidgetSizeClass.EXPANDED
    val dayAndTime = if (expanded) {
        "$dayLabel · ${widgetString(R.string.day_period_time_range, card.startHour, card.endHour)}"
    } else {
        dayLabel
    }
    val precipitation = if (expanded) card.precipitationSummary() else {
        card.precipitationProbabilityPercent?.let { WeatherFormatter.percent(it) }
            ?: card.precipitationMillimetres?.let { WeatherFormatter.millimetres(it) }
    }
    Column(
        modifier = modifier.fillMaxHeight()
            .background(WidgetPalette.tile)
            .cornerRadius(8.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = widgetString(card.period.labelRes()),
            maxLines = 1,
            style = TextStyle(
                color = WidgetPalette.onSurface,
                fontSize = WidgetPalette.secondarySizeFor(sizeClass),
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = card.temperatureRange,
            maxLines = 2,
            style = TextStyle(color = WidgetPalette.onSurface, fontSize = WidgetPalette.bodySizeFor(sizeClass)),
        )
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                val details = when {
                    !card.hasData -> "$dayLabel · ${widgetString(R.string.widget_period_no_data)}"
                    precipitation != null -> "$dayAndTime · $precipitation"
                    else -> dayAndTime
                }
                Text(
                    text = details,
                    maxLines = 2,
                    style = TextStyle(color = WidgetPalette.muted, fontSize = WidgetPalette.secondarySizeFor(sizeClass)),
                )
            }
            WidgetWeatherSymbol(
                iconRes = card.iconRes,
                windWarning = card.windWarning,
                size = WidgetPalette.rowIconSize(sizeClass),
                contentDescription = widgetString(card.conditionLabelRes),
            )
        }
    }
}
