package com.example.weatherapp.data.repository

import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.local.CachedForecast
import com.example.weatherapp.data.local.ForecastCacheStore
import com.example.weatherapp.data.remote.dto.CurrentDto
import com.example.weatherapp.data.remote.dto.ForecastResponseDto
import com.example.weatherapp.data.remote.dto.DailyDto
import com.example.weatherapp.data.remote.dto.HourlyDto
import com.example.weatherapp.testing.FakeForecastApi
import com.example.weatherapp.testing.FakeForecastCacheStore
import com.example.weatherapp.testing.MutableClock
import com.example.weatherapp.testing.errorResponse
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.sampleForecastDto
import com.example.weatherapp.domain.model.DataLoadFailure
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant

/**
 * Cache and error behaviour of the weather repository: when the network is used, when the
 * cache is served, what happens on failures and how status codes are translated.
 */
class WeatherRepositoryTest {

    private val now = Instant.parse("2026-11-22T11:00:00Z")
    private val clock = MutableClock(now)
    private val mapper = ForecastMapper()
    private val api = FakeForecastApi()
    private val cache = FakeForecastCacheStore()
    private val repository = WeatherRepository(api, cache, mapper, clock)

    private suspend fun seedCache(age: Duration, payload: ForecastResponseDto = sampleForecastDto()) {
        cache.save(hamburg.id, now.minus(age), mapper.encode(payload))
    }

    @Test
    fun `cache can be read without any network call`() = runTest {
        seedCache(Duration.ofMinutes(2))
        val snapshot = repository.loadCached(hamburg)
        assertEquals(0, api.callCount)
        assertEquals(6.4, requireNotNull(snapshot?.current)?.temperatureCelsius ?: 0.0, 0.0001)
        assertEquals(now.minus(Duration.ofMinutes(2)), snapshot?.fetchedAt)
    }

    @Test
    fun `fresh cache is served directly and no request is sent`() = runTest {
        seedCache(Duration.ofMinutes(29))
        val result = repository.load(hamburg, forceRefresh = false)
        assertEquals(SnapshotSource.CACHED, result.source)
        assertEquals(0, api.callCount)
        assertNull(result.failure)
        assertNotNull(result.snapshot)
    }

    @Test
    fun `cache older than thirty minutes triggers a refresh and is replaced`() = runTest {
        seedCache(Duration.ofMinutes(31))
        val result = repository.load(hamburg, forceRefresh = false)

        assertEquals(1, api.callCount)
        assertEquals(SnapshotSource.LIVE, result.source)
        assertNull(result.failure)
        assertEquals(now, cache.entries.getValue(hamburg.id).fetchedAt)
    }

    @Test
    fun `cache from the previous local day is refreshed although it is still young`() = runTest {
        // Downloaded at 23:50 local on 21 Nov; fifteen minutes later Berlin is on 22 November.
        val dayChangeClock = MutableClock(Instant.parse("2026-11-21T23:05:00Z"))
        cache.save(hamburg.id, Instant.parse("2026-11-21T22:50:00Z"), mapper.encode(sampleForecastDto()))

        val result = WeatherRepository(api, cache, mapper, dayChangeClock).load(hamburg, forceRefresh = false)

        assertEquals(1, api.callCount)
        assertEquals(SnapshotSource.LIVE, result.source)
        assertEquals(Instant.parse("2026-11-21T23:05:00Z"), cache.entries.getValue(hamburg.id).fetchedAt)
    }

    @Test
    fun `cache stays usable through the last interval in a fractional offset zone`() = runTest {
        val location = hamburg.copy(timeZoneId = "Asia/Kathmandu")
        val lastStart = Instant.parse("2026-11-22T10:15:00Z")
        val payload = ForecastResponseDto(
            timezone = location.timeZoneId,
            hourly = HourlyDto(time = listOf(lastStart.epochSecond.toString()), temperature = listOf(2.0)),
        )
        cache.save(location.id, now.minus(Duration.ofMinutes(2)), mapper.encode(payload))

        assertEquals(SnapshotSource.CACHED, repository.load(location).source)
        assertEquals(0, api.callCount)

        clock.advance(Duration.between(now, lastStart.plus(Duration.ofHours(1))))
        assertEquals(SnapshotSource.LIVE, repository.load(location).source)
        assertEquals(1, api.callCount)
    }

