package com.example.weatherapp.data.repository

import com.example.weatherapp.data.local.SettingsStore
import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.remote.OpenMeteoGeocodingApi
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.isSamePlaceAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Outcome of a location search: results plus an optional failure reason. */
data class LocationSearchResult(
    val query: String,
    val locations: List<SavedLocation> = emptyList(),
    val failure: DataLoadFailure? = null,
) {
    val isEmpty: Boolean get() = locations.isEmpty() && failure == null
}

/**
 * Owns all location related state: home location, ordered favorites and the selected
 * location, and performs location search against the Open-Meteo geocoding API.
 *
 * Mutations are serialised with a mutex so that rapid user actions cannot overwrite each
 * other.
 */
class LocationRepository(
    private val settingsStore: SettingsStore,
    private val geocodingApi: OpenMeteoGeocodingApi,
    private val mapper: ForecastMapper = ForecastMapper(),
) {

    val settings: Flow<LocationSettings> = settingsStore.settings

    private val mutationLock = Mutex()

    /** Reads the currently persisted settings without collecting the flow. */
    suspend fun currentSettings(): LocationSettings = settingsStore.settings.first()

    suspend fun searchLocations(rawQuery: String): LocationSearchResult {
        val query = rawQuery.trim()
        if (query.isBlank()) return LocationSearchResult(query = query)
        return try {
            val response = geocodingApi.search(name = query)
            if (!response.isSuccessful) {
                return LocationSearchResult(query, failure = mapHttpFailure(response.raw().code))
            }
            val results = response.body()?.results.orEmpty()
            LocationSearchResult(query = query, locations = mapper.toLocations(results))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ioException: IOException) {
            LocationSearchResult(query, failure = mapIoFailure(ioException))
        } catch (unexpected: Exception) {
            LocationSearchResult(query, failure = DataLoadFailure.INVALID_DATA)
        }
    }

    /**
     * Makes [location] the home location and selects it.
     *
     * The previous home location is never dropped: when [location] was a favorite, the two swap,
     * so the previous home takes over the exact slot the new home held in the favorites list.
     * A copy of [location] that survived in an older persisted state is removed from the other
     * slot, which keeps the number of distinct saved locations unchanged and never duplicates a
     * place. Identity is decided by [isSamePlaceAs], not by the display name.
     */
    suspend fun setHomeLocation(location: SavedLocation): LocationSettings = mutate { current ->
        val previousHome = current.homeLocation
        val favoriteIndex = current.favorites.indexOfFirst { it.isSamePlaceAs(location) }
        // Drop the new home from the favorites, and any stale copy of the old home that a
        // previous version of the persisted state may have left behind.
        val favorites = current.favorites
            .filterNot { it.isSamePlaceAs(location) }
            .filterNot { other -> previousHome != null && other.isSamePlaceAs(previousHome) }
            .toMutableList()
        if (previousHome != null && !previousHome.isSamePlaceAs(location)) {
            // The old home moves into the slot the new home just left, or to the end when the
            // new home was not a favorite before.
            val insertAt = if (favoriteIndex >= 0) favoriteIndex.coerceAtMost(favorites.size) else favorites.size
            favorites.add(insertAt, previousHome)
        }
        current.copy(
            homeLocation = location,
            favorites = favorites,
            // This is an explicit user action, so the new home location is what the dashboard has
            // to show afterwards - [keepValidSelection] must not restore the old selection here.
            selectedLocationId = location.id,
        )
    }

    /**
     * Adds [location] to the favorites.
     *
     * @return false when the location is already stored as home location or favorite.
     */
    suspend fun addFavorite(location: SavedLocation): Boolean {
        var added = false
        mutate { current ->
            if (current.selectableLocations.any { it.isSamePlaceAs(location) }) {
                current
            } else {
                added = true
                current.copy(favorites = current.favorites + location)
            }
        }
        return added
    }

    suspend fun removeFavorite(locationId: String): LocationSettings = mutate { current ->
        val updated = current.copy(favorites = current.favorites.filterNot { it.id == locationId })
        updated.copy(selectedLocationId = keepValidSelection(current, updated))
    }

    /** Removes a location from favorites or clears the home location. */
    suspend fun removeLocation(locationId: String): LocationSettings = mutate { current ->
        val updated = if (current.homeLocation?.id == locationId) {
            current.copy(homeLocation = null)
        } else {
            current.copy(favorites = current.favorites.filterNot { it.id == locationId })
        }
        updated.copy(selectedLocationId = keepValidSelection(current, updated))
    }

    /**
     * Moves a favorite [delta] positions towards the top of the list.
     * The home location is fixed and cannot be reordered.
     */
    suspend fun moveFavorite(locationId: String, delta: Int): LocationSettings = mutate { current ->
        val favorites = current.favorites.toMutableList()
        val from = favorites.indexOfFirst { it.id == locationId }
        if (from < 0) return@mutate current
        val to = (from + delta).coerceIn(0, favorites.lastIndex)
        if (to == from) return@mutate current
        val moved = favorites.removeAt(from)
        favorites.add(to, moved)
        current.copy(favorites = favorites)
    }

    /** Selects an already stored location. Unknown ids are ignored. */
    suspend fun selectLocation(locationId: String): LocationSettings = mutate { current ->
        if (current.selectableLocations.any { it.id == locationId }) {
            current.copy(selectedLocationId = locationId)
        } else {
            current
        }
    }

    /**
     * Applies [transform] to the latest persisted state under [mutationLock] and persists the
     * result. A transform that changes nothing (rapid repeated taps, an unknown id) does not
     * write to storage at all.
     */
    private suspend fun mutate(
        transform: (LocationSettings) -> LocationSettings,
    ): LocationSettings = mutationLock.withLock {
        val current = settingsStore.settings.first()
        val updated = transform(current)
        if (updated != current) {
            settingsStore.save(updated)
        }
        updated
    }

    /** Keeps the current selection when it still exists, otherwise falls back to home. */
    private fun keepValidSelection(
        previous: LocationSettings,
        updated: LocationSettings,
    ): String? {
        val selected = previous.selectedLocationId
        if (updated.selectableLocations.any { it.id == selected }) return selected
        return updated.homeLocation?.id ?: updated.favorites.firstOrNull()?.id
    }
}
