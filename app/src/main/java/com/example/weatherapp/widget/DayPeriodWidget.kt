package com.example.weatherapp.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.example.weatherapp.widget.presentation.WidgetSizes
import com.example.weatherapp.widget.presentation.WidgetStateMessage
import com.example.weatherapp.widget.presentation.WidgetWeatherSymbol
import com.example.weatherapp.widget.presentation.buildPeriodWidgetContent
import com.example.weatherapp.widget.presentation.labelRes
import com.example.weatherapp.widget.presentation.messageResOrNull
import com.example.weatherapp.widget.presentation.precipitationSummary
import com.example.weatherapp.widget.presentation.widgetString

/**
 * The day-period widget: the next four periods, starting with the one the location is in right now.
 *
 * The four cards are laid out two by two at every size; what changes with the size is how much each
 * card says. That keeps the grid stable - a period that lost its data still occupies its card and
 * says so - and it is the reason a compact widget is calm instead of a pile of clipped numbers.
 */
class DayPeriodWidget : GlanceWeatherWidget(WidgetSizes.dayPeriodSizes) {

    @Composable
    override fun WeatherContent(data: WidgetData?, sizeClass: WidgetSizeClass) {
        val content = buildPeriodWidgetContent(data)
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
            content.periods.chunked(CARDS_PER_ROW).forEach { row ->
                Spacer(GlanceModifier.height(4.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    row.forEach { card ->
                        PeriodCardView(
                            card = card,
                            sizeClass = sizeClass,
                            // The share of the row is asked for here, in the row's own scope, and the
                            // card itself only decorates the modifier it is handed.
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Spacer(GlanceModifier.width(3.dp))
                    }
                    repeat(CARDS_PER_ROW - row.size) {
                        Spacer(GlanceModifier.defaultWeight().height(1.dp))
                    }
                }
            }
        }
    }
}

/** The day-period widget as it appears in the Android widget picker. */
class DayPeriodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayPeriodWidget()
}

/**
 * One period card.
 *
 * A card is a labelled block, never a bare number: the symbol with the period name, the local time
 * range and the temperature range are shown at every size, because "18–22 Uhr" is what tells the night
 * from the evening. Each of those lines holds at most two elements, which is what keeps a card that is
 * only half the widget wide readable - a weighted text never shares its line with another text and so
 * is never squeezed into a sliver. Values that were never reported show the app's en dash, not a zero.
 */
@Composable
private fun PeriodCardView(
    card: PeriodCard,
    sizeClass: WidgetSizeClass,
    modifier: GlanceModifier,
) {
    Column(
        modifier = modifier
            .background(WidgetPalette.tile)
            .cornerRadius(8.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            PeriodSymbol(card, sizeClass)
            Spacer(GlanceModifier.width(3.dp))
            PeriodName(card, sizeClass, GlanceModifier.defaultWeight())
            if (sizeClass == WidgetSizeClass.EXPANDED) {
                Spacer(GlanceModifier.width(3.dp))
                DayLabel(card, sizeClass)
            }
        }
        TimeRange(card, sizeClass, withDayLabel = shouldNameTheDayInTimeLine(card, sizeClass))
        // The range gets a line of its own: the formatter writes "-2 – -1 °C" with spaces around the
        // dash so a row of dashes cannot be misread, and that is exactly the widest string a card has
        // to show. Next to a symbol it would be cut off, and a clipped temperature is worse than a
        // card with one more line.
        PeriodRange(card, sizeClass)
        when (sizeClass) {
            // Symbol with name, time range and temperature: three lines are what a compact card holds.
            WidgetSizeClass.COMPACT -> Unit

            WidgetSizeClass.MEDIUM -> {
                Spacer(GlanceModifier.height(2.dp))
                if (card.hasData) {
                    PeriodNote(
                        card.precipitationSummary()
                            ?: widgetString(R.string.widget_period_no_precipitation),
                    )
                } else {
                    PeriodNote(widgetString(R.string.widget_period_no_data))
                }
            }

            WidgetSizeClass.EXPANDED -> {
                Spacer(GlanceModifier.height(2.dp))
                if (card.hasData) {
                    PeriodValueColumn(
                        label = widgetString(R.string.day_period_precipitation_probability),
                        value = WeatherFormatter.percent(card.precipitationProbabilityPercent),
                    )
                    PeriodValueColumn(
                        label = widgetString(R.string.day_period_precipitation),
                        value = WeatherFormatter.millimetres(card.precipitationMillimetres),
                    )
                    // Wind only appears in the largest size: at two by two cards it fits as one more
                    // line, in the smaller ones it would cost a line the precipitation values use better.
                    PeriodValueColumn(
                        label = widgetString(R.string.day_period_wind_gusts),
                        value = WeatherFormatter.windSpeed(card.maximumWindGustsKilometresPerHour),
                    )
                } else {
                    PeriodNote(widgetString(R.string.widget_period_no_data))
                }
            }
        }
    }
}

