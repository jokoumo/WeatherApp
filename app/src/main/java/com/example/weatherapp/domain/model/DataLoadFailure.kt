package com.example.weatherapp.domain.model

/** Classified reason why data could not be loaded, mapped to German messages in the UI. */
enum class DataLoadFailure {
    NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    SERVER,
    INVALID_DATA,
    UNKNOWN,
}
