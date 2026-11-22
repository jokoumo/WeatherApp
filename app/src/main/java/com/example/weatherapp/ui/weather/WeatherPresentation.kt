package com.example.weatherapp.ui.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.ui.theme.HeaderClearDay
import com.example.weatherapp.ui.theme.HeaderClearNight
import com.example.weatherapp.ui.theme.HeaderCloudy
import com.example.weatherapp.ui.theme.HeaderFog
import com.example.weatherapp.ui.theme.HeaderRain
import com.example.weatherapp.ui.theme.HeaderSnow
import com.example.weatherapp.ui.theme.HeaderThunder

/** German label for a condition, resolved through string resources. */
@StringRes
fun WeatherCondition.descriptionRes(): Int = when (this) {
    WeatherCondition.CLEAR_DAY, WeatherCondition.CLEAR_NIGHT -> R.string.weather_clear
    WeatherCondition.MAINLY_CLEAR_DAY, WeatherCondition.MAINLY_CLEAR_NIGHT -> R.string.weather_mainly_clear
    WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.string.weather_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_overcast
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DEPOSITING_RIME_FOG -> R.string.weather_rime_fog
    WeatherCondition.LIGHT_DRIZZLE -> R.string.weather_drizzle_light
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.DENSE_DRIZZLE -> R.string.weather_drizzle_dense
    WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.DENSE_FREEZING_DRIZZLE -> R.string.weather_freezing_drizzle
    WeatherCondition.LIGHT_RAIN -> R.string.weather_rain_light
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.HEAVY_RAIN -> R.string.weather_rain_heavy
    WeatherCondition.FREEZING_RAIN, WeatherCondition.HEAVY_FREEZING_RAIN -> R.string.weather_freezing_rain
    WeatherCondition.LIGHT_SNOW -> R.string.weather_snow_light
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.HEAVY_SNOW -> R.string.weather_snow_heavy
    WeatherCondition.SNOW_GRAINS -> R.string.weather_snow_grains
    WeatherCondition.LIGHT_RAIN_SHOWERS -> R.string.weather_showers_light
    WeatherCondition.RAIN_SHOWERS -> R.string.weather_showers
    WeatherCondition.VIOLENT_RAIN_SHOWERS -> R.string.weather_showers_violent
    WeatherCondition.LIGHT_SNOW_SHOWERS -> R.string.weather_snow_showers_light
    WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.THUNDERSTORM_WITH_HAIL -> R.string.weather_thunderstorm_hail
    WeatherCondition.SEVERE_THUNDERSTORM_WITH_HAIL -> R.string.weather_thunderstorm_severe
    WeatherCondition.UNKNOWN -> R.string.weather_unknown
}

/** Vector drawable matching a condition. Day and night variants differ where it matters. */
@DrawableRes
fun WeatherCondition.iconRes(): Int = when (this) {
    WeatherCondition.CLEAR_DAY -> R.drawable.weather_clear_day
    WeatherCondition.CLEAR_NIGHT -> R.drawable.weather_clear_night
    WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.weather_clear_day
    WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.weather_clear_night
    WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.weather_partly_cloudy_day
    WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.weather_partly_cloudy_night
    WeatherCondition.OVERCAST -> R.drawable.weather_overcast
    WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.weather_fog
    WeatherCondition.LIGHT_DRIZZLE, WeatherCondition.DRIZZLE, WeatherCondition.DENSE_DRIZZLE ->
        R.drawable.weather_drizzle
    WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.DENSE_FREEZING_DRIZZLE,
    WeatherCondition.FREEZING_RAIN, WeatherCondition.HEAVY_FREEZING_RAIN -> R.drawable.weather_sleet
    WeatherCondition.LIGHT_RAIN, WeatherCondition.RAIN, WeatherCondition.HEAVY_RAIN -> R.drawable.weather_rain
    WeatherCondition.LIGHT_RAIN_SHOWERS, WeatherCondition.RAIN_SHOWERS,
    WeatherCondition.VIOLENT_RAIN_SHOWERS -> R.drawable.weather_showers
    WeatherCondition.LIGHT_SNOW, WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW,
    WeatherCondition.SNOW_GRAINS -> R.drawable.weather_snow
    WeatherCondition.LIGHT_SNOW_SHOWERS, WeatherCondition.SNOW_SHOWERS -> R.drawable.weather_snow_showers
    WeatherCondition.THUNDERSTORM -> R.drawable.weather_thunderstorm
    WeatherCondition.THUNDERSTORM_WITH_HAIL, WeatherCondition.SEVERE_THUNDERSTORM_WITH_HAIL ->
        R.drawable.weather_hail
    WeatherCondition.UNKNOWN -> R.drawable.weather_cloudy
}

/**
 * Gradient used behind the current weather card. Colours are mid toned so white text stays
 * readable in both themes; the sun state only decides between day and night clear colours.
 */
fun headerGradient(condition: WeatherCondition?, isDay: Boolean?): Pair<Color, Color> =
    when (condition) {
        WeatherCondition.CLEAR_DAY, WeatherCondition.MAINLY_CLEAR_DAY -> HeaderClearDay
        WeatherCondition.CLEAR_NIGHT, WeatherCondition.MAINLY_CLEAR_NIGHT -> HeaderClearNight
        WeatherCondition.PARTLY_CLOUDY_DAY,
        WeatherCondition.PARTLY_CLOUDY_NIGHT,
        WeatherCondition.OVERCAST,
        -> HeaderCloudy
        WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> HeaderFog
        WeatherCondition.LIGHT_DRIZZLE, WeatherCondition.DRIZZLE, WeatherCondition.DENSE_DRIZZLE,
        WeatherCondition.LIGHT_RAIN, WeatherCondition.RAIN, WeatherCondition.HEAVY_RAIN,
        WeatherCondition.LIGHT_RAIN_SHOWERS, WeatherCondition.RAIN_SHOWERS,
        WeatherCondition.VIOLENT_RAIN_SHOWERS, WeatherCondition.FREEZING_RAIN,
        WeatherCondition.HEAVY_FREEZING_RAIN, WeatherCondition.FREEZING_DRIZZLE,
        WeatherCondition.DENSE_FREEZING_DRIZZLE,
        -> HeaderRain
        WeatherCondition.LIGHT_SNOW, WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW,
        WeatherCondition.SNOW_GRAINS, WeatherCondition.LIGHT_SNOW_SHOWERS,
        WeatherCondition.SNOW_SHOWERS,
        -> HeaderSnow
        WeatherCondition.THUNDERSTORM, WeatherCondition.THUNDERSTORM_WITH_HAIL,
        WeatherCondition.SEVERE_THUNDERSTORM_WITH_HAIL,
        -> HeaderThunder
        null, WeatherCondition.UNKNOWN -> HeaderCloudy
    }

/** German error text for a load failure. */
@StringRes
fun DataLoadFailure.messageRes(): Int = when (this) {
    DataLoadFailure.NETWORK -> R.string.error_network
    DataLoadFailure.TIMEOUT -> R.string.error_timeout
    DataLoadFailure.RATE_LIMITED -> R.string.error_rate_limited
    DataLoadFailure.SERVER -> R.string.error_server
    DataLoadFailure.INVALID_DATA -> R.string.error_invalid_data
    DataLoadFailure.UNKNOWN -> R.string.error_unknown
}
