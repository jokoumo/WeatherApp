package com.example.weatherapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.example.weatherapp.R
import com.example.weatherapp.WeatherApplication
import com.example.weatherapp.widget.presentation.WidgetSizeClass
import com.example.weatherapp.widget.presentation.WidgetSizes
import com.example.weatherapp.widget.presentation.WidgetSurface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

abstract class GlanceWeatherWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_unavailable) {

    override val sizeMode: SizeMode = SizeMode.Exact

    final override suspend fun provideGlance(context: Context, id: GlanceId): Unit = coroutineScope {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val container = (context.applicationContext as WeatherApplication).container
        val rendering = container.widgetDataProvider.rendering(widgetId).catch { failure ->
            Log.w(TAG, "Could not read widget data", failure)
            emit(WidgetDataProvider.Rendering(null, null))
        }.stateIn(this)

        provideContent {
            val state by rendering.collectAsState()
            val sizeClass = WidgetSizes.sizeClassFor(LocalSize.current)
            val configuration = state.configuration
            val surface = if (configuration != null) {
                GlanceModifier.clickable(
                    actionStartActivity(WidgetLaunch.intentFor(context, widgetId, configuration, state.data?.location)),
                )
            } else {
                GlanceModifier
            }
            WidgetSurface(modifier = surface) {
                WeatherContent(state.data, sizeClass)
            }
        }
    }

    final override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val container = (context.applicationContext as WeatherApplication).container
        container.widgetRefreshCoordinator.onWidgetRemoved(listOf(widgetId))
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        // The launcher can remove an instance between Glance composing and publishing its views.
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId) != null) {
            super.onCompositionError(context, glanceId, appWidgetId, throwable)
        }
    }

    @Composable
    abstract fun WeatherContent(data: WidgetData?, sizeClass: WidgetSizeClass)

    private companion object {
        const val TAG = "GlanceWeatherWidget"
    }
}

abstract class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (appWidgetIds.isNotEmpty()) {
            val container = (context.applicationContext as WeatherApplication).container
            container.widgetRefreshCoordinator.onLauncherUpdate()
        }
    }
}