/** Name of the period ("Nachmittag"), the label that tells the four cards apart. */
@Composable
private fun PeriodName(
    card: PeriodCard,
    sizeClass: WidgetSizeClass,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text = widgetString(card.period.labelRes()),
        maxLines = 1,
        style = TextStyle(
            color = WidgetPalette.onSurface,
            fontSize = WidgetPalette.secondarySizeFor(sizeClass),
            fontWeight = FontWeight.Bold,
        ),
        modifier = modifier,
    )
}

/**
 * Local time range of the period - "18–22 Uhr", "22–06 Uhr" - which is what tells a night from an
 * evening. In the smaller cards the day the period belongs to is written in front of it, and only when
 * that day is *not* today: a rolling sequence always starts with the current period, so "today" is
 * already said by the absence of a label, and the word would cost width the values need.
 */
@Composable
private fun TimeRange(
    card: PeriodCard,
    sizeClass: WidgetSizeClass,
    withDayLabel: Boolean = false,
) {
    val time = widgetString(R.string.day_period_time_range, card.startHour, card.endHour)
    Text(
        text = if (withDayLabel) "${dayLabel(card)} · $time" else time,
        maxLines = 1,
        style = TextStyle(
            color = WidgetPalette.muted,
            fontSize = WidgetPalette.secondarySizeFor(sizeClass),
        ),
    )
}

/**
 * Whether the smaller cards write the day in front of the time range. An expanded card has width for a
 * separate word next to the period name instead; the smaller ones put the day where there is room, and
 * only for a day that is not today - a rolling sequence always starts with the current period, so
 * "today" is already said by the absence of a label.
 */
private fun shouldNameTheDayInTimeLine(card: PeriodCard, sizeClass: WidgetSizeClass): Boolean =
    sizeClass != WidgetSizeClass.EXPANDED && card.dayRelation != WidgetDayRelation.TODAY

/** Weather symbol of the period, with the wind glyph when its strongest wind crosses a threshold. */
@Composable
private fun PeriodSymbol(card: PeriodCard, sizeClass: WidgetSizeClass) {
    WidgetWeatherSymbol(
        iconRes = card.iconRes,
        windWarning = card.windWarning,
        size = WidgetPalette.rowIconSize(sizeClass),
        contentDescription = widgetString(card.conditionLabelRes),
    )
}

/** Temperature range of the period, on a line of its own so the widest string still fits. */
@Composable
private fun PeriodRange(card: PeriodCard, sizeClass: WidgetSizeClass) {
    Text(
        text = card.temperatureRange,
        maxLines = 1,
        style = TextStyle(
            color = WidgetPalette.onSurface,
            fontSize = WidgetPalette.bodySizeFor(sizeClass),
            fontWeight = FontWeight.Bold,
        ),
    )
}

/** "Heute" or "Morgen" of an expanded card, where the width is there for a separate word. */
@Composable
private fun DayLabel(card: PeriodCard, sizeClass: WidgetSizeClass) {
    Text(
        text = dayLabel(card),
        maxLines = 1,
        style = TextStyle(
            color = WidgetPalette.muted,
            fontSize = WidgetPalette.secondarySizeFor(sizeClass),
        ),
    )
}

/** One secondary line inside a card: the sentence for a period without data, or its summary. */
@Composable
private fun PeriodNote(text: String) {
    Text(
        text = text,
        maxLines = 2,
        style = TextStyle(
            color = WidgetPalette.muted,
            fontSize = WidgetPalette.secondarySizeFor(WidgetSizeClass.MEDIUM),
        ),
    )
}

/** Label above value, used by the two value columns of an expanded card. */
@Composable
private fun PeriodValueColumn(label: String, value: String) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = WidgetPalette.muted,
                fontSize = WidgetPalette.secondarySizeFor(WidgetSizeClass.EXPANDED),
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(
                color = WidgetPalette.onSurface,
                fontSize = WidgetPalette.secondarySizeFor(WidgetSizeClass.EXPANDED),
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** "Heute" or "Morgen" for the day the period belongs to, otherwise its weekday. */
@Composable
private fun dayLabel(card: PeriodCard): String =
    card.dayRelation.labelRes()?.let { widgetString(it) } ?: card.weekdayLabel

/** Cards side by side: two at every size, which is what keeps the four periods one grid. */
private const val CARDS_PER_ROW = 2
