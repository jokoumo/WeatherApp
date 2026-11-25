package com.example.weatherapp.data.local

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.weatherapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Persistent appearance preference.
 *
 * It is deliberately separate from [SettingsStore]: changing the theme must never rewrite the
 * saved locations, and the location code must never have to think about theme values. Both
 * stores share one DataStore file, and each of them only touches its own keys.
 */
interface ThemeStore {
    val themeMode: Flow<ThemeMode>

    /**
     * Writes [mode]. Returns `false` when the value could not be stored, so the caller can keep
     * the choice for the running session and say that it is not permanent instead of failing
     * silently.
     */
    suspend fun save(mode: ThemeMode): Boolean
}

class DataStoreThemeStore(
    private val dataStore: DataStore<Preferences>,
) : ThemeStore {

    override val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { throwable ->
            // A broken preferences file must not break the UI: fall back to the default mode.
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> decodeThemeMode(preferences[KEY_THEME_MODE]) }

    override suspend fun save(mode: ThemeMode): Boolean = try {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.name }
        true
    } catch (ioException: IOException) {
        Log.w(TAG, "Theme preference could not be stored", ioException)
        false
    }

    private companion object {
        const val TAG = "DataStoreThemeStore"
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}

/**
 * Reads a stored theme value. Kept outside the store so the fallback rules of a corrupted or
 * missing preference can be tested without any DataStore.
 */
internal fun decodeThemeMode(storedValue: String?): ThemeMode = ThemeMode.fromStoredValue(storedValue)
