package com.example.weatherapp.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.weatherapp.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Persistence of the per-widget configuration, keyed by App Widget id.
 *
 * The store keeps the widget layer free of Android storage types, so location resolution and cache
 * retention can be tested against an in-memory implementation - the same reason
 * [com.example.weatherapp.data.local.SettingsStore] is an interface.
 */
interface WidgetConfigStore {
    /** Every stored configuration, keyed by widget id. */
    val configurations: Flow<Map<Int, WidgetConfiguration>>

    /** Adds or replaces the entries of [configurations]; every other entry stays as it is. */
    suspend fun saveAll(configurations: Collection<WidgetConfiguration>)

    /** Removes the entries of [widgetIds]; ids that are not stored are ignored. */
    suspend fun remove(widgetIds: Collection<Int>)
}

/**
 * DataStore-backed [WidgetConfigStore].
 *
 * All entries live in one JSON value instead of one preference key per widget: the whole set has to
 * be read for cache retention anyway, and one value keeps "drop every id that disappeared" simple.
 * A damaged value reads as "no configurations" instead of failing the file, which is how stored
 * locations are handled as well - a broken entry must never take the widgets of the user down.
 */
class DataStoreWidgetConfigStore(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : WidgetConfigStore {

    override val configurations: Flow<Map<Int, WidgetConfiguration>> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map(::decode)

    override suspend fun saveAll(configurations: Collection<WidgetConfiguration>) {
        if (configurations.isEmpty()) return
        dataStore.edit { preferences ->
            val entries = decode(preferences).toMutableMap()
            configurations.forEach { entries[it.widgetId] = it }
            preferences[KEY_CONFIGURATIONS] = encode(entries.values)
        }
    }

    override suspend fun remove(widgetIds: Collection<Int>) {
        if (widgetIds.isEmpty()) return
        dataStore.edit { preferences ->
            val existing = decode(preferences)
            val remaining = existing.filterKeys { it !in widgetIds }
            if (remaining.size == existing.size) return@edit
            if (remaining.isEmpty()) {
                preferences.remove(KEY_CONFIGURATIONS)
            } else {
                preferences[KEY_CONFIGURATIONS] = encode(remaining.values)
            }
        }
    }

    private fun decode(preferences: Preferences): Map<Int, WidgetConfiguration> {
        val raw = preferences[KEY_CONFIGURATIONS]?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return runCatching {
            json.decodeFromString(ListSerializer(StoredWidgetConfiguration.serializer()), raw)
        }.getOrNull().orEmpty()
            .mapNotNull(StoredWidgetConfiguration::toConfiguration)
            .associateBy { it.widgetId }
    }

    private fun encode(configurations: Collection<WidgetConfiguration>): String =
        json.encodeToString(
            ListSerializer(StoredWidgetConfiguration.serializer()),
            configurations.map(StoredWidgetConfiguration::from),
        )

    companion object {
        private val KEY_CONFIGURATIONS = stringPreferencesKey("widget_configurations")
    }
}

/** On-disk form of [WidgetConfiguration]: the mode is stored by name so it survives renames. */
@Serializable
private data class StoredWidgetConfiguration(
    val widgetId: Int,
    val mode: String,
    val fixedLocation: SavedLocation? = null,
) {
    /** An entry whose mode cannot be read any more is dropped rather than guessed at. */
    fun toConfiguration(): WidgetConfiguration? {
        val storedMode = enumValues<WidgetLocationMode>().firstOrNull { it.name == mode }
            ?: return null
        return WidgetConfiguration(
            widgetId = widgetId,
            mode = storedMode,
            fixedLocation = fixedLocation,
        )
    }

    companion object {
        fun from(configuration: WidgetConfiguration): StoredWidgetConfiguration =
            StoredWidgetConfiguration(
                widgetId = configuration.widgetId,
                mode = configuration.mode.name,
                fixedLocation = configuration.fixedLocation,
            )
    }
}
