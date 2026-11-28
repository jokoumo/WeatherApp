package com.example.weatherapp.widget

import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.testing.dresden
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.paris
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which location a widget instance shows, and which places its configuration keeps alive.
 *
 * The three modes are the whole feature of this layer, so they are tested against an in-memory store:
 * the question "does a pinned widget still show its place after the user removed it from the
 * favourites?" has to have an answer in a test, not in a launcher.
 */
class WidgetConfigRepositoryTest {

    /** In-memory stand-in for the DataStore-backed store. */
    private class FakeWidgetConfigStore : WidgetConfigStore {
        private val entries = MutableStateFlow(emptyMap<Int, WidgetConfiguration>())

        override val configurations: Flow<Map<Int, WidgetConfiguration>> = entries

        override suspend fun saveAll(configurations: Collection<WidgetConfiguration>) {
            if (configurations.isEmpty()) return
            entries.value = entries.value + configurations.associateBy { it.widgetId }
        }

        override suspend fun remove(widgetIds: Collection<Int>) {
            entries.value = entries.value.filterKeys { it !in widgetIds }
        }

        /** Writes the way the DataStore store does: only the entries that really changed. */
        val snapshot: Map<Int, WidgetConfiguration> get() = entries.value
    }

    private val home = hamburg
    private val favorite = dresden
    private val pinned = paris

    private fun settings(
        homeLocation: SavedLocation? = home,
        favorites: List<SavedLocation> = listOf(favorite),
        recentLocations: List<SavedLocation> = emptyList(),
        selectedLocationId: String? = null,
    ) = LocationSettings(
        homeLocation = homeLocation,
        favorites = favorites,
        recentLocations = recentLocations,
        selectedLocationId = selectedLocationId,
    )

    // region storing and removing

    @Test
    fun `a saved configuration is found again by its widget id`() = runTest {
        val repository = WidgetConfigRepository(FakeWidgetConfigStore())

        repository.save(WidgetConfiguration(widgetId = 7, mode = WidgetLocationMode.HOME))

        assertEquals(WidgetLocationMode.HOME, repository.configuration(7)?.mode)
        assertNull(repository.configuration(8))
    }

    @Test
    fun `saving the same widget again replaces its configuration instead of adding a second one`() =
        runTest {
            val repository = WidgetConfigRepository(FakeWidgetConfigStore())

            repository.save(WidgetConfiguration(1, WidgetLocationMode.HOME))
            repository.save(
                WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = pinned),
            )

