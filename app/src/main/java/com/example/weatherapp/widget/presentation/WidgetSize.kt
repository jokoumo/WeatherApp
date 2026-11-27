package com.example.weatherapp.widget.presentation

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * How much room a widget actually has.
 *
 * Glance composes the content once per size a widget declares (see `SizeMode.Responsive`) and hands
 * the resulting set of layouts to the system, which then picks the one that fits the space the
 * launcher really offers. Launcher cells are not fixed dp values, so the classes below are derived
 * from the size that was picked for this composition - never from a cell count.
 */
enum class WidgetSizeClass { COMPACT, MEDIUM, EXPANDED }

/**
 * The sizes each widget type offers, and the rules that turn a size into a [WidgetSizeClass].
 *
 * A declared size is not decoration: it is the space the matching layout is designed to fit into,
 * which is what lets the system choose a layout that does not clip. Below [MIN_MEDIUM_HEIGHT_DP] or
 * [MIN_MEDIUM_WIDTH_DP] there is no room for a second block of values, above both
 * [EXPANDED_WIDTH_DP] and [EXPANDED_HEIGHT_DP] there is room for a full grid.
 */
object WidgetSizes {

    /** Current weather: header plus up to three rows of three metric tiles. */
    val currentWeatherSizes: Set<DpSize> = setOf(
        DpSize(140.dp, 100.dp),
        DpSize(250.dp, 165.dp),
        DpSize(320.dp, 250.dp),
    )

    /** Rolling day periods: two by two cards, or a compact stack of four lines. */
    val dayPeriodSizes: Set<DpSize> = setOf(
        DpSize(170.dp, 110.dp),
        DpSize(260.dp, 170.dp),
        DpSize(320.dp, 250.dp),
    )

    /** Three-day forecast: three rows of day, symbol, precipitation and temperature range. */
    val threeDaySizes: Set<DpSize> = setOf(
        DpSize(170.dp, 110.dp),
        DpSize(260.dp, 160.dp),
        DpSize(320.dp, 210.dp),
    )

    /** Width below which a second column of values would be unreadable. */
    const val MIN_MEDIUM_WIDTH_DP = 200

    /** Height below which a second block of values does not fit. */
    const val MIN_MEDIUM_HEIGHT_DP = 130

    /** Width from which a three-column grid is used. */
    const val EXPANDED_WIDTH_DP = 300

    /** Height from which a three-column grid is used. */
    const val EXPANDED_HEIGHT_DP = 200

    /** Classification of one composed size. */
    fun sizeClassFor(size: DpSize): WidgetSizeClass {
        val width = size.width.value
        val height = size.height.value
        return when {
            width < MIN_MEDIUM_WIDTH_DP || height < MIN_MEDIUM_HEIGHT_DP -> WidgetSizeClass.COMPACT
            width >= EXPANDED_WIDTH_DP && height >= EXPANDED_HEIGHT_DP -> WidgetSizeClass.EXPANDED
            else -> WidgetSizeClass.MEDIUM
        }
    }

    /** Smallest size of a set, used as the fallback when nothing else was declared. */
    fun smallest(sizes: Set<DpSize>): DpSize =
        sizes.minByOrNull { it.width.value * it.height.value } ?: DpSize(140.dp, 100.dp)
}
