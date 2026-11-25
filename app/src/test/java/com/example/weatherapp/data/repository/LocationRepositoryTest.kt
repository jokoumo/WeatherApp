package com.example.weatherapp.data.repository

import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.isSamePlaceAs
import com.example.weatherapp.testing.FakeGeocodingApi
import com.example.weatherapp.testing.FakeSettingsStore
import com.example.weatherapp.testing.berlin
import com.example.weatherapp.testing.dresden
import com.example.weatherapp.testing.geocoderResult
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.madrid
import com.example.weatherapp.testing.newYork
import com.example.weatherapp.testing.paris
import com.example.weatherapp.testing.tokyo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/**
 * Location management rules: home location swaps that never drop the previous home, ordered
 * favorites, duplicate prevention, and the selection fallback that keeps a valid location
 * selected after removals while an explicit home change always selects the new home. The home
 * designation survives every ordinary removal, and the favorite toggle decides inside the
 * serialized mutation so that rapid taps cannot both add the same place.
 *
 * The second half covers the history: selecting a searched place shows it without favouriting it,
 * the history stays duplicate-free and sorted by most recent use, its maximum evicts only the least
 * recently used entry, and favorites and history can never hold the same place at the same time.
 */
class LocationRepositoryTest {

    private fun repositoryWith(
        initial: LocationSettings,
        geocodingApi: FakeGeocodingApi = FakeGeocodingApi(),
    ): Pair<LocationRepository, FakeGeocodingApi> {
        val store = FakeSettingsStore(initial)
        return LocationRepository(store, geocodingApi) to geocodingApi
    }

