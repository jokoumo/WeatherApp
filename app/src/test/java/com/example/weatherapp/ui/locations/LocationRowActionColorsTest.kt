package com.example.weatherapp.ui.locations

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.example.weatherapp.ui.theme.DarkColorScheme
import com.example.weatherapp.ui.theme.LightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Contrast of the action icons in the location rows, checked against the colour schemes the
 * application actually ships. Enabled actions have to stand out from the row background, disabled
 * ones have to look clearly reduced but must not fade into the background.
 */
class LocationRowActionColorsTest {

    private val schemes: List<ColorScheme> = listOf(LightColorScheme, DarkColorScheme)

    private val emphasises: List<LocationActionEmphasis> = listOf(
        LocationActionEmphasis.STANDARD,
        LocationActionEmphasis.EMPHASISED,
        LocationActionEmphasis.DESTRUCTIVE,
    )

    @Test
    fun `every enabled action is clearly visible on the themed background`() {
        schemes.forEach { scheme ->
            emphasises.forEach { emphasis ->
                val ratio = contrastRatio(
                    foreground = locationActionColor(scheme, emphasis, enabled = true),
                    background = scheme.background,
                )
                assertTrue(
                    "$emphasis needs at least 3:1 on the background, was ${ratio.format()}",
                    ratio >= 3.0,
                )
            }
        }
    }

    @Test
    fun `a disabled action is visibly dimmer than the enabled one`() {
        schemes.forEach { scheme ->
            emphasises.forEach { emphasis ->
                val enabled = contrastRatio(
                    locationActionColor(scheme, emphasis, enabled = true),
                    scheme.background,
                )
                val disabled = contrastRatio(
                    locationActionColor(scheme, emphasis, enabled = false),
                    scheme.background,
                )
                assertNotEquals(
                    "disabled must not reuse the enabled colour",
                    locationActionColor(scheme, emphasis, enabled = true),
                    locationActionColor(scheme, emphasis, enabled = false),
                )
                assertTrue(
                    "disabled ${emphasis} at ${disabled.format()} vs enabled ${enabled.format()}",
                    enabled - disabled >= 1.5,
                )
            }
        }
    }

    @Test
    fun `a disabled action never disappears into the background`() {
        schemes.forEach { scheme ->
            emphasises.forEach { emphasis ->
                val color = locationActionColor(scheme, emphasis, enabled = false)
                val ratio = contrastRatio(color, scheme.background)
                assertTrue(
                    "disabled ${emphasis} at ${ratio.format()} is too faint",
                    ratio >= 1.5,
                )
            }
        }
    }

    @Test
    fun `all disabled actions share one reduced emphasis colour`() {
        schemes.forEach { scheme ->
            val expected = scheme.onSurfaceVariant.copy(alpha = DISABLED_ACTION_ALPHA)
            emphasises.forEach { emphasis ->
                assertEquals(expected, locationActionColor(scheme, emphasis, enabled = false))
            }
        }
    }

    @Test
    fun `enabled colours come from the theme instead of a fixed colour`() {
        schemes.forEach { scheme ->
            assertEquals(scheme.onSurfaceVariant, locationActionColor(scheme, LocationActionEmphasis.STANDARD, true))
            assertEquals(scheme.primary, locationActionColor(scheme, LocationActionEmphasis.EMPHASISED, true))
            assertEquals(scheme.error, locationActionColor(scheme, LocationActionEmphasis.DESTRUCTIVE, true))
        }
    }

    /** WCAG contrast ratio, with an alpha value blended over the background first. */
    private fun contrastRatio(foreground: Color, background: Color): Double {
        val alpha = foreground.alpha.toDouble()
        val blended = Color(
            red = (foreground.red.toDouble() * alpha + background.red.toDouble() * (1 - alpha)).toFloat(),
            green = (foreground.green.toDouble() * alpha + background.green.toDouble() * (1 - alpha)).toFloat(),
            blue = (foreground.blue.toDouble() * alpha + background.blue.toDouble() * (1 - alpha)).toFloat(),
        )
        val first = luminance(blended)
        val second = luminance(background)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
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
