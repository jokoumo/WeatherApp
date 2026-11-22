package com.example.weatherapp.testing

import com.example.weatherapp.data.local.CachedForecast
import com.example.weatherapp.data.local.ForecastCacheStore
import com.example.weatherapp.data.local.SettingsStore
import com.example.weatherapp.data.remote.OpenMeteoForecastApi
import com.example.weatherapp.data.remote.OpenMeteoGeocodingApi
import com.example.weatherapp.data.remote.dto.CurrentDto
import com.example.weatherapp.data.remote.dto.DailyDto
import com.example.weatherapp.data.remote.dto.ForecastResponseDto
import com.example.weatherapp.data.remote.dto.GeocoderResultDto
import com.example.weatherapp.data.remote.dto.GeocodingResponseDto
import com.example.weatherapp.data.remote.dto.HourlyDto
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.zoneIdOrDefault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Deterministic clock so cache freshness can be tested without waiting. */
class MutableClock(start: Instant) : Clock() {

    private var current: Instant = start

    /** Current test time, readable without the Java method form. */
    val now: Instant get() = current

    override fun instant(): Instant = current

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

/** Location used by most tests; values mirror a real Open-Meteo geocoding result. */
val hamburg = SavedLocation(
    id = "2911298",
    name = "Hamburg",
    latitude = 53.55,
    longitude = 9.99,
    admin1 = "Freie und Hansestadt Hamburg",
    country = "Deutschland",
    countryCode = "DE",
    timeZoneId = "Europe/Berlin",
)

val berlin = SavedLocation(
    id = "2950280",
    name = "Berlin",
    latitude = 52.52,
    longitude = 13.4,
    admin1 = "Berlin",
    country = "Deutschland",
    countryCode = "DE",
    timeZoneId = "Europe/Berlin",
)

val tokyo = SavedLocation(
    id = "1850144",
    name = "Tokio",
    latitude = 35.69,
    longitude = 139.69,
    country = "Japan",
    countryCode = "JP",
    timeZoneId = "Asia/Tokyo",
)

/** Fixtures for the home-location swap scenario: one home plus four favorites. */
val newYork = SavedLocation(
    id = "5128581",
    name = "New York",
    latitude = 40.71,
    longitude = -74.01,
    admin1 = "New York",
    country = "Vereinigte Staaten",
    countryCode = "US",
    timeZoneId = "America/New_York",
)

val dresden = SavedLocation(
    id = "2935022",
    name = "Dresden",
    latitude = 51.05,
    longitude = 13.74,
    admin1 = "Sachsen",
    country = "Deutschland",
    countryCode = "DE",
    timeZoneId = "Europe/Berlin",
)

val paris = SavedLocation(
    id = "2988507",
    name = "Paris",
    latitude = 48.85,
    longitude = 2.35,
    admin1 = "Île-de-France",
    country = "Frankreich",
    countryCode = "FR",
    timeZoneId = "Europe/Paris",
)

val madrid = SavedLocation(
    id = "3117735",
    name = "Madrid",
    latitude = 40.42,
    longitude = -3.7,
    admin1 = "Madrid",
    country = "Spanien",
    countryCode = "ES",
    timeZoneId = "Europe/Madrid",
)

/** Builds a Retrofit error response without starting a real HTTP call. */
fun <T> errorResponse(code: Int, body: String = ""): Response<T> {
    val raw = okhttp3.Response.Builder()
        .code(code)
        .message("HTTP $code")
        .protocol(Protocol.HTTP_1_1)
        .request(
            Request.Builder()
                .url("https://api.open-meteo.com/v1/forecast")
                .build(),
        )
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()
    return Response.error(body.toResponseBody("application/json".toMediaType()), raw)
}

/** Successful Retrofit response wrapping [payload]. */
fun successResponse(payload: ForecastResponseDto): Response<ForecastResponseDto> = Response.success(payload)

/**
 * Forecast API double: returns a configured payload or throws a configured error, counts the
 * calls and can be paused with [gate] to check cancellation and stale-response handling.
 */
class FakeForecastApi(
    initialPayload: ForecastResponseDto = sampleForecastDto(),
) : OpenMeteoForecastApi {

    var payload: ForecastResponseDto? = initialPayload
    var errorResponse: Response<ForecastResponseDto>? = null
    var thrown: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    /** Optional per-coordinate payloads, used to tell two locations apart in tests. */
    val payloadsByCoordinate = mutableMapOf<Pair<Double, Double>, ForecastResponseDto>()

    var callCount = 0
        private set
    val requestedCoordinates = mutableListOf<Pair<Double, Double>>()

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
        requestedCoordinates += latitude to longitude
        gate?.await()
        thrown?.let { throw it }
        errorResponse?.let { return it }
        val coordinatePayload = payloadsByCoordinate[latitude to longitude]
        return Response.success(coordinatePayload ?: requireNotNull(payload))
    }
}

/** Geocoding API double with a fixed result list or a thrown error. */
class FakeGeocodingApi(
    var results: List<GeocoderResultDto> = emptyList(),
    var errorResponse: Response<GeocodingResponseDto>? = null,
    var thrown: Throwable? = null,
) : OpenMeteoGeocodingApi {

    var callCount = 0
        private set
    val queries = mutableListOf<String>()

    override suspend fun search(
        name: String,
        count: Int,
        language: String,
        format: String,
    ): Response<GeocodingResponseDto> {
        callCount++
        queries += name
        thrown?.let { throw it }
        errorResponse?.let { return it }
        return Response.success(GeocodingResponseDto(results = results))
    }
}

