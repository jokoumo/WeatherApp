package com.example.weatherapp.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * The privacy page. It is a plain screen inside the existing navigation: no dialog, no bottom
 * sheet, no network request, and it has to stay readable in both colour schemes and while
 * scrolling.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun renderPrivacy(onBack: () -> Unit = {}, darkTheme: Boolean = false) {
        composeRule.setContent {
            WeatherAppTheme(darkTheme = darkTheme) {
                PrivacyScreen(onBack = onBack)
            }
        }
        composeRule.waitForIdle()
    }

    private val sectionTitles: List<Int>
        get() = listOf(
            R.string.privacy_section_general,
            R.string.privacy_section_local,
            R.string.privacy_section_requests,
            R.string.privacy_section_search,
            R.string.privacy_section_tracking,
            R.string.privacy_section_backup,
            R.string.privacy_section_provider,
        )

    @Test
    fun headerShowsGermanTitleAndBackAction() {
        renderPrivacy()

        composeRule.onNodeWithText(context.getString(R.string.privacy_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).assertIsDisplayed()
    }

    /** Every topic is its own heading; the page is long, so the lower ones are only scrolled to. */
    @Test
    fun everyPrivacyTopicHasItsOwnSection() {
        renderPrivacy()

        composeRule.onNodeWithText(context.getString(R.string.privacy_intro)).assertIsDisplayed()
        sectionTitles.forEach { titleRes ->
            composeRule.onNodeWithText(context.getString(titleRes)).assertExists()
        }
    }

    @Test
    fun backArrowLeavesThePage() {
        var back = 0
        renderPrivacy(onBack = { back++ })

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, back)
    }

    /** The page scrolls: the link at the end becomes visible after scrolling to it. */
    @Test
    fun linkAtTheEndOfThePageIsReachableByScrolling() {
        renderPrivacy()

        val link = composeRule.onNodeWithText(context.getString(R.string.privacy_provider_link_hint))
        link.assertExists()
        link.performScrollTo()
        composeRule.waitForIdle()
        link.assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.privacy_offline_note)).assertExists()
    }

    /** Nothing here depends on a network: the whole notice is part of the application. */
    @Test
    fun noticeIsCompleteWithoutAnyNetworkCall() {
        renderPrivacy()

        sectionTitles.forEach { titleRes ->
            val title = context.getString(titleRes)
            composeRule.onNodeWithText(title).assertExists()
        }
        composeRule.onNodeWithText(context.getString(R.string.privacy_general_body)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.privacy_provider_body)).assertExists()
    }

    /** Light and dark scheme both render the same content. */
    @Test
    fun pageIsReadableInLightTheme() {
        renderPrivacy(darkTheme = false)

        composeRule.onNodeWithText(context.getString(R.string.privacy_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_section_local)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.privacy_backup_included_title)).assertExists()
    }

    @Test
    fun pageIsReadableInDarkTheme() {
        renderPrivacy(darkTheme = true)

        composeRule.onNodeWithText(context.getString(R.string.privacy_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.privacy_section_local)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.privacy_backup_included_title)).assertExists()
    }

    /**
     * The wording has to match what the code really does: the two hosts that are contacted, the
     * fact that an IP address is visible to them, and the data that is stored on the device.
     */
    @Test
    fun wordingMatchesTheActualImplementation() {
        renderPrivacy()

        val requests = context.getString(R.string.privacy_requests_body)
        assertTrue("the forecast host is named", requests.contains("api.open-meteo.com"))
        assertTrue("the forecast endpoint is named", requests.contains("v1/forecast"))
        assertTrue("the visible IP address is mentioned", requests.contains("IP-Adresse"))
        assertTrue("no API key is claimed", requests.contains("API-Schlüssel"))

        val search = context.getString(R.string.privacy_search_body)
        assertTrue("the geocoding host is named", search.contains("geocoding-api.open-meteo.com"))
        assertTrue("the search is described", search.contains("Ortssuche"))
        assertTrue(
            "the entered search term is named as transmitted data",
            context.resources.getStringArray(R.array.privacy_search_items).any { it.contains("Suchbegriff") },
        )

        val tracking = context.getString(R.string.privacy_tracking_body)
        assertTrue("cookies are denied", tracking.contains("keine Cookies"))
        assertTrue("analytics libraries are denied", tracking.contains("Analyse"))

        val local = context.getString(R.string.privacy_local_body)
        listOf("Startort", "Favoriten", "Reihenfolge", "Darstellung").forEach { term ->
            assertTrue("stored data mentions $term", local.contains(term))
        }

        // The provider notice links to the official terms and privacy page of Open-Meteo.
        assertEquals("https://open-meteo.com/en/terms", AboutContent.PROVIDER_PRIVACY_URL)
    }

    /** The backup section describes the same policy the xml rules implement. */
    @Test
    fun backupSectionMatchesTheDeclaredRules() {
        renderPrivacy()

        val backup = context.getString(R.string.privacy_backup_body)
        assertTrue("the cache is described as excluded", backup.contains("ausgeschlossen"))
        assertTrue("locations stay in the backup", backup.contains("Favoriten"))

        val excluded = context.resources.getStringArray(R.array.privacy_backup_excluded).toList()
        assertTrue("the cache database is named as excluded", excluded.any { it.contains("Datenbankdatei") })

        val included = context.resources.getStringArray(R.array.privacy_backup_included).toList()
        assertTrue("the appearance choice stays in the backup", included.any { it.contains("Darstellung") })
    }
}
