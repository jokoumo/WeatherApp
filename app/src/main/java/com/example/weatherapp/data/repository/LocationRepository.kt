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

    /** Makes [location] the home location and selects it. */
    suspend fun setHomeLocation(location: SavedLocation): LocationSettings = mutate { current ->
        val withoutDuplicates = current.favorites.filterNot { it.isSamePlaceAs(location) }
        val updated = current.copy(
            homeLocation = location,
            favorites = withoutDuplicates,
            selectedLocationId = location.id,
        )
        updated.copy(selectedLocationId = keepValidSelection(current, updated))
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

    private suspend fun mutate(
        transform: (LocationSettings) -> LocationSettings,
    ): LocationSettings = mutationLock.withLock {
        val updated = transform(settingsStore.settings.first())
        settingsStore.save(updated)
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