/** In-memory forecast cache used by repository tests. */
class FakeForecastCacheStore : ForecastCacheStore {

    val entries = mutableMapOf<String, CachedForecast>()
    var deleteCount = 0

    override suspend fun find(locationId: String): CachedForecast? = entries[locationId]

    override suspend fun save(locationId: String, fetchedAt: Instant, payloadJson: String) {
        entries[locationId] = CachedForecast(locationId, fetchedAt, payloadJson)
    }

    override suspend fun delete(locationId: String) {
        deleteCount++
        entries.remove(locationId)
    }
}

/** In-memory settings store that behaves like a single subscriber DataStore. */
class FakeSettingsStore(
    initial: LocationSettings = LocationSettings(),
) : SettingsStore {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<LocationSettings> = state

    var saveCount = 0
        private set

    override suspend fun save(settings: LocationSettings) {
        saveCount++
        state.value = settings
    }
}

/**
 * Builds a realistic forecast payload with [days] full local days starting at
 * [startOfToday]; the shape matches the Open-Meteo response for the requested variables,
 * including `timeformat=unixtime`: every `time` value is a Unix timestamp in seconds.
 */
fun sampleForecastDto(
    timeZone: String? = "Europe/Berlin",
    startOfToday: LocalDate = LocalDate.of(2026, 11, 22),
    days: Int = 16,
): ForecastResponseDto {
    val zone = zoneIdOrDefault(timeZone, "Europe/Berlin")
    val utcOffsetSeconds = zone.rules
        .getOffset(startOfToday.atStartOfDay(zone).toInstant())
        .totalSeconds
        .toLong()

    val hourlyTimes = mutableListOf<String>()
    val temperatures = mutableListOf<Double?>()
    val apparent = mutableListOf<Double?>()
    val humidity = mutableListOf<Int?>()
    val precipitationProbability = mutableListOf<Int?>()
    val precipitation = mutableListOf<Double?>()
    val codes = mutableListOf<Int?>()
    val cloudCover = mutableListOf<Int?>()
    val pressure = mutableListOf<Double?>()
    val windSpeed = mutableListOf<Double?>()
    val windDirection = mutableListOf<Double?>()
    val windGusts = mutableListOf<Double?>()
    val isDay = mutableListOf<Int?>()

    for (day in 0 until days) {
        for (hour in 0..23) {
            val index = day * 24 + hour
            hourlyTimes += startOfToday.plusDays(day.toLong())
                .atTime(hour, 0)
                .atZone(zone)
                .toEpochSecond()
                .toString()
            temperatures += 5.0 + (index % 24) * 0.1
            apparent += 3.0 + (index % 24) * 0.1
            humidity += 60 + (index % 24)
            precipitationProbability += index % 3 * 10
            precipitation += if (index % 7 == 0) 0.4 else 0.0
            codes += if (index % 11 == 0) 61 else 1
            cloudCover += (index % 5) * 20
            pressure += 1013.0
            windSpeed += 10.0 + (index % 6)
            windDirection += ((index * 15) % 360).toDouble()
            windGusts += 20.0 + (index % 6)
            isDay += if (hour in 8..16) 1 else 0
        }
    }

    val dailyTimes = (0 until days).map { day ->
        startOfToday.plusDays(day.toLong())
            .atStartOfDay(zone)
            .toEpochSecond()
            .toString()
    }

    return ForecastResponseDto(
        latitude = 53.55,
        longitude = 9.99,
        elevation = 12.0,
        timezone = timeZone,
        utcOffsetSeconds = utcOffsetSeconds,
        timezoneAbbreviation = "CET",
        current = CurrentDto(
            time = startOfToday.atTime(12, 0).atZone(zone).toEpochSecond().toString(),
            temperature = 6.4,
            relativeHumidity = 81,
            apparentTemperature = 3.9,
            isDay = 1,
            precipitation = 0.0,
            weatherCode = 3,
            cloudCover = 100,
            pressureMsl = 1017.0,
            windSpeed = 11.2,
            windDirection = 300.0,
            windGusts = 25.4,
        ),
        hourly = HourlyDto(
            time = hourlyTimes,
            temperature = temperatures,
            apparentTemperature = apparent,
            relativeHumidity = humidity,
            precipitationProbability = precipitationProbability,
            precipitation = precipitation,
            weatherCode = codes,
            cloudCover = cloudCover,
            pressureMsl = pressure,
            windSpeed = windSpeed,
            windDirection = windDirection,
            windGusts = windGusts,
            isDay = isDay,
        ),
        daily = DailyDto(
            time = dailyTimes,
            weatherCode = List(days) { 2 },
            temperatureMax = (0 until days).map { day -> 6.0 - day * 0.2 },
            temperatureMin = (0 until days).map { day -> -1.0 - day * 0.2 },
            precipitationSum = List(days) { 0.6 },
            precipitationProbabilityMax = List(days) { 30 },
            windSpeedMax = List(days) { 18.0 },
            windGustsMax = List(days) { 34.0 },
            sunshineDuration = List(days) { 7200.0 },
            daylightDuration = List(days) { 29400.0 },
        ),
    )
}

fun geocoderResult(
    id: Long?,
    name: String?,
    latitude: Double?,
    longitude: Double?,
    admin1: String? = null,
    country: String? = "Deutschland",
    countryCode: String? = "DE",
    timezone: String? = "Europe/Berlin",
): GeocoderResultDto = GeocoderResultDto(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    admin1 = admin1,
    country = country,
    country_code = countryCode,
    timezone = timezone,
)
