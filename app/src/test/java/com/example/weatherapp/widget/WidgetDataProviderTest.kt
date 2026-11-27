package com.example.weatherapp.widget

import com.example.weatherapp.data.local.CachedForecast
import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.repository.SnapshotSource
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.testing.FakeForecastApi
import com.example.weatherapp.testing.FakeForecastCacheStore
import com.example.weatherapp.testing.FakeGeocodingApi
import com.example.weatherapp.testing.FakeSettingsStore
import com.example.weatherapp.testing.MutableClock
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.paris
import com.example.weatherapp.testing.sampleForecastDto
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.data.repository.LocationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * How widgets get their data: one request per place no matter how many widgets show it, the cache
 * policy of the app unchanged, and cached values kept on screen when a refresh fails.
 *
 * The widget layer deliberately owns no second cache and no second policy, so this file tests the
 * combination rather than new rules.
 */
class WidgetDataProviderTest {

    private val now: Instant = Instant.parse("2026-11-22T11:00:00Z")
    private val clock = MutableClock(now)
    private val mapper = ForecastMapper()
    private val api = FakeForecastApi()
    private val cache = FakeForecastCacheStore()
    private val weatherRepository = WeatherRepository(api, cache, mapper, clock)

    /** In-memory stand-in for the persisted widget configuration. */
    private class FakeWidgetConfigStore(
        initial: Map<Int, WidgetConfiguration> = emptyMap(),
    ) : WidgetConfigStore {
        private val entries = MutableStateFlow(initial)

        override val configurations = entries

        override suspend fun saveAll(configurations: Collection<WidgetConfiguration>) {
            entries.value = entries.value + configurations.associateBy { it.widgetId }
        }

        override suspend fun remove(widgetIds: Collection<Int>) {
            entries.value = entries.value.filterKeys { it !in widgetIds }
        }
    }

    private fun providerWith(
        settings: LocationSettings,
        configurations: Map<Int, WidgetConfiguration> = emptyMap(),
    ): Pair<WidgetDataProvider, LocationRepository> {
        val locationRepository = LocationRepository(FakeSettingsStore(settings), FakeGeocodingApi())
        val provider = WidgetDataProvider(
            locationRepository = locationRepository,
            weatherRepository = weatherRepository,
            configRepository = WidgetConfigRepository(FakeWidgetConfigStore(configurations)),
            clock = clock,
        )
        return provider to locationRepository
    }

    private suspend fun seedCache(age: Duration, locationId: String = hamburg.id) {
        cache.save(locationId, now.minus(age), mapper.encode(sampleForecastDto()))
    }

    private fun cached(locationId: String = hamburg.id): CachedForecast? = cache.entries[locationId]

    /** A widget update for a place that exists always carries data; a test failure says so clearly. */
    private fun WidgetData?.orFail(): WidgetData = requireNotNull(this)

    // region one request per place

    @Test
    fun `two widgets showing the same place cause one request`() = runTest {
        // The cache is older than the fresh window, so both widgets would refresh on their own.
        seedCache(Duration.ofMinutes(45))
        val (provider, _) = providerWith(
            settings = LocationSettings(homeLocation = hamburg),
            configurations = mapOf(
                1 to WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = hamburg),
                2 to WidgetConfiguration(2, WidgetLocationMode.FIXED, fixedLocation = hamburg),
            ),
        )

        api.gate = CompletableDeferred()
        val first = async { provider.loadForWidget(1) }
        runCurrent()
        val second = async { provider.loadForWidget(2) }
        runCurrent()
        api.gate?.complete(Unit)

