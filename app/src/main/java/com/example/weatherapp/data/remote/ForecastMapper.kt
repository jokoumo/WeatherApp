package com.example.weatherapp.data.remote

import com.example.weatherapp.data.remote.dto.CurrentDto
import com.example.weatherapp.data.remote.dto.DailyDto
import com.example.weatherapp.data.remote.dto.ForecastResponseDto
import com.example.weatherapp.data.remote.dto.GeocoderResultDto
import com.example.weatherapp.data.remote.dto.HourlyDto
import com.example.weatherapp.domain.model.CurrentWeather
import com.example.weatherapp.domain.model.DailyWeather
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.domain.zoneIdOrDefault
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Converts Open-Meteo DTOs into domain models.
 *
 * Missing, null or unparsable values are skipped instead of being replaced with invented
 * data, so a partially available day is still rendered with the values that do exist.
 */
class ForecastMapper(private val json: Json = NetworkModule.json) {

    /** Parses a cached payload; returns null when the stored JSON is unusable. */
    fun decodeCached(payload: String): ForecastResponseDto? =
        runCatching { json.decodeFromString(ForecastResponseDto.serializer(), payload) }.getOrNull()

    /** Serialises a response so that it can be written to the local cache. */
    fun encode(dto: ForecastResponseDto): String =
        json.encodeToString(ForecastResponseDto.serializer(), dto)

    fun toSnapshot(
        dto: ForecastResponseDto,
        location: SavedLocation,
        fetchedAt: Instant,
    ): WeatherSnapshot = toSnapshot(
        dto = dto,
        locationId = location.id,
        locationTimeZoneId = location.timeZoneId,
        fetchedAt = fetchedAt,
    )

    fun toSnapshot(
        dto: ForecastResponseDto,
        locationId: String,
        locationTimeZoneId: String,
        fetchedAt: Instant,
    ): WeatherSnapshot {
        val zone = resolveZone(dto.timezone, locationTimeZoneId)
        return WeatherSnapshot(
            locationId = locationId,
            fetchedAt = fetchedAt,
            timeZone = zone,
            current = toCurrent(dto.current, zone),
            hourly = toHourly(dto.hourly, zone),
            daily = toDaily(dto.daily),
        )
    }

    fun toCurrent(dto: CurrentDto?, zone: ZoneId): CurrentWeather? {
        if (dto == null) return null
        val temperature = dto.temperature ?: return null
        val instant = parseDateTime(dto.time)?.atZone(zone)?.toInstant() ?: Instant.now()
        val isDay = toBoolean(dto.isDay)
        return CurrentWeather(
            instant = instant,
            temperatureCelsius = temperature,
            apparentTemperatureCelsius = dto.apparentTemperature,
            weatherCode = dto.weatherCode,
            condition = WeatherCondition.fromWeatherCode(dto.weatherCode, isDay),
            relativeHumidityPercent = dto.relativeHumidity,
            precipitationMillimetres = dto.precipitation,
            cloudCoverPercent = dto.cloudCover,
            windSpeedKilometresPerHour = dto.windSpeed,
            windDirectionDegrees = dto.windDirection,
            windGustsKilometresPerHour = dto.windGusts,
            pressureHectopascal = dto.pressureMsl,
            isDay = isDay,
        )
    }

    fun toHourly(dto: HourlyDto?, zone: ZoneId): List<HourlyWeather> {
        if (dto == null || dto.time.isEmpty()) return emptyList()
        return dto.time.mapIndexedNotNull { index, rawTime ->
            val localDateTime = parseDateTime(rawTime) ?: return@mapIndexedNotNull null
            val code = dto.weatherCode.getOrNull(index)
            val isDay = toBoolean(dto.isDay.getOrNull(index))
            HourlyWeather(
                instant = localDateTime.atZone(zone).toInstant(),
                timeZone = zone,
                temperatureCelsius = dto.temperature.getOrNull(index),
                apparentTemperatureCelsius = dto.apparentTemperature.getOrNull(index),
                weatherCode = code,
                condition = if (code == null) null else WeatherCondition.fromWeatherCode(code, isDay),
                precipitationProbabilityPercent = dto.precipitationProbability.getOrNull(index),
                precipitationMillimetres = dto.precipitation.getOrNull(index),
                relativeHumidityPercent = dto.relativeHumidity.getOrNull(index),
                cloudCoverPercent = dto.cloudCover.getOrNull(index),
                windSpeedKilometresPerHour = dto.windSpeed.getOrNull(index),
                windDirectionDegrees = dto.windDirection.getOrNull(index),
                windGustsKilometresPerHour = dto.windGusts.getOrNull(index),
                pressureHectopascal = dto.pressureMsl.getOrNull(index),
                isDay = isDay,
            )
        }
    }

