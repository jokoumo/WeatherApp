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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
import androidx.test.espresso.Espresso
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
 * Interaction tests for the full screen location manager: the fixed header and search field, the
 * list that scrolls on its own and is grouped into Hauptstandort, Favoriten and Verlauf, the
 * floating suggestion dropdown, and the compact overflow menus.
 *
 * The central rule these tests protect is that a row tap selects a location and leaves the screen,
 * while every control inside a row - star, delete, overflow - performs only its own action.
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

    private fun recent(index: Int): SavedLocation = SavedLocation(
        id = "rec-$index",
        name = "Verlaufsort $index",
        latitude = 20.0 + index,
        longitude = 9.0,
        country = "Deutschland",
        timeZoneId = "Europe/Berlin",
    )

    /** Test double for the geocoding search: two characters are enough for one hit. */
    private fun fakeResults(query: String): List<SavedLocation> =
        if (query.trim().length >= 2) listOf(neustadt) else emptyList()

    private class Harness {
        var dismissed by mutableStateOf(false)
        var clearedHistory by mutableStateOf(false)
        val selected = mutableListOf<String>()
        val searched = mutableListOf<SavedLocation>()
        val toggled = mutableListOf<SavedLocation>()
        val setHome = mutableListOf<SavedLocation>()
        val removedRecents = mutableListOf<String>()
        var backPressed: (() -> Unit)? = null
    }

    private fun renderManager(
        home: SavedLocation? = hamburg,
        initialFavorites: List<SavedLocation> = listOf(berlin),
        initialRecents: List<SavedLocation> = emptyList(),
        message: UserMessage? = null,
    ): Harness {
        val harness = Harness()
        composeRule.setContent {
            WeatherAppTheme {
                var query by remember { mutableStateOf("") }
                var favorites by remember { mutableStateOf(initialFavorites) }
                var recents by remember { mutableStateOf(initialRecents) }
                var selectedId by remember { mutableStateOf(home?.id) }
                val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                harness.backPressed = { backDispatcher?.onBackPressed() }

                if (!harness.dismissed) {
                    LocationManagerScreen(
                        settings = LocationSettings(
                            homeLocation = home,
                            favorites = favorites,
                            recentLocations = recents,
                            selectedLocationId = selectedId,
                        ),
                        search = SearchUiState(query = query, results = fakeResults(query)),
                        message = message,
                        onSelect = { id ->
                            harness.selected += id
                            selectedId = id
                        },
                        onSelectSearched = { location ->
                            harness.searched += location
                            recents = (listOf(location) + recents.filterNot { it.id == location.id })
                            selectedId = location.id
                        },
                        onToggleFavorite = { location ->
                            harness.toggled += location
                            if (favorites.any { it.id == location.id }) {
                                favorites = favorites.filterNot { it.id == location.id }
                                recents = (listOf(location) + recents.filterNot { it.id == location.id })
                            } else {
                                favorites = favorites + location
                                recents = recents.filterNot { it.id == location.id }
                            }
                        },
                        onSetHome = { location ->
                            harness.setHome += location
                            favorites = favorites.filterNot { it.id == location.id }
                            recents = recents.filterNot { it.id == location.id }
                        },
                        onRemoveRecent = { id ->
                            harness.removedRecents += id
                            recents = recents.filterNot { it.id == id }
                        },
                        onClearRecent = { harness.clearedHistory = true },
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

    /**
     * The action button carrying [description]. In the merged semantics tree a button is one node
     * that owns its icon's label, so one match per button, in list order - and the node is the
     * clickable button itself, never the row around it.
     */
    private fun actionButtons(description: String) = composeRule
        .onAllNodes(hasContentDescription(description) and hasClickAction())

    /** Opens the overflow menu of the [index]-th row that has one, in list order. */
    private fun openOverflow(index: Int) {
        actionButtons(context.getString(R.string.action_more_actions))[index].performClick()
        composeRule.waitForIdle()
    }

    private fun string(res: Int) = context.getString(res)

    // ------------------------------------------------------------------ structure

    @Test
    fun theManagerShowsTitleSearchFieldAndSavedLocations() {
        renderManager()

        composeRule.onNodeWithText(string(R.string.locations_screen_title)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText(hamburg.name).assertIsDisplayed()
        composeRule.onNodeWithText(berlin.name).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.action_back)).assertIsDisplayed()
    }

    /** The three roles are separate sections, and they always come in the same order. */
    @Test
    fun sectionsAreOrderedHomeThenFavoritesThenHistory() {
        renderManager(initialFavorites = listOf(berlin), initialRecents = listOf(recent(1)))

        val homeTop = composeRule.onNodeWithText(string(R.string.locations_home)).fetchSemanticsNode().boundsInRoot.top
        val favoritesTop = composeRule
            .onNodeWithText(string(R.string.locations_favorites))
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val historyTop = composeRule.onNodeWithText(string(R.string.locations_history)).fetchSemanticsNode().boundsInRoot.top

        assertTrue("home section below favourites", homeTop < favoritesTop)
        assertTrue("favourites section below history", favoritesTop < historyTop)
        composeRule.onNodeWithText(recent(1).name).assertIsDisplayed()
    }

    /** The home location is marked by a house, and the star belongs to favourites alone. */
    @Test
    fun homeLocationIsMarkedByAHouseAndHasNoStar() {
        renderManager(initialFavorites = listOf(berlin), initialRecents = listOf(recent(1)))

        composeRule.onNodeWithContentDescription(string(R.string.content_description_home_location))
            .assertIsDisplayed()
        // Exactly one star per favourite (remove) and one per history entry (add); none for home.
        actionButtons(string(R.string.action_remove_favorite)).assertCountEquals(1)
        actionButtons(string(R.string.action_add_favorite)).assertCountEquals(1)
    }

    @Test
    fun emptyListsSaySoAndKeepTheSearchFieldAvailable() {
        renderManager(home = null, initialFavorites = emptyList())

        composeRule.onNodeWithText(string(R.string.no_home_location)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.locations_no_favorites)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.locations_no_history)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    /** Nothing offers "Alle löschen" while the history is empty. */
    @Test
    fun clearHistoryIsOfferedWhenTheHistoryHasEntries() {
        renderManager(initialRecents = listOf(recent(1)))

        composeRule.onNodeWithText(string(R.string.action_clear_history)).assertIsDisplayed()
    }

    @Test
    fun clearHistoryIsHiddenForAnEmptyHistory() {
        renderManager()

        composeRule.onNodeWithText(string(R.string.locations_no_history)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_clear_history)).assertDoesNotExist()
    }

    @Test
    fun searchFieldStaysFixedWhileLongFavouriteListIsScrolled() {
        renderManager(initialFavorites = (1..25).map { favorite(it) })

        composeRule.onNodeWithText("Ort 25").assertDoesNotExist()

        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("fav-25")

        composeRule.onNodeWithText("Ort 25").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.locations_screen_title)).assertIsDisplayed()
        // Only the list scrolled: the home entry left the viewport on its own.
        composeRule.onNodeWithText(hamburg.name).assertDoesNotExist()
    }

    /**
     * The last entry of the whole list - here a history row - ends close to the bottom edge
     * instead of stopping in front of an overlay, and it stays tappable.
     */
    @Test
    fun lastEntryEndsNearTheBottomEdgeWithoutAnOverlay() {
        renderManager(initialFavorites = (1..12).map { favorite(it) }, initialRecents = (1..8).map { recent(it) })

        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("rec-8")

        val lastRow = composeRule.onNodeWithText(recent(8).name).fetchSemanticsNode().boundsInWindow
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInWindow

        composeRule.onNodeWithText(recent(8).name).assertIsDisplayed()
        assertTrue("last entry is below the viewport", lastRow.bottom <= screen.bottom)
        assertTrue(
            "last entry stops far above the bottom edge: ${screen.bottom - lastRow.bottom}px",
            screen.bottom - lastRow.bottom < 160f,
        )
    }

    /**
     * Navigation is a plain toolbar action: exactly one back arrow, top left, at least one
     * 48 dp touch target, and nothing floating over the list.
     */
    @Test
    fun backArrowIsASingleTopLeftToolbarAction() {
        renderManager(initialFavorites = (1..20).map { favorite(it) })

        val label = string(R.string.action_back)
        composeRule.onAllNodesWithContentDescription(label).assertCountEquals(1)

        val arrow = composeRule.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInWindow
        val screen = composeRule.onRoot().fetchSemanticsNode().boundsInWindow
        assertTrue("back arrow top ${arrow.top} is not in the header area", arrow.top < screen.height * 0.15f)
        assertTrue("back arrow left ${arrow.left} is not at the screen edge", arrow.left < 200f)
        assertTrue("back arrow touch target ${arrow.height}", arrow.height >= minTouchTargetPx())
        assertTrue("back arrow touch target ${arrow.width}", arrow.width >= minTouchTargetPx())
    }

    /** Every row action is a full touch target, including the ones inside the overflow button. */
    @Test
    fun rowActionButtonsAreAtLeastFortyEightDp() {
        renderManager(initialRecents = listOf(recent(1)))

        listOf(
            string(R.string.action_add_favorite),
            string(R.string.action_remove_recent),
            string(R.string.action_more_actions),
        ).forEach { label ->
            val button = actionButtons(label)[0].fetchSemanticsNode().boundsInWindow
            assertTrue("$label touch target ${button.width}x${button.height}", button.width >= minTouchTargetPx())
            assertTrue("$label touch target ${button.width}x${button.height}", button.height >= minTouchTargetPx())
        }
    }

    // ------------------------------------------------------------------ navigation

    @Test
    fun backArrowReturnsToTheDashboard() {
        val harness = renderManager()

        composeRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        assertTrue(harness.dismissed)
    }

    @Test
    fun backClosesSuggestionsFirstAndOnlyThenTheScreen() {
        val harness = renderManager()

        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.onAllNodesWithText(neustadt.name).assertCountEquals(1)

        pressBack(harness)

        assertFalse(
            "back over the suggestions must not close the location screen",
            harness.dismissed,
        )
        composeRule.onAllNodesWithText(neustadt.name).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.locations_screen_title)).assertExists()

        pressBack(harness)
        assertTrue(harness.dismissed)
    }

    // ------------------------------------------------------------------- suggestions

    @Test
    fun suggestionsFloatOverTheListWithoutMovingIt() {
        renderManager()
        val before = composeRule.onNodeWithText(berlin.name).fetchSemanticsNode().boundsInWindow

        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.waitForIdle()

        val suggestion = composeRule.onNodeWithText(neustadt.name).fetchSemanticsNode().boundsInWindow
        val after = composeRule.onNodeWithText(berlin.name).fetchSemanticsNode().boundsInWindow

        composeRule.onNodeWithText(neustadt.name).assertIsDisplayed()
        assertTrue(
            "the favourites shifted by ${after.top - before.top}",
            kotlin.math.abs(after.top - before.top) < 1f,
        )
        val listBounds = composeRule
            .onNode(hasScrollToKeyAction() and hasAnyDescendant(hasText(berlin.name)))
            .fetchSemanticsNode()
            .boundsInWindow
        assertTrue(
            "dropdown ${suggestion.top}..${suggestion.bottom} must overlap the list " +
                "${listBounds.top}..${listBounds.bottom}",
            suggestion.bottom > listBounds.top && suggestion.top < listBounds.bottom,
        )
        val field = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInWindow
        assertTrue(
            "dropdown starts at ${suggestion.top}, the field ends at ${field.bottom}",
            suggestion.top >= field.bottom,
        )
    }

    /**
     * The suggestion row carries no save button at all: tapping it only shows the place, and the
     * screen is left so the dashboard can display it.
     */
    @Test
    fun choosingASuggestionShowsThePlaceAndLeavesTheScreen() {
        val harness = renderManager()

        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.onNodeWithText(neustadt.name).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(neustadt), harness.searched)
        assertTrue("the dashboard has to come up right away", harness.dismissed)
        assertEquals(0, harness.toggled.size)
    }

    @Test
    fun aSuggestionOffersNoSaveButtonAndSavesNothing() {
        val harness = renderManager()

        composeRule.onNode(hasSetTextAction()).performTextInput("neu")
        composeRule.waitForIdle()
        // The only outlined stars on this screen belong to history entries, and there are none.
        actionButtons(string(R.string.action_add_favorite)).assertCountEquals(0)

        composeRule.onNodeWithText(neustadt.name).performClick()

        assertEquals(listOf(neustadt), harness.searched)
        assertEquals("tapping a suggestion never toggles a favourite", 0, harness.toggled.size)
    }

    @Test
    fun duplicateFeedbackStaysVisibleOnLocationScreen() {
        renderManager(message = UserMessage.LOCATION_DUPLICATE)

        composeRule.onNodeWithText(string(R.string.message_location_duplicate)).assertIsDisplayed()
    }

    // ---------------------------------------------------------------- favourites

    /** Tapping the row itself is the only thing that selects and navigates. */
    @Test
    fun tappingAFavouriteRowSelectsItAndLeavesTheScreen() {
        val harness = renderManager(initialFavorites = listOf(berlin))

        composeRule.onNodeWithText(berlin.name).performClick()

        assertEquals(listOf(berlin.id), harness.selected)
        assertTrue(harness.dismissed)
    }

    /** The star of a favourite is a row action: it un-saves the place and stays on the screen. */
    @Test
    fun unFavouritingFromTheRowDoesNotNavigate() {
        val harness = renderManager(initialFavorites = listOf(berlin))

        actionButtons(string(R.string.action_remove_favorite))[0].performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(berlin), harness.toggled)
        assertEquals(0, harness.selected.size)
        assertFalse(harness.dismissed)
    }

    @Test
    fun firstFavouriteCannotMoveUpButCanMoveDown() {
        renderManager(initialFavorites = listOf(berlin, neustadt))

        openOverflow(0)

        composeRule.onNodeWithText(string(R.string.action_set_home)).assertIsDisplayed()
        assertTrue(isDisabled(string(R.string.action_move_up)))
        assertFalse(isDisabled(string(R.string.action_move_down)))
    }

    @Test
    fun lastFavouriteCannotMoveDownButCanMoveUp() {
        renderManager(initialFavorites = listOf(berlin, neustadt))

        openOverflow(1)

        assertFalse(isDisabled(string(R.string.action_move_up)))
        assertTrue(isDisabled(string(R.string.action_move_down)))
    }

    /**
     * Whether the menu entry with that label is marked disabled in its semantics. The merged tree
     * is used on purpose: the disabled state sits on the menu item, not on its text node.
     */
    private fun isDisabled(label: String): Boolean =
        composeRule.onNodeWithText(label)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Disabled) != null

    // -------------------------------------------------------------------- history

    @Test
    fun historyEntriesAreListedNewestFirst() {
        renderManager(initialRecents = listOf(recent(1), recent(2), recent(3)))

        val first = composeRule.onNodeWithText(recent(1).name).fetchSemanticsNode().boundsInRoot.top
        val second = composeRule.onNodeWithText(recent(2).name).fetchSemanticsNode().boundsInRoot.top
        val third = composeRule.onNodeWithText(recent(3).name).fetchSemanticsNode().boundsInRoot.top

        assertTrue("newest history entry must be on top", first < second)
        assertTrue("second newest below newest", second < third)
    }

    @Test
    fun tappingAHistoryRowShowsThatPlaceAndLeavesTheScreen() {
        val harness = renderManager(initialRecents = listOf(recent(1)))

        composeRule.onNodeWithText(recent(1).name).performClick()

        assertEquals(listOf(recent(1).id), harness.selected)
        assertTrue(harness.dismissed)
    }

    @Test
    fun theHistoryStarSavesThePlaceWithoutSelectingIt() {
        val harness = renderManager(initialRecents = listOf(recent(1)))

        actionButtons(string(R.string.action_add_favorite))[0].performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(recent(1)), harness.toggled)
        assertEquals(0, harness.selected.size)
        assertFalse("the star must not navigate away", harness.dismissed)
    }

    @Test
    fun deletingOneHistoryEntryLeavesTheOthersAlone() {
        val harness = renderManager(initialRecents = listOf(recent(1), recent(2)))

        actionButtons(string(R.string.action_remove_recent))[0].performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(recent(1).id), harness.removedRecents)
        composeRule.onNodeWithText(recent(2).name).assertIsDisplayed()
        composeRule.onNodeWithText(recent(1).name).assertDoesNotExist()
        assertEquals(0, harness.selected.size)
        assertFalse(harness.dismissed)
    }

    /** Clearing the whole history is destructive, so it is confirmed first. */
    @Test
    fun clearingTheHistoryAsksBeforeItRemovesAnything() {
        val harness = renderManager(initialRecents = listOf(recent(1)))

        composeRule.onNodeWithText(string(R.string.action_clear_history)).performClick()

        composeRule.onNodeWithText(string(R.string.dialog_clear_history_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.dialog_clear_history_message)).assertIsDisplayed()
        assertFalse("nothing may be cleared before the confirmation", harness.clearedHistory)

        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.dialog_clear_history_title)).assertDoesNotExist()
        assertFalse(harness.clearedHistory)
        composeRule.onNodeWithText(recent(1).name).assertIsDisplayed()
    }

    @Test
    fun confirmingTheDialogClearsTheHistory() {
        val harness = renderManager(initialRecents = listOf(recent(1)))

        composeRule.onNodeWithText(string(R.string.action_clear_history)).performClick()
        composeRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeRule.waitForIdle()

        assertTrue(harness.clearedHistory)
        composeRule.onNodeWithText(string(R.string.dialog_clear_history_title)).assertDoesNotExist()
    }

    /**
     * Back over the confirmation dialog closes the dialog, not the screen. This needs a real key
     * event: a Material dialog lives in its own window, and that window — not the activity's back
     * dispatcher — is what the system back gesture reaches first.
     */
    @Test
    fun backClosesTheConfirmationDialogWithoutLeavingTheScreen() {
        val harness = renderManager(initialRecents = listOf(recent(1)))

        composeRule.onNodeWithText(string(R.string.action_clear_history)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.dialog_clear_history_title)).assertIsDisplayed()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertFalse("back must not leave the location screen", harness.dismissed)
        assertFalse("back must not clear the history", harness.clearedHistory)
        composeRule.onNodeWithText(string(R.string.dialog_clear_history_title)).assertDoesNotExist()
        composeRule.onNodeWithText(recent(1).name).assertIsDisplayed()
    }

    // ---------------------------------------------------------------- overflow

    @Test
    fun openingTheOverflowMenuDoesNotSelectTheLocation() {
        val harness = renderManager(initialFavorites = listOf(berlin))

        openOverflow(0)

        assertEquals(0, harness.selected.size)
        assertFalse(harness.dismissed)
        composeRule.onNodeWithText(string(R.string.action_set_home)).assertIsDisplayed()
    }

    @Test
    fun makingAFavouriteTheHomeLocationComesFromTheOverflowMenu() {
        val harness = renderManager(initialFavorites = listOf(berlin))

        openOverflow(0)
        composeRule.onNodeWithText(string(R.string.action_set_home)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(berlin), harness.setHome)
        assertEquals(0, harness.selected.size)
    }

    /**
     * The home row has no action of its own: no overflow button, so nothing on that row can lift the
     * protected designation and leave the app with stored places but no home location. The single
     * action menu of this list belongs to the favorite, and it is the only place where the home
     * designation moves - which keeps the previous home as a favorite.
     */
    @Test
    fun theHomeLocationRowOffersNoActionOfItsOwn() {
        val harness = renderManager()

        actionButtons(string(R.string.action_more_actions)).assertCountEquals(1)
        // The removed action must not come back, not even under its old German label. The label is
        // written out here on purpose: the string resource it used to have no longer exists.
        composeRule.onNodeWithText("Hauptstandort aufheben").assertDoesNotExist()

        openOverflow(0)
        composeRule.onNodeWithText(string(R.string.action_set_home)).assertIsDisplayed()
        composeRule.onNodeWithText("Hauptstandort aufheben").assertDoesNotExist()
        assertEquals(0, harness.setHome.size)
        assertEquals(0, harness.selected.size)
        assertFalse(harness.dismissed)
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
            string(R.string.action_remove_favorite),
            string(R.string.action_more_actions),
        )
        val buttons = labels.flatMap { label ->
            actionButtons(label).fetchSemanticsNodes()
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

    // ------------------------------------------------- the real repository behind it

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

        openOverflow(0)
        composeRule.onNodeWithText(string(R.string.action_set_home)).performClick()
        composeRule.waitForIdle()

        // Hamburg is the home location and it is also what is displayed: the "currently shown"
        // marker sits above the favourites header, in the home section.
        val shownLabel = string(R.string.locations_currently_shown)
        val favouritesTop = composeRule
            .onNodeWithText(string(R.string.locations_favorites))
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

    /**
     * The star of a history entry moves the place into the favourites through the real rules, so
     * it disappears from the history instead of being listed twice.
     */
    @Test
    fun savingAHistoryEntryMovesItIntoTheFavourites() {
        val initial = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            recentLocations = listOf(dresden, paris),
            selectedLocationId = hamburg.id,
        )
        val store = InMemorySettingsStore(initial)
        renderManagerBackedBy(LocationRepository(store, NoSearchGeocodingApi()), initial)

        actionButtons(string(R.string.action_add_favorite))[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(dresden.name).assertIsDisplayed()
        composeRule.onAllNodesWithText(dresden.name).assertCountEquals(1)

        val stored = runBlocking { store.currentSettings() }
        assertEquals(listOf(berlin.id, dresden.id), stored.favorites.map { it.id })
        assertEquals(listOf(paris.id), stored.recentLocations.map { it.id })
    }

    /** Deleting one history entry through the real rules keeps everything else untouched. */
    @Test
    fun deletingAHistoryEntryThroughTheRepositoryKeepsTheRest() {
        val initial = LocationSettings(
            homeLocation = hamburg,
            favorites = listOf(berlin),
            recentLocations = listOf(dresden, paris),
            selectedLocationId = hamburg.id,
        )
        val store = InMemorySettingsStore(initial)
        renderManagerBackedBy(LocationRepository(store, NoSearchGeocodingApi()), initial)

        actionButtons(string(R.string.action_remove_recent))[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(dresden.name).assertDoesNotExist()
        composeRule.onNodeWithText(paris.name).assertIsDisplayed()

        val stored = runBlocking { store.currentSettings() }
        assertEquals(listOf(paris.id), stored.recentLocations.map { it.id })
        assertEquals(listOf(berlin.id), stored.favorites.map { it.id })
        assertEquals(hamburg.id, stored.selectedLocationId)
    }

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
                    onSelectSearched = { location -> scope.launch { repository.selectLocation(location) } },
                    onToggleFavorite = { location ->
                        scope.launch {
                            if (settings.isFavorite(location.id)) {
                                repository.removeFavorite(location.id)
                            } else {
                                repository.addFavorite(location)
                            }
                        }
                    },
                    onSetHome = { location -> scope.launch { repository.setHomeLocation(location) } },
                    onRemoveRecent = { id -> scope.launch { repository.removeRecentLocation(id) } },
                    onClearRecent = { scope.launch { repository.clearRecentLocations() } },
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
