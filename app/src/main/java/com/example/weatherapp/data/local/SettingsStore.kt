package com.example.weatherapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Persistent location settings: home location, ordered favorites, the recent locations history
 * and the selected location.
 *
 * [SettingsStore] keeps the repository free of Android types so that location management
 * can be tested with an in-memory implementation.
 */
interface SettingsStore {
    val settings: Flow<LocationSettings>
    suspend fun save(settings: LocationSettings)
}

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : SettingsStore {

    override val settings: Flow<LocationSettings> = dataStore.data
        .catch { throwable ->
            // Read failures must not break the UI: fall back to "no stored settings".
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map(::decode)

    override suspend fun save(settings: LocationSettings) {
        dataStore.edit { preferences ->
            preferences[KEY_HOME] = settings.homeLocation
                ?.let { json.encodeToString(SavedLocation.serializer(), it) }
                .orEmpty()
            preferences[KEY_FAVORITES] = encodeLocations(settings.favorites)
            preferences[KEY_RECENT] = encodeLocations(settings.recentLocations)
            preferences[KEY_SELECTED] = settings.selectedLocationId.orEmpty()
        }
    }

    private fun decode(preferences: Preferences): LocationSettings {
        val home = preferences[KEY_HOME]
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded -> runCatching { json.decodeFromString(SavedLocation.serializer(), encoded) }.getOrNull() }
        val favorites = decodeLocations(preferences[KEY_FAVORITES])
        // Older builds never wrote KEY_RECENT: a missing value simply means "no history yet",
        // which is what keeps the new format readable for installations that already exist.
        val recent = decodeLocations(preferences[KEY_RECENT])
        val selected = preferences[KEY_SELECTED]?.takeIf { it.isNotBlank() }
        return LocationSettings(
            homeLocation = home,
            favorites = favorites,
            recentLocations = recent,
            selectedLocationId = selected,
        )
    }

    private fun encodeLocations(locations: List<SavedLocation>): String =
        json.encodeToString(ListSerializer(SavedLocation.serializer()), locations)

    /** A damaged list is treated as empty instead of failing the whole settings file. */
    private fun decodeLocations(encoded: String?): List<SavedLocation> =
        encoded
            ?.takeIf { it.isNotBlank() }
            ?.let { value ->
                runCatching {
                    json.decodeFromString(ListSerializer(SavedLocation.serializer()), value)
                }.getOrNull()
            }
            .orEmpty()

    companion object {
        private val KEY_HOME = stringPreferencesKey("home_location")
        private val KEY_FAVORITES = stringPreferencesKey("favorite_locations")
        private val KEY_RECENT = stringPreferencesKey("recent_locations")
        private val KEY_SELECTED = stringPreferencesKey("selected_location_id")
    }
}
