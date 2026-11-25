package com.example.weatherapp.domain.model

/**
 * Normalised weather condition derived from a WMO weather interpretation code.
 *
 * Day/night variants exist so that the UI can pick a matching icon. Unknown or
 * missing codes map to [UNKNOWN]; the application never guesses a condition.
 */
enum class WeatherCondition {
    CLEAR_DAY,
    CLEAR_NIGHT,
    MAINLY_CLEAR_DAY,
    MAINLY_CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    OVERCAST,
    FOG,
    DEPOSITING_RIME_FOG,
    LIGHT_DRIZZLE,
    DRIZZLE,
    DENSE_DRIZZLE,
    FREEZING_DRIZZLE,
    DENSE_FREEZING_DRIZZLE,
    LIGHT_RAIN,
    RAIN,
    HEAVY_RAIN,
    FREEZING_RAIN,
    HEAVY_FREEZING_RAIN,
    LIGHT_SNOW,
    SNOW,
    HEAVY_SNOW,
    SNOW_GRAINS,
    LIGHT_RAIN_SHOWERS,
    RAIN_SHOWERS,
    VIOLENT_RAIN_SHOWERS,
    LIGHT_SNOW_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    THUNDERSTORM_WITH_HAIL,
    SEVERE_THUNDERSTORM_WITH_HAIL,
    UNKNOWN,
    ;

    companion object {
        /**
         * Maps a WMO code to a condition. [isDay] only refines cloudiness codes; when it
         * is null the day variant is assumed, which matches the API behaviour of not
         * reporting the sun state.
         */
        fun fromWeatherCode(code: Int?, isDay: Boolean? = null): WeatherCondition {
            val day = isDay ?: true
            return when (code) {
                0 -> if (day) CLEAR_DAY else CLEAR_NIGHT
                1 -> if (day) MAINLY_CLEAR_DAY else MAINLY_CLEAR_NIGHT
                2 -> if (day) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
                3 -> OVERCAST
                45 -> FOG
                48 -> DEPOSITING_RIME_FOG
                51 -> LIGHT_DRIZZLE
                53 -> DRIZZLE
                55 -> DENSE_DRIZZLE
                56 -> FREEZING_DRIZZLE
                57 -> DENSE_FREEZING_DRIZZLE
                61 -> LIGHT_RAIN
                63 -> RAIN
                65 -> HEAVY_RAIN
                66 -> FREEZING_RAIN
                67 -> HEAVY_FREEZING_RAIN
                71 -> LIGHT_SNOW
                73 -> SNOW
                75 -> HEAVY_SNOW
                77 -> SNOW_GRAINS
                80 -> LIGHT_RAIN_SHOWERS
                81 -> RAIN_SHOWERS
                82 -> VIOLENT_RAIN_SHOWERS
                85 -> LIGHT_SNOW_SHOWERS
                86 -> SNOW_SHOWERS
                95 -> THUNDERSTORM
                96 -> THUNDERSTORM_WITH_HAIL
                99 -> SEVERE_THUNDERSTORM_WITH_HAIL
                else -> UNKNOWN
            }
        }
    }
}
