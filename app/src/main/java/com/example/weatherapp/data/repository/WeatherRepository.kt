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

    /** Cached snapshot for a location without touching the network. */
    suspend fun loadCached(location: SavedLocation): WeatherSnapshot? {
        val cached = runCatching { cacheStore.find(location.id) }
            .onFailure { Log.w(TAG, "Reading forecast cache failed", it) }
            .getOrNull() ?: return null
        val dto = mapper.decodeCached(cached.payloadJson)
        if (dto == null) {
            Log.w(TAG, "Cached forecast payload could not be decoded")
            runCatching { cacheStore.delete(location.id) }
            return null
        }
        return mapper.toSnapshot(dto, location, cached.fetchedAt)
    }

    /**
     * Drops the cached forecast of [locationId]. Used when a location is deleted: without this
     * call the row of a location nobody uses any more would stay in the database indefinitely.
     */
    suspend fun dropCache(locationId: String) {
        runCatching { cacheStore.delete(locationId) }
            .onFailure { Log.w(TAG, "Removing forecast cache failed", it) }
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
    ): WeatherLoadResult {
        val cached = loadCached(location)
        if (cached != null && !forceRefresh && isFresh(cached, now)) {
            return WeatherLoadResult(snapshot = cached, source = SnapshotSource.CACHED)
        }

        return when (val outcome = fetch(location)) {
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
                Log.w(TAG, "Open-Meteo reported an error: ${body.reason.orEmpty()}")
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
        runCatching {
            cacheStore.save(locationId, outcome.fetchedAt, mapper.encode(outcome.dto))
        }.onFailure { Log.w(TAG, "Writing forecast cache failed", it) }
    }

    private sealed interface FetchOutcome {
        data class Success(val dto: ForecastResponseDto, val fetchedAt: Instant) : FetchOutcome
        data class Failure(val failure: DataLoadFailure) : FetchOutcome
    }

    companion object {
        private const val TAG = "WeatherRepository"
    }
}
