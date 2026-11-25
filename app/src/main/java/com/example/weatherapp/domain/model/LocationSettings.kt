package com.example.weatherapp.domain.model

/**
 * Location related user settings, persisted with DataStore.
 *
 * The home location is kept separately from [favorites] so that the user can always
 * return to it. A location is never stored twice: it is either the home location or a
 * favorite, never both.
 */
data class LocationSettings(
    val homeLocation: SavedLocation? = null,
    val favorites: List<SavedLocation> = emptyList(),
    val selectedLocationId: String? = null,
) {
    /** Home location first, then favorites in the order chosen by the user. */
    val selectableLocations: List<SavedLocation>
        get() = listOfNotNull(homeLocation) + favorites

    val hasAnyLocation: Boolean
        get() = homeLocation != null || favorites.isNotEmpty()

    /** The location the dashboard should show, falling back to the home location. */
    fun resolveSelectedLocation(): SavedLocation? =
        selectableLocations.firstOrNull { it.id == selectedLocationId }
            ?: homeLocation
            ?: favorites.firstOrNull()
}
