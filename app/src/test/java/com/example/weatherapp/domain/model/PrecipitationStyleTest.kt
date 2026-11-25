package com.example.weatherapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Classification of rain and snowfall intensity.
 *
 * The application shows exactly three levels per kind of precipitation, so the tests pin down
 * which WMO code produces which level, which conditions keep their own family, and how a
 * reported amount may refine a code without ever overriding it by more than one step.
 */
class PrecipitationStyleTest {

    private fun classifyRain(code: Int, millimetres: Double? = null) = PrecipitationClassifier.classify(
        condition = WeatherCondition.fromWeatherCode(code),
        precipitationMillimetres = millimetres,
    )

    private fun classifySnow(code: Int, centimetres: Double? = null) = PrecipitationClassifier.classify(
        condition = WeatherCondition.fromWeatherCode(code),
        snowfallCentimetres = centimetres,
    )

    @Test
    fun `steady rain maps to the three rain levels`() {
        assertEquals(
            PrecipitationStyle(PrecipitationFamily.RAIN, PrecipitationIntensity.LIGHT),
            classifyRain(61),
        )
        assertEquals(
            PrecipitationStyle(PrecipitationFamily.RAIN, PrecipitationIntensity.MODERATE),
            classifyRain(63),
        )
        assertEquals(
            PrecipitationStyle(PrecipitationFamily.RAIN, PrecipitationIntensity.HEAVY),
            classifyRain(65),
        )
    }

    @Test
    fun `rain showers use the same three rain levels as steady rain`() {
        assertEquals(
            PrecipitationStyle(PrecipitationFamily.RAIN, PrecipitationIntensity.LIGHT),
            classifyRain(80),
        )
        assertEquals(
            PrecipitationStyle(PrecipitationFamily.RAIN, PrecipitationIntensity.MODERATE),
            classifyRain(81),
        )
        assertEquals(
            PrecipitationStyle(PrecipitationFamily.RAIN, PrecipitationIntensity.HEAVY),
            classifyRain(82),
        )
    }

    @Test
    fun `snowfall and snow showers map to the three snow levels`() {
        listOf(71, 85).forEach { code ->
            assertEquals(
                "code $code",
                PrecipitationStyle(PrecipitationFamily.SNOW, PrecipitationIntensity.LIGHT),
                classifySnow(code),
            )
        }
        assertEquals(
            PrecipitationStyle(PrecipitationFamily.SNOW, PrecipitationIntensity.MODERATE),
            classifySnow(73),
        )
        listOf(75, 86).forEach { code ->
            assertEquals(
                "code $code",
                PrecipitationStyle(PrecipitationFamily.SNOW, PrecipitationIntensity.HEAVY),
                classifySnow(code),
            )
        }
    }

    @Test
    fun `drizzle freezing precipitation snow grains and thunder keep their own family`() {
        listOf(51, 53, 55).forEach { code ->
            assertEquals(PrecipitationFamily.DRIZZLE, classifyRain(code).family)
        }
        listOf(56, 57, 66, 67).forEach { code ->
            assertEquals(PrecipitationFamily.FREEZING_PRECIPITATION, classifyRain(code).family)
        }
        assertEquals(PrecipitationFamily.SNOW_GRAINS, classifySnow(77).family)
        assertEquals(
            PrecipitationFamily.THUNDERSTORM,
            PrecipitationClassifier.classify(WeatherCondition.fromWeatherCode(95)).family,
        )
        listOf(96, 99).forEach { code ->
            assertEquals(
                "code $code",
                PrecipitationFamily.HAIL,
                PrecipitationClassifier.classify(WeatherCondition.fromWeatherCode(code)).family,
            )
        }
        // None of these is presented as a drop or flake count.
        listOf(51, 53, 55, 56, 57, 66, 67, 77, 95, 96, 99).forEach { code ->
            assertNull(
                "code $code must not carry an intensity",
                PrecipitationClassifier.classify(WeatherCondition.fromWeatherCode(code)).intensity,
            )
        }
    }