    fun toDaily(dto: DailyDto?): List<DailyWeather> {
        if (dto == null || dto.time.isEmpty()) return emptyList()
        return dto.time.mapIndexedNotNull { index, rawDate ->
            val date = parseDate(rawDate) ?: return@mapIndexedNotNull null
            val code = dto.weatherCode.getOrNull(index)
            val sunshine = dto.sunshineDuration.getOrNull(index)
            val daylight = dto.daylightDuration.getOrNull(index)
            DailyWeather(
                date = date,
                weatherCode = code,
                condition = if (code == null) null else WeatherCondition.fromWeatherCode(code, isDay = true),
                minTemperatureCelsius = dto.temperatureMin.getOrNull(index),
                maxTemperatureCelsius = dto.temperatureMax.getOrNull(index),
                precipitationProbabilityPercent = dto.precipitationProbabilityMax.getOrNull(index),
                precipitationMillimetres = dto.precipitationSum.getOrNull(index),
                maxWindSpeedKilometresPerHour = dto.windSpeedMax.getOrNull(index),
                maxWindGustsKilometresPerHour = dto.windGustsMax.getOrNull(index),
                sunshineSeconds = sunshine?.takeIf { it > 0 }?.toLong(),
                daylightSeconds = daylight?.takeIf { it > 0 }?.toLong(),
            )
        }
    }

    /**
     * Maps geocoding results to selectable locations. Results without name or coordinates
     * are dropped because they cannot be queried or displayed meaningfully.
     */
    fun toLocations(dtos: List<GeocoderResultDto>): List<SavedLocation> =
        dtos.mapNotNull { dto ->
            val name = dto.name?.trim().orEmpty()
            val latitude = dto.latitude ?: return@mapNotNull null
            val longitude = dto.longitude ?: return@mapNotNull null
            if (name.isEmpty()) return@mapNotNull null
            SavedLocation(
                id = dto.id?.toString() ?: "coord-${latitude.formatted(4)}-${longitude.formatted(4)}",
                name = name,
                latitude = latitude,
                longitude = longitude,
                admin1 = dto.admin1?.trim()?.takeIf { it.isNotEmpty() },
                country = dto.country?.trim()?.takeIf { it.isNotEmpty() },
                countryCode = dto.country_code?.trim()?.takeIf { it.isNotEmpty() },
                timeZoneId = dto.timezone?.trim()?.takeIf { it.isNotEmpty() }
                    ?: SavedLocation.DEFAULT_TIME_ZONE_ID,
            )
        }
}

private fun resolveZone(apiTimeZone: String?, locationTimeZone: String): ZoneId =
    zoneIdOrDefault(apiTimeZone, locationTimeZone)

private fun parseDateTime(raw: String?): LocalDateTime? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    // Open-Meteo uses "2026-11-22T14:00"; seconds may be present in some responses.
    return runCatching { LocalDateTime.parse(value) }.getOrNull()
}

private fun parseDate(raw: String?): LocalDate? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { LocalDate.parse(value) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).toLocalDate() }.getOrNull()
}

private fun toBoolean(value: Int?): Boolean? = when (value) {
    1 -> true
    0 -> false
    else -> null
}

private fun Double.formatted(digits: Int): String =
    String.format(java.util.Locale.ROOT, "%.${digits}f", this)
