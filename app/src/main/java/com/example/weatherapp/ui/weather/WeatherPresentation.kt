package com.example.weatherapp.ui.weather

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.PrecipitationFamily
import com.example.weatherapp.domain.model.PrecipitationIntensity
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.domain.model.WindWarningLevel
import com.example.weatherapp.ui.theme.HeaderClearDay
import com.example.weatherapp.ui.theme.HeaderClearNight
import com.example.weatherapp.ui.theme.HeaderCloudy
import com.example.weatherapp.ui.theme.HeaderFog
import com.example.weatherapp.ui.theme.HeaderRain
import com.example.weatherapp.ui.theme.HeaderSnow
import com.example.weatherapp.ui.theme.HeaderThunder
import com.example.weatherapp.ui.theme.WindIndicatorColors

/**
 * Precise German label for a condition, resolved through string resources.
 *
 * This is the full meteorological wording ("Leichte Regenschauer", "Gefrierender Nieselregen").
 * It is used where there is room for it, in the hourly detail sheet and in accessibility
 * labels; the compact cards use [shortDescriptionRes] instead.
 */
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

/**
 * Short German label that matches the intensity the icon shows.
 *
 * Rain and snowfall are presented as exactly three levels, so a shower and the matching steady
 * precipitation are described with the same words the drop count expresses. Everything that has
 * no rain or snowfall intensity keeps its precise label.
 */
@StringRes
fun shortDescriptionRes(condition: WeatherCondition?, style: PrecipitationStyle): Int =
    when (style.family) {
        PrecipitationFamily.RAIN -> when (style.intensity) {
            PrecipitationIntensity.LIGHT -> R.string.weather_rain_light
            PrecipitationIntensity.HEAVY -> R.string.weather_rain_heavy
            else -> R.string.weather_rain
        }
        PrecipitationFamily.SNOW -> when (style.intensity) {
            PrecipitationIntensity.LIGHT -> R.string.weather_snow_light
            PrecipitationIntensity.HEAVY -> R.string.weather_snow_heavy
            else -> R.string.weather_snow
        }
        else -> (condition ?: WeatherCondition.UNKNOWN).descriptionRes()
    }

/**
 * Vector drawable for a condition.
 *
 * The precipitation family decides the symbol, so rain and snowfall are drawn with one, two or
 * three drops or flakes according to their classified intensity. Conditions that are not plain
 * rain or snowfall keep their own symbol, and without precipitation the sky state decides.
 */
@DrawableRes
fun weatherIconRes(condition: WeatherCondition?, style: PrecipitationStyle): Int =
    when (style.family) {
        PrecipitationFamily.RAIN -> when (style.intensity) {
            PrecipitationIntensity.LIGHT -> R.drawable.weather_rain_light
            PrecipitationIntensity.HEAVY -> R.drawable.weather_rain_heavy
            else -> R.drawable.weather_rain_moderate
        }
        PrecipitationFamily.SNOW -> when (style.intensity) {
            PrecipitationIntensity.LIGHT -> R.drawable.weather_snow_light
            PrecipitationIntensity.HEAVY -> R.drawable.weather_snow_heavy
            else -> R.drawable.weather_snow_moderate
        }
        PrecipitationFamily.DRIZZLE -> R.drawable.weather_drizzle
        PrecipitationFamily.FREEZING_PRECIPITATION -> R.drawable.weather_sleet
        PrecipitationFamily.SNOW_GRAINS -> R.drawable.weather_snow_grains
        PrecipitationFamily.THUNDERSTORM -> R.drawable.weather_thunderstorm
        PrecipitationFamily.HAIL -> R.drawable.weather_hail
        PrecipitationFamily.NONE -> skyIconRes(condition ?: WeatherCondition.UNKNOWN)
    }

/** Symbol of the sky itself, used when no precipitation is reported. */
@DrawableRes
private fun skyIconRes(condition: WeatherCondition): Int = when (condition) {
    WeatherCondition.CLEAR_DAY, WeatherCondition.MAINLY_CLEAR_DAY -> R.drawable.weather_clear_day
    WeatherCondition.CLEAR_NIGHT, WeatherCondition.MAINLY_CLEAR_NIGHT -> R.drawable.weather_clear_night
    WeatherCondition.PARTLY_CLOUDY_DAY -> R.drawable.weather_partly_cloudy_day
    WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.weather_partly_cloudy_night
    WeatherCondition.OVERCAST -> R.drawable.weather_overcast
    WeatherCondition.FOG, WeatherCondition.DEPOSITING_RIME_FOG -> R.drawable.weather_fog
    else -> R.drawable.weather_cloudy
}

/** German label of the wind indicator; it names the cause so colour is never the only cue. */
@StringRes
fun WindWarning.labelRes(): Int = when {
    level == WindWarningLevel.STORM -> R.string.wind_warning_storm
    causedByGusts -> R.string.wind_warning_gusts
    else -> R.string.wind_warning_strong
}

/**
 * Colour of the wind indicator. Strong wind stays neutral and storm turns red; both colours come
 * from the active theme, so they keep their contrast in the light and in the dark theme.
 */
fun WindWarning.indicatorColor(colors: WindIndicatorColors): Color = when (level) {
    WindWarningLevel.STORM -> colors.storm
    WindWarningLevel.STRONG -> colors.strong
    WindWarningLevel.NONE -> Color.Transparent
}

/**
 * Short German explanation of a wind warning for the detailed views.
 *
 * It always names the measured value the warning is based on, so a gust warning is never read as
 * a sustained wind. Only meaningful when [WindWarning.isWarning] is true.
 */
@StringRes
fun WindWarning.explanationRes(): Int = when {
    level == WindWarningLevel.STORM && causedByGusts -> R.string.wind_warning_explanation_storm_gusts
    level == WindWarningLevel.STORM -> R.string.wind_warning_explanation_storm
    causedByGusts -> R.string.wind_warning_explanation_strong_gusts
    else -> R.string.wind_warning_explanation_strong
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