    @Test
    fun `cache whose hourly data no longer reaches the current hour is refreshed`() = runTest {
        val expiredCoverage = sampleForecastDto(startOfToday = java.time.LocalDate.of(2026, 11, 20), days = 2)
        cache.save(hamburg.id, now.minus(Duration.ofMinutes(2)), mapper.encode(expiredCoverage))

        val result = repository.load(hamburg, forceRefresh = false)

        assertEquals(1, api.callCount)
        assertEquals(SnapshotSource.LIVE, result.source)
    }

    @Test
    fun `cache dated in the future is treated as stale instead of never expiring`() = runTest {
        cache.save(hamburg.id, now.plus(Duration.ofMinutes(10)), mapper.encode(sampleForecastDto()))

        val result = repository.load(hamburg, forceRefresh = false)

        assertEquals(1, api.callCount)
        assertEquals(SnapshotSource.LIVE, result.source)
    }

    @Test
    fun `a usable cache survives a failed refresh across a local day change`() = runTest {
        val dayChangeClock = MutableClock(Instant.parse("2026-11-21T23:05:00Z"))
        cache.save(hamburg.id, Instant.parse("2026-11-21T22:50:00Z"), mapper.encode(sampleForecastDto()))
        api.thrown = UnknownHostException("api.open-meteo.com")

        val result = WeatherRepository(api, cache, mapper, dayChangeClock).load(hamburg, forceRefresh = false)

        assertEquals(SnapshotSource.CACHED, result.source)
        assertEquals(DataLoadFailure.NETWORK, result.failure)
        assertNotNull(result.snapshot)
        // The usable entry is kept, not deleted.
        assertEquals(1, cache.entries.size)
    }

    @Test
    fun `the hourly window moving one hour ahead does not cause another request`() = runTest {
        val first = repository.load(hamburg, forceRefresh = false)
        assertEquals(1, api.callCount)

        clock.advance(Duration.ofMinutes(25))
        val second = repository.load(hamburg, forceRefresh = false)

        assertEquals(1, api.callCount)
        assertEquals(SnapshotSource.CACHED, second.source)
        assertEquals(SnapshotSource.LIVE, first.source)
        assertEquals(first.snapshot?.hourly?.map { it.instant }, second.snapshot?.hourly?.map { it.instant })
    }

    @Test
    fun `pull to refresh bypasses even a fresh cache`() = runTest {
        seedCache(Duration.ofSeconds(1))
        val result = repository.load(hamburg, forceRefresh = true)
        assertEquals(1, api.callCount)
        assertEquals(SnapshotSource.LIVE, result.source)
    }

    @Test
    fun `a location without cache always requests data`() = runTest {
        val result = repository.load(hamburg)
        assertEquals(1, api.callCount)
        assertEquals(SnapshotSource.LIVE, result.source)
        assertEquals(16, result.snapshot?.daily?.size)
        assertEquals(
            Pair(hamburg.latitude, hamburg.longitude),
            api.requestedCoordinates.single(),
        )
    }

    @Test
    fun `failed refresh keeps the cached snapshot and reports the failure`() = runTest {
        seedCache(Duration.ofHours(2))
        api.thrown = UnknownHostException("api.open-meteo.com")

        val result = repository.load(hamburg)
        assertEquals(SnapshotSource.CACHED, result.source)
        assertEquals(DataLoadFailure.NETWORK, result.failure)
        assertNotNull(result.snapshot)
        assertEquals(1, api.callCount)
    }

    @Test
    fun `failure without cache leaves no snapshot`() = runTest {
        api.thrown = SocketTimeoutException("timeout")
        val result = repository.load(hamburg)
        assertNull(result.snapshot)
        assertNull(result.source)
        assertEquals(DataLoadFailure.TIMEOUT, result.failure)
    }

    @Test
    fun `connection and generic io errors are both reported as network problems`() = runTest {
        api.thrown = ConnectException("failed to connect")
        assertEquals(DataLoadFailure.NETWORK, repository.load(hamburg).failure)

        api.thrown = InterruptedIOException("interrupted")
        assertEquals(DataLoadFailure.NETWORK, repository.load(hamburg).failure)
    }

    @Test
    fun `http status codes are mapped to distinct failures`() = runTest {
        api.payload = null
        for ((code, expected) in listOf(
            429 to DataLoadFailure.RATE_LIMITED,
            400 to DataLoadFailure.INVALID_DATA,
            404 to DataLoadFailure.INVALID_DATA,
            500 to DataLoadFailure.SERVER,
            503 to DataLoadFailure.SERVER,
        )) {
            api.errorResponse = errorResponse(code)
            val result = repository.load(hamburg, forceRefresh = true)
            assertEquals("HTTP $code", expected, result.failure)
            assertNull(result.snapshot)
        }
    }

