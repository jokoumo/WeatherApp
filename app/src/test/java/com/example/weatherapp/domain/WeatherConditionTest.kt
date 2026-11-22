package com.example.weatherapp.domain

import com.example.weatherapp.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks the WMO weather code table that the whole UI relies on, including day and night
 * variants and the fallback for codes that Open-Meteo does not document.
 */
class WeatherConditionTest {

    @Test
    fun `clear sky uses day and night variants`() {
        assertEquals(WeatherCondition.CLEAR_DAY, WeatherCondition.fromWeatherCode(0, isDay = true))
        assertEquals(WeatherCondition.CLEAR_NIGHT, WeatherCondition.fromWeatherCode(0, isDay = false))
        assertEquals(WeatherCondition.MAINLY_CLEAR_DAY, WeatherCondition.fromWeatherCode(1, isDay = true))
        assertEquals(
            WeatherCondition.MAINLY_CLEAR_NIGHT,
            WeatherCondition.fromWeatherCode(1, isDay = false),
        )
        assertEquals(
            WeatherCondition.PARTLY_CLOUDY_DAY,
            WeatherCondition.fromWeatherCode(2, isDay = true),
        )
        assertEquals(
            WeatherCondition.PARTLY_CLOUDY_NIGHT,
            WeatherCondition.fromWeatherCode(2, isDay = false),
        )
    }

    @Test
    fun `missing isDay flag is treated as daytime`() {
        assertEquals(WeatherCondition.CLEAR_DAY, WeatherCondition.fromWeatherCode(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY_DAY, WeatherCondition.fromWeatherCode(2, isDay = null))
    }

    @Test
    fun `overcast is independent of day and night`() {
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromWeatherCode(3, isDay = true))
        assertEquals(WeatherCondition.OVERCAST, WeatherCondition.fromWeatherCode(3, isDay = false))
    }

    @Test
    fun `fog codes are separated`() {
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWeatherCode(45))
        assertEquals(WeatherCondition.DEPOSITING_RIME_FOG, WeatherCondition.fromWeatherCode(48))
    }

    @Test
    fun `drizzle rain and freezing precipitation map to their intensities`() {
        assertEquals(WeatherCondition.LIGHT_DRIZZLE, WeatherCondition.fromWeatherCode(51))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCondition.fromWeatherCode(53))
        assertEquals(WeatherCondition.DENSE_DRIZZLE, WeatherCondition.fromWeatherCode(55))
        assertEquals(WeatherCondition.FREEZING_DRIZZLE, WeatherCondition.fromWeatherCode(56))
        assertEquals(WeatherCondition.DENSE_FREEZING_DRIZZLE, WeatherCondition.fromWeatherCode(57))
        assertEquals(WeatherCondition.LIGHT_RAIN, WeatherCondition.fromWeatherCode(61))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromWeatherCode(63))
        assertEquals(WeatherCondition.HEAVY_RAIN, WeatherCondition.fromWeatherCode(65))
        assertEquals(WeatherCondition.FREEZING_RAIN, WeatherCondition.fromWeatherCode(66))
        assertEquals(WeatherCondition.HEAVY_FREEZING_RAIN, WeatherCondition.fromWeatherCode(67))
    }

    @Test
    fun `snow and shower codes map to their intensities`() {
        assertEquals(WeatherCondition.LIGHT_SNOW, WeatherCondition.fromWeatherCode(71))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWeatherCode(73))
        assertEquals(WeatherCondition.HEAVY_SNOW, WeatherCondition.fromWeatherCode(75))
        assertEquals(WeatherCondition.SNOW_GRAINS, WeatherCondition.fromWeatherCode(77))
        assertEquals(WeatherCondition.LIGHT_RAIN_SHOWERS, WeatherCondition.fromWeatherCode(80))
        assertEquals(WeatherCondition.RAIN_SHOWERS, WeatherCondition.fromWeatherCode(81))
        assertEquals(WeatherCondition.VIOLENT_RAIN_SHOWERS, WeatherCondition.fromWeatherCode(82))
        assertEquals(WeatherCondition.LIGHT_SNOW_SHOWERS, WeatherCondition.fromWeatherCode(85))
        assertEquals(WeatherCondition.SNOW_SHOWERS, WeatherCondition.fromWeatherCode(86))
    }

    @Test
    fun `thunderstorm codes keep the hail information`() {
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromWeatherCode(95))
        assertEquals(WeatherCondition.THUNDERSTORM_WITH_HAIL, WeatherCondition.fromWeatherCode(96))
        assertEquals(WeatherCondition.SEVERE_THUNDERSTORM_WITH_HAIL, WeatherCondition.fromWeatherCode(99))
    }

    @Test
    fun `unknown and missing codes fall back to the unknown condition`() {
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWeatherCode(null))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWeatherCode(-1))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWeatherCode(100))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWeatherCode(999))
    }
}
