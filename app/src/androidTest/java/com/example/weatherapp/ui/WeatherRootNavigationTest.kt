package com.example.weatherapp.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.testing.EmptyGeocodingApi
import com.example.weatherapp.testing.InMemorySettingsStore
import com.example.weatherapp.testing.InMemoryThemeStore
import com.example.weatherapp.testing.NoForecastCache
import com.example.weatherapp.testing.OfflineForecastApi
import com.example.weatherapp.ui.settings.AppearanceViewModel
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation through the real root composable: dashboard, location management, settings,
 * appearance, about and a licence detail, with the back arrow and the system back gesture
 * following the same hierarchy. Weather and geocoding are faked, so the test never reaches the
 * network and the number of forecast requests can be inspected.
 */
@RunWith(AndroidJUnit4::class)
class WeatherRootNavigationTest {

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

    private lateinit var forecastApi: OfflineForecastApi
    private lateinit var settingsStore: InMemorySettingsStore
    private lateinit var mainViewModel: MainViewModel
    private lateinit var appearanceViewModel: AppearanceViewModel
    private var systemBack: (() -> Unit)? = null

    @Before
    fun setUp() {
        forecastApi = OfflineForecastApi()
        settingsStore = InMemorySettingsStore(
            LocationSettings(
                homeLocation = hamburg,
                favorites = listOf(berlin),
                selectedLocationId = hamburg.id,
            ),
        )
        mainViewModel = MainViewModel(
            locationRepository = LocationRepository(settingsStore, EmptyGeocodingApi()),
            weatherRepository = WeatherRepository(forecastApi, NoForecastCache(), ForecastMapper()),
        )
        appearanceViewModel = AppearanceViewModel(InMemoryThemeStore())
    }

    private fun renderRoot() {
        composeRule.setContent {
            val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            systemBack = { dispatcher?.onBackPressed() }
            WeatherAppTheme {
                WeatherRoot(viewModel = mainViewModel, appearanceViewModel = appearanceViewModel)
            }
        }
        composeRule.waitForIdle()
    }

