package com.example.weatherapp.widget.presentation

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

enum class WidgetSizeClass { COMPACT, EXPANDED }

/** The host supplies real dimensions. Width selects the layout and height goes to weighted content. */
object WidgetSizes {
    const val EXPANDED_WIDTH_DP = 240

    fun sizeClassFor(size: DpSize): WidgetSizeClass =
        if (size.width >= EXPANDED_WIDTH_DP.dp) WidgetSizeClass.EXPANDED else WidgetSizeClass.COMPACT
}
