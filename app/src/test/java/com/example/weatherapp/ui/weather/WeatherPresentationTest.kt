package com.example.weatherapp.ui.weather

import androidx.compose.ui.graphics.Color
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.PrecipitationClassifier
import com.example.weatherapp.domain.model.PrecipitationFamily
import com.example.weatherapp.domain.model.PrecipitationIntensity
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.domain.model.WindWarningCause
import com.example.weatherapp.domain.model.WindWarningLevel
import com.example.weatherapp.ui.theme.DarkColorScheme
import com.example.weatherapp.ui.theme.DarkWindIndicatorColors
import com.example.weatherapp.ui.theme.HeaderClearDay
import com.example.weatherapp.ui.theme.HeaderClearNight
import com.example.weatherapp.ui.theme.HeaderCloudy
import com.example.weatherapp.ui.theme.HeaderFog
import com.example.weatherapp.ui.theme.HeaderRain
import com.example.weatherapp.ui.theme.HeaderSnow
import com.example.weatherapp.ui.theme.HeaderThunder
import com.example.weatherapp.ui.theme.HeaderWindIndicatorColors
import com.example.weatherapp.ui.theme.LightColorScheme
import com.example.weatherapp.ui.theme.LightWindIndicatorColors
import com.example.weatherapp.ui.theme.WindIndicatorColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Mapping from a classified condition to the symbol and the short label the user sees, plus the
 * colours of the wind indicator. The resource ids are plain integers in a unit test, which is
 * enough to check that every level really resolves to a different, intended drawable.
 */
class WeatherPresentationTest {

    private fun styleOf(code: Int): PrecipitationStyle =
        PrecipitationClassifier.classify(WeatherCondition.fromWeatherCode(code))

    @Test
    fun `the three rain levels use three different drop symbols`() {
        val light = weatherIconRes(WeatherCondition.LIGHT_RAIN, styleOf(61))
        val moderate = weatherIconRes(WeatherCondition.RAIN, styleOf(63))
        val heavy = weatherIconRes(WeatherCondition.HEAVY_RAIN, styleOf(65))
        assertEquals(R.drawable.weather_rain_light, light)
        assertEquals(R.drawable.weather_rain_moderate, moderate)
        assertEquals(R.drawable.weather_rain_heavy, heavy)
        assertNotEquals(light, moderate)
        assertNotEquals(moderate, heavy)
    }

    @Test
    fun `the three snow levels use three different flake symbols`() {
        val light = weatherIconRes(WeatherCondition.LIGHT_SNOW, styleOf(71))
        val moderate = weatherIconRes(WeatherCondition.SNOW, styleOf(73))
        val heavy = weatherIconRes(WeatherCondition.HEAVY_SNOW, styleOf(75))
        assertEquals(R.drawable.weather_snow_light, light)
        assertEquals(R.drawable.weather_snow_moderate, moderate)
        assertEquals(R.drawable.weather_snow_heavy, heavy)
        assertNotEquals(light, moderate)
        assertNotEquals(moderate, heavy)
    }

    @Test
    fun `showers use the symbol of their intensity, not a separate one`() {
        assertEquals(
            R.drawable.weather_rain_light,
            weatherIconRes(WeatherCondition.LIGHT_RAIN_SHOWERS, styleOf(80)),
        )
        assertEquals(
            R.drawable.weather_rain_heavy,
            weatherIconRes(WeatherCondition.VIOLENT_RAIN_SHOWERS, styleOf(82)),
        )
        assertEquals(
            R.drawable.weather_snow_heavy,
            weatherIconRes(WeatherCondition.SNOW_SHOWERS, styleOf(86)),
        )
    }

    @Test
    fun `a refined intensity changes the symbol as well as the label`() {
        val style = PrecipitationClassifier.classify(
            condition = WeatherCondition.RAIN,
            precipitationMillimetres = 6.0,
        )
        assertEquals(PrecipitationIntensity.HEAVY, style.intensity)
        assertEquals(R.drawable.weather_rain_heavy, weatherIconRes(WeatherCondition.RAIN, style))
        assertEquals(R.string.weather_rain_heavy, shortDescriptionRes(WeatherCondition.RAIN, style))
    }

    @Test
    fun `conditions that are not plain rain or snow keep their own symbol`() {
        assertEquals(R.drawable.weather_drizzle, weatherIconRes(WeatherCondition.DRIZZLE, styleOf(53)))
        assertEquals(
            R.drawable.weather_sleet,
            weatherIconRes(WeatherCondition.FREEZING_RAIN, styleOf(66)),
        )
        assertEquals(
            R.drawable.weather_snow_grains,
            weatherIconRes(WeatherCondition.SNOW_GRAINS, styleOf(77)),
        )
        assertEquals(
            R.drawable.weather_thunderstorm,
            weatherIconRes(WeatherCondition.THUNDERSTORM, styleOf(95)),
        )
        assertEquals(R.drawable.weather_hail, weatherIconRes(WeatherCondition.THUNDERSTORM_WITH_HAIL, styleOf(96)))
    }

