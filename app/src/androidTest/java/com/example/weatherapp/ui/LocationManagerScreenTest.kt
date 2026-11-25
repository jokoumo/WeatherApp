package com.example.weatherapp.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.data.local.SettingsStore
import com.example.weatherapp.data.remote.OpenMeteoGeocodingApi
import com.example.weatherapp.data.remote.dto.GeocodingResponseDto
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.locations.LocationManagerScreen
import com.example.weatherapp.ui.theme.WeatherAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

/**
 * Interaction tests for the full screen location manager: the navigation header with its top-left
 * back arrow, the fixed search field, the favourites list that scrolls on its own and the floating
 * suggestion dropdown. The state wiring mirrors the dashboard, and the search is a deterministic
 * fake.
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
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back))
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

    /**
     * The floating back button and the scroll space that kept rows clear of it are gone: after
     * scrolling, the last favourite ends close to the bottom edge instead of stopping in front of
     * an overlay, and it stays tappable.
     */
    @Test
    fun lastFavouriteEndsNearTheBottomEdgeWithoutAnOverlay() {
        renderManager(initialFavorites = (1..20).map { favorite(it) })

        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("fav-20")

        val lastRow = composeRule.onNodeWithText("Ort 20").fetchSemanticsNode().boundsInWindow
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInWindow

        composeRule.onNodeWithText("Ort 20").assertIsDisplayed()
        assertTrue("last favourite is below the viewport", lastRow.bottom <= screen.bottom)
        // The old footer kept the last row about 96 dp above the edge; now it may come close.
        assertTrue(
            "last favourite stops far above the bottom edge: ${screen.bottom - lastRow.bottom}px",
            screen.bottom - lastRow.bottom < 160f,
        )
    }

    /**
     * Navigation is a plain toolbar action now: exactly one back arrow, top left, at least one
     * 48 dp touch target, and nothing floating over the favourites list.
     */
    @Test
    fun backArrowIsASingleTopLeftToolbarAction() {
        renderManager(initialFavorites = (1..20).map { favorite(it) })

        val label = context.getString(R.string.action_back)
        composeRule.onAllNodesWithContentDescription(label).assertCountEquals(1)

        val arrow = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInWindow
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInWindow
        assertTrue("back arrow top ${arrow.top} is not in the header area", arrow.top < screen.height * 0.15f)
        assertTrue("back arrow left ${arrow.left} is not at the screen edge", arrow.left < 200f)
        assertTrue("back arrow touch target ${arrow.height}", arrow.height >= minTouchTargetPx())
        assertTrue("back arrow touch target ${arrow.width}", arrow.width >= minTouchTargetPx())
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
    fun backArrowReturnsToTheDashboard() {
        val harness = renderManager()

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()

        assertTrue(harness.dismissed)
    }

    @Test
    fun emptyFavouriteListKeepsSearchFieldAvailable() {
        renderManager(home = null, initialFavorites = emptyList())

        composeRule.onNodeWithText(context.getString(R.string.no_home_location)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.locations_no_favorites)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    /**
     * Home switching through the real repository rules: the previous home has to end up in the
     * favourites at the slot the new home left, and it has to become the selected location.
     */
    @Test
    fun makingAFavouriteTheHomeKeepsThePreviousHomeInTheFavourites() {
        val initial = LocationSettings(
            homeLocation = newYork,
            favorites = listOf(hamburg, dresden, paris, madrid),
            selectedLocationId = newYork.id,
        )
        val store = InMemorySettingsStore(initial)
        renderManagerBackedBy(LocationRepository(store, NoSearchGeocodingApi()), initial)

        val setHomeLabel = context.getString(R.string.action_set_home)
        composeRule.onAllNodesWithContentDescription(setHomeLabel).assertCountEquals(4)
        composeRule.onNodeWithText(newYork.name).assertIsDisplayed()

        composeRule.onAllNodesWithContentDescription(setHomeLabel)[0].performClick()
        composeRule.waitForIdle()

        // Hamburg is the home location and it is also what is displayed: the "currently shown"
        // marker sits above the favourites header, in the home section.
        val shownLabel = context.getString(R.string.locations_currently_shown)
        val favouritesTop = composeRule
            .onNodeWithText(context.getString(R.string.locations_favorites))
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val markerTop = composeRule.onNodeWithText(shownLabel).fetchSemanticsNode().boundsInRoot.top
        assertTrue("the selection marker must sit in the home section", markerTop < favouritesTop)

        // Nothing was lost and nothing doubled: each place appears exactly once on screen.
        composeRule.onAllNodesWithText(newYork.name).assertCountEquals(1)
        composeRule.onAllNodesWithText(hamburg.name).assertCountEquals(1)

        // New York City took the first favourite slot, the others kept their relative order.
        val firstTop = composeRule.onNodeWithText(newYork.name).fetchSemanticsNode().boundsInRoot.top
        val secondTop = composeRule.onNodeWithText(dresden.name).fetchSemanticsNode().boundsInRoot.top
        val thirdTop = composeRule.onNodeWithText(madrid.name).fetchSemanticsNode().boundsInRoot.top
        assertTrue("New York City ${firstTop} must be above Dresden $secondTop", firstTop < secondTop)
        assertTrue("Dresden $secondTop must be above Madrid $thirdTop", secondTop < thirdTop)

        val stored = runBlocking { store.currentSettings() }
        assertEquals(hamburg.id, stored.homeLocation?.id)
        assertEquals(listOf(newYork.id, dresden.id, paris.id, madrid.id), stored.favorites.map { it.id })
        assertEquals(hamburg.id, stored.selectedLocationId)
    }

    @Test
    fun reorderArrowsAreDisabledOnlyAtTheEndsOfTheList() {
        renderManager(home = hamburg, initialFavorites = listOf(berlin, neustadt))

        val up = context.getString(R.string.action_move_up)
        val down = context.getString(R.string.action_move_down)
        // The disabled state is also exposed through semantics, so it never relies on colour alone.
        assertTrue("the first row cannot move up", composeRule.onAllNodesWithContentDescription(up)[0].isDisabled())
        assertFalse("the second row can move up", composeRule.onAllNodesWithContentDescription(up)[1].isDisabled())
        assertFalse("the first row can move down", composeRule.onAllNodesWithContentDescription(down)[0].isDisabled())
        assertTrue("the last row cannot move down", composeRule.onAllNodesWithContentDescription(down)[1].isDisabled())
    }

    @Test
    fun longLocationNamesStayClearOfTheActionButtons() {
        val longPlace = SavedLocation(
            id = "long-name-1",
            name = "Sankt Georgen im Schwarzwald Mittelstadt",
            latitude = 48.1,
            longitude = 8.3,
            admin1 = "Regierungsbezirk Freiburg im Breisgau",
            country = "Deutschland",
            timeZoneId = "Europe/Berlin",
        )
        renderManager(home = hamburg, initialFavorites = listOf(longPlace, berlin))

        // The unmerged tree is needed here: the row itself is a single clickable node, so its text
        // only has its own bounds when the tree is not merged.
        val nameBounds = composeRule
            .onNodeWithText(longPlace.name, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val labels = listOf(
            context.getString(R.string.action_move_up),
            context.getString(R.string.action_move_down),
            context.getString(R.string.action_set_home),
            context.getString(R.string.action_remove_location),
        )
        val buttons = labels.flatMap { label ->
            composeRule.onAllNodesWithContentDescription(label, useUnmergedTree = true).fetchSemanticsNodes()
        }
        assertTrue("the row must offer its action buttons", buttons.isNotEmpty())
        val screenRight = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        buttons.forEach { button ->
            val buttonBounds = button.boundsInRoot
            assertTrue(
                "the name of the location overlaps an action button",
                !nameBounds.overlaps(buttonBounds),
            )
            // Every action stays reachable inside the window.
            assertTrue("an action button is cut off at the screen edge", buttonBounds.right <= screenRight)
        }
    }

    /** Whether a node is marked disabled in its semantics configuration. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.isDisabled(): Boolean =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled) != null

    /** In-memory stand-in for DataStore, so the UI can drive the real repository rules. */
    private class InMemorySettingsStore(initial: LocationSettings) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: Flow<LocationSettings> = state
        override suspend fun save(settings: LocationSettings) {
            state.value = settings
        }

        suspend fun currentSettings(): LocationSettings = state.value
    }

    private class NoSearchGeocodingApi : OpenMeteoGeocodingApi {
        override suspend fun search(
            name: String,
            count: Int,
            language: String,
            format: String,
        ): Response<GeocodingResponseDto> =
            throw UnsupportedOperationException("location search is not part of this test")
    }

    private fun renderManagerBackedBy(repository: LocationRepository, initial: LocationSettings) {
        composeRule.setContent {
            WeatherAppTheme {
                val settings by repository.settings.collectAsStateWithLifecycle(
                    lifecycle = LocalLifecycleOwner.current.lifecycle,
                    initialValue = initial,
                )
                val scope = rememberCoroutineScope()
                LocationManagerScreen(
                    settings = settings,
                    search = SearchUiState(query = ""),
                    message = null,
                    onSelect = { id -> scope.launch { repository.selectLocation(id) } },
                    onSetHome = { location -> scope.launch { repository.setHomeLocation(location) } },
                    onAddFavorite = { location -> scope.launch { repository.addFavorite(location) } },
                    onRemove = { id -> scope.launch { repository.removeLocation(id) } },
                    onMove = { id, delta -> scope.launch { repository.moveFavorite(id, delta) } },
                    onQueryChange = { },
                    onMessageShown = { },
                    onDismiss = { },
                )
            }
        }
    }

    private val newYork = SavedLocation(
        id = "5128581",
        name = "New York City",
        latitude = 40.71,
        longitude = -74.01,
        admin1 = "New York",
        country = "Vereinigte Staaten",
        timeZoneId = "America/New_York",
    )

    private val dresden = SavedLocation(
        id = "2935022",
        name = "Dresden",
        latitude = 51.05,
        longitude = 13.74,
        admin1 = "Sachsen",
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    private val paris = SavedLocation(
        id = "2988507",
        name = "Paris",
        latitude = 48.85,
        longitude = 2.35,
        admin1 = "Île-de-France",
        country = "Frankreich",
        timeZoneId = "Europe/Paris",
    )

    private val madrid = SavedLocation(
        id = "3117735",
        name = "Madrid",
        latitude = 40.42,
        longitude = -3.7,
        admin1 = "Madrid",
        country = "Spanien",
        timeZoneId = "Europe/Madrid",
    )
}
