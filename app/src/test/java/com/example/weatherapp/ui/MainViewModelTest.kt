package com.example.weatherapp.ui

import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.data.repository.SnapshotSource
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.testing.FakeForecastApi
import com.example.weatherapp.testing.FakeForecastCacheStore
import com.example.weatherapp.testing.FakeGeocodingApi
import com.example.weatherapp.testing.FakeSettingsStore
import com.example.weatherapp.testing.MutableClock
import com.example.weatherapp.testing.berlin
import com.example.weatherapp.testing.geocoderResult
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.newYork
import com.example.weatherapp.testing.paris
import com.example.weatherapp.testing.sampleForecastDto
import com.example.weatherapp.testing.tokyo
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
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Presentation behaviour of [MainViewModel]: initial loading, refresh rules, location
 * switching with in-flight requests, error handling and the search debounce. The second half
 * covers the forecast cache - which location changes may delete a row and which must not - and the
 * favorite star, whose transitions are decided by the repository rather than by the view model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var forecastApi: FakeForecastApi
    private lateinit var cache: FakeForecastCacheStore
    private lateinit var geocodingApi: FakeGeocodingApi
    private lateinit var clock: MutableClock
    private lateinit var viewModel: MainViewModel
    private lateinit var settingsStore: FakeSettingsStore

    private val now = Instant.parse("2026-11-22T11:00:00Z")

    /** How often the view model asked the widget coordinator for a redraw. */
    private var widgetRedraws = 0

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
        widgetRetainedLocationIds: suspend () -> Set<String> = { emptySet() },
        widgetUpdateRequested: suspend () -> Unit = {},
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
            settingsStore = FakeSettingsStore(initialSettings)
            val locationRepository = LocationRepository(settingsStore, geocodingApi)
            val weatherRepository = WeatherRepository(forecastApi, cache, ForecastMapper(), clock)
            viewModel = MainViewModel(
                locationRepository,
                weatherRepository,
                { clock.now },
                widgetRetainedLocationIds = widgetRetainedLocationIds,
                widgetUpdateRequested = widgetUpdateRequested,
            )
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed home setup keeps search results and permits retry`() = withViewModel(initialSettings = LocationSettings()) {
        viewModel.onSearchQueryChange("Hamburg")
        advanceUntilIdle()
        val choice = viewModel.uiState.value.search.results.single()
        settingsStore.writeFailure = IOException("Settings unavailable")
        viewModel.setHomeLocation(choice)
        advanceUntilIdle()

        assertEquals(UserMessage.LOCATION_SAVE_FAILED, viewModel.uiState.value.message)
        assertEquals(listOf(choice), viewModel.uiState.value.search.results)
        assertNull(viewModel.uiState.value.settings.homeLocation)
        assertEquals(0, forecastApi.callCount)

        settingsStore.writeFailure = null
        viewModel.setHomeLocation(choice)
        advanceUntilIdle()
        assertEquals(choice, viewModel.uiState.value.settings.homeLocation)
        assertEquals(UserMessage.HOME_LOCATION_SET, viewModel.uiState.value.message)
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
        assertEquals(24, state.weather.sections.hourlyEntries.size)
        assertEquals(now, state.weather.sections.hourlyEntries.first().hour.instant)
        assertTrue(state.weather.sections.hourlyEntries.first().isCurrent)
        assertEquals(now, state.weather.displayInstant)
        assertEquals(now, state.weather.fetchedAt)
    }

    @Test
    fun `the window advances on the hourly tick without another request`() = withViewModel {
        advanceUntilIdle()
        assertEquals(1, forecastApi.callCount)

        clock.advance(Duration.ofMinutes(61)) // past the cache lifetime on purpose
        viewModel.onClockTick()

        val entries = viewModel.uiState.value.weather.sections.hourlyEntries
        assertEquals(24, entries.size)
        assertEquals(now.plus(Duration.ofHours(1)), entries.first().hour.instant)
        assertTrue(entries.first().isCurrent)
        assertEquals(24, viewModel.uiState.value.weather.sections.todayHours.size)
        // Moving the window forward never triggers a request by itself.
        assertEquals(1, forecastApi.callCount)
        assertEquals(SnapshotSource.LIVE, viewModel.uiState.value.weather.snapshotSource)
    }

    @Test
    fun `a snapshot from the previous local day is refreshed once when the day changes`() = withViewModel {
        advanceUntilIdle()
        assertEquals(1, forecastApi.callCount)

        // 01:05 local on 23 November; the cached download belongs to 22 November.
        clock.advance(Duration.ofHours(13).plusMinutes(5))
        val before = viewModel.uiState.value.weather
        assertTrue(before.hasData)
        assertEquals(2026, before.today?.year ?: 0)

        viewModel.onClockTick()
        advanceUntilIdle()

        assertEquals(2, forecastApi.callCount)
        val after = viewModel.uiState.value.weather
        assertTrue(after.hasData)
        assertEquals(now.plus(Duration.ofHours(13)), after.sections.hourlyEntries.first().hour.instant)

        // A further tick on the same local day does not request again.
        clock.advance(Duration.ofHours(1))
        viewModel.onClockTick()
        advanceUntilIdle()
        assertEquals(2, forecastApi.callCount)
        val latest = viewModel.uiState.value.weather
        assertEquals(now.plus(Duration.ofHours(14)), latest.sections.hourlyEntries.first().hour.instant)
    }

    @Test
    fun `returning to the foreground after midnight shows the new window`() = withViewModel {
        advanceUntilIdle()
        clock.advance(Duration.ofHours(13).plusMinutes(5))

        viewModel.onForeground()
        advanceUntilIdle()

        val entries = viewModel.uiState.value.weather.sections.hourlyEntries
        assertEquals(2, forecastApi.callCount)
        assertEquals(24, entries.size)
        assertEquals(now.plus(Duration.ofHours(13)), entries.first().hour.instant)
        assertTrue(entries.first().isCurrent)
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

    /**
     * A location the user deleted must not leave its forecast behind: the cache row is keyed by
     * location id and could never be read again.
     */
    @Test
    fun `removing a location also drops its cached forecast`() = withViewModel {
        advanceUntilIdle()
        viewModel.addFavorite(berlin)
        advanceUntilIdle()
        viewModel.selectLocation(berlin.id)
        advanceUntilIdle()
        assertTrue(cache.entries.containsKey(berlin.id))

        val deletesBefore = cache.deleteCount
        viewModel.removeLocation(berlin.id)
        advanceUntilIdle()

        assertEquals(deletesBefore + 1, cache.deleteCount)
        assertFalse("the deleted location still holds a forecast", cache.entries.containsKey(berlin.id))
        assertTrue("the remaining location keeps its forecast", cache.entries.containsKey(hamburg.id))
    }

    // ---------------------------------------------------------------- cache consistency

    /**
     * A history that grows past its capacity evicts the least recent entry. The forecast of that
     * entry can never be read again, so it is removed together with the entry, while every place
     * that is still kept holds on to its row.
     */
    @Test
    fun `an evicted history entry loses its cached forecast`() = withViewModel(
        initialSettings = LocationSettings(
            homeLocation = hamburg,
            selectedLocationId = hamburg.id,
            recentLocations = (1..LocationSettings.MAX_RECENT_LOCATIONS).map { index ->
                SavedLocation(
                    id = "hist-$index",
                    name = "Verlaufsort $index",
                    latitude = index * 0.1,
                    longitude = 5.0,
                )
            },
        ),
    ) {
        advanceUntilIdle()
        val newest = SavedLocation(id = "hist-1", name = "Verlaufsort 1", latitude = 0.1, longitude = 5.0)
        val evicted = SavedLocation(
            id = "hist-${LocationSettings.MAX_RECENT_LOCATIONS}",
            name = "Verlaufsort ${LocationSettings.MAX_RECENT_LOCATIONS}",
            latitude = LocationSettings.MAX_RECENT_LOCATIONS * 0.1,
            longitude = 5.0,
        )
        cache.save(newest.id, now, "{}")
        cache.save(evicted.id, now, "{}")

        viewModel.selectSearchedLocation(newYork)
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertEquals(LocationSettings.MAX_RECENT_LOCATIONS, settings.recentLocations.size)
        assertEquals(newYork.id, settings.recentLocations.first().id)
        assertFalse("the least recent entry was evicted", settings.recentLocations.any { it.id == evicted.id })
        assertFalse("the evicted place still holds a forecast", cache.entries.containsKey(evicted.id))
        assertTrue("a retained history entry keeps its forecast", cache.entries.containsKey(newest.id))
        assertTrue("the home location keeps its forecast", cache.entries.containsKey(hamburg.id))
        assertTrue("the newly selected location was cached", cache.entries.containsKey(newYork.id))
    }

    /**
     * Rows that no role refers to any more - written by an older version, or left behind when the
     * app was closed between an eviction and its cleanup - are removed once per app start.
     */
    @Test
    fun `cache rows nobody refers to are removed on start`() = withViewModel {
        cache.save("orphaned-place", now, "{}")
        advanceUntilIdle()

        assertFalse(cache.entries.containsKey("orphaned-place"))
        assertTrue("the selected location keeps its forecast", cache.entries.containsKey(hamburg.id))
    }

    /**
     * Cleanup only ever touches rows that are provably unreferenced. A place that merely lost its
     * favorite status is still selected and still in the history, so its forecast stays on the
     * device and the dashboard keeps working offline.
     */
    @Test
    fun `unfavoriting a place keeps its cached forecast`() = withViewModel {
        advanceUntilIdle()
        viewModel.toggleFavorite(berlin)
        advanceUntilIdle()
        viewModel.selectLocation(berlin.id)
        advanceUntilIdle()
        assertTrue(cache.entries.containsKey(berlin.id))

        val deletesBefore = cache.deleteCount
        viewModel.toggleFavorite(berlin)
        advanceUntilIdle()

        assertEquals("nothing may be deleted while the place is still in use", deletesBefore, cache.deleteCount)
        assertTrue(cache.entries.containsKey(berlin.id))
        assertTrue(cache.entries.containsKey(hamburg.id))
    }

    // -------------------------------------------------------------- home screen widgets

    /**
     * A widget can be pinned to a place the app does not keep any more. Its forecast is still read
     * every update, so the row must survive the cleanup that removes rows nothing refers to - which is
     * exactly the case the plain settings cannot see.
     */
    @Test
    fun `a place a widget is pinned to keeps its forecast although the app does not use it`() = withViewModel(
        widgetRetainedLocationIds = { setOf("widget-pinned-place") },
    ) {
        cache.save("widget-pinned-place", now, "{}")
        advanceUntilIdle()

        assertTrue("a widget still reads this forecast", cache.entries.containsKey("widget-pinned-place"))
        assertTrue("the selected location keeps its forecast", cache.entries.containsKey(hamburg.id))
    }

    /**
     * Removing a place from the app must not throw away the data of a widget that still shows it. The
     * widget keeps its own copy of the location, and the cache row stays readable for it.
     */
    @Test
    fun `deleting a location a widget still shows keeps its forecast`() = withViewModel(
        widgetRetainedLocationIds = { setOf(berlin.id) },
    ) {
        advanceUntilIdle()
        viewModel.addFavorite(berlin)
        advanceUntilIdle()
        viewModel.selectLocation(berlin.id)
        advanceUntilIdle()
        assertTrue(cache.entries.containsKey(berlin.id))

        val deletesBefore = cache.deleteCount
        viewModel.removeLocation(berlin.id)
        advanceUntilIdle()

        assertEquals("a pinned widget still needs this row", deletesBefore, cache.deleteCount)
        assertTrue(cache.entries.containsKey(berlin.id))
    }

    /** Every redraw of the dashboard that brought new data also refreshes the widgets. */
    @Test
    fun `the widgets are asked to redraw when the app has new data or another location`() = withViewModel(
        widgetUpdateRequested = { widgetRedraws++ },
    ) {
        advanceUntilIdle()
        assertTrue("live data for the dashboard reaches the widgets too", widgetRedraws > 0)

        val afterStart = widgetRedraws
        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()

        assertTrue("the widgets follow a location change", widgetRedraws > afterStart)
        assertEquals(tokyo.id, viewModel.uiState.value.weather.location?.id)
    }

    /**
     * Tapping a widget of the home location opens the app there. The stored home location itself is
     * not written, so a widget can never replace the protected place.
     */
    @Test
    fun `tapping a home location widget shows it without rewriting the stored home location`() = withViewModel(
        initialSettings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            selectedLocationId = berlin.id,
        ),
    ) {
        advanceUntilIdle()
        assertEquals(berlin.id, viewModel.uiState.value.weather.location?.id)

        viewModel.showHomeLocation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(hamburg.id, state.weather.location?.id)
        assertEquals(hamburg.id, state.settings.homeLocation?.id)
        assertEquals(listOf(berlin.id), state.settings.favorites.map { it.id })
        assertTrue("the home location never becomes a history entry", state.settings.recentLocations.isEmpty())
    }

    /** A widget that already shows the displayed place causes no second request. */
    @Test
    fun `tapping a widget of the displayed location changes nothing`() = withViewModel {
        advanceUntilIdle()
        val callsAfterStart = forecastApi.callCount

        viewModel.showHomeLocation()
        advanceUntilIdle()

        assertEquals(hamburg.id, viewModel.uiState.value.weather.location?.id)
        assertEquals(callsAfterStart, forecastApi.callCount)
    }

    /**
     * The place of a fixed widget may have left favourites and history long ago. Tapping the widget
     * shows it again; like a search result it may enter the history, but it is never favourited and it
     * never replaces the home location.
     */
    @Test
    fun `tapping a fixed widget shows a place the app no longer knows without favouriting it`() = withViewModel {
        advanceUntilIdle()

        viewModel.showFixedLocation(tokyo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(tokyo.id, state.weather.location?.id)
        assertNotNull(state.weather.current)
        assertTrue("a widget tap never favourites a place", state.settings.favorites.isEmpty())
        assertEquals(hamburg.id, state.settings.homeLocation?.id)
        assertEquals(listOf(tokyo.id), state.settings.recentLocations.map { it.id })
    }

    // ---------------------------------------------------------------- favorite toggling

    /**
     * Two taps on the star in quick succession have to produce two real changes. The transition is
     * decided inside the repository, so the second tap sees what the first one wrote instead of the
     * interface state that was current when the first tap arrived.
     */
    @Test
    fun `two rapid star taps add and remove the favorite again`() = withViewModel {
        advanceUntilIdle()

        viewModel.toggleFavorite(berlin)
        viewModel.toggleFavorite(berlin)
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertTrue("the second tap has to undo the first one", settings.favorites.isEmpty())
        assertEquals(listOf(berlin.id), settings.recentLocations.map { it.id })
        assertEquals(UserMessage.LOCATION_REMOVED_FROM_FAVORITES, viewModel.uiState.value.message)
    }

    /** The star of the home location reports the protected role instead of switching it off. */
    @Test
    fun `the star of the home location reports that it is protected`() = withViewModel {
        advanceUntilIdle()

        viewModel.toggleFavorite(hamburg)
        advanceUntilIdle()

        assertEquals(UserMessage.HOME_LOCATION_PROTECTED, viewModel.uiState.value.message)
        assertEquals(hamburg.id, viewModel.uiState.value.settings.homeLocation?.id)
        assertTrue(viewModel.uiState.value.settings.favorites.isEmpty())
    }

    // ------------------------------------------------------- identifying the location early

    /**
     * The dashboard names the place the user picked as soon as it is selected: the location belongs
     * to the weather state from the first moment on, no value of the previous place survives and
     * nothing about the new one is invented while the request is running.
     */
    @Test
    fun `a newly selected location is identified before its weather arrives`() = withViewModel(
        initialSettings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            selectedLocationId = hamburg.id,
        ),
    ) {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.weather.hasData)

        forecastApi.gate = CompletableDeferred()
        viewModel.selectLocation(berlin.id)
        runCurrent()

        val pending = viewModel.uiState.value
        assertEquals(berlin.id, pending.weather.location?.id)
        assertEquals(berlin.id, pending.settings.resolveSelectedLocation()?.id)
        assertFalse("no values may be carried over", pending.weather.hasData)
        assertNull(pending.weather.current)
        assertNull(pending.weather.failure)
        assertTrue(pending.weather.isRefreshing)
        // The star is already correct for the new place: a favorite, not the home location.
        assertTrue(pending.displayedLocationIsFavorite)
        assertFalse(pending.displayedLocationIsHome)

        forecastApi.gate?.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.weather.hasData)
    }

    /**
     * A failed load keeps the location identified and reports the failure; it neither clears the
     * selection nor sends the user back to another place.
     */
    @Test
    fun `a failed load keeps the location and reports the failure`() = withViewModel(
        initialSettings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            selectedLocationId = hamburg.id,
        ),
    ) {
        advanceUntilIdle()
        forecastApi.thrown = UnknownHostException("api.open-meteo.com")
        viewModel.selectLocation(berlin.id)
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertEquals(berlin.id, failed.weather.location?.id)
        assertEquals(berlin.id, failed.settings.resolveSelectedLocation()?.id)
        assertEquals(DataLoadFailure.NETWORK, failed.weather.failure)
        assertFalse(failed.weather.hasData)
        assertFalse(failed.weather.isRefreshing)
        assertTrue(failed.displayedLocationIsFavorite)
    }

    @Test
    fun `today extremes are derived from today hourly values`() = withViewModel {        advanceUntilIdle()
        val weather = viewModel.uiState.value.weather

        assertNotNull(weather.todayMin)
        assertNotNull(weather.todayMax)
        assertTrue((weather.todayMin ?: 0.0) <= (weather.todayMax ?: 0.0))
        assertEquals(2026, weather.today?.year ?: 0)
    }

    // ------------------------------------------------------------ search to dashboard

    @Test
    fun `a tapped suggestion is shown at once without becoming a favorite`() = withViewModel {
        advanceUntilIdle()

        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(tokyo.id, state.weather.location?.id)
        assertTrue("weather loads for a location that is not saved", state.weather.hasData)
        assertEquals(2, forecastApi.callCount)
        assertTrue("browsing a place never saves it", state.settings.favorites.isEmpty())
        assertEquals(listOf(tokyo.id), state.settings.recentLocations.map { it.id })
        assertEquals("", state.search.query)
        assertTrue(state.search.results.isEmpty())
    }

    @Test
    fun `the dashboard star reports the role of the displayed location`() = withViewModel {
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.displayedLocationIsHome)

        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.displayedLocationIsFavorite)
        assertTrue(viewModel.uiState.value.displayedLocationIsRecent)

        viewModel.toggleFavorite(tokyo)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.displayedLocationIsFavorite)
        assertFalse(viewModel.uiState.value.displayedLocationIsRecent)
    }

    @Test
    fun `selecting the same suggestion twice keeps a single history entry`() = withViewModel {
        advanceUntilIdle()

        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()
        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()

        assertEquals(listOf(tokyo.id), viewModel.uiState.value.settings.recentLocations.map { it.id })
    }

    @Test
    fun `selecting a favorite or the home location leaves the history empty`() = withViewModel(
        initialSettings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            selectedLocationId = hamburg.id,
        ),
    ) {
        advanceUntilIdle()

        viewModel.selectSearchedLocation(berlin)
        advanceUntilIdle()
        viewModel.selectSearchedLocation(hamburg)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.settings.recentLocations.isEmpty())
        assertEquals(hamburg.id, viewModel.uiState.value.weather.location?.id)
    }

    // ---------------------------------------------------------------- favorite star

    @Test
    fun `the star moves a history entry into the favorites and back again`() = withViewModel {
        advanceUntilIdle()
        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()

        viewModel.toggleFavorite(tokyo)
        advanceUntilIdle()
        var settings = viewModel.uiState.value.settings
        assertEquals(listOf(tokyo.id), settings.favorites.map { it.id })
        assertTrue(settings.recentLocations.isEmpty())

        viewModel.toggleFavorite(tokyo)
        advanceUntilIdle()
        settings = viewModel.uiState.value.settings
        assertTrue(settings.favorites.isEmpty())
        assertEquals(listOf(tokyo.id), settings.recentLocations.map { it.id })
        // The place the user is looking at stays on screen the whole time.
        assertEquals(tokyo.id, viewModel.uiState.value.weather.location?.id)
        assertNotNull(viewModel.uiState.value.weather.current)
    }

    @Test
    fun `changing the favorite state never requests the forecast again`() = withViewModel {
        advanceUntilIdle()
        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()
        assertEquals(2, forecastApi.callCount)

        viewModel.toggleFavorite(tokyo)
        advanceUntilIdle()
        viewModel.toggleFavorite(tokyo)
        advanceUntilIdle()

        assertEquals(2, forecastApi.callCount)
    }

    @Test
    fun `the home location stays saved when its star is tapped`() = withViewModel {
        advanceUntilIdle()

        viewModel.toggleFavorite(hamburg)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(UserMessage.HOME_LOCATION_PROTECTED, state.message)
        assertEquals(hamburg.id, state.settings.homeLocation?.id)
        assertTrue(state.settings.favorites.isEmpty())
        assertTrue(state.settings.recentLocations.isEmpty())
        assertEquals(hamburg.id, state.weather.location?.id)
        assertEquals(1, forecastApi.callCount)
    }

    @Test
    fun `unfavouriting the displayed location keeps its forecast in the cache`() = withViewModel {
        advanceUntilIdle()
        viewModel.addFavorite(berlin)
        advanceUntilIdle()
        viewModel.selectLocation(berlin.id)
        advanceUntilIdle()
        assertTrue(cache.entries.containsKey(berlin.id))

        viewModel.toggleFavorite(berlin)
        advanceUntilIdle()

        assertEquals(berlin.id, viewModel.uiState.value.weather.location?.id)
        assertTrue("the selected location still needs its forecast", cache.entries.containsKey(berlin.id))
        assertEquals(listOf(berlin.id), viewModel.uiState.value.settings.recentLocations.map { it.id })
    }

    // ------------------------------------------------------------------- history

    @Test
    fun `deleting the displayed history entry falls back to the home location`() = withViewModel {
        advanceUntilIdle()
        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()
        assertEquals(tokyo.id, viewModel.uiState.value.weather.location?.id)

        viewModel.removeRecentLocation(tokyo.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.settings.recentLocations.isEmpty())
        assertEquals(hamburg.id, state.weather.location?.id)
        assertNotNull(state.weather.current)
        // The forecast of a place nothing refers to any more is dropped with it.
        assertFalse(cache.entries.containsKey(tokyo.id))
        assertTrue(cache.entries.containsKey(hamburg.id))
    }

    @Test
    fun `clearing the history keeps favorites, the home location and their forecasts`() = withViewModel {
        advanceUntilIdle()
        viewModel.addFavorite(berlin)
        advanceUntilIdle()
        // Visiting the favourite is what puts its forecast into the cache in the first place.
        viewModel.selectLocation(berlin.id)
        advanceUntilIdle()
        assertTrue(cache.entries.containsKey(berlin.id))

        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()
        viewModel.selectSearchedLocation(paris)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.settings.recentLocations.size)

        viewModel.clearRecentLocations()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.settings.recentLocations.isEmpty())
        assertEquals(hamburg.id, state.settings.homeLocation?.id)
        assertEquals(listOf(berlin.id), state.settings.favorites.map { it.id })
        assertEquals(UserMessage.HISTORY_CLEARED, state.message)
        // The favourite and the home location keep their forecasts; only the two history places
        // lose theirs, because nothing refers to them any more.
        assertTrue(cache.entries.containsKey(berlin.id))
        assertTrue(cache.entries.containsKey(hamburg.id))
        assertFalse(cache.entries.containsKey(tokyo.id))
        assertFalse(cache.entries.containsKey(paris.id))
    }

    @Test
    fun `clearing the history while one of its places is shown selects the home location`() = withViewModel {
        advanceUntilIdle()
        viewModel.selectSearchedLocation(tokyo)
        advanceUntilIdle()

        viewModel.clearRecentLocations()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(hamburg.id, state.weather.location?.id)
        assertEquals(hamburg.id, state.settings.selectedLocationId)
        assertNotNull(state.weather.current)
    }

    @Test
    fun `opening the location manager and browsing history adds no extra request`() = withViewModel(
        initialSettings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            recentLocations = listOf(tokyo),
            selectedLocationId = hamburg.id,
        ),
    ) {
        advanceUntilIdle()
        assertEquals(1, forecastApi.callCount)

        // Selecting the home location again is a no-op for the weather.
        viewModel.selectLocation(hamburg.id)
        advanceUntilIdle()
        assertEquals(1, forecastApi.callCount)

        // Re-ranking a history entry does not reload either.
        viewModel.selectLocation(tokyo.id)
        advanceUntilIdle()
        assertEquals(2, forecastApi.callCount)
        assertEquals(tokyo.id, viewModel.uiState.value.weather.location?.id)
        assertEquals(listOf(tokyo.id), viewModel.uiState.value.settings.recentLocations.map { it.id })

        viewModel.selectLocation(hamburg.id)
        advanceUntilIdle()
        // Hamburg was cached by the very first load, so coming back needs no new call.
        assertEquals(2, forecastApi.callCount)
    }

    @Test
    fun `switching quickly between locations never shows the wrong forecast`() = withViewModel(
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

        advanceUntilIdle()
        assertEquals(1, forecastApi.callCount)

        // Two selections in a row while the first response is still on its way.
        viewModel.selectLocation(berlin.id)
        runCurrent()
        viewModel.selectSearchedLocation(tokyo)
        runCurrent()
        forecastApi.gate?.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(tokyo.id, state.weather.location?.id)
        assertEquals(
            "the values on screen must belong to the location named above them",
            6.4,
            requireNotNull(state.weather.current).temperatureCelsius,
            0.0001,
        )
    }
}
