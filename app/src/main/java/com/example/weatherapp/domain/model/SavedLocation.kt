package com.example.weatherapp.domain.model

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * A location the user can select for weather forecasts.
 *
 * Instances are either the home location or a favorite location and are persisted
 * locally. [id] is stable for a given geocoding result so that duplicates can be
 * detected reliably.
 */
@Serializable
data class SavedLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val timeZoneId: String = DEFAULT_TIME_ZONE_ID,
) {
    /** Secondary region line, for example "Berlin" or "Upper Austria". */
    val regionLabel: String?
        get() = admin1?.takeIf { it.isNotBlank() && it != name }

    /** "Berlin, Deutschland" style label used in lists. */
    val subtitle: String
        get() = listOfNotNull(regionLabel, country).joinToString(", ")

    /** Coordinates rounded to ~11 m, used to detect the same place entered twice. */
    val coordinateKey: String
        get() = "${formatCoordinate(latitude)},${formatCoordinate(longitude)}"

    companion object {
        const val DEFAULT_TIME_ZONE_ID = "UTC"
    }
}

/** Returns true when both entries describe the same place on earth. */
fun SavedLocation.isSamePlaceAs(other: SavedLocation): Boolean =
    id == other.id || coordinateKey == other.coordinateKey

private fun formatCoordinate(value: Double): String =
    String.format(Locale.ROOT, "%.4f", value)
