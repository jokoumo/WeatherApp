package com.example.weatherapp.testing

import com.example.weatherapp.data.local.CachedForecast
import com.example.weatherapp.data.local.ForecastCacheStore
import com.example.weatherapp.data.local.SettingsStore
import com.example.weatherapp.data.local.ThemeStore
import com.example.weatherapp.data.remote.OpenMeteoForecastApi
import com.example.weatherapp.data.remote.OpenMeteoGeocodingApi
import com.example.weatherapp.data.remote.dto.CurrentDto
import com.example.weatherapp.data.remote.dto.DailyDto
import com.example.weatherapp.data.remote.dto.ForecastResponseDto
import com.example.weatherapp.data.remote.dto.GeocodingResponseDto
import com.example.weatherapp.data.remote.dto.HourlyDto
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import retrofit2.Response
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

/**
 * Forecast API with a complete, deterministic payload: [forecastDays] days of hourly and daily
 * values starting at midnight of the current local day, in the same `unixtime` form the real
 * request asks for. [callCount] lets a test prove that opening a detail sheet reuses this
 * response instead of asking the weather service again.
 */
class FakeForecastApi(private val forecastDays: Int = 4) : OpenMeteoForecastApi {

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
    ): Response<ForecastResponseDto> {
        callCount++
        val zone = ZoneId.of("Europe/Berlin")
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val hours = (0 until this.forecastDays * 24L).map { startOfToday.plus(Duration.ofHours(it)) }
        val days = (0 until this.forecastDays.toLong()).map { startOfToday.plus(Duration.ofDays(it)) }
        return Response.success(
            ForecastResponseDto(
                timezone = zone.id,
                utcOffsetSeconds = zone.rules.getOffset(startOfToday).totalSeconds.toLong(),
                current = CurrentDto(
                    time = hours.first().epochSecond.toString(),
                    temperature = 6.0,
                    relativeHumidity = 80,
                    apparentTemperature = 3.5,
                    isDay = 1,
                    precipitation = 0.0,
                    weatherCode = 3,
                    cloudCover = 90,
                    pressureMsl = 1013.0,
                    windSpeed = 12.0,
                    windDirection = 270.0,
                    windGusts = 25.0,
                ),
                hourly = HourlyDto(
                    time = hours.map { it.epochSecond.toString() },
                    temperature = hours.map { 6.0 },
                    apparentTemperature = hours.map { 4.0 },
                    relativeHumidity = hours.map { 80 },
                    precipitationProbability = hours.map { 40 },
                    precipitation = hours.map { 0.2 },
                    weatherCode = hours.map { 61 },
                    cloudCover = hours.map { 90 },
                    pressureMsl = hours.map { 1013.0 },
                    windSpeed = hours.map { 14.0 },
                    windDirection = hours.map { 270.0 },
                    windGusts = hours.map { 32.0 },
                    isDay = hours.map { 1 },
                ),
                daily = DailyDto(
                    time = days.map { it.epochSecond.toString() },
                    weatherCode = days.map { 3 },
                    temperatureMax = days.map { 8.0 },
                    temperatureMin = days.map { 1.0 },
                    precipitationSum = days.map { 4.8 },
                    precipitationProbabilityMax = days.map { 40 },
                    windSpeedMax = days.map { 14.0 },
                    windGustsMax = days.map { 32.0 },
                    sunshineDuration = days.map { 7_200.0 },
                    daylightDuration = days.map { 28_800.0 },
                ),
            ),
        )
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
