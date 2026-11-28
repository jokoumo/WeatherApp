package com.example.weatherapp.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.glance.appwidget.GlanceAppWidget
import com.example.weatherapp.MainActivity

/** Widget taps carry only the instance ID. Coordinates and modes come from its stored configuration. */
object WidgetLaunch {
    private const val EXTRA_WIDGET_ID = "com.example.weatherapp.extra.WIDGET_ID"

    fun intentFor(context: Context, widgetId: Int): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(context, MainActivity::class.java))
        .apply {
            data = "wetterplus://widget/$widgetId".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_WIDGET_ID, widgetId)
        }

    internal suspend fun resolveConfiguration(
        intent: Intent?,
        liveWidgetIds: Set<Int>,
        repository: WidgetConfigRepository,
    ): WidgetConfiguration? {
        val widgetId = widgetIdFrom(intent) ?: return null
        if (widgetId !in liveWidgetIds) return null
        return repository.configuration(widgetId)
    }

    internal fun widgetIdFrom(intent: Intent?): Int? =
        intent?.getIntExtra(EXTRA_WIDGET_ID, -1)?.takeIf { it > 0 }
}

/**
 * Reads which widget instances the launcher currently holds.
 *
 * Android hands out widget ids and takes them back, but a deletion is not always announced - a
 * crashed launcher, a restored device. This is the reconciliation the widget layer needs: it prunes
 * stored configurations and decides whether background work is still worth running at all.
 */
object WidgetRegistry {

    /** The three widget types of the app. */
    private val receivers: List<Class<out androidx.glance.appwidget.GlanceAppWidgetReceiver>> = listOf(
        CurrentWeatherWidgetReceiver::class.java,
        DayPeriodWidgetReceiver::class.java,
        ThreeDayWidgetReceiver::class.java,
    )

    /** Every live App Widget id of the three WetterPlus widget types. */
    fun liveWidgetIds(context: Context): Set<Int> {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        return receivers.flatMap { receiver ->
            runCatching { manager.getAppWidgetIds(ComponentName(context, receiver)).toList() }
                .getOrDefault(emptyList())
        }.toSet()
    }

    /** True while at least one WetterPlus widget exists on some screen. */
    fun hasWidgets(context: Context): Boolean = liveWidgetIds(context).isNotEmpty()

    /**
     * The widget content behind a provider class name, the way the App Widget service reports it.
     * Needed to redraw exactly one instance instead of every widget of every type.
     */
    fun widgetFor(providerClassName: String?): GlanceAppWidget? = when (providerClassName) {
        CurrentWeatherWidgetReceiver::class.java.name -> CurrentWeatherWidget()
        DayPeriodWidgetReceiver::class.java.name -> DayPeriodWidget()
        ThreeDayWidgetReceiver::class.java.name -> ThreeDayWidget()
        else -> null
    }
}
