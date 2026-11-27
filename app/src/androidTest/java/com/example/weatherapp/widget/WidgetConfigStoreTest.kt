package com.example.weatherapp.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The widget configuration against a real DataStore file.
 *
 * The unit tests of the widget layer work with an in-memory store, which cannot answer the questions
 * that only a stored file raises: does a pinned location come back with its coordinates and time zone
 * after a restart, does a preferences value an older or damaged build wrote take the widgets down, and
 * does removing one widget really leave the others alone.
 */
@RunWith(AndroidJUnit4::class)
class WidgetConfigStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var directory: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "widget-config-store-${System.nanoTime()}").apply { mkdirs() }
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(directory, "widget-configurations.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun store(): WidgetConfigStore = DataStoreWidgetConfigStore(dataStore, json)

    private suspend fun stored(): Map<Int, WidgetConfiguration> = store().configurations.first()

    @Test
    fun aFreshFileHoldsNoWidgets() = runBlocking {
        assertTrue(stored().isEmpty())
    }

    /**
     * What the next app start sees: all three modes come back, each with its own widget id, and the
     * pinned place is intact down to its coordinates and time zone.
     */
    @Test
    fun everyModeSurvivesAFreshStore() = runBlocking {
        store().saveAll(
            listOf(
                WidgetConfiguration(1, WidgetLocationMode.HOME),
                WidgetConfiguration(2, WidgetLocationMode.SELECTED),
                WidgetConfiguration(3, WidgetLocationMode.FIXED, fixedLocation = tokyo),
            ),
        )

        val restored = stored()

        assertEquals(listOf(1, 2, 3), restored.keys.sorted())
        assertEquals(WidgetLocationMode.HOME, restored.getValue(1).mode)
        assertEquals(WidgetLocationMode.SELECTED, restored.getValue(2).mode)
        assertEquals(tokyo, restored.getValue(3).fixedLocation)
        assertEquals("Asia/Tokyo", restored.getValue(3).fixedLocation?.timeZoneId)
        assertEquals(35.69, restored.getValue(3).fixedLocation?.latitude ?: 0.0, 0.0001)
    }

    /**
     * A pinned place is stored as a complete place, not as a reference into the app's lists: the
     * coordinates, the country and the time zone all come back, which is what lets a widget keep
     * working after the user removed the place from favourites and history.
     */
    @Test
    fun aPinnedPlaceIsStoredComplete() = runBlocking {
        store().saveAll(listOf(WidgetConfiguration(7, WidgetLocationMode.FIXED, fixedLocation = paris)))

        val configuration = stored().getValue(7)

        assertEquals(paris, configuration.fixedLocation)
        assertEquals("Frankreich", configuration.fixedLocation?.country)
        assertEquals("Europe/Paris", configuration.fixedLocation?.timeZoneId)
        assertTrue(configuration.isUsable)
    }

    @Test
    fun savingAWidgetAgainReplacesItsEntry() = runBlocking {
        val store = store()
        store.saveAll(listOf(WidgetConfiguration(4, WidgetLocationMode.HOME)))
        store.saveAll(listOf(WidgetConfiguration(4, WidgetLocationMode.FIXED, fixedLocation = hamburg)))

        val restored = stored()

        assertEquals(setOf(4), restored.keys)
        assertEquals(hamburg, restored.getValue(4).fixedLocation)
    }

    @Test
    fun otherWidgetsAreKeptWhenOneIsWritten() = runBlocking {
        val store = store()
        store.saveAll(
            listOf(
                WidgetConfiguration(1, WidgetLocationMode.HOME),
                WidgetConfiguration(2, WidgetLocationMode.FIXED, fixedLocation = paris),
            ),
        )

        store.saveAll(listOf(WidgetConfiguration(1, WidgetLocationMode.SELECTED)))

        assertEquals(WidgetLocationMode.SELECTED, stored().getValue(1).mode)
        assertEquals(paris, stored().getValue(2).fixedLocation)
    }

    @Test
    fun removingOneWidgetLeavesTheOthersAlone() = runBlocking {
        val store = store()
        store.saveAll(
            listOf(
                WidgetConfiguration(1, WidgetLocationMode.HOME),
                WidgetConfiguration(2, WidgetLocationMode.HOME),
                WidgetConfiguration(3, WidgetLocationMode.FIXED, fixedLocation = tokyo),
            ),
        )

        store.remove(listOf(2))

        assertEquals(setOf(1, 3), stored().keys)
    }

    @Test
    fun removingTheLastWidgetEmptiesTheFile() = runBlocking {
        val store = store()
        store.saveAll(listOf(WidgetConfiguration(1, WidgetLocationMode.HOME)))

        store.remove(listOf(1))

        assertTrue(stored().isEmpty())
    }

    @Test
    fun anUnknownWidgetIdIsIgnoredWhenRemoving() = runBlocking {
        val store = store()
        store.saveAll(listOf(WidgetConfiguration(1, WidgetLocationMode.HOME)))

        store.remove(listOf(99))

        assertEquals(setOf(1), stored().keys)
    }

    /**
     * Writing nothing must not clear what is stored: the coordinator saves a pruned set, and an empty
     * live-widget list must never erase the configuration of the widgets that are still there.
     */
    @Test
    fun anEmptyWriteChangesNothing() = runBlocking {
        val store = store()
        store.saveAll(listOf(WidgetConfiguration(1, WidgetLocationMode.HOME)))

        store.saveAll(emptyList())
        store.remove(emptyList())

        assertEquals(setOf(1), stored().keys)
    }

    /**
     * A value that cannot be read - a file edited by hand, a write interrupted mid-way - must not
     * break every widget. The store answers "no configuration" and the widget asks the user again.
     */
    @Test
    fun aDamagedValueIsReadAsNoWidgets() = runBlocking {
        dataStore.edit { preferences -> preferences[KEY_CONFIGURATIONS] = "not a stored configuration{" }

        assertTrue(stored().isEmpty())
    }

    /**
     * An entry whose mode this build does not know is dropped instead of guessed at: a wrong mode
     * would show the wrong place, which is worse than asking the user to configure the widget again.
     * The healthy entries of the same file survive.
     */
    @Test
    fun anEntryWithAnUnknownModeIsSkippedAndTheRestSurvives() = runBlocking {
        dataStore.edit { preferences ->
            preferences[KEY_CONFIGURATIONS] =
                """[{"widgetId":1,"mode":"HOME"},""" +
                """{"widgetId":2,"mode":"A_MODE_FROM_THE_FUTURE","fixedLocation":null}]"""
        }

        val restored = stored()

        assertEquals(setOf(1), restored.keys)
        assertEquals(WidgetLocationMode.HOME, restored.getValue(1).mode)
    }

    /** A location stored without the fields this version added still loads. */
    @Test
    fun anEntryMissingOptionalLocationFieldsStillLoads() = runBlocking {
        dataStore.edit { preferences ->
            preferences[KEY_CONFIGURATIONS] =
                """[{"widgetId":5,"mode":"FIXED","fixedLocation":{"id":"x1","name":"Ort","latitude":50.0,"longitude":10.0}}]"""
        }

        val configuration = stored().getValue(5)

        assertEquals(WidgetLocationMode.FIXED, configuration.mode)
        assertEquals("Ort", configuration.fixedLocation?.name)
        assertEquals(SavedLocation.DEFAULT_TIME_ZONE_ID, configuration.fixedLocation?.timeZoneId)
    }

    private companion object {
        /** The key exactly as [DataStoreWidgetConfigStore] writes it. */
        val KEY_CONFIGURATIONS = stringPreferencesKey("widget_configurations")

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        val hamburg = SavedLocation("2911298", "Hamburg", 53.55, 9.99, timeZoneId = "Europe/Berlin")
        val paris = SavedLocation("2988507", "Paris", 48.85, 2.35, "Île-de-France", "Frankreich", "FR", "Europe/Paris")
        val tokyo = SavedLocation("1850144", "Tokio", 35.69, 139.69, null, "Japan", "JP", "Asia/Tokyo")
    }
}
