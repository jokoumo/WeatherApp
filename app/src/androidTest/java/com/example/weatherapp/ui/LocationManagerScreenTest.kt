package com.example.weatherapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.locations.LocationManagerScreen
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Interaction tests for the full screen location manager: the fixed title and search field, the
 * favourites list that scrolls on its own, the floating suggestion dropdown and the floating back
 * button. The state wiring mirrors the dashboard, and the search is a deterministic fake.
 */
@RunWith(AndroidJUnit4::class)
class LocationManagerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val hamburg = SavedLocation(
        id = "2911298",
        name = "Hamburg",
        latitude = 53.55,
        longitude = 9.99,
        admin1 = "Freie und Hansestadt Hamburg",
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    private val berlin = SavedLocation(
        id = "2950280",
        name = "Berlin",
        latitude = 52.52,
        longitude = 13.4,
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    private val neustadt = SavedLocation(
        id = "6556991",
        name = "Neustadt",
        latitude = 49.35,
        longitude = 8.15,
        admin1 = "Rheinland-Pfalz",
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    private fun favorite(index: Int): SavedLocation = SavedLocation(
        id = "fav-$index",
        name = "Ort $index",
        latitude = 50.0 + index,
        longitude = 9.0,
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    /** Test double for the geocoding search: two characters are enough for one hit. */
    private fun fakeResults(query: String): List<SavedLocation> =
        if (query.trim().length >= 2) listOf(neustadt) else emptyList()

    private class Harness {
        var dismissed by mutableStateOf(false)
        var added by mutableStateOf<List<SavedLocation>>(emptyList())
        var backPressed: (() -> Unit)? = null
    }

    private fun renderManager(
        home: SavedLocation? = hamburg,
        initialFavorites: List<SavedLocation> = listOf(berlin),
        message: UserMessage? = null,
    ): Harness {
        val harness = Harness()
        composeRule.setContent {
            WeatherAppTheme {
                var query by remember { mutableStateOf("") }
                var favorites by remember { mutableStateOf(initialFavorites) }
                var selectedId by remember { mutableStateOf(home?.id) }
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                harness.backPressed = { backDispatcher?.onBackPressed() }

                if (!harness.dismissed) {
                    LocationManagerScreen(
                        settings = LocationSettings(
                            homeLocation = home,
                            favorites = favorites + harness.added,
                            selectedLocationId = selectedId,
                        ),
                        search = SearchUiState(query = query, results = fakeResults(query)),
                        message = message,
                        onSelect = { selectedId = it },
                        onSetHome = { location ->
                            favorites = favorites.filterNot { it.id == location.id }
                        },
                        onAddFavorite = { location ->
                            if ((favorites + harness.added).none { it.id == location.id }) {
                                harness.added = harness.added + location
                            }
                        },
                        onRemove = { id -> favorites = favorites.filterNot { it.id == id } },
                        onMove = { _, _ -> },
                        onQueryChange = { query = it },
                        onMessageShown = { },
                        onDismiss = { harness.dismissed = true },
                    )
                }
            }
        }
        return harness
    }

    private fun pressBack(harness: Harness) {
        val action = harness.backPressed ?: error("back dispatcher not available")
        composeRule.runOnUiThread(action)
        composeRule.waitForIdle()
    }

    /** Smallest accepted touch target, derived from density instead of a fixed pixel count. */
    private fun minTouchTargetPx(): Float =
        48f * context.resources.displayMetrics.density

    /** The text actually typed into the search field, without the field's label. */
    private fun editableTextOfSearchField(): String =
        composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.EditableText)?.text ?: ""

    @Test
    fun theManagerShowsTitleSearchFieldAndSavedLocations() {
        renderManager()

        composeRule.onNodeWithText(context.getString(R.string.locations_screen_title)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText(hamburg.name).assertIsDisplayed()
        composeRule.onNodeWithText(berlin.name).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back_to_weather))
            .assertIsDisplayed()
    }

    @Test
    fun searchFieldStaysFixedWhileLongFavouriteListIsScrolled() {
        renderManager(initialFavorites = (1..25).map { favorite(it) })

        composeRule.onNodeWithText("Ort 25").assertDoesNotExist()

        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("fav-25")

        composeRule.onNodeWithText("Ort 25").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.locations_screen_title)).assertIsDisplayed()
        // Only the list scrolled: the home entry left the viewport on its own.
        composeRule.onNodeWithText(hamburg.name).assertDoesNotExist()
    }

    @Test
    fun lastFavouriteScrollsClearOfTheFloatingBackButton() {
        renderManager(initialFavorites = (1..20).map { favorite(it) })

        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("fav-20")

        val lastRow = composeRule.onNodeWithText("Ort 20").fetchSemanticsNode().boundsInWindow
        val button = composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_back_to_weather))
            .fetchSemanticsNode()
            .boundsInWindow

        composeRule.onNodeWithText("Ort 20").assertIsDisplayed()
        assertTrue(
            "last favourite ends at ${lastRow.bottom}, back button starts at ${button.top}",
            lastRow.bottom <= button.top,
        )
        assertTrue("back button height ${button.height}", button.height >= minTouchTargetPx())
    }

    @Test
    fun suggestionsFloatOverFavouritesWithoutMovingThem() {
        renderManager()
        val before = composeRule.onNodeWithText(berlin.name).fetchSemanticsNode().boundsInWindow

        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.waitForIdle()

        val suggestion = composeRule.onNodeWithText(neustadt.name).fetchSemanticsNode().boundsInWindow
        val after = composeRule.onNodeWithText(berlin.name).fetchSemanticsNode().boundsInWindow

        composeRule.onNodeWithText(neustadt.name).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_add_favorite))
            .assertIsDisplayed()
        assertTrue(
            "the favourites shifted by ${after.top - before.top}",
            kotlin.math.abs(after.top - before.top) < 1f,
        )
        // The dropdown sits inside the area the favourites list already occupies, so it covers the
        // list instead of pushing it down. The favourites list is the scrollable node that holds
        // the saved places; the dropdown is a second, smaller scrollable node.
        val listBounds = composeRule
            .onNode(hasScrollToKeyAction() and hasAnyDescendant(hasText(berlin.name)))
            .fetchSemanticsNode()
            .boundsInWindow
        assertTrue(
            "dropdown ${suggestion.top}..${suggestion.bottom} must overlap the favourites list " +
                "${listBounds.top}..${listBounds.bottom}",
            suggestion.bottom > listBounds.top && suggestion.top < listBounds.bottom,
        )
        val field = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInWindow
        assertTrue(
            "dropdown starts at ${suggestion.top}, the field ends at ${field.bottom}",
            suggestion.top >= field.bottom,
        )
    }

    @Test
    fun choosingASuggestionAddsFavouriteAndClosesDropdown() {
        val harness = renderManager()

        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.onNodeWithText(neustadt.name).performClick()
        composeRule.waitForIdle()

        assertEquals("", editableTextOfSearchField())
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.action_add_favorite))
            .assertCountEquals(0)
        assertEquals(listOf(neustadt), harness.added)
        // Still on the location screen, and the new favourite appears in the list below.
        composeRule.onNodeWithText(context.getString(R.string.locations_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText(neustadt.name).assertIsDisplayed()
    }

    @Test
    fun alreadySavedLocationIsNotAddedTwice() {
        val harness = renderManager()
        val addLabel = context.getString(R.string.action_add_favorite)

        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.onNodeWithText(neustadt.name).performClick()
        composeRule.waitForIdle()

        // Searching the same place again: the suggestion row is recognised by its add icon.
        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.onAllNodesWithContentDescription(addLabel)[0].performClick()
        composeRule.waitForIdle()

        assertEquals(1, harness.added.size)
        composeRule.onNodeWithText(context.getString(R.string.locations_screen_title)).assertIsDisplayed()
    }

    @Test
    fun duplicateFeedbackStaysVisibleOnLocationScreen() {
        renderManager(message = UserMessage.LOCATION_DUPLICATE)

        composeRule.onNodeWithText(context.getString(R.string.message_location_duplicate)).assertIsDisplayed()
    }

    @Test
    fun backClosesSuggestionsFirstAndOnlyThenTheScreen() {
        val harness = renderManager()
        val addLabel = context.getString(R.string.action_add_favorite)

        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.onAllNodesWithContentDescription(addLabel).assertCountEquals(1)

        pressBack(harness)

        assertFalse(
            "back over the suggestions must not close the location screen",
            harness.dismissed,
        )
        composeRule.onAllNodesWithContentDescription(addLabel).assertCountEquals(0)
        composeRule.onNodeWithText(context.getString(R.string.locations_screen_title)).assertExists()

        pressBack(harness)
        assertTrue(harness.dismissed)
    }

    @Test
    fun floatingButtonReturnsToTheDashboard() {
        val harness = renderManager()

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back_to_weather))
            .performClick()

        assertTrue(harness.dismissed)
    }

    @Test
    fun emptyFavouriteListKeepsSearchFieldAvailable() {
        renderManager(home = null, initialFavorites = emptyList())

        composeRule.onNodeWithText(context.getString(R.string.no_home_location)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.locations_no_favorites)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
    }
}