    @Test
    fun `making a favorite the home location swaps it with the previous home`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin, tokyo)),
        )

        val settings = repository.setHomeLocation(berlin)

        assertEquals(berlin.id, settings.homeLocation?.id)
        // Hamburg does not disappear: it takes the slot Berlin left in the favorites list.
        assertEquals(listOf(hamburg.id, tokyo.id), settings.favorites.map { it.id })
        assertEquals(berlin.id, settings.selectedLocationId)
        assertEquals(berlin.id, repository.currentSettings().homeLocation?.id)
        assertEquals(listOf(hamburg.id, tokyo.id), repository.currentSettings().favorites.map { it.id })
    }

    @Test
    fun `the documented new york scenario keeps all five locations`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = newYork,
                favorites = listOf(hamburg, dresden, paris, madrid),
                selectedLocationId = newYork.id,
            ),
        )

        val settings = repository.setHomeLocation(hamburg)

        assertEquals(hamburg.id, settings.homeLocation?.id)
        assertEquals(listOf(newYork.id, dresden.id, paris.id, madrid.id), settings.favorites.map { it.id })
        assertEquals(hamburg.id, settings.selectedLocationId)
        // Nothing lost and nothing doubled: home plus four favorites is still five places.
        assertEquals(5, settings.selectableLocations.size)
        assertEquals(5, settings.selectableLocations.distinctBy { it.coordinateKey }.size)
    }

    @Test
    fun `repeated home changes keep every location in place`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = newYork, favorites = listOf(hamburg, dresden, paris)),
        )

        // Dresden is the second favorite: New York has to land in exactly that slot.
        assertEquals(
            listOf(hamburg.id, newYork.id, paris.id),
            repository.setHomeLocation(dresden).favorites.map { it.id },
        )
        assertEquals(
            listOf(hamburg.id, newYork.id, dresden.id),
            repository.setHomeLocation(paris).favorites.map { it.id },
        )
        // Switching back to a former home: New York was second-to-last and moves to that slot.
        val backToNewYork = repository.setHomeLocation(newYork)
        assertEquals(newYork.id, backToNewYork.homeLocation?.id)
        assertEquals(listOf(hamburg.id, paris.id, dresden.id), backToNewYork.favorites.map { it.id })
        assertEquals(4, backToNewYork.selectableLocations.size)
        assertEquals(4, backToNewYork.selectableLocations.distinctBy { it.coordinateKey }.size)
        assertEquals(newYork.id, backToNewYork.selectedLocationId)
    }

    @Test
    fun `the new home location wins over a previously selected favorite`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = newYork,
                favorites = listOf(hamburg, dresden),
                selectedLocationId = dresden.id,
            ),
        )

        val settings = repository.setHomeLocation(hamburg)

        // The generic "keep the selection when it is still valid" rule must not undo the tap on
        // the home action: Dresden is still a favorite, yet Hamburg has to be selected now.
        assertEquals(hamburg.id, settings.selectedLocationId)
        assertEquals(hamburg.id, settings.resolveSelectedLocation()?.id)
    }

    @Test
    fun `the new home selection survives persistence and a fresh repository`() = runTest {
        val store = FakeSettingsStore(
            LocationSettings(
                homeLocation = newYork,
                favorites = listOf(hamburg, dresden),
                selectedLocationId = newYork.id,
            ),
        )
        LocationRepository(store, FakeGeocodingApi()).setHomeLocation(hamburg)

        // A second repository instance stands in for an application restart.
        val restored = LocationRepository(store, FakeGeocodingApi()).currentSettings()

        assertEquals(hamburg.id, restored.homeLocation?.id)
        assertEquals(listOf(newYork.id, dresden.id), restored.favorites.map { it.id })
        assertEquals(hamburg.id, restored.selectedLocationId)
        assertEquals(hamburg.id, restored.resolveSelectedLocation()?.id)
    }

    @Test
    fun `assigning the current home location again changes nothing and does not write`() = runTest {
        val store = FakeSettingsStore(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(dresden, paris),
                selectedLocationId = hamburg.id,
            ),
        )
        val repository = LocationRepository(store, FakeGeocodingApi())
        val writesBefore = store.saveCount

        val settings = repository.setHomeLocation(hamburg)

        assertEquals(hamburg.id, settings.homeLocation?.id)
        assertEquals(listOf(dresden.id, paris.id), settings.favorites.map { it.id })
        assertEquals(hamburg.id, settings.selectedLocationId)
        assertEquals(3, settings.selectableLocations.size)
        assertEquals(writesBefore, store.saveCount)
    }

    @Test
    fun `a home location stored twice does not end up in the favorites twice`() = runTest {
        // An older persisted state kept a second copy of the home location as a favorite.
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = newYork, favorites = listOf(newYork.copy(id = "other-id"), dresden)),
        )

        val settings = repository.setHomeLocation(dresden)

        assertEquals(dresden.id, settings.homeLocation?.id)
        assertEquals(1, settings.favorites.count { it.isSamePlaceAs(newYork) })
        assertEquals(listOf(newYork.id), settings.favorites.map { it.id })
        assertEquals(2, settings.selectableLocations.distinctBy { it.coordinateKey }.size)
    }

    @Test
    fun `rapid repeated home actions leave one home and no duplicates`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = newYork, favorites = listOf(hamburg, dresden, paris, madrid)),
        )
        val taps = listOf(hamburg, dresden, paris, madrid, hamburg, dresden)

        coroutineScope {
            taps.map { target -> async { repository.setHomeLocation(target) } }.forEach { it.await() }
        }

        // Which of the taps landed last does not matter - the invariants have to hold either way.
        val stored = repository.currentSettings()
        assertEquals(5, stored.selectableLocations.size)
        assertEquals(5, stored.selectableLocations.distinctBy { it.coordinateKey }.size)
        assertEquals(
            setOf(newYork.id, hamburg.id, dresden.id, paris.id, madrid.id),
            stored.selectableLocations.map { it.id }.toSet(),
        )
        // The last home change also decided the selection, so both agree.
        assertEquals(stored.homeLocation?.id, stored.selectedLocationId)
    }

    @Test
    fun `a favorite can be promoted even when it is the only saved favorite`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = newYork, favorites = listOf(hamburg)),
        )

        val settings = repository.setHomeLocation(hamburg)

        assertEquals(hamburg.id, settings.homeLocation?.id)
        assertEquals(listOf(newYork.id), settings.favorites.map { it.id })
        assertEquals(hamburg.id, settings.selectedLocationId)
    }

    @Test
    fun `a favorite becomes the home location when nothing is stored as home`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(favorites = listOf(hamburg, dresden), selectedLocationId = dresden.id),
        )

        val settings = repository.setHomeLocation(dresden)

        assertEquals(dresden.id, settings.homeLocation?.id)
        assertEquals(listOf(hamburg.id), settings.favorites.map { it.id })
        assertEquals(dresden.id, settings.selectedLocationId)
    }

    @Test
    fun `favorites reject duplicates by id and by identical coordinates`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin)),
        )

        assertFalse(repository.addFavorite(hamburg))
        assertFalse(repository.addFavorite(berlin))
        // Another geocoding entry with the same coordinates is still the same place.
        assertFalse(repository.addFavorite(berlin.copy(id = "different-id", name = "Berlin, Stadt")))

        assertEquals(listOf(berlin.id), repository.currentSettings().favorites.map { it.id })
    }

    @Test
    fun `a new favorite is appended and persisted`() = runTest {
        val (repository, _) = repositoryWith(LocationSettings(homeLocation = hamburg))

        assertTrue(repository.addFavorite(berlin))
        assertTrue(repository.addFavorite(tokyo))

        val settings = repository.currentSettings()
        assertEquals(listOf(berlin.id, tokyo.id), settings.favorites.map { it.id })
        assertEquals(hamburg.id, settings.homeLocation?.id)
        // Adding a favorite does not change what is selected.
        assertNull(settings.selectedLocationId)
    }

    @Test
    fun `favorites can be reordered and moves outside the list are ignored`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin, tokyo)),
        )
        repository.addFavorite(
            com.example.weatherapp.domain.model.SavedLocation(
                id = "2929680",
                name = "Muenchen",
                latitude = 48.14,
                longitude = 11.58,
            ),
        )

        val movedUp = repository.moveFavorite(tokyo.id, -1)
        assertEquals(listOf(tokyo.id, berlin.id, "2929680"), movedUp.favorites.map { it.id })

        val movedDown = repository.moveFavorite(tokyo.id, 5)
        assertEquals(listOf(berlin.id, "2929680", tokyo.id), movedDown.favorites.map { it.id })

        // The first favorite cannot move further up.
        val unchanged = repository.moveFavorite(berlin.id, -1)
        assertEquals(listOf(berlin.id, "2929680", tokyo.id), unchanged.favorites.map { it.id })

        // Unknown ids do not change anything.
        val unknown = repository.moveFavorite("does-not-exist", 1)
        assertEquals(listOf(berlin.id, "2929680", tokyo.id), unknown.favorites.map { it.id })
    }

    @Test
    fun `removing or reordering another favorite keeps the current selection`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = newYork,
                favorites = listOf(hamburg, dresden, paris),
                selectedLocationId = hamburg.id,
            ),
        )

        assertEquals(hamburg.id, repository.removeFavorite(paris.id).selectedLocationId)
        assertEquals(hamburg.id, repository.moveFavorite(hamburg.id, 1).selectedLocationId)
        assertTrue(repository.addFavorite(tokyo))

        val settings = repository.currentSettings()
        assertEquals(hamburg.id, settings.selectedLocationId)
        assertEquals(listOf(dresden.id, hamburg.id, tokyo.id), settings.favorites.map { it.id })
    }

    @Test
    fun `removing the selected favorite keeps it selected and moves it into the history`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin, tokyo),
                selectedLocationId = tokyo.id,
            ),
        )

        val settings = repository.removeFavorite(tokyo.id)

        assertEquals(listOf(berlin.id), settings.favorites.map { it.id })
        // Un-favouriting a place does not forget it: it stays on screen and lands in the history.
        assertEquals(listOf(tokyo.id), settings.recentLocations.map { it.id })
        assertEquals(tokyo.id, settings.selectedLocationId)
        assertEquals(tokyo.id, settings.resolveSelectedLocation()?.id)
    }

    // ------------------------------------------------------------- favorite toggle

    /**
     * The role a tap applies to is decided inside the serialized mutation, so two taps that arrive
     * back to back produce two real changes instead of both deciding "not a favorite" and both
     * adding the same place.
     */
    @Test
    fun `two rapid favorite taps produce one add and one remove`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, selectedLocationId = hamburg.id),
        )

        val results = coroutineScope {
            listOf(
                async { repository.toggleFavorite(berlin) },
                async { repository.toggleFavorite(berlin) },
            ).map { it.await() }
        }

        // Which coroutine won the lock first does not matter; what must never happen is two adds.
        assertEquals(
            setOf(FavoriteToggle.ADDED, FavoriteToggle.REMOVED),
            results.map { it.change }.toSet(),
        )
        val stored = repository.currentSettings()
        assertTrue("the second tap has to undo the first one", stored.favorites.isEmpty())
        // Un-favouriting does not forget the place: it lands on top of the history.
        assertEquals(listOf(berlin.id), stored.recentLocations.map { it.id })
        // Toggling the star never changes which location is displayed.
        assertEquals(hamburg.id, stored.selectedLocationId)
    }

    /** The star of the home location cannot switch the protected role off, whatever the UI saw. */
    @Test
    fun `the home location cannot be unfavorited`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin), selectedLocationId = hamburg.id),
        )

        val result = repository.toggleFavorite(hamburg)

        assertEquals(FavoriteToggle.HOME_LOCATION, result.change)
        assertEquals(hamburg, result.settings.homeLocation)
        assertEquals(listOf(berlin.id), result.settings.favorites.map { it.id })
        assertFalse(result.settings.recentLocations.any { it.isSamePlaceAs(hamburg) })
    }

    /** A place is favorite or history, never both: adding it clears the history entry. */
    @Test
    fun `favoriting a history entry moves it out of the history`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, recentLocations = listOf(berlin, tokyo)),
        )

        val result = repository.toggleFavorite(berlin)

        assertEquals(FavoriteToggle.ADDED, result.change)
        assertEquals(listOf(berlin.id), result.settings.favorites.map { it.id })
        assertEquals(listOf(tokyo.id), result.settings.recentLocations.map { it.id })
    }

    /** Identity is the place, not the id: the same coordinates under another id still match. */
    @Test
    fun `a favorite stored under another id is still recognized by its coordinates`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin)),
        )

        val result = repository.toggleFavorite(berlin.copy(id = "search-hit-99"))

        assertEquals(FavoriteToggle.REMOVED, result.change)
        assertTrue(result.settings.favorites.isEmpty())
        // The stored copy keeps its identity instead of being replaced by the search hit.
        assertEquals(listOf(berlin.id), result.settings.recentLocations.map { it.id })
    }

    /**
     * The designation of the home location only moves through [LocationRepository.setHomeLocation].
     * An ordinary "forget this place" action on the home id changes nothing at all, so no list
     * action can leave the app with favorites and a history but no home.
     */
    @Test
    fun `removing the home location is refused and changes nothing`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin),
                recentLocations = listOf(tokyo),
                selectedLocationId = hamburg.id,
            ),
        )

        val settings = repository.removeLocation(hamburg.id)

        assertEquals(hamburg, settings.homeLocation)
        assertEquals(listOf(berlin.id), settings.favorites.map { it.id })
        assertEquals(listOf(tokyo.id), settings.recentLocations.map { it.id })
        assertEquals(hamburg.id, settings.selectedLocationId)
        assertEquals(hamburg, repository.currentSettings().homeLocation)
    }

    /** The last remaining location is the home one: it stays, and the app stays usable. */
    @Test
    fun `the only home location survives every removal attempt`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = emptyList(), selectedLocationId = hamburg.id),
        )

        val settings = repository.removeLocation(hamburg.id)

        assertTrue("the app never ends up without its only location", settings.hasAnyLocation)
        assertTrue(settings.hasSelectableLocation)
        assertEquals(hamburg.id, settings.resolveSelectedLocation()?.id)
    }

    @Test
    fun `selecting a stored location works and unknown ids are ignored`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin), selectedLocationId = hamburg.id),
        )

        assertEquals(berlin.id, repository.selectLocation(berlin.id).selectedLocationId)
        assertEquals(berlin.id, repository.selectLocation("unknown").selectedLocationId)
    }

    @Test
    fun `the settings flow exposes the current state`() = runTest {
        val (repository, _) = repositoryWith(LocationSettings(homeLocation = hamburg))
        assertEquals(hamburg.id, repository.settings.first().homeLocation?.id)

        repository.addFavorite(berlin)
        assertEquals(1, repository.settings.first().favorites.size)
    }

    @Test
    fun `a blank query does not call the geocoding api`() = runTest {
        val (repository, api) = repositoryWith(LocationSettings())
        val result = repository.searchLocations("   ")

        assertEquals(0, api.callCount)
        assertEquals("", result.query)
        assertTrue(result.locations.isEmpty())
        assertNull(result.failure)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `search trims the query and maps the api results`() = runTest {
        val api = FakeGeocodingApi(
            results = listOf(
                geocoderResult(id = 2911298, name = "Hamburg", latitude = 53.55, longitude = 9.99),
                geocoderResult(id = 12048316, name = "Hamburg", latitude = 42.71, longitude = -78.9),
            ),
        )
        val (repository, _) = repositoryWith(LocationSettings(), api)

        val result = repository.searchLocations("  Hamburg  ")

        assertEquals(listOf("Hamburg"), api.queries)
        assertEquals("Hamburg", result.query)
        assertEquals(2, result.locations.size)
        assertNull(result.failure)
    }

    @Test
    fun `search reports failures instead of throwing`() = runTest {
        val offlineApi = FakeGeocodingApi(thrown = UnknownHostException("geocoding-api.open-meteo.com"))
        val (offlineRepository, _) = repositoryWith(LocationSettings(), offlineApi)
        assertEquals(DataLoadFailure.NETWORK, offlineRepository.searchLocations("Hamburg").failure)

        val failingApi = FakeGeocodingApi(errorResponse = com.example.weatherapp.testing.errorResponse(503))
        val (failingRepository, _) = repositoryWith(LocationSettings(), failingApi)
        val failingResult = failingRepository.searchLocations("Hamburg")
        assertEquals(DataLoadFailure.SERVER, failingResult.failure)
        assertTrue(failingResult.locations.isEmpty())
    }

    @Test
    fun `an empty geocoding response is a valid empty result`() = runTest {
        val api = FakeGeocodingApi(results = emptyList())
        val (repository, _) = repositoryWith(LocationSettings(), api)

        val result = repository.searchLocations("Xyzabc")
        assertTrue(result.isEmpty)
        assertNull(result.failure)
    }

    // ---------------------------------------------------------------- history

    @Test
    fun `selecting a searched location shows it without favouriting it`() = runTest {
        val (repository, _) = repositoryWith(LocationSettings(homeLocation = hamburg))

        val settings = repository.selectLocation(tokyo)

        assertEquals(tokyo.id, settings.selectedLocationId)
        assertEquals(tokyo.id, settings.resolveSelectedLocation()?.id)
        assertTrue("viewing a place never saves it as a favourite", settings.favorites.isEmpty())
        assertEquals(listOf(tokyo.id), settings.recentLocations.map { it.id })
    }

    @Test
    fun `selecting the same searched location twice keeps one history entry`() = runTest {
        val (repository, _) = repositoryWith(LocationSettings(homeLocation = hamburg))

        repository.selectLocation(tokyo)
        val settings = repository.selectLocation(tokyo)

        assertEquals(listOf(tokyo.id), settings.recentLocations.map { it.id })
    }

    @Test
    fun `a searched place that matches a saved one by coordinates reuses the saved entry`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin)),
        )
        // Same rounded coordinates as the stored Berlin, but a different id and name.
        val lookalike = SavedLocation(
            id = "999999",
            name = "Berlin (Stadt)",
            latitude = berlin.latitude,
            longitude = berlin.longitude,
        )

        val settings = repository.selectLocation(lookalike)

        assertEquals(berlin.id, settings.selectedLocationId)
        assertTrue(settings.recentLocations.isEmpty())
        assertEquals(listOf(berlin.id), settings.favorites.map { it.id })
    }

    @Test
    fun `selecting a favorite or the home location does not add it to the history`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin)),
        )

        repository.selectLocation(berlin)
        val settings = repository.selectLocation(hamburg)

        assertTrue(settings.recentLocations.isEmpty())
        assertEquals(hamburg.id, settings.selectedLocationId)
    }

    @Test
    fun `the history is ordered by most recent use`() = runTest {
        val (repository, _) = repositoryWith(LocationSettings(homeLocation = hamburg))

        repository.selectLocation(tokyo)
        repository.selectLocation(paris)
        val settings = repository.selectLocation(madrid)

        assertEquals(listOf(madrid.id, paris.id, tokyo.id), settings.recentLocations.map { it.id })
    }

    @Test
    fun `selecting a history entry again moves it to the top`() = runTest {
        val (repository, _) = repositoryWith(LocationSettings(homeLocation = hamburg))
        repository.selectLocation(tokyo)
        repository.selectLocation(paris)
        repository.selectLocation(madrid)

        val settings = repository.selectLocation(tokyo.id)

        assertEquals(listOf(tokyo.id, madrid.id, paris.id), settings.recentLocations.map { it.id })
        assertEquals(tokyo.id, settings.selectedLocationId)
    }

    @Test
    fun `the history holds at most the configured maximum and drops the oldest entry`() = runTest {
        val (repository, _) = repositoryWith(LocationSettings(homeLocation = hamburg))
        val many = (1..LocationSettings.MAX_RECENT_LOCATIONS + 5).map { index ->
            SavedLocation(
                id = "gen-$index",
                name = "Ort $index",
                latitude = 10.0 + index,
                longitude = 20.0 + index,
            )
        }

        var settings = LocationSettings()
        many.forEach { location -> settings = repository.selectLocation(location) }

        assertEquals(LocationSettings.MAX_RECENT_LOCATIONS, settings.recentLocations.size)
        assertEquals(many.last().id, settings.recentLocations.first().id)
        // The six least recently used places are the ones that fell off.
        assertTrue(settings.recentLocations.none { it.id == many.first().id })
        assertTrue(settings.recentLocations.any { it.id == many[6].id })
        assertEquals(many.last().id, settings.selectedLocationId)
    }

    @Test
    fun `the history never evicts a favorite or the home location`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin)),
        )
        val many = (1..LocationSettings.MAX_RECENT_LOCATIONS + 3).map { index ->
            SavedLocation(
                id = "busy-$index",
                name = "Ort $index",
                latitude = 30.0 + index,
                longitude = 40.0 + index,
            )
        }

        var settings = LocationSettings()
        many.forEach { location -> settings = repository.selectLocation(location) }

        assertEquals(hamburg.id, settings.homeLocation?.id)
        assertEquals(listOf(berlin.id), settings.favorites.map { it.id })
        assertEquals(LocationSettings.MAX_RECENT_LOCATIONS, settings.recentLocations.size)
        assertTrue(settings.recentLocations.none { it.id == hamburg.id || it.id == berlin.id })
    }

    @Test
    fun `adding a favorite takes the place out of the history`() = runTest {
        val (repository, _) = repositoryWith(LocationSettings(homeLocation = hamburg))
        repository.selectLocation(tokyo)
        repository.selectLocation(paris)

        assertTrue(repository.addFavorite(tokyo))

        val settings = repository.currentSettings()
        assertEquals(listOf(tokyo.id), settings.favorites.map { it.id })
        assertEquals(listOf(paris.id), settings.recentLocations.map { it.id })
    }

    @Test
    fun `removing a favorite puts it at the top of the history`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin, tokyo),
                recentLocations = listOf(paris),
                selectedLocationId = hamburg.id,
            ),
        )

        val settings = repository.removeFavorite(berlin.id)

        assertEquals(listOf(berlin.id, paris.id), settings.recentLocations.map { it.id })
        assertEquals(listOf(tokyo.id), settings.favorites.map { it.id })
        assertEquals(hamburg.id, settings.selectedLocationId)
    }

    @Test
    fun `the home location cannot be removed through the favorite toggle`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin)),
        )

        val settings = repository.removeFavorite(hamburg.id)

        assertEquals(hamburg.id, settings.homeLocation?.id)
        assertEquals(listOf(berlin.id), settings.favorites.map { it.id })
        assertTrue("the protected home location never lands in the history", settings.recentLocations.isEmpty())
    }

    @Test
    fun `removing one history entry leaves favorites and home location alone`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin),
                recentLocations = listOf(tokyo, paris),
                selectedLocationId = hamburg.id,
            ),
        )

        val settings = repository.removeRecentLocation(tokyo.id)

        assertEquals(listOf(paris.id), settings.recentLocations.map { it.id })
        assertEquals(hamburg.id, settings.homeLocation?.id)
        assertEquals(listOf(berlin.id), settings.favorites.map { it.id })
        assertEquals(hamburg.id, settings.selectedLocationId)
    }

    @Test
    fun `clearing the history keeps favorites, the home location and the selection`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin, tokyo),
                recentLocations = listOf(paris, madrid),
                selectedLocationId = berlin.id,
            ),
        )

        val settings = repository.clearRecentLocations()

        assertTrue(settings.recentLocations.isEmpty())
        assertEquals(hamburg.id, settings.homeLocation?.id)
        assertEquals(listOf(berlin.id, tokyo.id), settings.favorites.map { it.id })
        assertEquals(berlin.id, settings.selectedLocationId)
    }

    @Test
    fun `deleting the selected history entry falls back to the home location`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin),
                recentLocations = listOf(tokyo),
                selectedLocationId = tokyo.id,
            ),
        )

        val settings = repository.removeRecentLocation(tokyo.id)

        assertTrue(settings.recentLocations.isEmpty())
        assertEquals(hamburg.id, settings.selectedLocationId)
        assertEquals(hamburg.id, settings.resolveSelectedLocation()?.id)
    }

    @Test
    fun `clearing the history while a history entry is shown selects the home location`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin),
                recentLocations = listOf(tokyo, paris),
                selectedLocationId = paris.id,
            ),
        )

        val settings = repository.clearRecentLocations()

        assertEquals(hamburg.id, settings.selectedLocationId)
    }

    @Test
    fun `history entries survive a restart because they are persisted`() = runTest {
        val store = FakeSettingsStore(LocationSettings(homeLocation = hamburg))
        val repository = LocationRepository(store, FakeGeocodingApi())

        repository.selectLocation(tokyo)
        repository.selectLocation(paris)

        // A fresh repository over the same store is what the next app start does.
        val restarted = LocationRepository(store, FakeGeocodingApi())
        val settings = restarted.currentSettings()

        assertEquals(listOf(paris.id, tokyo.id), settings.recentLocations.map { it.id })
        assertEquals(paris.id, settings.selectedLocationId)
    }

    @Test
    fun `a history entry can become the home location without duplicates`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin, tokyo),
                recentLocations = listOf(paris, madrid),
                selectedLocationId = paris.id,
            ),
        )

        val settings = repository.setHomeLocation(paris)

        assertEquals(paris.id, settings.homeLocation?.id)
        assertTrue(settings.recentLocations.none { it.id == paris.id })
        assertEquals(listOf(madrid.id), settings.recentLocations.map { it.id })
        // The previous home becomes a regular favourite at the end of the list.
        assertEquals(listOf(berlin.id, tokyo.id, hamburg.id), settings.favorites.map { it.id })
        assertEquals(paris.id, settings.selectedLocationId)
        assertEquals(5, settings.selectableLocations.size)
        assertEquals(5, settings.selectableLocations.distinctBy { it.coordinateKey }.size)
    }

    @Test
    fun `selecting an unknown location changes nothing`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, selectedLocationId = hamburg.id),
        )

        val settings = repository.selectLocation("no-such-id")

        assertEquals(hamburg.id, settings.selectedLocationId)
        assertTrue(settings.recentLocations.isEmpty())
    }
}
