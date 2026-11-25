package com.example.weatherapp.data.repository

import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.LocationSettings
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
 * selected after removals while an explicit home change always selects the new home.
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
    fun `removing the selected favorite falls back to the home location`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin, tokyo),
                selectedLocationId = tokyo.id,
            ),
        )

        val settings = repository.removeFavorite(tokyo.id)

        assertEquals(listOf(berlin.id), settings.favorites.map { it.id })
        assertEquals(hamburg.id, settings.selectedLocationId)
    }

    @Test
    fun `removing the home location falls back to the first favorite`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin), selectedLocationId = hamburg.id),
        )

        val settings = repository.removeLocation(hamburg.id)

        assertNull(settings.homeLocation)
        assertEquals(listOf(berlin.id), settings.favorites.map { it.id })
        assertEquals(berlin.id, settings.selectedLocationId)
    }

    @Test
    fun `removing the last location leaves nothing selected`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = emptyList(), selectedLocationId = hamburg.id),
        )

        val settings = repository.removeLocation(hamburg.id)

        assertFalse(settings.hasAnyLocation)
        assertNull(settings.selectedLocationId)
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
}
