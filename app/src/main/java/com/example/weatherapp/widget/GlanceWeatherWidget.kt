package com.example.weatherapp.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.example.weatherapp.WeatherApplication
import com.example.weatherapp.widget.presentation.WidgetSizeClass
import com.example.weatherapp.widget.presentation.WidgetSizes
import com.example.weatherapp.widget.presentation.WidgetState
import com.example.weatherapp.widget.presentation.WidgetStateMessage
import com.example.weatherapp.widget.presentation.WidgetSurface

/**
 * What the three widgets have in common: they belong to one App Widget id, they show the weather of
 * the location that id was configured for, and they lay out for the size the launcher really gives
 * them.
 *
 * The order of [provideGlance] matters. Data is loaded *before* the composition starts, because the
 * composition runs on the widget's own clock and has no business waiting for a database or a network
 * call. Everything that can fail - a missing configuration, a broken cache row, a location that no
 * longer exists - is turned into a state the layout can describe, so a widget shows a German sentence
 * instead of an empty surface and the launcher never receives a half-built layout.
 *
 * [SizeMode.Responsive] is what makes the widgets resizable: Glance composes the content once per
 * declared size and hands the set to the system, which picks the layout that fits. [LocalSize] then
 * says which of those compositions is being built, and [WidgetSizes] turns it into a size class.
 */
abstract class GlanceWeatherWidget(
    private val offeredSizes: Set<DpSize>,
) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(offeredSizes)

    final override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }
            .onFailure { Log.i(TAG, "This composition belongs to a widget the launcher has not bound yet") }
            .getOrNull()
        val container = (context.applicationContext as? WeatherApplication)?.container

        val configuration = widgetId?.let { id ->
            runCatching { container?.widgetConfigRepository?.configuration(id) }
                .onFailure { Log.w(TAG, "Widget configuration could not be read", it) }
                .getOrNull()
        }
        val data = widgetId?.let { id ->
            runCatching { container?.widgetDataProvider?.loadForWidget(id) }
                .onFailure { Log.w(TAG, "Widget weather could not be loaded", it) }
                .getOrNull()
        }
        val launchIntent: Intent? = widgetId?.let { id ->
            runCatching { WidgetLaunch.intentFor(context, id, configuration, data?.location) }
                .onFailure { Log.w(TAG, "The tap target of widget $id could not be built", it) }
                .getOrNull()
        }

        provideContent {
            val sizeClass = WidgetSizes.sizeClassFor(LocalSize.current)
            if (widgetId == null || configuration == null) {
                // Nothing is known about this instance yet - most likely because the launcher composed
                // it while the user was still choosing its location, before this screen had confirmed
                // anything. "No location configured" would be a claim this composition cannot make: an
                // unconfigured widget is removed by Android, so the only real state here is "not drawn
                // from a configuration yet". The loading line is what is true; a later composition that
                // carries the bound id - the refresh this screen asks for, a launcher re-bind, or the
                // periodic job - draws the weather into it.
                WidgetSurface {
                    WidgetStateMessage(WidgetState.LOADING, sizeClass)
                }
            } else {
                val clickableSurface = launchIntent
                    ?.let { GlanceModifier.clickable(actionStartActivity(it)) }
                    ?: GlanceModifier
                WidgetSurface(modifier = clickableSurface) {
                    WeatherContent(data, sizeClass)
                }
            }
        }
    }

    /**
     * A deleted widget loses its configuration.
     *
     * Nothing else is deleted: the forecast cache is shared, and the place this widget showed may
     * still be the app's home location, a favourite, or the target of another widget.
     */
    final override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }
            .getOrNull() ?: return
        val container = (context.applicationContext as? WeatherApplication)?.container ?: return
        runCatching { container.widgetConfigRepository.remove(listOf(widgetId)) }
            .onFailure { Log.w(TAG, "Widget configuration could not be removed", it) }
        runCatching { container.widgetRefreshCoordinator.onWidgetRemoved(listOf(widgetId)) }
            .onFailure { Log.w(TAG, "Widget scheduling could not be updated", it) }
    }

    /** The widget's own content, drawn inside the shared surface. */
    @Composable
    abstract fun WeatherContent(data: WidgetData?, sizeClass: WidgetSizeClass)
}

private const val TAG = "GlanceWeatherWidget"