    @Test
    fun `sky conditions without precipitation keep their day and night symbols`() {
        assertEquals(R.drawable.weather_clear_day, weatherIconRes(WeatherCondition.CLEAR_DAY, styleOf(0)))
        assertEquals(R.drawable.weather_clear_night, weatherIconRes(WeatherCondition.CLEAR_NIGHT, styleOf(0)))
        assertEquals(
            R.drawable.weather_partly_cloudy_night,
            weatherIconRes(WeatherCondition.PARTLY_CLOUDY_NIGHT, styleOf(2)),
        )
        assertEquals(R.drawable.weather_overcast, weatherIconRes(WeatherCondition.OVERCAST, styleOf(3)))
        assertEquals(R.drawable.weather_fog, weatherIconRes(WeatherCondition.FOG, styleOf(45)))
        assertEquals(R.drawable.weather_cloudy, weatherIconRes(WeatherCondition.UNKNOWN, PrecipitationStyle.NONE))
    }

    @Test
    fun `short labels name the intensity the symbol shows`() {
        assertEquals(R.string.weather_rain_light, shortDescriptionRes(WeatherCondition.LIGHT_RAIN, styleOf(61)))
        assertEquals(R.string.weather_rain, shortDescriptionRes(WeatherCondition.RAIN, styleOf(63)))
        assertEquals(R.string.weather_rain_heavy, shortDescriptionRes(WeatherCondition.HEAVY_RAIN, styleOf(65)))
        assertEquals(R.string.weather_snow_light, shortDescriptionRes(WeatherCondition.LIGHT_SNOW, styleOf(71)))
        assertEquals(R.string.weather_snow, shortDescriptionRes(WeatherCondition.SNOW, styleOf(73)))
        assertEquals(R.string.weather_snow_heavy, shortDescriptionRes(WeatherCondition.HEAVY_SNOW, styleOf(75)))
    }

    @Test
    fun `showers are described by their intensity in the compact views`() {
        assertEquals(
            R.string.weather_rain_heavy,
            shortDescriptionRes(WeatherCondition.VIOLENT_RAIN_SHOWERS, styleOf(82)),
        )
        assertEquals(
            R.string.weather_snow_light,
            shortDescriptionRes(WeatherCondition.LIGHT_SNOW_SHOWERS, styleOf(85)),
        )
    }

    @Test
    fun `the detailed description stays meteorologically precise`() {
        assertEquals(R.string.weather_showers_violent, WeatherCondition.VIOLENT_RAIN_SHOWERS.descriptionRes())
        assertEquals(R.string.weather_freezing_rain, WeatherCondition.FREEZING_RAIN.descriptionRes())
        assertEquals(R.string.weather_snow_grains, WeatherCondition.SNOW_GRAINS.descriptionRes())
        // A condition without a rain or snow intensity keeps that precise label.
        assertEquals(
            R.string.weather_freezing_rain,
            shortDescriptionRes(WeatherCondition.FREEZING_RAIN, styleOf(66)),
        )
    }

    @Test
    fun `wind labels name the level and the measurement that caused it`() {
        val strong = WindWarning(WindWarningLevel.STRONG, WindWarningCause.SUSTAINED_WIND)
        val gusts = WindWarning(WindWarningLevel.STRONG, WindWarningCause.GUSTS)
        val storm = WindWarning(WindWarningLevel.STORM, WindWarningCause.SUSTAINED_WIND)
        assertEquals(R.string.wind_warning_strong, strong.labelRes())
        assertEquals(R.string.wind_warning_gusts, gusts.labelRes())
        assertEquals(R.string.wind_warning_storm, storm.labelRes())
        assertNotEquals(strong.labelRes(), gusts.labelRes())
        assertNotEquals(strong.labelRes(), storm.labelRes())
    }

    @Test
    fun `storm is red and strong wind stays neutral in both themes`() {
        listOf(LightWindIndicatorColors, DarkWindIndicatorColors).forEach { colors ->
            val strong = WindWarning(WindWarningLevel.STRONG, WindWarningCause.SUSTAINED_WIND)
            val storm = WindWarning(WindWarningLevel.STORM, WindWarningCause.SUSTAINED_WIND)
            assertNotEquals(strong.indicatorColor(colors), storm.indicatorColor(colors))
            assertEquals(colors.storm, storm.indicatorColor(colors))
            assertEquals(colors.strong, strong.indicatorColor(colors))
            assertTrue(
                "storm red must actually be red",
                storm.indicatorColor(colors).red.toDouble() >
                    storm.indicatorColor(colors).green.toDouble(),
            )
        }
    }