    private fun pressSystemBack() {
        val action = systemBack ?: error("back dispatcher not available")
        composeRule.runOnUiThread(action)
        composeRule.waitForIdle()
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_settings)).performClick()
        composeRule.waitForIdle()
    }

    private fun openLocations() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_locations)).performClick()
        composeRule.waitForIdle()
    }

    private fun clickBackArrow() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.waitForIdle()
    }

    private fun dashboardTitle() = composeRule.onNodeWithText(context.getString(R.string.app_name))

    @Test
    fun dashboardCarriesTheApplicationNameInItsToolbar() {
        renderRoot()

        dashboardTitle().assertIsDisplayed()
    }

    @Test
    fun dashboardOpensSettingsAndSettingsReturnsToDashboard() {
        renderRoot()

        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).assertIsDisplayed()

        clickBackArrow()
        dashboardTitle().assertIsDisplayed()
    }

    @Test
    fun settingsOpensAppearanceAsItsOwnScreen() {
        renderRoot()

        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).performClick()
        composeRule.waitForIdle()

        // The appearance page replaced the settings page instead of expanding a card.
        composeRule.onNodeWithText(context.getString(R.string.appearance_intro)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.theme_mode_system)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.theme_mode_light)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.theme_mode_dark)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).assertDoesNotExist()
    }

    @Test
    fun appearanceBackArrowReturnsToSettingsAndNotToTheDashboard() {
        renderRoot()

        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).performClick()
        composeRule.waitForIdle()

        clickBackArrow()

        // Back landed on the settings page, which is still there with both of its entries.
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).assertIsDisplayed()
    }

    @Test
    fun settingsOpensAboutAndItsBackArrowReturnsToSettings() {
        renderRoot()

        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.about_section_sources)).assertIsDisplayed()

        clickBackArrow()

        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).assertIsDisplayed()
    }

    @Test
    fun aboutOpensALicenceDetailThatReturnsToAbout() {
        renderRoot()

        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).performClick()
        composeRule.waitForIdle()

        val licenceRow = composeRule.onNodeWithText(context.getString(R.string.licence_open_meteo_title))
        licenceRow.performScrollTo()
        composeRule.waitForIdle()
        licenceRow.performClick()
        composeRule.waitForIdle()

        // The detail page is long: the offline notice is reached by scrolling, not by chance.
        val offlineNote = composeRule.onNodeWithText(context.getString(R.string.about_license_offline_note))
        offlineNote.performScrollTo()
        composeRule.waitForIdle()
        offlineNote.assertIsDisplayed()

        clickBackArrow()

        val licencesSection = composeRule.onNodeWithText(context.getString(R.string.about_section_licenses))
        licencesSection.performScrollTo()
        composeRule.waitForIdle()
        licencesSection.assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_license_offline_note)).assertDoesNotExist()
    }

    @Test
    fun dashboardOpensLocationManagementAndItsBackArrowReturns() {
        renderRoot()

        openLocations()
        composeRule.onNodeWithText(context.getString(R.string.locations_screen_title)).assertIsDisplayed()

        clickBackArrow()

        dashboardTitle().assertIsDisplayed()
    }

    /**
     * The floating bottom-left button is gone: exactly one back arrow exists on the location
     * screen, and it is the header action.
     */
    @Test
    fun locationManagementHasNoFloatingBackButtonAnymore() {
        renderRoot()

        openLocations()

        composeRule
            .onAllNodesWithContentDescription(context.getString(R.string.action_back))
            .assertCountEquals(1)
    }

    @Test
    fun systemBackFollowsTheSameHierarchyAsTheArrow() {
        renderRoot()

        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).performClick()
        composeRule.waitForIdle()

        pressSystemBack()
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).assertIsDisplayed()

        pressSystemBack()
        dashboardTitle().assertIsDisplayed()
    }

    /**
     * The deepest path of the hierarchy, walked with the system back button only: licence detail
     * returns to about, about to settings, settings to the dashboard.
     */
    @Test
    fun systemBackWalksTheWholeHierarchyOutOfTheSettingsBranch() {
        renderRoot()

        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).performClick()
        composeRule.waitForIdle()
        val licenceRow = composeRule.onNodeWithText(context.getString(R.string.licence_open_meteo_title))
        licenceRow.performScrollTo()
        licenceRow.performClick()
        composeRule.waitForIdle()

        pressSystemBack()
        val licencesSection = composeRule.onNodeWithText(context.getString(R.string.about_section_licenses))
        licencesSection.performScrollTo()
        composeRule.waitForIdle()
        licencesSection.assertIsDisplayed()

        pressSystemBack()
        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).assertIsDisplayed()

        pressSystemBack()
        dashboardTitle().assertIsDisplayed()
    }

    @Test
    fun systemBackFromLocationManagementReturnsToTheDashboard() {
        renderRoot()

        openLocations()
        pressSystemBack()

        dashboardTitle().assertIsDisplayed()
    }

    /**
     * Opening and closing a settings page must not disturb the weather or the saved locations:
     * no new forecast request, no settings write, and the selected location stays the same.
     */
    @Test
    fun visitingSettingsLeavesWeatherAndLocationsUntouched() {
        renderRoot()
        composeRule.waitForIdle()
        val requestsAfterStart = forecastApi.callCount
        val settingsWritesAfterStart = settingsStore.saveCount

        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).performClick()
        composeRule.waitForIdle()
        clickBackArrow()
        openLocations()
        clickBackArrow()
        composeRule.waitForIdle()

        assertEquals("a settings visit triggered forecast requests", requestsAfterStart, forecastApi.callCount)
        assertEquals(
            "a settings visit wrote the location settings",
            settingsWritesAfterStart,
            settingsStore.saveCount,
        )
        assertEquals(hamburg.id, mainViewModel.uiState.value.settings.selectedLocationId)
        assertEquals(hamburg.id, mainViewModel.uiState.value.weather.location?.id)
        assertTrue(mainViewModel.uiState.value.settings.favorites.isNotEmpty())
    }

    /** The privacy page is a screen of its own, entered from the information page. */
    private fun openPrivacyPage() {
        openSettings()
        composeRule.onNodeWithText(context.getString(R.string.settings_about_title)).performClick()
        composeRule.waitForIdle()
        val entry = composeRule.onNodeWithText(context.getString(R.string.about_privacy_title))
        entry.performScrollTo()
        composeRule.waitForIdle()
        entry.performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun aboutOpensThePrivacyPageAndItsBackArrowReturnsToAbout() {
        renderRoot()

        openPrivacyPage()

        composeRule.onNodeWithText(context.getString(R.string.privacy_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_intro)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_section_backup)).assertExists()

        clickBackArrow()

        // Back landed on the information page again, and the privacy text is gone.
        val licencesSection = composeRule.onNodeWithText(context.getString(R.string.about_section_licenses))
        licencesSection.performScrollTo()
        composeRule.waitForIdle()
        licencesSection.assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_intro)).assertDoesNotExist()
    }

    /** System back walks out of the privacy page along the same hierarchy as the arrow. */
    @Test
    fun systemBackLeavesThePrivacyPageStepByStep() {
        renderRoot()

        openPrivacyPage()
        composeRule.onNodeWithText(context.getString(R.string.privacy_screen_title)).assertIsDisplayed()

        pressSystemBack()
        val licencesSection = composeRule.onNodeWithText(context.getString(R.string.about_section_licenses))
        licencesSection.performScrollTo()
        composeRule.waitForIdle()
        licencesSection.assertIsDisplayed()

        pressSystemBack()
        composeRule.onNodeWithText(context.getString(R.string.settings_appearance_title)).assertIsDisplayed()

        pressSystemBack()
        dashboardTitle().assertIsDisplayed()
    }

    /** The privacy page is pure information: it must not load weather or write any setting. */
    @Test
    fun visitingPrivacyLeavesWeatherAndLocationsUntouched() {
        renderRoot()
        composeRule.waitForIdle()
        val requestsAfterStart = forecastApi.callCount
        val settingsWritesAfterStart = settingsStore.saveCount

        openPrivacyPage()
        composeRule.onNodeWithText(context.getString(R.string.privacy_section_provider)).performScrollTo()
        composeRule.waitForIdle()
        clickBackArrow()
        composeRule.waitForIdle()

        assertEquals("the privacy page triggered forecast requests", requestsAfterStart, forecastApi.callCount)
        assertEquals(
            "the privacy page wrote the location settings",
            settingsWritesAfterStart,
            settingsStore.saveCount,
        )
        assertEquals(hamburg.id, mainViewModel.uiState.value.settings.selectedLocationId)
        assertEquals(hamburg.id, mainViewModel.uiState.value.weather.location?.id)
        assertTrue(mainViewModel.uiState.value.settings.favorites.isNotEmpty())
    }
}
