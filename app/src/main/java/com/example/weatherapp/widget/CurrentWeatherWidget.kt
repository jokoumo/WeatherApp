package com.example.weatherapp.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.weatherapp.R
import com.example.weatherapp.widget.presentation.CurrentWidgetContent
import com.example.weatherapp.widget.presentation.WidgetHeader
import com.example.weatherapp.widget.presentation.WidgetPalette
import com.example.weatherapp.widget.presentation.WidgetSizeClass
import com.example.weatherapp.widget.presentation.WidgetSizes
import com.example.weatherapp.widget.presentation.WidgetStateMessage
import com.example.weatherapp.widget.presentation.WidgetTile
import com.example.weatherapp.widget.presentation.WidgetWeatherSymbol
import com.example.weatherapp.widget.presentation.buildCurrentWidgetContent
import com.example.weatherapp.widget.presentation.messageResOrNull
import com.example.weatherapp.widget.presentation.tileCountFor
import com.example.weatherapp.widget.presentation.widgetString

/**
 * The current weather widget: the detailed one.
 *
 * One header line, one prominent weather block, and below it as many information tiles as the data
 * and the size both allow - up to three rows of three. The choice of metrics happens in
 * `buildCurrentWidgetContent`; this file only arranges what it is given, which is why the same nine
 * metrics can be tested without a launcher and still adapt to whatever the API happened to report.
 */
class CurrentWeatherWidget : GlanceWeatherWidget(WidgetSizes.currentWeatherSizes) {

    @Composable
    override fun WeatherContent(data: WidgetData?, sizeClass: WidgetSizeClass) {
        val content = buildCurrentWidgetContent(data, sizeClass)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeader(
                locationName = content.locationName,
                updatedAgo = content.updatedAgo,
                sizeClass = sizeClass,
                stale = data?.isProbablyOutdated == true,
            )
            Spacer(GlanceModifier.height(4.dp))
            content.state.messageResOrNull()?.let {
                WidgetStateMessage(content.state, sizeClass, GlanceModifier.defaultWeight())
            } ?: WeatherBlock(content, sizeClass)
            InformationTiles(content, sizeClass)
        }
    }
}

/** The current weather widget as it appears in the Android widget picker. */
class CurrentWeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrentWeatherWidget()
}

/** Temperature, condition, feels-like and the large symbol. */
@Composable
private fun WeatherBlock(content: CurrentWidgetContent, sizeClass: WidgetSizeClass) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = content.temperature,
                maxLines = 1,
                style = TextStyle(
                    color = WidgetPalette.onSurface,
                    fontSize = WidgetPalette.temperatureSize(sizeClass),
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = widgetString(content.conditionLabelRes),
                maxLines = 2,
                style = TextStyle(
                    color = WidgetPalette.onSurface,
                    fontSize = WidgetPalette.bodySizeFor(sizeClass),
                ),
            )
            if (sizeClass != WidgetSizeClass.COMPACT) {
                content.feelsLike?.let {
                    Text(
                        text = widgetString(R.string.feels_like_value, it),
                        maxLines = 1,
                        style = TextStyle(
                            color = WidgetPalette.muted,
                            fontSize = WidgetPalette.secondarySizeFor(sizeClass),
                        ),
                    )
                }
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Column {
            WidgetWeatherSymbol(
                iconRes = content.iconRes,
                windWarning = content.windWarning,
                size = WidgetPalette.iconSize(sizeClass),
                contentDescription = widgetString(content.conditionLabelRes),
            )
            // The compact size has no tile grid, so the two most important values sit here instead.
            if (sizeClass == WidgetSizeClass.COMPACT) {
                content.headlineValues.forEach { value ->
                    Text(
                        text = value,
                        maxLines = 1,
                        style = TextStyle(
                            color = WidgetPalette.muted,
                            fontSize = WidgetPalette.secondarySizeFor(sizeClass),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The information tiles below the weather block, in rows of three or of two.
 *
 * Nothing is padded out: a row is drawn for the tiles that exist, and a widget whose API response had
 * no humidity simply has one tile less.
 */
@Composable
private fun InformationTiles(content: CurrentWidgetContent, sizeClass: WidgetSizeClass) {
    if (tileCountFor(sizeClass) == 0 || content.tiles.isEmpty()) return
    val columns = if (sizeClass == WidgetSizeClass.EXPANDED) TILE_COLUMNS_EXPANDED else TILE_COLUMNS_MEDIUM
    content.tiles.chunked(columns).forEach { row ->
        Spacer(GlanceModifier.height(4.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            row.forEach { tile ->
                WidgetTile(
                    tile = tile,
                    sizeClass = sizeClass,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(4.dp))
            }
            // A short last row gets empty weighted columns, so its tiles stay exactly as wide as the
            // tiles of the full rows above it.
            repeat(columns - row.size) {
                Spacer(GlanceModifier.defaultWeight().height(1.dp))
                Spacer(GlanceModifier.width(4.dp))
            }
        }
    }
}

/** Columns of the expanded tile grid. */
private const val TILE_COLUMNS_EXPANDED = 3

/** Columns of the medium tile grid. */
private const val TILE_COLUMNS_MEDIUM = 2
