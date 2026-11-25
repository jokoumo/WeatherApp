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
import com.example.weatherapp.domain.model.ThemeMode
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
 * The theme preference against a real DataStore file: the default, a round trip across a simulated
 * restart, a damaged value, and the guarantee that storing the theme leaves the saved locations in
 * the same file alone.
 */
@RunWith(AndroidJUnit4::class)
class ThemeStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var directory: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        directory = File(context.cacheDir, "theme-store-${System.nanoTime()}").apply { mkdirs() }
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(directory, "weather-settings.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun defaultIsSystemForAFreshFile() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, DataStoreThemeStore(dataStore).themeMode.collectFirst())
    }

    /**
     * What a restart sees: the value that ended up in the preferences file, and a store object
     * created afterwards reading exactly that value back.
     */
    @Test
    fun chosenModeIsWrittenAndReadBackByAFreshStore() = runBlocking {
        assertTrue(DataStoreThemeStore(dataStore).save(ThemeMode.DARK))

        assertEquals("DARK", dataStore.data.first()[RAW_THEME_KEY])
        assertEquals(ThemeMode.DARK, DataStoreThemeStore(dataStore).themeMode.collectFirst())
    }

    @Test
    fun everyModeIsStoredAsItself() = runBlocking {
        ThemeMode.entries.forEach { mode ->
            assertTrue(DataStoreThemeStore(dataStore).save(mode))
            assertEquals(mode, DataStoreThemeStore(dataStore).themeMode.collectFirst())
        }
    }

    /**
     * A value that was not written by this version — edited by hand or left by an older build —
     * must not leave the app without a theme.
     */
    @Test
    fun damagedStoredValueFallsBackToSystem() = runBlocking {
        dataStore.edit { preferences -> preferences[RAW_THEME_KEY] = "MEDIUM" }

        assertEquals(ThemeMode.SYSTEM, DataStoreThemeStore(dataStore).themeMode.collectFirst())
    }

    /**
     * Both stores share one DataStore file. Writing the theme may not rewrite or drop the saved
     * locations, which is why they are read back after the theme write.
     */
    @Test
    fun themeWriteLeavesLocationSettingsIntact() = runBlocking {
        val settingsStore = DataStoreSettingsStore(dataStore, json)
        val saved = LocationSettings(
            homeLocation = SavedLocation("2911298", "Hamburg", 53.55, 9.99, timeZoneId = "Europe/Berlin"),
            favorites = listOf(SavedLocation("2950280", "Berlin", 52.52, 13.4, timeZoneId = "Europe/Berlin")),
            selectedLocationId = "2911298",
        )
        settingsStore.save(saved)
        assertEquals(saved, settingsStore.settings.collectFirst())

        assertTrue(DataStoreThemeStore(dataStore).save(ThemeMode.LIGHT))

        assertEquals(saved, settingsStore.settings.collectFirst())
        assertEquals(ThemeMode.LIGHT, DataStoreThemeStore(dataStore).themeMode.collectFirst())
    }

    private companion object {
        val RAW_THEME_KEY = stringPreferencesKey("theme_mode")
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Small helper: the first value of a flow, which is what the UI reads on start. */
private suspend fun <T> Flow<T>.collectFirst(): T = first()
