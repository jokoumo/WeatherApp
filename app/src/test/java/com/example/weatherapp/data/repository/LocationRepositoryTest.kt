package com.example.weatherapp.data.repository

import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.testing.FakeGeocodingApi
import com.example.weatherapp.testing.FakeSettingsStore
import com.example.weatherapp.testing.berlin
import com.example.weatherapp.testing.geocoderResult
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.tokyo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

/**
 * Location management rules: home location, ordered favorites, duplicate prevention and the
 * selection fallback that keeps a valid location selected after a removal.
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
    fun `setting the home location selects it and removes the duplicate favorite`() = runTest {
        val (repository, _) = repositoryWith(
            LocationSettings(homeLocation = hamburg, favorites = listOf(berlin, tokyo)),
        )

        val settings = repository.setHomeLocation(berlin)

        assertEquals(berlin.id, settings.homeLocation?.id)
        assertEquals(listOf(tokyo.id), settings.favorites.map { it.id })
        assertEquals(berlin.id, settings.selectedLocationId)
        assertEquals(berlin.id, repository.currentSettings().homeLocation?.id)
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
