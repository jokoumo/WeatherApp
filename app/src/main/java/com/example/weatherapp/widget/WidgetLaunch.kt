package com.example.weatherapp.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.glance.appwidget.GlanceAppWidget
import com.example.weatherapp.MainActivity
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.zoneIdOrDefault

/**
 * The intent a widget sends when it is tapped.
 *
 * Two rules decide its content:
 *
 * - a widget that follows the app (home or selected location) sends its **mode** but no location. The
 *   app knows that place itself, so a tap selects the home location when that differs from the current
 *   selection and never writes a new one;
 * - a fixed-location widget sends the location itself - id, name, coordinates and time zone - because
 *   that place may have left the favourites and the history long ago. The app can then show it
 *   without a search request and without ever touching the home location.
 *
 * Every widget instance also carries a unique `data` uri. Two intents that differ only in their
 * extras are equal for [Intent.filterEquals], and PendingIntents are identified that way: without the
 * uri, the tap of the second widget could deliver the extras of the first one.
 */
object WidgetLaunch {

    const val EXTRA_WIDGET_ID = "com.example.weatherapp.extra.WIDGET_ID"
    const val EXTRA_LOCATION_MODE = "com.example.weatherapp.extra.WIDGET_LOCATION_MODE"
    const val EXTRA_LOCATION_ID = "com.example.weatherapp.extra.WIDGET_LOCATION_ID"
    const val EXTRA_LOCATION_NAME = "com.example.weatherapp.extra.WIDGET_LOCATION_NAME"
    const val EXTRA_LOCATION_LATITUDE = "com.example.weatherapp.extra.WIDGET_LOCATION_LATITUDE"
    const val EXTRA_LOCATION_LONGITUDE = "com.example.weatherapp.extra.WIDGET_LOCATION_LONGITUDE"
    const val EXTRA_LOCATION_TIME_ZONE = "com.example.weatherapp.extra.WIDGET_LOCATION_TIME_ZONE"

    /** Upper bounds of the incoming values; a widget intent arrives from outside this process. */
    private const val MAX_ID_LENGTH = 64
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_TIME_ZONE_LENGTH = 64

    /**
     * Launch intent of one widget instance. [configuration] may be null while a widget has not been
     * configured yet; that behaves like the home-location mode and sends no location.
     */
    fun intentFor(
        context: Context,
        widgetId: Int,
        configuration: WidgetConfiguration?,
        location: SavedLocation?,
    ): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(context, MainActivity::class.java))
        .apply {
            data = "wetterplus://widget/$widgetId".toUri()
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(EXTRA_WIDGET_ID, widgetId)
            putExtra(EXTRA_LOCATION_MODE, (configuration?.mode ?: WidgetLocationMode.HOME).name)
            if (configuration != null && location != null && configuration.isFixed) {
                putExtra(EXTRA_LOCATION_ID, location.id)
                putExtra(EXTRA_LOCATION_NAME, location.name)
                putExtra(EXTRA_LOCATION_LATITUDE, location.latitude)
                putExtra(EXTRA_LOCATION_LONGITUDE, location.longitude)
                putExtra(EXTRA_LOCATION_TIME_ZONE, location.timeZoneId)
            }
        }

    /**
     * Which kind of widget opened the app, or null for a normal launch.
     *
     * An unknown mode is treated as no request at all: an intent this app did not create has no say
     * in which location the app shows.
     */
    fun locationModeFrom(intent: Intent?): WidgetLocationMode? =
        intent?.getStringExtra(EXTRA_LOCATION_MODE)?.let { name ->
            WidgetLocationMode.entries.firstOrNull { it.name == name }
        }

    /**
     * The fixed location a tap asks for, or null when the intent asks for nothing - which covers a
     * plain app launch, a home or selected widget, and an intent whose values cannot be trusted.
     *
     * Invalid input is dropped rather than repaired: an out-of-range coordinate or an oversized name
     * would otherwise end up as a location entry the user never chose.
     */
    fun locationFrom(intent: Intent?): SavedLocation? {
        val id = intent?.getStringExtra(EXTRA_LOCATION_ID)?.takeIf { isValidText(it, MAX_ID_LENGTH) }
            ?: return null
        val name = intent.getStringExtra(EXTRA_LOCATION_NAME)?.takeIf { isValidText(it, MAX_NAME_LENGTH) }
            ?: return null
        val latitude = intent.getDoubleExtra(EXTRA_LOCATION_LATITUDE, Double.NaN)
            .takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val longitude = intent.getDoubleExtra(EXTRA_LOCATION_LONGITUDE, Double.NaN)
            .takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        val timeZoneId = intent.getStringExtra(EXTRA_LOCATION_TIME_ZONE)
            ?.takeIf { isValidText(it, MAX_TIME_ZONE_LENGTH) }
            ?: SavedLocation.DEFAULT_TIME_ZONE_ID
        return SavedLocation(
            id = id,
            name = name,
            latitude = latitude,
            longitude = longitude,
            timeZoneId = zoneIdOrDefault(timeZoneId, SavedLocation.DEFAULT_TIME_ZONE_ID).id,
        )
    }

    /** The widget id a tap came from, or null for a normal launch. */
    fun widgetIdFrom(intent: Intent?): Int? = intent?.getIntExtra(EXTRA_WIDGET_ID, -1)
        ?.takeIf { it >= 0 }

    private fun isValidText(value: String, maxLength: Int): Boolean =
        value.isNotBlank() && value.length <= maxLength
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
