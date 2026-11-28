package com.example.weatherapp.widget.presentation

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizesTest {
    @Test
    fun `width selects one of the two widget layouts at the breakpoint`() {
        assertEquals(WidgetSizeClass.COMPACT, WidgetSizes.sizeClassFor(DpSize(146.dp, 148.dp)))
        assertEquals(WidgetSizeClass.COMPACT, WidgetSizes.sizeClassFor(DpSize(239.dp, 148.dp)))
        assertEquals(WidgetSizeClass.EXPANDED, WidgetSizes.sizeClassFor(DpSize(240.dp, 148.dp)))
        assertEquals(WidgetSizeClass.EXPANDED, WidgetSizes.sizeClassFor(DpSize(300.dp, 148.dp)))
    }

    @Test
    fun `extra height does not switch to a denser layout`() {
        assertEquals(WidgetSizeClass.COMPACT, WidgetSizes.sizeClassFor(DpSize(190.dp, 300.dp)))
        assertEquals(WidgetSizeClass.EXPANDED, WidgetSizes.sizeClassFor(DpSize(270.dp, 300.dp)))
    }

    @Test
    fun `forecast tiles fit within the two height cell designs`() {
        assertEquals(2, tileCountFor(WidgetSizeClass.COMPACT))
        assertEquals(4, tileCountFor(WidgetSizeClass.EXPANDED))
    }
}
