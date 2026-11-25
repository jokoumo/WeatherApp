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
 * saved location for the "does the app still need its setup screen?" question. The second half
 * covers which ids still hold a forecast worth keeping, and how a stored state that breaks the role
 * rules is repaired without discarding anything valid.
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

        assertFalse("no place is saved", settings.hasAnyLocation)
        assertTrue("the remembered place is still reachable", settings.hasSelectableLocation)
        assertEquals(tokyo.id, settings.resolveSelectedLocation()?.id)
    }

    /**
     * A stored state without a home location is unusual, not broken: its favorites and history
     * entries stay reachable instead of being hidden behind the initial setup screen.
     */
    @Test
    fun `places without a home location are still selectable`() {
        val withoutHome = LocationSettings(favorites = listOf(berlin), recentLocations = listOf(tokyo))

        assertTrue(withoutHome.hasSelectableLocation)
        assertEquals(berlin.id, withoutHome.resolveSelectedLocation()?.id)
        assertFalse("only a state that knows no place at all is empty", LocationSettings().hasSelectableLocation)
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

    // ------------------------------------------------------------------ cache retention

    /**
     * Every id that some role or the selection refers to. A cached forecast outside this set can
     * never be read again, which is what makes it safe to delete.
     */
    @Test
    fun `retained ids cover all three roles and the selection`() {
        val settings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            recentLocations = listOf(tokyo),
            selectedLocationId = paris.id,
        )

        assertEquals(setOf(hamburg.id, berlin.id, tokyo.id, paris.id), settings.retainedLocationIds)
        assertTrue(
            "a state that knows nothing retains nothing",
            LocationSettings().retainedLocationIds.isEmpty(),
        )
    }

    // ------------------------------------------------------------------- normalization

    @Test
    fun `normalizing a consistent state changes nothing`() {
        val settings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin, tokyo),
            recentLocations = listOf(paris),
            selectedLocationId = tokyo.id,
        )

        assertEquals(settings, settings.normalized())
    }

    /** Home wins over favorites, favorites win over history; the place itself never disappears. */
    @Test
    fun `a place stored twice keeps the role with the higher precedence`() {
        val settings = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin, hamburg.copy(name = "Hamburg (alt)")),
            recentLocations = listOf(tokyo, berlin, paris),
            selectedLocationId = hamburg.id,
        )

        val repaired = settings.normalized()

        assertEquals(listOf(berlin.id), repaired.favorites.map { it.id })
        assertEquals(listOf(tokyo.id, paris.id), repaired.recentLocations.map { it.id })
        assertEquals(hamburg, repaired.homeLocation)
    }

    @Test
    fun `duplicates are found by place and not by display name`() {
        val samePlaceOtherId = SavedLocation(
            id = "other-id",
            name = berlin.name,
            latitude = berlin.latitude,
            longitude = berlin.longitude,
        )

        val repaired = LocationSettings(favorites = listOf(berlin, samePlaceOtherId)).normalized()

        assertEquals(listOf(berlin.id), repaired.favorites.map { it.id })
    }

    /** The cap is applied, but only to the entries that are least recent. */
    @Test
    fun `an oversized history keeps its order and drops the least recent entries`() {
        val many = (1..35).map { index ->
            SavedLocation(
                id = "id-$index",
                name = "Ort $index",
                latitude = index * 0.1,
                longitude = 5.0,
            )
        }

        val repaired = LocationSettings(recentLocations = many).normalized()

        assertEquals(LocationSettings.MAX_RECENT_LOCATIONS, repaired.recentLocations.size)
        assertEquals(many.take(30).map { it.id }, repaired.recentLocations.map { it.id })
    }

    @Test
    fun `a selection pointing at a missing place is repaired`() {
        val repaired = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            selectedLocationId = "somewhere-gone",
        ).normalized()

        assertEquals(hamburg.id, repaired.selectedLocationId)
    }

    /**
     * Normalizing has to be safe to run more than once, and it may only remove what is wrong: an
     * empty home location stays empty instead of being filled with a guess.
     */
    @Test
    fun `normalizing is idempotent and never invents a home location`() {
        val broken = LocationSettings(
            favorites = listOf(berlin, berlin),
            recentLocations = listOf(tokyo, tokyo),
            selectedLocationId = "gone",
        )

        val once = broken.normalized()
        val twice = once.normalized()

        assertEquals(once, twice)
        assertNull("nothing is invented", once.homeLocation)
        assertEquals(listOf(berlin.id), once.favorites.map { it.id })
        assertEquals(listOf(tokyo.id), once.recentLocations.map { it.id })
        assertEquals(berlin.id, once.selectedLocationId)
    }

    /** A state that knows no place at all stays empty after normalization. */
    @Test
    fun `an empty state stays empty`() {
        val empty = LocationSettings().normalized()

        assertFalse(empty.hasSelectableLocation)
        assertNull(empty.selectedLocationId)
    }
}
