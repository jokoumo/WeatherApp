package com.example.weatherapp.testing

import com.example.weatherapp.data.local.CachedForecast
import com.example.weatherapp.data.local.ForecastCacheStore
import com.example.weatherapp.data.local.SettingsStore
import com.example.weatherapp.data.local.ThemeStore
import com.example.weatherapp.data.remote.OpenMeteoForecastApi
import com.example.weatherapp.data.remote.OpenMeteoGeocodingApi
import com.example.weatherapp.data.remote.dto.GeocodingResponseDto
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import retrofit2.Response
import java.net.UnknownHostException
import java.time.Instant

/**
 * Test doubles for the instrumented tests. The unit test source set is not visible here, and the
 * UI tests only need the smallest behaviour that keeps the repositories honest: no network, no
 * search, and a theme store that can be inspected.
 */

/** Theme store backed by a state flow; [failSave] simulates an unwritable preferences file. */
class InMemoryThemeStore(
    initial: ThemeMode = ThemeMode.SYSTEM,
    private val failSave: Boolean = false,
) : ThemeStore {

    /** Current value, so a test can check what the next start of the app would read back. */
    val state = MutableStateFlow(initial)

    override val themeMode: Flow<ThemeMode> = state

    val savedModes = mutableListOf<ThemeMode>()

    override suspend fun save(mode: ThemeMode): Boolean {
        if (failSave) return false
        state.value = mode
        savedModes += mode
        return true
    }
}

/** Forecast API that never touches the network: every call fails as if the service were down. */
class OfflineForecastApi : OpenMeteoForecastApi {

    var callCount = 0
        private set

    override suspend fun forecast(
        latitude: Double,
        longitude: Double,
        current: String,
        hourly: String,
        daily: String,
        timezone: String,
        forecastDays: Int,
        timeformat: String,
        temperatureUnit: String,
        windSpeedUnit: String,
        precipitationUnit: String,
    ): Response<com.example.weatherapp.data.remote.dto.ForecastResponseDto> {
        callCount++
        throw UnknownHostException("instrumented tests never call the weather service")
    }
}

/** Geocoding API without results, so a search stays deterministic. */
class EmptyGeocodingApi : OpenMeteoGeocodingApi {

    var callCount = 0
        private set

    override suspend fun search(
        name: String,
        count: Int,
        language: String,
        format: String,
    ): Response<GeocodingResponseDto> {
        callCount++
        return Response.success(GeocodingResponseDto(results = emptyList()))
    }
}

/** In-memory location settings, so the UI tests start from a known home location. */
class InMemorySettingsStore(
    initial: LocationSettings = LocationSettings(),
) : SettingsStore {

    val state = MutableStateFlow(initial)

    override val settings: Flow<LocationSettings> = state

    var saveCount = 0
        private set

    override suspend fun save(settings: LocationSettings) {
        saveCount++
        state.value = settings
    }
}

/** Forecast cache that keeps nothing: the UI tests never read a cached response. */
class NoForecastCache : ForecastCacheStore {

    override suspend fun find(locationId: String): CachedForecast? = null

    override suspend fun save(locationId: String, fetchedAt: Instant, payloadJson: String) = Unit

    override suspend fun delete(locationId: String) = Unit
}
