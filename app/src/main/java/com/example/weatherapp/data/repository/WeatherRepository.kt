package com.example.weatherapp.data.repository

import android.util.Log
import com.example.weatherapp.data.local.ForecastCacheStore
import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.remote.OpenMeteoForecastApi
import com.example.weatherapp.data.remote.dto.ForecastResponseDto
import com.example.weatherapp.domain.CachePolicy
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.Instant

/** Where the displayed snapshot comes from. */
enum class SnapshotSource { LIVE, CACHED }

/**
 * Result of a load attempt. [failure] is non-null when a refresh did not work; in that case
 * a cached [snapshot] may still be presented.
 */
data class WeatherLoadResult(
    val snapshot: WeatherSnapshot?,
    val source: SnapshotSource?,
    val failure: DataLoadFailure? = null,
) {
    val hasData: Boolean get() = snapshot != null && !snapshot.isEmpty
}

/**
 * Loads forecasts from Open-Meteo and caches them per location.
 *
 * Fresh cached data is reused without a network call. When a refresh fails the cached
 * snapshot is returned together with the failure reason so the UI can mark it as outdated.
 */
class WeatherRepository(
    private val forecastApi: OpenMeteoForecastApi,
    private val cacheStore: ForecastCacheStore,
    private val mapper: ForecastMapper = ForecastMapper(),
    private val clock: Clock = Clock.systemUTC(),
) {

    private val now: Instant get() = clock.instant()
    private val registryLock = Mutex()
    private val locationLocks = mutableMapOf<String, LocationLock>()

    /** Cached snapshot for a location without touching the network. */
    suspend fun loadCached(location: SavedLocation): WeatherSnapshot? {
        val cached = cacheOperation("Reading forecast cache failed") { cacheStore.find(location.id) }
            ?: return null
        val dto = mapper.decodeCached(cached.payloadJson)
        val snapshot = dto?.takeUnless { it.error == true }?.let {
            mapper.toSnapshot(it, location, cached.fetchedAt)
        }
        if (snapshot != null && !snapshot.isEmpty) return snapshot
        Log.w(TAG, "Cached forecast has no usable weather")
        cacheOperation("Removing unusable forecast failed") { cacheStore.deleteIfUnchanged(cached) }
        return null
    }

    /**
     * Drops the cached forecast of [locationId]. Used when a location stops being referenced:
     * without this call the row of a location nobody uses any more would stay in the database
     * indefinitely.
     */
    suspend fun dropCache(locationId: String) {
        cacheOperation("Removing forecast cache failed") { cacheStore.delete(locationId) }
    }

    /** Every location id that currently holds a cached forecast. */
    suspend fun cachedLocationIds(): List<String> =
        cacheOperation("Reading forecast cache keys failed") { cacheStore.locationIds() }.orEmpty()

    /** Drops several cached forecasts at once; an empty collection does nothing. */
    suspend fun dropCaches(locationIds: Collection<String>) {
        if (locationIds.isEmpty()) return
        cacheOperation("Removing forecast cache entries failed") { cacheStore.deleteAll(locationIds.toList()) }
    }

    /**
     * Returns forecast data for [location].
     *
     * A cached snapshot is served without a network call only while it is fresh: within the TTL,
     * downloaded on the current calendar day in the location time zone and still covering the
     * current hour. Stale cache entries are never deleted; they stay visible while a refresh
     * runs and are the fallback when the refresh fails.
     *
     * @param forceRefresh bypasses the cache freshness check, for example on pull to refresh.
     */
    suspend fun load(
        location: SavedLocation,
        forceRefresh: Boolean = false,
    ): WeatherLoadResult = withLocationLock(location.id) {
        val cached = loadCached(location)
        if (cached != null && !forceRefresh && isFresh(cached, now)) {
            return@withLocationLock WeatherLoadResult(snapshot = cached, source = SnapshotSource.CACHED)
        }

        when (val outcome = fetch(location)) {
            is FetchOutcome.Success -> {
                val snapshot = mapper.toSnapshot(outcome.dto, location, outcome.fetchedAt)
                if (snapshot.isEmpty) {
                    WeatherLoadResult(cached, cached?.let { SnapshotSource.CACHED }, DataLoadFailure.INVALID_DATA)
                } else {
                    persist(location.id, outcome)
                    WeatherLoadResult(snapshot, SnapshotSource.LIVE)
                }
            }

            is FetchOutcome.Failure -> {
                val failure = outcome.failure
                if (cached != null) {
                    WeatherLoadResult(cached, SnapshotSource.CACHED, failure)
                } else {
                    WeatherLoadResult(null, null, failure)
                }
            }
        }
    }

    /** Cached data that is young enough, from today and still covers the current hour. */
    private fun isFresh(snapshot: WeatherSnapshot, now: Instant): Boolean =
        CachePolicy.isFresh(snapshot.fetchedAt, now, snapshot.timeZone) &&
            CachePolicy.hasHourlyCoverage(snapshot, now)

    private suspend fun fetch(location: SavedLocation): FetchOutcome {
        val requestedAt = now
        return try {
            val response = forecastApi.forecast(
                latitude = location.latitude,
                longitude = location.longitude,
                current = OpenMeteoForecastApi.CURRENT_VARIABLES.joinToString(","),
                hourly = OpenMeteoForecastApi.HOURLY_VARIABLES.joinToString(","),
                daily = OpenMeteoForecastApi.DAILY_VARIABLES.joinToString(","),
            )
            if (!response.isSuccessful) {
                return FetchOutcome.Failure(mapHttpFailure(response.raw().code))
            }
            val body = response.body()
                ?: return FetchOutcome.Failure(DataLoadFailure.INVALID_DATA)
            if (body.error == true) {
                Log.w(TAG, "Open-Meteo reported an error")
                return FetchOutcome.Failure(DataLoadFailure.INVALID_DATA)
            }
            FetchOutcome.Success(dto = body, fetchedAt = requestedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ioException: IOException) {
            FetchOutcome.Failure(mapIoFailure(ioException))
        } catch (unexpected: Exception) {
            Log.w(TAG, "Forecast response could not be processed", unexpected)
            FetchOutcome.Failure(DataLoadFailure.INVALID_DATA)
        }
    }

    private suspend fun persist(locationId: String, outcome: FetchOutcome.Success) {
        cacheOperation("Writing forecast cache failed") {
            cacheStore.save(locationId, outcome.fetchedAt, mapper.encode(outcome.dto))
        }
    }

    private inline fun <T> cacheOperation(message: String, block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w(TAG, message, failure)
        null
    }

    /** Dashboard and widgets serialize loads of one place and recheck cache freshness on entry. */
    private suspend fun <T> withLocationLock(locationId: String, block: suspend () -> T): T {
        val lock = registryLock.withLock {
            locationLocks.getOrPut(locationId, ::LocationLock).also { it.users++ }
        }
        try {
            return lock.mutex.withLock { block() }
        } finally {
            withContext(NonCancellable) {
                registryLock.withLock {
                    lock.users--
                    if (lock.users == 0) locationLocks.remove(locationId)
                }
            }
        }
    }

    private class LocationLock(val mutex: Mutex = Mutex(), var users: Int = 0)

    private sealed interface FetchOutcome {
        data class Success(val dto: ForecastResponseDto, val fetchedAt: Instant) : FetchOutcome
        data class Failure(val failure: DataLoadFailure) : FetchOutcome
    }

    companion object {
        private const val TAG = "WeatherRepository"
    }
}
