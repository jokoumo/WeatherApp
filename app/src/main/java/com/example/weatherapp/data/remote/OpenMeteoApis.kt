package com.example.weatherapp.data.remote

import com.example.weatherapp.data.remote.dto.ForecastResponseDto
import com.example.weatherapp.data.remote.dto.GeocodingResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** Standard Open-Meteo forecast endpoint. No API key is required. */
interface OpenMeteoForecastApi {

    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
        @Query("hourly") hourly: String,
        @Query("daily") daily: String,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = FORECAST_DAYS,
        @Query("timeformat") timeformat: String = TIME_FORMAT,
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("precipitation_unit") precipitationUnit: String = "mm",
    ): Response<ForecastResponseDto>

    companion object {
        /** Full horizon of the standard forecast API: today plus 15 days. */
        const val FORECAST_DAYS = 16

        /**
         * Absolute Unix timestamps instead of naive local date-times. Hourly points then carry
         * unambiguous instants, so a daylight saving transition can never collapse two different
         * hours into one entry. Daily timestamps are converted back to calendar dates with the
         * offset reported by the API (see `ForecastMapper`).
         */
        const val TIME_FORMAT = "unixtime"

        val CURRENT_VARIABLES = listOf(
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "is_day",
            "precipitation",
            "snowfall",
            "weather_code",
            "cloud_cover",
            "pressure_msl",
            "wind_speed_10m",
            "wind_direction_10m",
            "wind_gusts_10m",
        )

        val HOURLY_VARIABLES = listOf(
            "temperature_2m",
            "apparent_temperature",
            "relative_humidity_2m",
            "precipitation_probability",
            "precipitation",
            "snowfall",
            "weather_code",
            "cloud_cover",
            "pressure_msl",
            "wind_speed_10m",
            "wind_direction_10m",
            "wind_gusts_10m",
            "is_day",
        )

        val DAILY_VARIABLES = listOf(
            "weather_code",
            "temperature_2m_max",
            "temperature_2m_min",
            "precipitation_sum",
            "precipitation_probability_max",
            "wind_speed_10m_max",
            "wind_gusts_10m_max",
            "sunshine_duration",
            "daylight_duration",
        )
    }
}

/** Open-Meteo geocoding endpoint used for location search. */
interface OpenMeteoGeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "de",
        @Query("format") format: String = "json",
    ): Response<GeocodingResponseDto>
}
