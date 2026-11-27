package com.example.weatherapp.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.example.weatherapp.widget.presentation.DayRow
import com.example.weatherapp.widget.presentation.WidgetHeader
import com.example.weatherapp.widget.presentation.WidgetPalette
import com.example.weatherapp.widget.presentation.WidgetSizeClass
import com.example.weatherapp.widget.presentation.WidgetSizes
import com.example.weatherapp.widget.presentation.WidgetStateMessage
import com.example.weatherapp.widget.presentation.WidgetWeatherSymbol
import com.example.weatherapp.widget.presentation.buildThreeDayWidgetContent
import com.example.weatherapp.widget.presentation.labelRes
import com.example.weatherapp.widget.presentation.messageResOrNull
import com.example.weatherapp.widget.presentation.precipitationSummary
import com.example.weatherapp.widget.presentation.widgetString

/**
 * The three-day widget: heute, morgen, übermorgen.
 *
 * Its content is a layout only. Which day is "today", which values belong to it, and how much of
 * them the current size has room for are all answered by `buildThreeDayWidgetContent`, so what the
 * widget shows can be read and tested without a launcher.
 */
class ThreeDayWidget : GlanceWeatherWidget(WidgetSizes.threeDaySizes) {

    @Composable
    override fun WeatherContent(data: WidgetData?, sizeClass: WidgetSizeClass) {
        val content = buildThreeDayWidgetContent(data)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeader(
                locationName = content.locationName,
                updatedAgo = content.updatedAgo,
                sizeClass = sizeClass,
                stale = data?.isProbablyOutdated == true,
            )
            if (content.state.messageResOrNull() != null) {
                Spacer(GlanceModifier.height(6.dp))
                WidgetStateMessage(content.state, sizeClass, GlanceModifier.defaultWeight())
            }
            content.days.forEach { row ->
                Spacer(GlanceModifier.height(6.dp))
                when (sizeClass) {
                    WidgetSizeClass.COMPACT -> CompactDayRow(row)
                    WidgetSizeClass.MEDIUM -> MediumDayRow(row)
                    WidgetSizeClass.EXPANDED -> ExpandedDayRow(row)
                }
            }
        }
    }
}

/** The three-day widget as it appears in the Android widget picker. */
class ThreeDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThreeDayWidget()
}

/**
 * Expanded row: day and date on the left, symbol, condition and precipitation in the middle, the
 * temperature range on the right.
 */
@Composable
private fun ExpandedDayRow(row: DayRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = dayLabel(row),
                maxLines = 1,
                style = TextStyle(
                    color = WidgetPalette.onSurface,
                    fontSize = WidgetPalette.bodySize,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = row.shortDate,
                maxLines = 1,
                style = TextStyle(color = WidgetPalette.muted, fontSize = WidgetPalette.footnoteSize),
            )
        }
        Spacer(GlanceModifier.width(4.dp))
        WidgetWeatherSymbol(
            iconRes = row.iconRes,
            windWarning = row.windWarning,
            size = WidgetPalette.rowIconSize(WidgetSizeClass.EXPANDED),
            contentDescription = widgetString(row.conditionLabelRes),
        )
        Spacer(GlanceModifier.width(4.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = widgetString(row.conditionLabelRes),
                maxLines = 1,
                style = TextStyle(color = WidgetPalette.onSurface, fontSize = WidgetPalette.bodySize),
            )
            row.precipitationSummary()?.let { summary ->
                Text(
                    text = summary,
                    maxLines = 1,
                    style = TextStyle(color = WidgetPalette.muted, fontSize = WidgetPalette.footnoteSize),
                )
            }
        }
        Spacer(GlanceModifier.width(4.dp))
        TemperatureRange(row, WidgetSizeClass.EXPANDED)
    }
}

/** Medium row: the same order, with the date and the condition text giving way to the values. */
@Composable
private fun MediumDayRow(row: DayRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = dayLabel(row),
            maxLines = 1,
            style = TextStyle(
                color = WidgetPalette.onSurface,
                fontSize = WidgetPalette.bodySizeFor(WidgetSizeClass.MEDIUM),
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(4.dp))
        WidgetWeatherSymbol(
            iconRes = row.iconRes,
            windWarning = row.windWarning,
            size = WidgetPalette.rowIconSize(WidgetSizeClass.MEDIUM),
            contentDescription = widgetString(row.conditionLabelRes),
        )
        Spacer(GlanceModifier.width(4.dp))
        row.precipitationSummary()?.let { summary ->
            Text(
                text = summary,
                maxLines = 1,
                style = TextStyle(
                    color = WidgetPalette.muted,
                    fontSize = WidgetPalette.secondarySizeFor(WidgetSizeClass.MEDIUM),
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(4.dp))
        }
        TemperatureRange(row, WidgetSizeClass.MEDIUM)
    }
}

/** Compact row: day, symbol and temperature range - the least that still answers the question. */
@Composable
private fun CompactDayRow(row: DayRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = row.weekdayLabel,
            maxLines = 1,
            style = TextStyle(
                color = WidgetPalette.onSurface,
                fontSize = WidgetPalette.secondarySizeFor(WidgetSizeClass.COMPACT),
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(2.dp))
        WidgetWeatherSymbol(
            iconRes = row.iconRes,
            windWarning = row.windWarning,
            size = WidgetPalette.rowIconSize(WidgetSizeClass.COMPACT),
            contentDescription = widgetString(row.conditionLabelRes),
        )
        Spacer(GlanceModifier.width(2.dp))
        TemperatureRange(row, WidgetSizeClass.COMPACT)
    }
}

/**
 * The temperature range of one day, on the right edge.
 *
 * One string, "7–12 °C", at every size: it is the spelling the app uses, and splitting minimum and
 * maximum into two columns would need labels the compact row has no room for.
 */
@Composable
private fun TemperatureRange(row: DayRow, sizeClass: WidgetSizeClass) {
    Text(
        text = row.temperatureRange,
        maxLines = 1,
        style = TextStyle(
            color = WidgetPalette.onSurface,
            fontSize = WidgetPalette.bodySizeFor(sizeClass),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        ),
    )
}

/** "Heute", "Morgen", "Übermorgen" - or the weekday of a day further ahead. */
@Composable
private fun dayLabel(row: DayRow): String =
    row.dayRelation.labelRes()?.let { widgetString(it) } ?: row.weekdayLabel
