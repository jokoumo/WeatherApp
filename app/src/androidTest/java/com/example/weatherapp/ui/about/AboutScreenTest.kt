package com.example.weatherapp.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The information page: the version of the installed package rather than a string written into the
 * layout, the weather services behind the data, and the licences that come with them.
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun renderAbout(onOpenLicence: (String) -> Unit = {}) {
        composeRule.setContent {
            WeatherAppTheme {
                AboutScreen(onBack = {}, onOpenLicence = onOpenLicence)
            }
        }
        composeRule.waitForIdle()
    }

    /** The launcher name and the system application information both read this label. */
    @Test
    fun installedApplicationIsNamedWetterPlus() {
        val label = context.packageManager
            .getApplicationInfo(context.packageName, 0)
            .loadLabel(context.packageManager)
            .toString()

        assertEquals("WetterPlus", label)
        assertEquals(context.getString(R.string.app_name), label)
    }

    @Test
    fun versionAndBuildComeFromTheInstalledPackage() {
        renderAbout()

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = requireNotNull(packageInfo.versionName)

        composeRule.onNodeWithText(context.getString(R.string.about_section_application)).assertIsDisplayed()
        composeRule.onNodeWithText("WetterPlus").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_version_label)).assertIsDisplayed()
        composeRule.onNodeWithText(versionName).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_build_label)).assertIsDisplayed()
        composeRule.onNodeWithText(packageInfo.longVersionCode.toString()).assertIsDisplayed()
        // A readable fallback is used instead of an empty or invented value.
        composeRule.onNodeWithText(context.getString(R.string.about_value_unknown)).assertDoesNotExist()
    }

    @Test
    fun weatherSourcesAndLicenceSectionsArePresent() {
        renderAbout()

        composeRule.onNodeWithText(context.getString(R.string.about_section_sources)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_source_forecast_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_source_geocoding_title)).assertIsDisplayed()
        // Further down the scrolling page.
        composeRule.onNodeWithText(context.getString(R.string.about_source_models_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.about_source_geonames_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.about_section_licenses)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.about_data_attribution)).assertExists()
    }

    /**
     * The attribution Open-Meteo asks for, and the model wording that does not claim one fixed
     * model: the app shows that the choice depends on the location.
     */
    @Test
    fun attributionAndModelWordingAreHonest() {
        renderAbout()

        val modelText = context.getString(R.string.about_source_models_summary)
        assertTrue("the model text must not promise a single model", modelText.contains("hängt"))
        assertTrue("the automatic selection is named", modelText.contains("best_match"))
        composeRule.onNodeWithText(modelText).assertExists()
    }

    @Test
    fun everyLicenceRowIsListedAndOpensItsOwnPage() {
        val opened = mutableListOf<String>()
        renderAbout(onOpenLicence = { opened += it })

        // The page scrolls, so off-screen rows are checked for existence rather than visibility.
        AboutContent.licences.forEach { entry ->
            composeRule.onNodeWithText(context.getString(entry.titleRes)).assertExists()
        }

        val licenceRow = composeRule.onNodeWithText(context.getString(R.string.licence_open_meteo_title))
        licenceRow.performScrollTo()
        composeRule.waitForIdle()
        licenceRow.performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("open-meteo-data"), opened)
    }

    /**
     * A licence page has to be readable on a phone without a network: author, licence identifier
     * and the German summary are part of the application, only the full text is a browser link.
     */
    @Test
    fun licenceDetailCarriesItsNoticeOffline() {
        val entry = requireNotNull(AboutContent.licenceById("open-meteo-data"))

        composeRule.setContent {
            WeatherAppTheme {
                LicenceDetailScreen(entry = entry, onBack = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(entry.titleRes)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_license_name, context.getString(entry.licenceNameRes)))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_license_holder, entry.holder)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(entry.summaryRes)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_license_offline_note)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.about_license_link_hint)).assertIsDisplayed()
    }

    /** The library page names the components it covers, so the attribution can be checked. */
    @Test
    fun libraryLicenceListsItsComponents() {
        val entry = requireNotNull(AboutContent.licenceById("apache-libraries"))

        composeRule.setContent {
            WeatherAppTheme {
                LicenceDetailScreen(entry = entry, onBack = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.about_license_components_title)).assertIsDisplayed()
        // The list is long and the page scrolls: the first entries are checked visibly, the rest
        // only for existence.
        composeRule.onNodeWithText(entry.components.first()).assertIsDisplayed()
        entry.components.forEach { component ->
            composeRule.onNodeWithText(component).assertExists()
        }
    }
}
