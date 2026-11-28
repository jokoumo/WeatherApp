package com.example.weatherapp.widget

import com.example.weatherapp.domain.model.SavedLocation

/** Which location a widget instance displays. */
enum class WidgetLocationMode {
    /** Follow the app's home location. */
    HOME,

    /** Follow the location the app currently has selected. */
    SELECTED,

    /** Stay on one location that was chosen for this widget instance. */
    FIXED,
}

/**
 * Persisted settings of one widget instance.
 *
 * [widgetId] is the Android App Widget id, so every instance on the home screen owns its own entry
 * and two widgets of the same type can show different places. [fixedLocation] carries the whole
 * location - name, coordinates and time zone - rather than only a reference into the app's lists:
 * a fixed widget keeps working after the place has left the favourites and the history, which is
 * what makes it independent of the app's location management.
 */
data class WidgetConfiguration(
    val widgetId: Int,
    val mode: WidgetLocationMode = WidgetLocationMode.HOME,
    val fixedLocation: SavedLocation? = null,
) {
    /** True when this entry points at one specific place of its own. */
    val isFixed: Boolean get() = mode == WidgetLocationMode.FIXED

    /**
     * The location id a fixed entry pins down, or null while the mode follows the app. A fixed
     * entry whose location is missing is invalid and treated as such by [isUsable].
     */
    val fixedLocationId: String? get() = if (mode == WidgetLocationMode.FIXED) fixedLocation?.id else null

    /** A fixed entry without a location cannot show anything meaningful. */
    val isUsable: Boolean get() = mode != WidgetLocationMode.FIXED || fixedLocation != null
}
