package com.example.weatherapp.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Measured or modelled conditions for the moment the forecast was requested.
 *
 * [instant] is the observation time reported by the API. It is null when the response did not
 * contain a usable timestamp; nothing is substituted for it, so the UI can say so instead of
 * showing a time that was never reported.
 */
data class CurrentWeather(
    val instant: Instant?,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double?,
    val weatherCode: Int?,
    val condition: WeatherCondition,
    val relativeHumidityPercent: Int?,
    val precipitationMillimetres: Double?,
    val cloudCoverPercent: Int?,
    val windSpeedKilometresPerHour: Double?,
    val windDirectionDegrees: Double?,
    val windGustsKilometresPerHour: Double?,
    val pressureHectopascal: Double?,
    val isDay: Boolean?,
    /** Snowfall of the preceding hour in centimetres; null when the API did not report it. */
    val snowfallCentimetres: Double? = null,
) {
    /** Rain or snowfall intensity used by the icons and the short descriptions. */
    val precipitationStyle: PrecipitationStyle
        get() = PrecipitationClassifier.classify(
            condition = condition,
            precipitationMillimetres = precipitationMillimetres,
            snowfallCentimetres = snowfallCentimetres,
        )

    /** Wind warning of the observation; sustained wind and gusts are kept apart. */
    val windWarning: WindWarning
        get() = WindWarningClassifier.classify(
            sustainedKilometresPerHour = windSpeedKilometresPerHour,
            gustsKilometresPerHour = windGustsKilometresPerHour,
        )
}

/**
 * One forecast value for a single hourly interval.
 *
 * [instant] is the absolute start of the interval and the unique key of the entry: two hours
 * that share a local clock time because a daylight saving transition repeated it stay distinct.
 * [timeZone] is only used to derive the local date and clock time shown to the user.
 */
data class HourlyWeather(
    val instant: Instant,
    val timeZone: ZoneId,
    val temperatureCelsius: Double?,
    val apparentTemperatureCelsius: Double?,
    val weatherCode: Int?,
    val condition: WeatherCondition?,
    val precipitationProbabilityPercent: Int?,
    val precipitationMillimetres: Double?,
    val relativeHumidityPercent: Int?,
    val cloudCoverPercent: Int?,
    val windSpeedKilometresPerHour: Double?,
    val windDirectionDegrees: Double?,
    val windGustsKilometresPerHour: Double?,
    val pressureHectopascal: Double?,
    val isDay: Boolean?,
    /** Snowfall of this interval in centimetres; null when the API did not report it. */
    val snowfallCentimetres: Double? = null,
) {
    val localDateTime: java.time.LocalDateTime
        get() = java.time.LocalDateTime.ofInstant(instant, timeZone)

    val localDate: LocalDate
        get() = localDateTime.toLocalDate()

    /** Rain or snowfall intensity used by the icons and the short descriptions. */
    val precipitationStyle: PrecipitationStyle
        get() = PrecipitationClassifier.classify(
            condition = condition,
            precipitationMillimetres = precipitationMillimetres,
            snowfallCentimetres = snowfallCentimetres,
        )

    /** Wind warning of this hour; sustained wind and gusts are kept apart. */
    val windWarning: WindWarning
        get() = WindWarningClassifier.classify(
            sustainedKilometresPerHour = windSpeedKilometresPerHour,
            gustsKilometresPerHour = windGustsKilometresPerHour,
        )
}

/** Aggregated forecast values for one calendar day in the location time zone. */
data class DailyWeather(
    val date: LocalDate,
    val weatherCode: Int?,
    val condition: WeatherCondition?,
    val minTemperatureCelsius: Double?,
    val maxTemperatureCelsius: Double?,
    val precipitationProbabilityPercent: Int?,
    val precipitationMillimetres: Double?,
    val maxWindSpeedKilometresPerHour: Double?,
    val maxWindGustsKilometresPerHour: Double?,
    /** Preferable measured/modelled sunshine duration in seconds. */
    val sunshineSeconds: Long?,
    /** Length of the day (sunrise to sunset) in seconds, used when sunshine is unavailable. */
    val daylightSeconds: Long?,
) {
    /**
     * Rain or snowfall intensity of the day.
     *
     * The daily sum is deliberately not used to refine it: a 24 hour accumulation cannot be
     * compared with the hourly thresholds of [PrecipitationClassifier], and the daily WMO code
     * already describes the dominant intensity of the day. Nothing is derived that the API did
     * not report as an intensity.
     */
    val precipitationStyle: PrecipitationStyle
        get() = PrecipitationClassifier.classify(condition = condition)

    /** Wind warning built from the strongest sustained wind and the strongest gust of the day. */
    val windWarning: WindWarning
        get() = WindWarningClassifier.classify(
            sustainedKilometresPerHour = maxWindSpeedKilometresPerHour,
            gustsKilometresPerHour = maxWindGustsKilometresPerHour,
        )
}

/** All forecast data that belongs to one location and one retrieval. */
data class WeatherSnapshot(
    val locationId: String,
    val fetchedAt: Instant,
    val timeZone: ZoneId,
    val current: CurrentWeather?,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
) {
    val isEmpty: Boolean
        get() = current == null && hourly.isEmpty() && daily.isEmpty()
}
