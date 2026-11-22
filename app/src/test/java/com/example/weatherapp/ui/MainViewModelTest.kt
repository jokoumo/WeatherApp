package com.example.weatherapp.ui

import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.data.repository.SnapshotSource
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.testing.FakeForecastApi
import com.example.weatherapp.testing.FakeForecastCacheStore
import com.example.weatherapp.testing.FakeGeocodingApi
import com.example.weatherapp.testing.FakeSettingsStore
import com.example.weatherapp.testing.MutableClock
import com.example.weatherapp.testing.berlin
import com.example.weatherapp.testing.geocoderResult
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.sampleForecastDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant

/**
 * Presentation behaviour of [MainViewModel]: initial loading, refresh rules, location
 * switching with in-flight requests, error handling and the search debounce.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var forecastApi: FakeForecastApi
    private lateinit var cache: FakeForecastCacheStore
    private lateinit var geocodingApi: FakeGeocodingApi
    private lateinit var clock: MutableClock
    private lateinit var viewModel: MainViewModel

    private val now = Instant.parse("2026-11-22T11:00:00Z")

    /**
     * Runs [body] with a ViewModel wired to fakes. The main dispatcher shares the test
     * scheduler, so `advanceUntilIdle` also drives viewModelScope work.
     */
    private fun withViewModel(
        initialSettings: LocationSettings = LocationSettings(
            homeLocation = hamburg,
            selectedLocationId = hamburg.id,
        ),
        gate: CompletableDeferred<Unit>? = null,
        body: suspend TestScope.() -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            clock = MutableClock(now)
            forecastApi = FakeForecastApi().apply { this.gate = gate }
            cache = FakeForecastCacheStore()
            geocodingApi = FakeGeocodingApi(
                results = listOf(geocoderResult(2911298, "Hamburg", 53.55, 9.99)),
            )
            val locationRepository = LocationRepository(FakeSettingsStore(initialSettings), geocodingApi)
            val weatherRepository = WeatherRepository(forecastApi, cache, ForecastMapper(), clock)
            viewModel = MainViewModel(locationRepository, weatherRepository) { clock.now }
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `start with a saved location loads it and shows live data`() = withViewModel {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.settingsLoaded)
        assertFalse(state.isInitialLoading)
        assertEquals(hamburg.id, state.weather.location?.id)
        assertTrue(state.weather.hasData)
        assertEquals(SnapshotSource.LIVE, state.weather.snapshotSource)
        assertNull(state.weather.failure)
        assertEquals(1, forecastApi.callCount)
        assertEquals(24, state.weather.sections.todayHours.size)
        assertEquals(12, state.weather.hourlyAnchorIndex)
        assertEquals(now, state.weather.fetchedAt)
    }

    @Test
    fun `without any saved location the app stays on the setup screen`() =
        withViewModel(initialSettings = LocationSettings()) {
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.settingsLoaded)
            assertFalse(state.settings.hasAnyLocation)
            assertFalse(state.weather.hasData)
            assertEquals(0, forecastApi.callCount)
        }

    @Test
    fun `selecting another location loads that location`() =
        withViewModel(
            initialSettings = LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin),
                selectedLocationId = hamburg.id,
            ),
        ) {
            advanceUntilIdle()
        viewModel.selectLocation(berlin.id)
        advanceUntilIdle()

        assertEquals(berlin.id, viewModel.uiState.value.weather.location?.id)
        assertEquals(2, forecastApi.callCount)
        assertEquals(
            listOf(hamburg.latitude to hamburg.longitude, berlin.latitude to berlin.longitude),
            forecastApi.requestedCoordinates,
        )
    }

    @Test
    fun `a late response for a location that is no longer selected is ignored`() =
        withViewModel(
            initialSettings = LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin),
                selectedLocationId = hamburg.id,
            ),
            gate = CompletableDeferred(),
        ) {
            val berlinPayload = sampleForecastDto().copy(
                current = sampleForecastDto().current?.copy(temperature = -2.0, weatherCode = 71),
            )
            forecastApi.payloadsByCoordinate[hamburg.latitude to hamburg.longitude] = sampleForecastDto()
            forecastApi.payloadsByCoordinate[berlin.latitude to berlin.longitude] = berlinPayload

            // Loading Hamburg starts but is blocked inside the API double.
            advanceUntilIdle()
            assertEquals(1, forecastApi.callCount)

            viewModel.selectLocation(berlin.id)
            runCurrent()
            val gate = requireNotNull(forecastApi.gate)
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(berlin.id, state.weather.location?.id)
            assertEquals(-2.0, requireNotNull(state.weather.current).temperatureCelsius, 0.0001)
            assertEquals(SnapshotSource.LIVE, state.weather.snapshotSource)
        }

    @Test
    fun `returning to the foreground refreshes data that is past the cache lifetime`() =
        withViewModel {
            advanceUntilIdle()
            assertEquals(1, forecastApi.callCount)

            clock.advance(Duration.ofMinutes(45))
            viewModel.onForeground()
            advanceUntilIdle()

            assertEquals(2, forecastApi.callCount)
            assertEquals(
                now.plus(Duration.ofMinutes(45)),
                cache.entries.getValue(hamburg.id).fetchedAt,
            )
        }

    @Test
    fun `returning to the foreground keeps data that is still fresh`() = withViewModel {
        advanceUntilIdle()
        clock.advance(Duration.ofMinutes(10))
        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(1, forecastApi.callCount)
    }

    @Test
    fun `manual refresh bypasses the fresh cache`() = withViewModel {
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, forecastApi.callCount)
        assertEquals(SnapshotSource.LIVE, viewModel.uiState.value.weather.snapshotSource)
    }

    @Test
    fun `a failed refresh keeps the cached snapshot and marks the data as stale`() =
        withViewModel {
            advanceUntilIdle()
            clock.advance(Duration.ofMinutes(45))
            forecastApi.thrown = UnknownHostException("api.open-meteo.com")

            viewModel.refresh()
            advanceUntilIdle()

            val weather = viewModel.uiState.value.weather
            assertTrue(weather.hasData)
            assertEquals(SnapshotSource.CACHED, weather.snapshotSource)
            assertEquals(DataLoadFailure.NETWORK, weather.failure)
            assertTrue(weather.isShowingStaleData)
        }

    @Test
    fun `a failed first load exposes the error without any data`() = withViewModel {
            forecastApi.thrown = UnknownHostException("api.open-meteo.com")
            advanceUntilIdle()

            val weather = viewModel.uiState.value.weather
            assertFalse(weather.hasData)
            assertTrue(weather.hasLoadedOnce)
            assertEquals(DataLoadFailure.NETWORK, weather.failure)
            assertNull(weather.current)
        }

    @Test
    fun `cached data is shown immediately while the refresh is running`() =
        withViewModel {
            advanceUntilIdle()
            clock.advance(Duration.ofMinutes(45))
            forecastApi.gate = CompletableDeferred()
            viewModel.refresh()
            runCurrent()

            // The cached snapshot from the first load is still on screen.
            val duringRefresh = viewModel.uiState.value.weather
            assertTrue(duringRefresh.hasData)
            assertTrue(duringRefresh.isRefreshing)
            assertEquals(SnapshotSource.CACHED, duringRefresh.snapshotSource)

            forecastApi.gate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(SnapshotSource.LIVE, viewModel.uiState.value.weather.snapshotSource)
        }

    @Test
    fun `adding a duplicate favorite reports it and changes nothing`() = withViewModel {
        advanceUntilIdle()

        viewModel.addFavorite(berlin)
        advanceUntilIdle()
        assertEquals(UserMessage.LOCATION_ADDED, viewModel.uiState.value.message)
        viewModel.consumeMessage()
        assertEquals(1, viewModel.uiState.value.settings.favorites.size)

        viewModel.addFavorite(berlin)
        advanceUntilIdle()
        assertEquals(UserMessage.LOCATION_DUPLICATE, viewModel.uiState.value.message)
        assertEquals(1, viewModel.uiState.value.settings.favorites.size)

        viewModel.consumeMessage()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `setting the home location reports it and selects it`() = withViewModel {
        advanceUntilIdle()
        viewModel.addFavorite(berlin)
        viewModel.consumeMessage()
        advanceUntilIdle()

        viewModel.setHomeLocation(berlin)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(UserMessage.HOME_LOCATION_SET, state.message)
        assertEquals(berlin.id, state.settings.homeLocation?.id)
        assertEquals(berlin.id, state.weather.location?.id)
    }

    @Test
    fun `search waits for the debounce and only sends the final query`() = withViewModel {
        advanceUntilIdle()

        viewModel.onSearchQueryChange("H")
        runCurrent()
        assertTrue(viewModel.uiState.value.search.queryTooShort)
        assertEquals(0, geocodingApi.callCount)

        viewModel.onSearchQueryChange("Ham")
        viewModel.onSearchQueryChange("Hamburg")
        advanceUntilIdle()

        assertEquals(listOf("Hamburg"), geocodingApi.queries)
        val search = viewModel.uiState.value.search
        assertEquals("Hamburg", search.query)
        assertFalse(search.isSearching)
        assertEquals(1, search.results.size)
        assertEquals(hamburg.id, search.results.first().id)
    }

    @Test
    fun `search reports failures and can be cleared`() = withViewModel {
        advanceUntilIdle()
        geocodingApi.thrown = UnknownHostException("geocoding-api.open-meteo.com")

        viewModel.onSearchQueryChange("Hamburg")
        advanceUntilIdle()

        assertEquals(DataLoadFailure.NETWORK, viewModel.uiState.value.search.failure)
        assertTrue(viewModel.uiState.value.search.results.isEmpty())

        geocodingApi.thrown = null
        viewModel.clearSearch()
        val cleared = viewModel.uiState.value.search
        assertEquals("", cleared.query)
        assertTrue(cleared.results.isEmpty())
        assertNull(cleared.failure)
    }

    @Test
    fun `removing the selected location switches to a remaining one`() = withViewModel {
        advanceUntilIdle()
        viewModel.addFavorite(berlin)
        advanceUntilIdle()
        viewModel.selectLocation(berlin.id)
        advanceUntilIdle()
        assertEquals(berlin.id, viewModel.uiState.value.weather.location?.id)

        viewModel.removeLocation(berlin.id)
        advanceUntilIdle()

        assertEquals(hamburg.id, viewModel.uiState.value.weather.location?.id)
        assertNotNull(viewModel.uiState.value.weather.current)
    }

    @Test
    fun `today extremes are derived from today hourly values`() = withViewModel {
        advanceUntilIdle()
        val weather = viewModel.uiState.value.weather

        assertNotNull(weather.todayMin)
        assertNotNull(weather.todayMax)
        assertTrue((weather.todayMin ?: 0.0) <= (weather.todayMax ?: 0.0))
        assertEquals(2026, weather.today?.year ?: 0)
    }
}
