package com.example.weatherapp.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
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
import com.example.weatherapp.widget.presentation.CurrentWidgetContent
import com.example.weatherapp.widget.presentation.WidgetHeader
import com.example.weatherapp.widget.presentation.WidgetPalette
import com.example.weatherapp.widget.presentation.WidgetSizeClass
import com.example.weatherapp.widget.presentation.WidgetState
import com.example.weatherapp.widget.presentation.WidgetStateMessage
import com.example.weatherapp.widget.presentation.WidgetTile
import com.example.weatherapp.widget.presentation.WidgetWeatherSymbol
import com.example.weatherapp.widget.presentation.buildCurrentWidgetContent
import com.example.weatherapp.widget.presentation.widgetString

class CurrentWeatherWidget : GlanceWeatherWidget() {

    @Composable
    override fun WeatherContent(data: WidgetData?, sizeClass: WidgetSizeClass) {
        val content = buildCurrentWidgetContent(data, sizeClass)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            WidgetHeader(
                locationName = content.locationName,
                updatedAgo = content.updatedAgo,
                sizeClass = sizeClass,
                stale = data?.let { it.isShowingStaleData || it.isProbablyOutdated } == true,
            )
            if (content.state == WidgetState.READY) {
                WeatherBlock(content, sizeClass, GlanceModifier.defaultWeight())
                if (content.tiles.isNotEmpty()) {
                    Spacer(GlanceModifier.height(4.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        content.tiles.forEachIndexed { index, tile ->
                            if (index > 0) Spacer(GlanceModifier.width(4.dp))
                            WidgetTile(tile, sizeClass, GlanceModifier.defaultWeight())
                        }
                    }
                }
            } else {
                WidgetStateMessage(content.state, sizeClass, GlanceModifier.defaultWeight())
            }
        }
    }
}

class CurrentWeatherWidgetReceiver : WeatherWidgetReceiver() {
    override val glanceAppWidget = CurrentWeatherWidget()
}

@Composable
private fun WeatherBlock(
    content: CurrentWidgetContent,
    sizeClass: WidgetSizeClass,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                style = TextStyle(color = WidgetPalette.onSurface, fontSize = WidgetPalette.bodySizeFor(sizeClass)),
            )
        }
        Spacer(GlanceModifier.width(4.dp))
        WidgetWeatherSymbol(
            iconRes = content.iconRes,
            windWarning = content.windWarning,
            size = WidgetPalette.iconSize(sizeClass),
            contentDescription = widgetString(content.conditionLabelRes),
        )
    }
}
