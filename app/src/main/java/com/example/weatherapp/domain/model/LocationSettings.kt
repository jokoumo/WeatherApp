package com.example.weatherapp.domain.model

/**
 * Location related user settings, persisted with DataStore.
 *
 * Three roles are kept apart on purpose:
 *
 * - [homeLocation] is the protected default location, marked with a house in the UI.
 * - [favorites] are locations the user explicitly saved, marked with a star.
 * - [recentLocations] remembers places that were only looked at, most recent first.
 *
 * A location occupies exactly one of these roles, so it is never stored twice. The
 * [selectedLocationId] is independent of all three: any known location can be displayed without
 * being saved.
 */
data class LocationSettings(
    val homeLocation: SavedLocation? = null,
    val favorites: List<SavedLocation> = emptyList(),
    val recentLocations: List<SavedLocation> = emptyList(),
    val selectedLocationId: String? = null,
) {
    /** Home location first, then favorites in the order chosen by the user. */
    val savedLocations: List<SavedLocation>
        get() = listOfNotNull(homeLocation) + favorites

    /** Everything the app knows about: the saved locations plus the history. */
    val selectableLocations: List<SavedLocation>
        get() = savedLocations + recentLocations

    /** True while at least one location is *saved*; history alone does not count. */
    val hasAnyLocation: Boolean
        get() = homeLocation != null || favorites.isNotEmpty()

    /** Whether [locationId] belongs to the favorites; the home location is not a favorite. */
    fun isFavorite(locationId: String): Boolean = favorites.any { it.id == locationId }

    /** Whether [locationId] is the protected home location. */
    fun isHomeLocation(locationId: String): Boolean = homeLocation?.id == locationId

    /** Whether [locationId] is only remembered in the history. */
    fun isRecent(locationId: String): Boolean = recentLocations.any { it.id == locationId }

    /** The stored copy of [locationId], regardless of which role it has. */
    fun findLocation(locationId: String): SavedLocation? =
        selectableLocations.firstOrNull { it.id == locationId }

    /**
     * The location the dashboard should show. A selection that is still known wins; otherwise the
     * home location is the fallback, then a favorite, and only as a last resort a history entry.
     */
    fun resolveSelectedLocation(): SavedLocation? =
        selectableLocations.firstOrNull { it.id == selectedLocationId }
            ?: homeLocation
            ?: favorites.firstOrNull()
            ?: recentLocations.firstOrNull()

    companion object {
        /** Upper bound of the history; the least recently used entry is dropped first. */
        const val MAX_RECENT_LOCATIONS = 30
    }
}
