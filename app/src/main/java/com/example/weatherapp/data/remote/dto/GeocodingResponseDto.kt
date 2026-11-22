package com.example.weatherapp.data.remote.dto

import kotlinx.serialization.Serializable

/** Network payload of the Open-Meteo geocoding endpoint. */
@Serializable
data class GeocodingResponseDto(
    val results: List<GeocoderResultDto>? = null,
    val generationtime_ms: Double? = null,
)

@Serializable
data class GeocoderResultDto(
    val id: Long? = null,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val elevation: Double? = null,
    val feature_code: String? = null,
    val country_code: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val admin3: String? = null,
    val population: Long? = null,
    val timezone: String? = null,
)