    @Test
    fun `an error flag in a successful response is treated as invalid data`() = runTest {
        api.payload = ForecastResponseDto(error = true, reason = "No data found for these coordinates.")
        val result = repository.load(hamburg, forceRefresh = true)

        assertEquals(DataLoadFailure.INVALID_DATA, result.failure)
        assertNull(result.snapshot)
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `timestamp only responses do not overwrite a useful cached forecast`() = runTest {
        val previous = now.minus(Duration.ofHours(1))
        val payload = mapper.encode(sampleForecastDto())
        cache.save(hamburg.id, previous, payload)
        api.payload = ForecastResponseDto(
            hourly = HourlyDto(time = listOf("2026-11-22T12:00")),
            daily = DailyDto(time = listOf("2026-11-22")),
        )

        val result = repository.load(hamburg)
        assertEquals(DataLoadFailure.INVALID_DATA, result.failure)
        assertEquals(SnapshotSource.CACHED, result.source)
        assertEquals(previous, result.snapshot?.fetchedAt)
        assertEquals(payload, cache.entries[hamburg.id]?.payloadJson)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `concurrent dashboard and widget loads share the first successful fetch`() = runTest {
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        val dashboard = async { repository.load(hamburg) }
        runCurrent()
        val widget = async { repository.load(hamburg) }
        runCurrent()
        gate.complete(Unit)

        assertEquals(SnapshotSource.LIVE, dashboard.await().source)
        assertEquals(SnapshotSource.CACHED, widget.await().source)
        assertEquals(1, api.callCount)
    }

    @Test
    fun `a payload without any usable values is not cached`() = runTest {
        api.payload = ForecastResponseDto(latitude = 53.55, timezone = "Europe/Berlin")
        val result = repository.load(hamburg, forceRefresh = true)

        assertEquals(DataLoadFailure.INVALID_DATA, result.failure)
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `usable payload keeps partial data instead of failing on incomplete blocks`() = runTest {
        api.payload = ForecastResponseDto(
            timezone = "Europe/Berlin",
            current = CurrentDto(time = "2026-11-22T10:00", temperature = 4.0, weatherCode = 3),
        )
        val result = repository.load(hamburg, forceRefresh = true)

        assertNull(result.failure)
        assertEquals(SnapshotSource.LIVE, result.source)
        assertEquals(4.0, requireNotNull(result.snapshot?.current).temperatureCelsius, 0.0001)
        assertTrue(result.snapshot?.hourly?.isEmpty() == true)
        assertEquals(1, cache.entries.size)
    }

    @Test
    fun `corrupt cache entry is decoded as missing and deleted`() = runTest {
        cache.save(hamburg.id, now.minus(Duration.ofMinutes(1)), "{ this is not json")

        assertNull(repository.loadCached(hamburg))
        assertEquals(1, cache.deleteCount)
        assertTrue(cache.entries.isEmpty())

        // A follow-up load therefore goes to the network instead of serving garbage.
        val result = repository.load(hamburg)
        assertEquals(SnapshotSource.LIVE, result.source)
        assertEquals(1, api.callCount)
    }

    @Test
    fun `a failing cache read degrades to a network request`() = runTest {
        val throwingCache = object : ForecastCacheStore by cache {
            override suspend fun find(locationId: String): CachedForecast =
                throw IOException("disk failure")
        }
        val result = WeatherRepository(api, throwingCache, mapper, clock).load(hamburg)
        assertEquals(SnapshotSource.LIVE, result.source)
        assertEquals(1, api.callCount)
    }

    @Test
    fun `cancelled cache read does not reach the forecast API`() = runTest {
        val cancelled = CancellationException("Request was replaced")
        val cancelledCache = object : ForecastCacheStore by cache {
            override suspend fun find(locationId: String): CachedForecast = throw cancelled
        }
        val result = runCatching { WeatherRepository(api, cancelledCache, mapper, clock).load(hamburg) }
        assertEquals(cancelled, result.exceptionOrNull())
        assertEquals(0, api.callCount)
    }

    @Test
    fun `successful live data is written to the cache in a re-decodable form`() = runTest {
        repository.load(hamburg, forceRefresh = true)
        val entry = cache.entries.getValue(hamburg.id)
        val decoded = mapper.decodeCached(entry.payloadJson)
        assertNotNull(decoded)
        assertEquals(
            sampleForecastDto().current?.temperature,
            decoded?.current?.temperature,
        )
    }
}
