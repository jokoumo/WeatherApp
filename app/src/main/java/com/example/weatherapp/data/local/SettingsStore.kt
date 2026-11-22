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
 * Persistent location settings: home location, ordered favorites and the selected location.
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
            preferences[KEY_FAVORITES] =
                json.encodeToString(ListSerializer(SavedLocation.serializer()), settings.favorites)
            preferences[KEY_SELECTED] = settings.selectedLocationId.orEmpty()
        }
    }

    private fun decode(preferences: Preferences): LocationSettings {
        val home = preferences[KEY_HOME]
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded -> runCatching { json.decodeFromString(SavedLocation.serializer(), encoded) }.getOrNull() }
        val favorites = preferences[KEY_FAVORITES]
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString(ListSerializer(SavedLocation.serializer()), encoded)
                }.getOrNull()
            }
            .orEmpty()
        val selected = preferences[KEY_SELECTED]?.takeIf { it.isNotBlank() }
        return LocationSettings(
            homeLocation = home,
            favorites = favorites,
            selectedLocationId = selected,
        )
    }

    companion object {
        private val KEY_HOME = stringPreferencesKey("home_location")
        private val KEY_FAVORITES = stringPreferencesKey("favorite_locations")
        private val KEY_SELECTED = stringPreferencesKey("selected_location_id")
    }
}
