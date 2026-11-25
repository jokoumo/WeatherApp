package com.example.weatherapp.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Creates the HTTP clients and API interfaces used by the repositories. */
object NetworkModule {

    const val FORECAST_BASE_URL = "https://api.open-meteo.com/"
    const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"

    /**
     * Tolerant JSON parser: unknown keys and missing values are normal for Open-Meteo
     * because optional variables are omitted when they are not available.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun createForecastApi(client: OkHttpClient, json: Json = this.json): OpenMeteoForecastApi =
        Retrofit.Builder()
            .baseUrl(FORECAST_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(OpenMeteoForecastApi::class.java)

    fun createGeocodingApi(client: OkHttpClient, json: Json = this.json): OpenMeteoGeocodingApi =
        Retrofit.Builder()
            .baseUrl(GEOCODING_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(OpenMeteoGeocodingApi::class.java)
}
