package com.example.weatherapp.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.ThemeMode
import com.example.weatherapp.domain.model.resolveDarkTheme
import com.example.weatherapp.ui.theme.WeatherAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The appearance page: three single-choice options, the selected one marked, the choice reported
 * to the caller, and the colour scheme that the stored mode produces.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val systemLabel get() = context.getString(R.string.theme_mode_system)
    private val lightLabel get() = context.getString(R.string.theme_mode_light)
    private val darkLabel get() = context.getString(R.string.theme_mode_dark)

    private fun render(
        selected: ThemeMode = ThemeMode.SYSTEM,
        persistenceFailed: Boolean = false,
        onSelect: (ThemeMode) -> Unit = {},
    ) {
        composeRule.setContent {
            WeatherAppTheme {
                AppearanceScreen(
                    selectedMode = selected,
                    persistenceFailed = persistenceFailed,
                    onSelectMode = onSelect,
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
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
    fun headerUsesTheSharedBackArrowAndTitle() {
        render()

        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.appearance_screen_title)).assertIsDisplayed()
    }

    @Test
    fun allThreeOptionsAreOffered() {
        render()

        composeRule.onNodeWithText(systemLabel).assertIsDisplayed()
        composeRule.onNodeWithText(lightLabel).assertIsDisplayed()
        composeRule.onNodeWithText(darkLabel).assertIsDisplayed()
    }

    @Test
    fun exactlyTheSelectedModeIsMarked() {
        render(selected = ThemeMode.DARK)

        assertEquals(true, rowIsMarkedSelected(darkLabel))
        assertEquals(false, rowIsMarkedSelected(lightLabel))
        assertEquals(false, rowIsMarkedSelected(systemLabel))
    }

    @Test
    fun systemIsMarkedWhenNothingWasChosen() {
        render(selected = ThemeMode.SYSTEM)

        assertEquals(true, rowIsMarkedSelected(systemLabel))
        assertEquals(false, rowIsMarkedSelected(darkLabel))
    }

    @Test
    fun tappingARowReportsExactlyThatMode() {
        val chosen = mutableListOf<ThemeMode>()
        render(onSelect = { chosen += it })

        composeRule.onNodeWithText(lightLabel).performClick()
        composeRule.onNodeWithText(darkLabel).performClick()
        composeRule.onNodeWithText(systemLabel).performClick()

        assertEquals(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM), chosen)
    }

    @Test
    fun aFailedWriteIsReportedOnThePage() {
        render(persistenceFailed = true)

        composeRule.onNodeWithText(context.getString(R.string.theme_persistence_failed)).assertIsDisplayed()
    }

    @Test
    fun noWarningIsShownWhenStoringWorked() {
        render()

        composeRule.onNodeWithText(context.getString(R.string.theme_persistence_failed)).assertDoesNotExist()
    }

    /**
     * The stored mode, not the system setting, decides which palette the whole application draws:
     * a pinned light scheme stays light even while Android is in dark mode, and a pinned dark
     * scheme stays dark while Android is light.
     */
    @Test
    fun storedModeDecidesTheColourScheme() {
        val backgrounds = mutableListOf<Color>()
        composeRule.setContent {
            WeatherAppTheme(darkTheme = ThemeMode.LIGHT.resolveDarkTheme(systemInDarkTheme = true)) {
                backgrounds += MaterialTheme.colorScheme.background
            }
            WeatherAppTheme(darkTheme = ThemeMode.SYSTEM.resolveDarkTheme(systemInDarkTheme = false)) {
                backgrounds += MaterialTheme.colorScheme.background
            }
            WeatherAppTheme(darkTheme = ThemeMode.DARK.resolveDarkTheme(systemInDarkTheme = false)) {
                backgrounds += MaterialTheme.colorScheme.background
            }
            WeatherAppTheme(darkTheme = ThemeMode.SYSTEM.resolveDarkTheme(systemInDarkTheme = true)) {
                backgrounds += MaterialTheme.colorScheme.background
            }
        }
        composeRule.waitForIdle()

        assertEquals(4, backgrounds.size)
        val lightForced = backgrounds[0]
        val lightSystem = backgrounds[1]
        val darkForced = backgrounds[2]
        val darkSystem = backgrounds[3]

        assertEquals("LIGHT must match a light system", lightSystem, lightForced)
        assertEquals("DARK must match a dark system", darkSystem, darkForced)
        assertNotEquals("LIGHT and DARK must differ", lightForced, darkForced)
    }
}