    @Test
    fun `cloudiness and fog are not precipitation`() {
        listOf(0, 1, 2, 3, 45, 48).forEach { code ->
            assertEquals(
                "code $code",
                PrecipitationStyle.NONE,
                PrecipitationClassifier.classify(WeatherCondition.fromWeatherCode(code)),
            )
        }
        assertEquals(PrecipitationStyle.NONE, PrecipitationClassifier.classify(null))
        assertEquals(
            PrecipitationStyle.NONE,
            PrecipitationClassifier.classify(WeatherCondition.UNKNOWN),
        )
    }

    @Test
    fun `a missing amount keeps the intensity of the weather code`() {
        assertEquals(
            PrecipitationIntensity.MODERATE,
            classifyRain(63, millimetres = null).intensity,
        )
        assertEquals(
            PrecipitationIntensity.MODERATE,
            classifySnow(73, centimetres = null).intensity,
        )
    }

    @Test
    fun `zero negative and non finite amounts carry no information`() {
        listOf(null, 0.0, -1.5, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
            assertEquals(
                "amount $amount",
                PrecipitationIntensity.MODERATE,
                classifyRain(63, millimetres = amount).intensity,
            )
        }
    }

    @Test
    fun `a reported amount refines the code by at most one level`() {
        // A light code with a lot of water is raised, but not straight to heavy.
        assertEquals(
            PrecipitationIntensity.MODERATE,
            classifyRain(61, millimetres = 9.0).intensity,
        )
        // A moderate code with a heavy amount becomes heavy, one step away.
        assertEquals(
            PrecipitationIntensity.HEAVY,
            classifyRain(63, millimetres = 4.0).intensity,
        )
        // A heavy code with a trace is lowered, but not straight to light.
        assertEquals(
            PrecipitationIntensity.MODERATE,
            classifyRain(65, millimetres = 0.1).intensity,
        )
        assertEquals(
            PrecipitationIntensity.LIGHT,
            classifyRain(63, millimetres = 0.2).intensity,
        )
    }

    @Test
    fun `rain amount thresholds are the documented ones`() {
        assertEquals(
            PrecipitationIntensity.MODERATE,
            classifyRain(61, millimetres = PrecipitationClassifier.MODERATE_RAIN_MILLIMETRES).intensity,
        )
        // Still one step from light, because the code said light.
        assertEquals(
            PrecipitationIntensity.MODERATE,
            classifyRain(61, millimetres = PrecipitationClassifier.HEAVY_RAIN_MILLIMETRES).intensity,
        )
        assertEquals(
            PrecipitationIntensity.HEAVY,
            classifyRain(65, millimetres = PrecipitationClassifier.HEAVY_RAIN_MILLIMETRES).intensity,
        )
        assertEquals(
            PrecipitationIntensity.LIGHT,
            classifyRain(61, millimetres = 0.4).intensity,
        )
    }

    @Test
    fun `snowfall amount thresholds are the documented ones`() {
        assertEquals(
            PrecipitationIntensity.MODERATE,
            classifySnow(71, centimetres = PrecipitationClassifier.MODERATE_SNOW_CENTIMETRES).intensity,
        )
        assertEquals(
            PrecipitationIntensity.MODERATE,
            classifySnow(75, centimetres = 0.1).intensity,
        )
        assertEquals(
            PrecipitationIntensity.HEAVY,
            classifySnow(75, centimetres = PrecipitationClassifier.HEAVY_SNOW_CENTIMETRES).intensity,
        )
    }

    @Test
    fun `a snow code stays snow even when only a millimetre amount is known`() {
        val style = PrecipitationClassifier.classify(
            condition = WeatherCondition.fromWeatherCode(73),
            precipitationMillimetres = 3.0,
            snowfallCentimetres = null,
        )
        assertEquals(PrecipitationFamily.SNOW, style.family)
        assertEquals(PrecipitationIntensity.MODERATE, style.intensity)
    }

    @Test
    fun `no amount alone can create a precipitation family`() {
        // Without a condition there is nothing to classify; nothing is invented.
        assertEquals(
            PrecipitationStyle.NONE,
            PrecipitationClassifier.classify(
                condition = WeatherCondition.OVERCAST,
                precipitationMillimetres = 12.0,
            ),
        )
    }
}
