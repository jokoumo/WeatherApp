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
 * being saved. [normalized] brings stored data back into that shape when it does not hold.
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

    /**
     * True when the app knows at least one place it can show, saved or only remembered.
     *
     * This is what decides whether the initial setup screen is needed: a state without a home
     * location but with favorites or history is unusual, not broken, and its places stay reachable.
     */
    val hasSelectableLocation: Boolean
        get() = selectableLocations.isNotEmpty()

    /**
     * Every location id whose cached forecast has to survive: the three roles plus the selection.
     * A cache row for an id outside this set can never be read again and is therefore orphaned.
     */
    val retainedLocationIds: Set<String>
        get() = buildSet {
            selectableLocations.forEach { add(it.id) }
            selectedLocationId?.let { add(it) }
        }

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

    /**
     * Repairs a settings object so that it satisfies the invariants the whole app relies on.
     * Stored data can predate those rules or be partially damaged, and every consumer would
     * otherwise have to defend itself against the same broken states.
     *
     * The rules, in this order:
     *
     * 1. the home location wins over every other role;
     * 2. a favorite wins over a history entry of the same place;
     * 3. duplicates are removed by [isSamePlaceAs], never by the display name, and the first
     *    occurrence is kept, which preserves the order the user gave the favorites and the
     *    most-recent-first order of the history;
     * 4. the history never exceeds [MAX_RECENT_LOCATIONS], dropping the least recent entries;
     * 5. a selection that no longer exists falls back to [resolveSelectedLocation].
     *
     * Nothing is invented and no unique valid entry is discarded, so an empty home location stays
     * empty rather than being filled with a guess. The function is deterministic and idempotent:
     * normalizing an already normalized value returns the same value.
     */
    fun normalized(): LocationSettings {
        val home = homeLocation
        val repairedFavorites = keepFirstOfEachPlace(
            favorites.filterNot { home != null && it.isSamePlaceAs(home) },
        )
        val repairedRecent = keepFirstOfEachPlace(
            recentLocations
                .filterNot { recent -> home != null && recent.isSamePlaceAs(home) }
                .filterNot { recent -> repairedFavorites.any { it.isSamePlaceAs(recent) } },
        ).take(MAX_RECENT_LOCATIONS)

        val repaired = LocationSettings(
            homeLocation = home,
            favorites = repairedFavorites,
            recentLocations = repairedRecent,
            selectedLocationId = selectedLocationId,
        )
        val selected = repaired.selectableLocations.firstOrNull { it.id == selectedLocationId }
        return repaired.copy(selectedLocationId = (selected ?: repaired.resolveSelectedLocation())?.id)
    }

    companion object {
        /** Upper bound of the history; the least recently used entry is dropped first. */
        const val MAX_RECENT_LOCATIONS = 30
    }
}

/**
 * Removes entries that describe a place an earlier entry already describes, keeping the first
 * occurrence. Identity is [isSamePlaceAs], so neither the display name nor the raw id alone
 * decides. Keeping the first occurrence is what makes the result deterministic and order stable.
 */
private fun keepFirstOfEachPlace(locations: List<SavedLocation>): List<SavedLocation> {
    val kept = ArrayList<SavedLocation>(locations.size)
    for (location in locations) {
        if (kept.none { it.isSamePlaceAs(location) }) kept += location
    }
    return kept
}
