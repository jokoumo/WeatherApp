package com.example.weatherapp.domain.model

import com.example.weatherapp.data.local.decodeThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Theme mode rules: the stored representation, the fallback for anything unusable, and the
 * decision whether the dark scheme is drawn. The last one is the whole contract of the
 * appearance screen, so it is worth testing without any UI.
 */
class ThemeModeTest {

    @Test
    fun systemIsTheDefaultWhenNothingIsStored() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(null))
        assertEquals(ThemeMode.SYSTEM, decodeThemeMode(null))
    }

    @Test
    fun storedNamesAreReadBack() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStoredValue("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStoredValue("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("SYSTEM"))
    }

    /**
     * A preference written by another version, edited by hand or truncated must never leave the
     * app without a theme: everything that is not a known mode resolves to the default.
     */
    @Test
    fun unknownBlankAndMalformedValuesFallBackToSystem() {
        listOf("", "   ", "light-mode", "AUTO", "null", "0", "solar").forEach { value ->
            assertEquals("value '$value'", ThemeMode.SYSTEM, ThemeMode.fromStoredValue(value))
        }
    }

    @Test
    fun storedNamesAreCaseInsensitiveAndTrimmed() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStoredValue("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStoredValue("  Light  "))
    }

    @Test
    fun decodeUsesTheSameFallbackRules() {
        assertEquals(ThemeMode.DARK, decodeThemeMode("DARK"))
        assertEquals(ThemeMode.SYSTEM, decodeThemeMode("solar"))
        assertEquals(ThemeMode.SYSTEM, decodeThemeMode(""))
    }

    @Test
    fun systemModeFollowsTheAndroidSetting() {
        assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(true))
        assertFalse(ThemeMode.SYSTEM.resolveDarkTheme(false))
    }

    /** A manual choice deliberately ignores the system, which is the point of the feature. */
    @Test
    fun manualChoicesIgnoreTheSystemSetting() {
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(true))
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(false))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(false))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(true))
    }

    @Test
    fun enumNameIsTheStoredRepresentation() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStoredValue(mode.name))
        }
    }
}
