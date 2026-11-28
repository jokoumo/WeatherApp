package com.example.weatherapp.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.SearchUiState
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The configuration screen every widget shows when it is added.
 *
 * What matters here is the decision the user makes once and never sees again: which location the
 * widget instance belongs to. The screen has to offer all three modes with an explanation, gate the
 * Save button on a complete choice, and give feedback when a place is missing - all of it in German,
 * with the widgets' own strings.
 */
@RunWith(AndroidJUnit4::class)
class WidgetConfigurationScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val homeLabel get() = context.getString(R.string.widget_mode_home)
    private val selectedLabel get() = context.getString(R.string.widget_mode_selected)
    private val fixedLabel get() = context.getString(R.string.widget_mode_fixed)
    private val saveLabel get() = context.getString(R.string.action_save)
    private val cancelLabel get() = context.getString(R.string.action_cancel)

    private val hamburg = SavedLocation("2911298", "Hamburg", 53.55, 9.99, timeZoneId = "Europe/Berlin")
    private val tokyo = SavedLocation("1850144", "Tokio", 35.69, 139.69, timeZoneId = "Asia/Tokyo")

    private var backRequests = 0
    private val modeChoices = mutableListOf<WidgetLocationMode>()
    private val pickedLocations = mutableListOf<SavedLocation>()
    private var saveRequests = 0

    private fun render(
        mode: WidgetLocationMode = WidgetLocationMode.HOME,
        fixedLocation: SavedLocation? = null,
        storedLocations: List<SavedLocation> = listOf(hamburg, tokyo),
        hasHomeLocation: Boolean = true,
        hasSelectedLocation: Boolean = true,
        search: SearchUiState = SearchUiState(),
        saveFailed: Boolean = false,
    ) {
        backRequests = 0
        modeChoices.clear()
        pickedLocations.clear()
        saveRequests = 0
        composeRule.setContent {
            WeatherAppTheme {
                WidgetConfigurationScreen(
                    state = WidgetConfigurationUiState(
                        mode = mode,
                        fixedLocation = fixedLocation,
                        storedLocations = storedLocations,
                        hasHomeLocation = hasHomeLocation,
                        hasSelectedLocation = hasSelectedLocation,
                        search = search,
                        saveFailed = saveFailed,
                    ),
                    onBack = { backRequests++ },
                    onModeSelected = { modeChoices += it },
                    onQueryChange = {},
                    onLocationPicked = { pickedLocations += it },
                    onSave = { saveRequests++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun everyModeIsOfferedWithTheSentenceThatExplainsIt() {
        render()

        composeRule.onNodeWithText(context.getString(R.string.widget_configure_title)).assertIsDisplayed()
        composeRule.onNodeWithText(homeLabel).assertIsDisplayed()
        composeRule.onNodeWithText(selectedLabel).assertIsDisplayed()
        composeRule.onNodeWithText(fixedLabel).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widget_mode_fixed_description)).assertIsDisplayed()
    }

    /**
     * True when the row of [label] carries the selection marker. The selectable row merges its
     * descendants, so in the merged tree the row itself holds both the text and the state.
     */
    private fun rowIsMarkedSelected(label: String): Boolean =
        composeRule
            .onAllNodes(isSelected().and(hasText(label)))
            .fetchSemanticsNodes()
            .isNotEmpty()

    @Test
    fun exactlyTheCurrentModeCarriesTheSelectionMarker() {
        render(mode = WidgetLocationMode.SELECTED)

        assertTrue(rowIsMarkedSelected(selectedLabel))
        assertFalse(rowIsMarkedSelected(homeLabel))
        assertFalse(rowIsMarkedSelected(fixedLabel))
    }

    @Test
    fun choosingAModeIsReportedImmediately() {
        render()

        composeRule.onNodeWithText(fixedLabel).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(WidgetLocationMode.FIXED), modeChoices)
    }

    @Test
    fun theFixedModeAsksForAPlaceAndOffersTheStoredOnes() {
        render(mode = WidgetLocationMode.FIXED)

        composeRule.onNodeWithText(hamburg.name).assertIsDisplayed()
        composeRule.onNodeWithText(tokyo.name).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widget_configure_no_fixed_location)).assertIsDisplayed()
        composeRule.onNodeWithText(saveLabel).assertIsNotEnabled()
    }

    @Test
    fun pickingAStoredPlaceNamesItAndMakesSavingPossible() {
        render(mode = WidgetLocationMode.FIXED)

        composeRule.onNodeWithText(tokyo.name).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(tokyo), pickedLocations)
    }

    @Test
    fun aFixedWidgetWithAPlaceCanBeSaved() {
        render(mode = WidgetLocationMode.FIXED, fixedLocation = tokyo)

        composeRule.onNodeWithText(context.getString(R.string.widget_configure_no_fixed_location))
            .assertDoesNotExist()
        composeRule.onNodeWithText(saveLabel).assertIsEnabled()

        composeRule.onNodeWithText(saveLabel).performClick()

        assertEquals(1, saveRequests)
    }

    @Test
    fun aHomeWidgetWithoutAHomeLocationExplainsWhySavingIsUnavailable() {
        render(mode = WidgetLocationMode.HOME, hasHomeLocation = false)

        composeRule.onNodeWithText(context.getString(R.string.widget_configure_no_home)).assertIsDisplayed()
        composeRule.onNodeWithText(saveLabel).assertIsNotEnabled()
    }

    @Test
    fun missingSelectedLocationPreventsSaving() {
        render(mode = WidgetLocationMode.SELECTED, hasHomeLocation = false, hasSelectedLocation = false)

        composeRule.onNodeWithText(context.getString(R.string.widget_configure_no_selected)).assertIsDisplayed()
        composeRule.onNodeWithText(saveLabel).assertIsNotEnabled()
    }

    @Test
    fun selectedLocationCanBeUsedWithoutHome() {
        render(mode = WidgetLocationMode.SELECTED, hasHomeLocation = false)
        composeRule.onNodeWithText(saveLabel).assertIsEnabled()
    }

    @Test
    fun fixedLocationMustHaveValidCoordinates() {
        render(mode = WidgetLocationMode.FIXED, fixedLocation = tokyo.copy(latitude = 120.0))
        composeRule.onNodeWithText(saveLabel).assertIsNotEnabled()
    }

    @Test
    fun actionsStayVisibleWhileScrollingToTheEndOfSearchResults() {
        val locations = (1..30).map { tokyo.copy(id = "result-$it", name = "Suchergebnis $it") }
        render(
            mode = WidgetLocationMode.FIXED,
            fixedLocation = hamburg,
            search = SearchUiState(query = "Tokio", results = locations),
        )
        composeRule.onNodeWithText(saveLabel).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(locations.last().name).performScrollTo().performClick()
        composeRule.onNodeWithText(saveLabel).assertIsDisplayed()
        composeRule.onNodeWithText(cancelLabel).assertIsDisplayed()
        assertEquals(listOf(locations.last()), pickedLocations)
    }

    @Test
    fun aSaveThatDidNotReachStorageIsReported() {
        render(mode = WidgetLocationMode.SELECTED, saveFailed = true)

        composeRule.onNodeWithText(context.getString(R.string.widget_configure_failed)).assertIsDisplayed()
    }

    @Test
    fun cancellingClosesTheScreenWithoutWritingAnything() {
        render(mode = WidgetLocationMode.SELECTED)

        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.waitForIdle()

        assertEquals(1, backRequests)
        assertTrue("cancel must not save", saveRequests == 0)
    }
}