    @Test
    fun `both wind colours are readable on the shipped backgrounds`() {
        listOf(
            LightColorScheme to LightWindIndicatorColors,
            DarkColorScheme to DarkWindIndicatorColors,
        ).forEach { (scheme, colors) ->
            listOf(colors.strong, colors.storm).forEach { color ->
                val ratio = contrastRatio(color, scheme.background)
                assertTrue(
                    "$color needs 3:1 on $scheme, was ${ratio.format()}",
                    ratio >= 3.0,
                )
            }
        }
    }

    @Test
    fun `no warning has no colour at all`() {
        val colors: WindIndicatorColors = LightWindIndicatorColors
        assertEquals(Color.Transparent, WindWarning.NONE.indicatorColor(colors))
    }

    /**
     * The icon family is a closed set. No condition combined with any intensity the presentation
     * can produce may fall out of it, because a missing drawable is a crash on the device.
     */
    @Test
    fun `every condition and intensity resolves to an icon of the family`() {
        val styles = PrecipitationFamily.entries.flatMap { family ->
            (listOf<PrecipitationIntensity?>(null) + PrecipitationIntensity.entries)
                .map { intensity -> PrecipitationStyle(family, intensity) }
        }
        WeatherCondition.entries.forEach { condition ->
            (styles + PrecipitationStyle.NONE).forEach { style ->
                val res = weatherIconRes(condition, style)
                assertTrue("$condition with $style has no icon", res in iconFamily)
            }
        }
    }

    @Test
    fun `day and night variants of one sky state are different symbols`() {
        assertNotEquals(
            weatherIconRes(WeatherCondition.CLEAR_DAY, PrecipitationStyle.NONE),
            weatherIconRes(WeatherCondition.CLEAR_NIGHT, PrecipitationStyle.NONE),
        )
        assertNotEquals(
            weatherIconRes(WeatherCondition.PARTLY_CLOUDY_DAY, PrecipitationStyle.NONE),
            weatherIconRes(WeatherCondition.PARTLY_CLOUDY_NIGHT, PrecipitationStyle.NONE),
        )
    }

    /**
     * The hero card draws its symbol on the header gradient, which stays dark in both themes, so
     * the header palette has to be a light neutral next to a lighter storm red. The glyph sits in
     * the upper end corner of the card, that is on the darker part of the gradient, so the check
     * runs against the gradient at the height of the symbol instead of against its lightest stop.
     *
     * The neutral keeps the 3:1 a graphical object needs. The storm red cannot reach 3:1 on the
     * blue header tones without turning into a pale salmon that no longer reads as a warning, so
     * it is held at 1.5:1 and the storm stays additionally marked as text in the same card.
     */
    @Test
    fun `the header wind palette stays readable on every header`() {
        val colors = HeaderWindIndicatorColors
        assertTrue(
            "the neutral header indicator has to be light",
            luminance(colors.strong) > 0.45,
        )
        assertTrue(
            "storm has to stay red on the header as well",
            colors.storm.red.toDouble() > colors.storm.green.toDouble(),
        )
        listOf(
            HeaderClearDay,
            HeaderClearNight,
            HeaderCloudy,
            HeaderRain,
            HeaderSnow,
            HeaderThunder,
            HeaderFog,
        ).forEach { (top, bottom) ->
            val behindSymbol = blend(top, bottom, SYMBOL_BAND)
            val neutral = contrastRatio(colors.strong, behindSymbol)
            assertTrue(
                "strong wind needs 3:1 on $behindSymbol, was ${neutral.format()}",
                neutral >= 3.0,
            )
            val storm = contrastRatio(colors.storm, behindSymbol)
            assertTrue(
                "storm needs 1.5:1 on $behindSymbol, was ${storm.format()}",
                storm >= 1.5,
            )
        }
    }

    private fun blend(first: Color, second: Color, fraction: Float): Color = Color(
        red = first.red + (second.red - first.red) * fraction,
        green = first.green + (second.green - first.green) * fraction,
        blue = first.blue + (second.blue - first.blue) * fraction,
    )

    private val iconFamily = setOf(
        R.drawable.weather_clear_day,
        R.drawable.weather_clear_night,
        R.drawable.weather_partly_cloudy_day,
        R.drawable.weather_partly_cloudy_night,
        R.drawable.weather_cloudy,
        R.drawable.weather_overcast,
        R.drawable.weather_rain_light,
        R.drawable.weather_rain_moderate,
        R.drawable.weather_rain_heavy,
        R.drawable.weather_drizzle,
        R.drawable.weather_snow_light,
        R.drawable.weather_snow_moderate,
        R.drawable.weather_snow_heavy,
        R.drawable.weather_snow_grains,
        R.drawable.weather_sleet,
        R.drawable.weather_fog,
        R.drawable.weather_thunderstorm,
        R.drawable.weather_hail,
    )

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val component = value.toDouble()
            return if (component <= 0.03928) component / 12.92 else ((component + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun Double.format(): String = String.format("%.2f", this)
}

/** Fraction of the header gradient the weather symbol of the hero card covers. */
private const val SYMBOL_BAND = 0.35f
