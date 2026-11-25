package com.example.weatherapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The location settings against a real DataStore file: a round trip across a simulated restart,
 * the history surviving with it, and - most important for an installed app - a preferences file
 * written by an older build that never had a history key at all.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var directory: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "settings-store-${System.nanoTime()}").apply { mkdirs() }
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(directory, "weather-settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun aFreshFileHoldsNoLocationsAndNoHistory() = runBlocking {
        val settings = DataStoreSettingsStore(dataStore, json).settings.collectFirst()

        assertNull(settings.homeLocation)
        assertTrue(settings.favorites.isEmpty())
        assertTrue(settings.recentLocations.isEmpty())
        assertNull(settings.selectedLocationId)
    }

    /**
     * What the next app start sees: everything that was saved comes back unchanged, including the
     * order of the favorites and the order of the history.
     */
    @Test
    fun everyRoleSurvivesAFreshStore() = runBlocking {
        val store = DataStoreSettingsStore(dataStore, json)
        val saved = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin, tokyo, paris),
            recentLocations = listOf(madrid, dresden),
            selectedLocationId = paris.id,
        )
        store.save(saved)

        val restored = DataStoreSettingsStore(dataStore, json).settings.collectFirst()

        assertEquals(saved, restored)
        assertEquals(listOf(berlin.id, tokyo.id, paris.id), restored.favorites.map { it.id })
        assertEquals(listOf(madrid.id, dresden.id), restored.recentLocations.map { it.id })
        assertEquals(paris.id, restored.resolveSelectedLocation()?.id)
    }

    /**
     * Migration case that matters in the field: an installation from before the history existed.
     * Its file carries only the three old keys, and reading it must keep every saved location,
     * keep the favourite order, keep the selection valid and simply report an empty history.
     */
    @Test
    fun aFileWrittenBeforeTheHistoryExistedStillLoadsCompletely() = runBlocking {
        val legacyFavorites = listOf(berlin, tokyo, paris)
        dataStore.edit { preferences ->
            preferences[LEGACY_HOME] = json.encodeToString(SavedLocation.serializer(), hamburg)
            preferences[LEGACY_FAVORITES] =
                json.encodeToString(ListSerializer(SavedLocation.serializer()), legacyFavorites)
            preferences[LEGACY_SELECTED] = tokyo.id
        }

        val settings = DataStoreSettingsStore(dataStore, json).settings.collectFirst()

        assertEquals(hamburg, settings.homeLocation)
        assertEquals(legacyFavorites, settings.favorites)
        assertTrue("a missing history key means an empty history", settings.recentLocations.isEmpty())
        assertEquals(tokyo.id, settings.selectedLocationId)
        assertEquals(tokyo.id, settings.resolveSelectedLocation()?.id)
    }

    /**
     * A value written by hand or left by a damaged build must not leave the app without any
     * location: the unreadable list becomes empty while the rest of the file stays usable.
     */
    @Test
    fun aDamagedListLeavesTheRestOfTheFileUsable() = runBlocking {
        dataStore.edit { preferences ->
            preferences[LEGACY_HOME] = json.encodeToString(SavedLocation.serializer(), hamburg)
            preferences[LEGACY_FAVORITES] = "{ not a list"
            preferences[LEGACY_RECENT] = "[ also not a list"
            preferences[LEGACY_SELECTED] = hamburg.id
        }

        val settings = DataStoreSettingsStore(dataStore, json).settings.collectFirst()

        assertEquals(hamburg, settings.homeLocation)
        assertTrue(settings.favorites.isEmpty())
        assertTrue(settings.recentLocations.isEmpty())
        assertEquals(hamburg.id, settings.resolveSelectedLocation()?.id)
    }

    /**
     * A selection pointing at a place that is not stored any more is repaired while reading, so
     * every consumer sees a selection that exists instead of having to defend itself against it.
     */
    @Test
    fun aSelectionOfAnUnknownPlaceIsRepairedToTheHomeLocation() = runBlocking {
        val store = DataStoreSettingsStore(dataStore, json)
        store.save(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin),
                selectedLocationId = "somewhere-gone",
            ),
        )

        val settings = store.settings.collectFirst()

        assertEquals(hamburg.id, settings.selectedLocationId)
        assertEquals(hamburg.id, settings.resolveSelectedLocation()?.id)
    }

    /**
     * An older build could store the same place in two roles. Reading such a file hands it to the
     * higher ranked role only and keeps everything else, so the app starts with a consistent state
     * without losing a single place.
     */
    @Test
    fun aFileStoringAPlaceTwiceIsReadAsOneConsistentState() = runBlocking {
        dataStore.edit { preferences ->
            preferences[LEGACY_HOME] = json.encodeToString(SavedLocation.serializer(), hamburg)
            preferences[LEGACY_FAVORITES] =
                json.encodeToString(ListSerializer(SavedLocation.serializer()), listOf(berlin, tokyo, hamburg))
            preferences[LEGACY_RECENT] =
                json.encodeToString(ListSerializer(SavedLocation.serializer()), listOf(berlin, madrid, dresden))
            preferences[LEGACY_SELECTED] = berlin.id
        }

        val settings = DataStoreSettingsStore(dataStore, json).settings.collectFirst()

        assertEquals(hamburg.id, settings.homeLocation?.id)
        assertEquals(listOf(berlin.id, tokyo.id), settings.favorites.map { it.id })
        assertEquals(listOf(madrid.id, dresden.id), settings.recentLocations.map { it.id })
        assertEquals(berlin.id, settings.selectedLocationId)
    }

    private companion object {
        /** The keys exactly as the previous application version wrote them. */
        val LEGACY_HOME = stringPreferencesKey("home_location")
        val LEGACY_FAVORITES = stringPreferencesKey("favorite_locations")
        val LEGACY_SELECTED = stringPreferencesKey("selected_location_id")
        val LEGACY_RECENT = stringPreferencesKey("recent_locations")

        val json = Json { ignoreUnknownKeys = true }

        val hamburg = SavedLocation("2911298", "Hamburg", 53.55, 9.99, "Freie und Hansestadt Hamburg", "Deutschland", "DE", "Europe/Berlin")
        val berlin = SavedLocation("2950280", "Berlin", 52.52, 13.4, "Berlin", "Deutschland", "DE", "Europe/Berlin")
        val tokyo = SavedLocation("1850144", "Tokio", 35.69, 139.69, null, "Japan", "JP", "Asia/Tokyo")
        val paris = SavedLocation("2988507", "Paris", 48.85, 2.35, "Île-de-France", "Frankreich", "FR", "Europe/Paris")
        val madrid = SavedLocation("3117735", "Madrid", 40.42, -3.7, "Madrid", "Spanien", "ES", "Europe/Madrid")
        val dresden = SavedLocation("2935022", "Dresden", 51.05, 13.74, "Sachsen", "Deutschland", "DE", "Europe/Berlin")
    }
}

/** Small helper: the first value of a flow, which is what the UI reads on start. */
private suspend fun <T> Flow<T>.collectFirst(): T = first()
