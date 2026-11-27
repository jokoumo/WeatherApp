package com.example.weatherapp.widget.presentation

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The size classes the three widgets are built for.
 *
 * Glance composes the content once per declared size, so a declared size that classified into an
 * unintended layout would show the wrong density of information on a real launcher. That invariant is
 * what this file pins down: every offered size of every widget has to land in the layout it was
 * designed for, and all three layouts have to stay reachable.
 */
class WidgetSizesTest {

    private val allWidgetSizes = listOf(
        WidgetSizes.currentWeatherSizes,
        WidgetSizes.dayPeriodSizes,
        WidgetSizes.threeDaySizes,
    )

    @Test
    fun `every offered size of every widget has its own layout class`() {
        allWidgetSizes.forEach { sizes ->
            val classes = sizes.map(WidgetSizes::sizeClassFor).sortedBy { it.ordinal }
            assertEquals(
                listOf(WidgetSizeClass.COMPACT, WidgetSizeClass.MEDIUM, WidgetSizeClass.EXPANDED),
                classes,
            )
        }
    }

    @Test
    fun `the smallest offered size of a widget is the one it falls back to`() {
        assertEquals(DpSize(140.dp, 100.dp), WidgetSizes.smallest(WidgetSizes.currentWeatherSizes))
        assertEquals(DpSize(170.dp, 110.dp), WidgetSizes.smallest(WidgetSizes.dayPeriodSizes))
        assertEquals(DpSize(170.dp, 110.dp), WidgetSizes.smallest(WidgetSizes.threeDaySizes))
    }

    @Test
    fun `the grid needs width and height, not one of them`() {
        // Wide but shallow: no room for three rows of tiles, so the medium layout is used.
        assertEquals(WidgetSizeClass.MEDIUM, WidgetSizes.sizeClassFor(DpSize(320.dp, 150.dp)))
        // Tall but narrow: no room for a second column of values, so the compact layout is used.
        assertEquals(WidgetSizeClass.COMPACT, WidgetSizes.sizeClassFor(DpSize(190.dp, 250.dp)))
        assertEquals(WidgetSizeClass.EXPANDED, WidgetSizes.sizeClassFor(DpSize(320.dp, 250.dp)))
    }

    @Test
    fun `information density grows with the size class`() {
        assertEquals(0, tileCountFor(WidgetSizeClass.COMPACT))
        assertEquals(4, tileCountFor(WidgetSizeClass.MEDIUM))
        assertEquals(9, tileCountFor(WidgetSizeClass.EXPANDED))
    }

    @Test
    fun `the offered sizes are large enough for the launcher cells they claim to fill`() {
        // Every offered size has to be at least the minimum the widget provider declares, otherwise
        // the system can pick a layout the widget was never designed for.
        allWidgetSizes.flatten().forEach { size ->
            assertEquals(true, size.width.value >= 140f && size.height.value >= 100f)
        }
    }
}
