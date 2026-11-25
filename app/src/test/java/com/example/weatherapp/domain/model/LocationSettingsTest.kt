package com.example.weatherapp.domain.model

import com.example.weatherapp.testing.berlin
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.paris
import com.example.weatherapp.testing.tokyo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The location model on its own: which roles a location can have at the same time, how the
 * displayed location is resolved when the stored selection no longer exists, and what counts as a
 * saved location for the "does the app still need its setup screen?" question.
 */
class LocationSettingsTest {

    @Test
    fun `a settings object starts empty and knows nothing`() {
        val settings = LocationSettings()

        assertFalse(settings.hasAnyLocation)
        assertTrue(settings.savedLocations.isEmpty())
        assertTrue(settings.selectableLocations.isEmpty())
        assertNull(settings.resolveSelectedLocation())
    }

    @Test
    fun `saved locations are home first and then the favorites in their own order`() {
        val settings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(tokyo, berlin),
            recentLocations = listOf(paris),
        )

        assertEquals(listOf(hamburg.id, tokyo.id, berlin.id), settings.savedLocations.map { it.id })
        assertEquals(
            listOf(hamburg.id, tokyo.id, berlin.id, paris.id),
            settings.selectableLocations.map { it.id },
        )
    }

    @Test
    fun `every role is answered for the right location only`() {
        val settings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            recentLocations = listOf(tokyo),
        )

        assertTrue(settings.isHomeLocation(hamburg.id))
        assertFalse(settings.isFavorite(hamburg.id))

        assertTrue(settings.isFavorite(berlin.id))
        assertFalse(settings.isHomeLocation(berlin.id))

        assertTrue(settings.isRecent(tokyo.id))
        assertFalse(settings.isFavorite(tokyo.id))

        assertNull(settings.findLocation("nope"))
        assertEquals(tokyo, settings.findLocation(tokyo.id))
    }

    @Test
    fun `a selection that still exists wins over every fallback`() {
        val settings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            recentLocations = listOf(tokyo),
            selectedLocationId = tokyo.id,
        )

        assertEquals(tokyo.id, settings.resolveSelectedLocation()?.id)
    }

    @Test
    fun `a selection that no longer exists falls back to home, then favorites, then history`() {
        val withHome = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            selectedLocationId = "gone",
        )
        assertEquals(hamburg.id, withHome.resolveSelectedLocation()?.id)

        val favoritesOnly = LocationSettings(favorites = listOf(berlin, tokyo), selectedLocationId = "gone")
        assertEquals(berlin.id, favoritesOnly.resolveSelectedLocation()?.id)

        val historyOnly = LocationSettings(recentLocations = listOf(paris, tokyo), selectedLocationId = "gone")
        assertEquals(paris.id, historyOnly.resolveSelectedLocation()?.id)
    }

    @Test
    fun `history alone does not count as a saved location`() {
        val settings = LocationSettings(recentLocations = listOf(tokyo))

        assertFalse("the setup screen is still needed", settings.hasAnyLocation)
        assertEquals(tokyo.id, settings.resolveSelectedLocation()?.id)
    }

    @Test
    fun `the history capacity is a single central number`() {
        assertEquals(30, LocationSettings.MAX_RECENT_LOCATIONS)
    }

    @Test
    fun `identity is the stored id, not the display name`() {
        val sameName = SavedLocation(id = "1", name = berlin.name, latitude = 1.0, longitude = 2.0)

        assertFalse(sameName.isSamePlaceAs(berlin))
        assertTrue(berlin.isSamePlaceAs(berlin.copy(name = "Hamburg an der Elbe")))
    }
}