            val stored = repository.currentConfigurations()
            assertEquals(setOf(1), stored.keys)
            assertEquals(WidgetLocationMode.FIXED, stored.getValue(1).mode)
            assertEquals(pinned, stored.getValue(1).fixedLocation)
        }

    @Test
    fun `widgets of the same type with different ids keep different configurations`() = runTest {
        val repository = WidgetConfigRepository(FakeWidgetConfigStore())

        repository.save(WidgetConfiguration(1, WidgetLocationMode.HOME))
        repository.save(WidgetConfiguration(2, WidgetLocationMode.SELECTED))
        repository.save(WidgetConfiguration(3, WidgetLocationMode.FIXED, fixedLocation = pinned))

        assertEquals(
            mapOf(
                1 to WidgetLocationMode.HOME,
                2 to WidgetLocationMode.SELECTED,
                3 to WidgetLocationMode.FIXED,
            ),
            repository.currentConfigurations().mapValues { it.value.mode },
        )
    }

    @Test
    fun `removing one widget leaves the others alone`() = runTest {
        val store = FakeWidgetConfigStore()
        val repository = WidgetConfigRepository(store)
        repository.save(WidgetConfiguration(1, WidgetLocationMode.HOME))
        repository.save(WidgetConfiguration(2, WidgetLocationMode.HOME))

        repository.remove(listOf(1))

        assertEquals(setOf(2), repository.currentConfigurations().keys)
    }

    @Test
    fun `pruning keeps exactly the widgets the launcher still holds`() = runTest {
        val repository = WidgetConfigRepository(FakeWidgetConfigStore())
        repository.save(WidgetConfiguration(1, WidgetLocationMode.HOME))
        repository.save(WidgetConfiguration(2, WidgetLocationMode.HOME))
        repository.save(WidgetConfiguration(3, WidgetLocationMode.FIXED, fixedLocation = pinned))

        repository.pruneTo(liveWidgetIds = setOf(1, 3))

        assertEquals(setOf(1, 3), repository.currentConfigurations().keys)
    }

    @Test
    fun `deleting the last widget empties the store`() = runTest {
        val store = FakeWidgetConfigStore()
        val repository = WidgetConfigRepository(store)
        repository.save(WidgetConfiguration(1, WidgetLocationMode.HOME))

        repository.remove(listOf(1))

        assertTrue(store.snapshot.isEmpty())
    }

    // endregion

    // region which location a widget shows

    @Test
    fun `a home location widget follows the app`() {
        val location = WidgetLocationResolver.resolve(
            WidgetConfiguration(1, WidgetLocationMode.HOME),
            settings(),
        )

        assertEquals(home, location)
    }

    @Test
    fun `a home location widget with no home location shows nothing`() {
        val location = WidgetLocationResolver.resolve(
            WidgetConfiguration(1, WidgetLocationMode.HOME),
            settings(homeLocation = null, favorites = listOf(favorite)),
        )

        assertNull(location)
    }

    @Test
    fun `an aktueller-ort widget shows what the app has selected`() {
        val selected = WidgetLocationResolver.resolve(
            WidgetConfiguration(1, WidgetLocationMode.SELECTED),
            settings(selectedLocationId = favorite.id),
        )
        val fallBack = WidgetLocationResolver.resolve(
            WidgetConfiguration(1, WidgetLocationMode.SELECTED),
            settings(),
        )

        assertEquals(favorite, selected)
        // No explicit selection is the state a fresh install is in; the app falls back to home there,
        // and the widget follows the same rule instead of picking a favourite of its own.
        assertEquals(home, fallBack)
    }

    @Test
    fun `a fixed widget keeps its place even after it left favourites and history`() {
        val configuration = WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = pinned)
        // The app knows nothing about Paris any more: not home, not a favourite, not in the history.
        val location = WidgetLocationResolver.resolve(configuration, settings(favorites = emptyList()))

        assertEquals(pinned, location)
        assertTrue(pinned.id !in settings(favorites = emptyList()).selectableLocations.map { it.id })
    }

    @Test
    fun `a fixed widget whose location is missing shows nothing rather than a random place`() {
        val location = WidgetLocationResolver.resolve(
            WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = null),
            settings(),
        )

        assertNull(location)
    }

    @Test
    fun `an unconfigured widget behaves like the home location`() {
        assertEquals(home, WidgetLocationResolver.resolve(null, settings()))
    }

    @Test
    fun `two widgets of the same type can show two different places`() {
        val first = WidgetLocationResolver.resolve(WidgetConfiguration(1, WidgetLocationMode.HOME), settings())
        val second = WidgetLocationResolver.resolve(
            WidgetConfiguration(2, WidgetLocationMode.FIXED, fixedLocation = pinned),
            settings(),
        )

        assertEquals(home, first)
        assertEquals(pinned, second)
    }

    // endregion

    // region cache retention

    @Test
    fun `the pinned places of widgets are collected for cache retention`() = runTest {
        val repository = WidgetConfigRepository(FakeWidgetConfigStore())
        repository.save(WidgetConfiguration(1, WidgetLocationMode.HOME))
        repository.save(WidgetConfiguration(2, WidgetLocationMode.SELECTED))
        repository.save(WidgetConfiguration(3, WidgetLocationMode.FIXED, fixedLocation = pinned))
        repository.save(WidgetConfiguration(4, WidgetLocationMode.FIXED, fixedLocation = favorite))

        assertEquals(setOf(pinned.id, favorite.id), repository.fixedLocationIds())
    }

    @Test
    fun `two widgets on the same pinned place keep one location id`() = runTest {
        val repository = WidgetConfigRepository(FakeWidgetConfigStore())
        repository.save(WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = pinned))
        repository.save(WidgetConfiguration(2, WidgetLocationMode.FIXED, fixedLocation = pinned))

        assertEquals(setOf(pinned.id), repository.fixedLocationIds())
    }

    @Test
    fun `a deleted widget stops protecting its place`() = runTest {
        val repository = WidgetConfigRepository(FakeWidgetConfigStore())
        repository.save(WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = pinned))

        repository.remove(listOf(1))

        assertFalse(repository.fixedLocationIds().contains(pinned.id))
    }

    @Test
    fun `a pinned location id survives while the settings no longer mention the place`() = runTest {
        val repository = WidgetConfigRepository(FakeWidgetConfigStore())
        repository.save(WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = pinned))

        val retained = settings(favorites = emptyList()).retainedLocationIds + repository.fixedLocationIds()

        assertTrue(retained.contains(pinned.id))
    }

    // endregion

    // region the configuration value itself

    @Test
    fun `only a fixed configuration names a fixed location`() {
        val homeWidget = WidgetConfiguration(1, WidgetLocationMode.HOME)
        val pinnedWidget = WidgetConfiguration(2, WidgetLocationMode.FIXED, fixedLocation = pinned)

        assertFalse(homeWidget.isFixed)
        assertNull(homeWidget.fixedLocationId)
        assertTrue(pinnedWidget.isFixed)
        assertEquals(pinned.id, pinnedWidget.fixedLocationId)
    }

    @Test
    fun `a configuration is usable when it can name a place`() {
        assertTrue(WidgetConfiguration(1, WidgetLocationMode.HOME).isUsable)
        assertTrue(WidgetConfiguration(2, WidgetLocationMode.FIXED, fixedLocation = pinned).isUsable)
        assertFalse(WidgetConfiguration(3, WidgetLocationMode.FIXED, fixedLocation = null).isUsable)
    }

    // endregion
}