        assertNotNull(first.await().orFail())
        val secondData = second.await().orFail()
        assertEquals(1, api.callCount)
        // The second widget reads what the first one just fetched.
        assertEquals(SnapshotSource.CACHED, secondData.source)
    }

    @Test
    fun `widgets of two different places are loaded independently`() = runTest {
        val (provider, _) = providerWith(
            settings = LocationSettings(homeLocation = hamburg),
            configurations = mapOf(
                1 to WidgetConfiguration(1, WidgetLocationMode.HOME),
                2 to WidgetConfiguration(2, WidgetLocationMode.FIXED, fixedLocation = paris),
            ),
        )

        assertNotNull(provider.loadForWidget(1))
        assertNotNull(provider.loadForWidget(2))

        assertEquals(2, api.callCount)
        assertEquals(
            listOf(hamburg.latitude to hamburg.longitude, paris.latitude to paris.longitude),
            api.requestedCoordinates,
        )
    }

    // endregion

    // region the cache policy of the app

    @Test
    fun `a fresh cache is served to a widget without a network call`() = runTest {
        seedCache(Duration.ofMinutes(10))
        val (provider, _) = providerWith(LocationSettings(homeLocation = hamburg))

        val data = provider.loadForWidget(1).orFail()

        assertEquals(0, api.callCount)
        assertEquals(SnapshotSource.CACHED, data.source)
        assertEquals(now.minus(Duration.ofMinutes(10)), data.fetchedAt)
        assertFalse(data.isProbablyOutdated)
    }

    @Test
    fun `an outdated cache is refreshed and the widget shows the new values`() = runTest {
        seedCache(Duration.ofMinutes(45))
        val (provider, _) = providerWith(LocationSettings(homeLocation = hamburg))

        val data = provider.loadForWidget(1).orFail()

        assertEquals(1, api.callCount)
        assertEquals(SnapshotSource.LIVE, data.source)
        assertEquals(now, cached()?.fetchedAt)
    }

    @Test
    fun `a failed refresh keeps the cached values visible`() = runTest {
        seedCache(Duration.ofMinutes(45))
        api.thrown = IOException("no network")
        val (provider, _) = providerWith(LocationSettings(homeLocation = hamburg))

        val data = provider.loadForWidget(1).orFail()

        assertEquals(1, api.callCount)
        assertNotNull("The old forecast has to stay on screen", data.snapshot)
        assertEquals(now.minus(Duration.ofMinutes(45)), data.fetchedAt)
        assertNotNull(data.failure)
        assertEquals(SnapshotSource.CACHED, data.source)
        assertTrue(data.isShowingStaleData)
        // Nothing is deleted from the cache on a failed request.
        assertNotNull(cached())
    }

    @Test
    fun `a place that was never fetched reports no data instead of a guess`() = runTest {
        val (provider, _) = providerWith(LocationSettings(homeLocation = hamburg))
        api.thrown = IOException("no network")

        val data = provider.loadForWidget(1).orFail()

        assertNull(data.snapshot)
        assertNotNull(data.failure)
        assertFalse(data.hasData)
    }

    @Test
    fun `a forced refresh ignores the age of the cache`() = runTest {
        seedCache(Duration.ofSeconds(5))
        val (provider, _) = providerWith(LocationSettings(homeLocation = hamburg))

        provider.loadForWidget(1, forceRefresh = true)

        assertEquals(1, api.callCount)
    }

    @Test
    fun `a presentation refresh reads the cache and never the network`() = runTest {
        seedCache(Duration.ofMinutes(45))

        val (provider, _) = providerWith(LocationSettings(homeLocation = hamburg))
        val snapshot = provider.loadCached(hamburg)

        assertEquals(0, api.callCount)
        assertNotNull(snapshot)
        assertEquals(now.minus(Duration.ofMinutes(45)), snapshot?.fetchedAt)
    }

    // endregion

    // region which place each widget shows

    @Test
    fun `a widget without a configuration shows the home location`() = runTest {
        val (provider, _) = providerWith(LocationSettings(homeLocation = hamburg))

        assertEquals(hamburg, provider.loadForWidget(1).orFail().location)
    }

    @Test
    fun `a widget with nothing to show returns null instead of a random place`() = runTest {
        val (provider, _) = providerWith(LocationSettings(), mapOf(1 to WidgetConfiguration(1, WidgetLocationMode.HOME)))

        assertNull(provider.loadForWidget(1))
        assertEquals(0, api.callCount)
    }

    @Test
    fun `a fixed widget ignores what the app has selected`() = runTest {
        val (provider, locationRepository) = providerWith(
            settings = LocationSettings(homeLocation = hamburg, favorites = listOf(paris)),
            configurations = mapOf(1 to WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = hamburg)),
        )
        locationRepository.selectLocation(paris)

        val data = provider.loadForWidget(1).orFail()

        assertEquals(hamburg, data.location)
        assertEquals(hamburg.latitude to hamburg.longitude, api.requestedCoordinates.last())
    }

    @Test
    fun `the displayed places are the places of all widgets without duplicates`() = runTest {
        val (provider, _) = providerWith(
            settings = LocationSettings(homeLocation = hamburg, favorites = listOf(paris)),
            configurations = mapOf(
                1 to WidgetConfiguration(1, WidgetLocationMode.HOME),
                2 to WidgetConfiguration(2, WidgetLocationMode.HOME),
                3 to WidgetConfiguration(3, WidgetLocationMode.FIXED, fixedLocation = paris),
                4 to WidgetConfiguration(4, WidgetLocationMode.HOME),
            ),
        )

        assertEquals(
            listOf(hamburg.id, paris.id),
            provider.displayedLocations().map(SavedLocation::id),
        )
    }

    @Test
    fun `a place that left the app is still loaded for a fixed widget`() = runTest {
        val (provider, _) = providerWith(
            settings = LocationSettings(homeLocation = hamburg),
            configurations = mapOf(1 to WidgetConfiguration(1, WidgetLocationMode.FIXED, fixedLocation = paris)),
        )

        val data = provider.loadForWidget(1).orFail()

        assertEquals(paris, data.location)
        assertEquals(1, api.callCount)
        assertTrue(cache.entries.containsKey(paris.id))
    }

    // endregion

    // region the clock the widgets agree on

    @Test
    fun `every builder is handed the same reference instant`() = runTest {
        seedCache(Duration.ofMinutes(5))
        val (provider, _) = providerWith(LocationSettings(homeLocation = hamburg))

        val data = provider.loadForWidget(1).orFail()

        assertEquals(now, data.now)
        assertEquals(LocalDate.of(2026, 11, 22), data.now.atZone(data.timeZone).toLocalDate())
    }

    // endregion
}
