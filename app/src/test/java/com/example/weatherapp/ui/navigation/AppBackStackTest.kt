package com.example.weatherapp.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigation hierarchy of the application as a plain data structure: dashboard at the root,
 * location management and settings below it, appearance and about below settings. These are the
 * transitions the screens and the system back gesture have to follow.
 */
class AppBackStackTest {

    @Test
    fun theApplicationStartsOnTheDashboard() {
        val stack = AppBackStack.atDashboard()

        assertEquals(AppDestination.DASHBOARD, stack.current)
        assertFalse("the dashboard must not offer a back action", stack.canGoBack)
    }

    @Test
    fun dashboardOpensSettingsAndSettingsReturnsToDashboard() {
        val settings = AppBackStack.atDashboard().push(AppDestination.SETTINGS)

        assertEquals(AppDestination.SETTINGS, settings.current)
        assertTrue(settings.canGoBack)
        assertEquals(AppDestination.DASHBOARD, settings.pop().current)
    }

    @Test
    fun dashboardOpensLocationManagementAndItReturnsToDashboard() {
        val locations = AppBackStack.atDashboard().push(AppDestination.LOCATIONS)

        assertEquals(AppDestination.LOCATIONS, locations.current)
        assertEquals(AppDestination.DASHBOARD, locations.pop().current)
    }

    @Test
    fun settingsOpensAppearanceAndAppearanceReturnsToSettings() {
        val appearance = AppBackStack.atDashboard()
            .push(AppDestination.SETTINGS)
            .push(AppDestination.APPEARANCE)

        assertEquals(AppDestination.APPEARANCE, appearance.current)
        assertEquals(AppDestination.SETTINGS, appearance.pop().current)
        assertEquals(AppDestination.DASHBOARD, appearance.pop().pop().current)
    }

    @Test
    fun settingsOpensAboutAndAboutReturnsToSettings() {
        val about = AppBackStack.atDashboard()
            .push(AppDestination.SETTINGS)
            .push(AppDestination.ABOUT)

        assertEquals(AppDestination.ABOUT, about.current)
        assertEquals(AppDestination.SETTINGS, about.pop().current)
    }

    @Test
    fun licenceDetailReturnsToAboutAndNotToSettings() {
        val detail = AppBackStack.atDashboard()
            .push(AppDestination.SETTINGS)
            .push(AppDestination.ABOUT)
            .push(AppDestination.LICENSE)

        assertEquals(AppDestination.ABOUT, detail.pop().current)
    }

    /** The privacy page hangs below the information page, exactly like the licence detail. */
    @Test
    fun privacyPageReturnsToAboutAndNotToSettings() {
        val privacy = AppBackStack.atDashboard()
            .push(AppDestination.SETTINGS)
            .push(AppDestination.ABOUT)
            .push(AppDestination.PRIVACY)

        assertEquals(AppDestination.PRIVACY, privacy.current)
        assertEquals(AppDestination.ABOUT, privacy.pop().current)
        assertEquals(AppDestination.SETTINGS, privacy.pop().pop().current)
        assertEquals(
            listOf("DASHBOARD", "SETTINGS", "ABOUT", "PRIVACY"),
            privacy.toStoredNames(),
        )
        assertEquals(privacy, AppBackStack.fromStoredNames(privacy.toStoredNames()))
    }

    /** Popping on the dashboard is a no-op, so back can never leave the application by accident. */
    @Test
    fun poppingTheDashboardKeepsItAndNeverEmptiesTheStack() {
        val stack = AppBackStack.atDashboard()

        assertEquals(stack, stack.pop())
        assertEquals(stack, stack.pop().pop().pop())
        assertEquals(1, stack.pop().destinations.size)
    }

    /**
     * The saved form of a stack and its restoration: this is what survives a configuration
     * change, so the stored names and the restored stack have to describe the same screen.
     */
    @Test
    fun stackRoundTripsThroughItsStoredNames() {
        val stack = AppBackStack.atDashboard()
            .push(AppDestination.SETTINGS)
            .push(AppDestination.APPEARANCE)

        val restored = AppBackStack.fromStoredNames(stack.toStoredNames())

        assertEquals(stack, restored)
        assertEquals(AppDestination.APPEARANCE, restored.current)
        assertEquals(listOf("DASHBOARD", "SETTINGS", "APPEARANCE"), stack.toStoredNames())
    }

    /**
     * A restored stack with unknown entries — an older version of the app, or a hand edited
     * bundle — has to come back usable instead of crashing.
     */
    @Test
    fun restoringUnknownNamesFallsBackToTheDashboard() {
        assertEquals(AppDestination.DASHBOARD, AppBackStack.fromStoredNames(listOf("NOWHERE")).current)
        assertEquals(AppDestination.DASHBOARD, AppBackStack.fromStoredNames(emptyList()).current)
        assertEquals(AppDestination.DASHBOARD, AppBackStack.fromStoredNames(listOf(null)).current)
        assertEquals(
            "a stack that no longer starts at the dashboard is rejected",
            AppDestination.DASHBOARD,
            AppBackStack.fromStoredNames(listOf("SETTINGS", "ABOUT")).current,
        )
        assertEquals(
            AppDestination.APPEARANCE,
            AppBackStack.fromStoredNames(listOf("DASHBOARD", "SETTINGS", "APPEARANCE", "GHOST")).current,
        )
    }

    @Test
    fun everyPathStaysInsideTheKnownHierarchy() {
        val stack = AppBackStack.atDashboard()
            .push(AppDestination.SETTINGS)
            .push(AppDestination.APPEARANCE)

        assertEquals(listOf(AppDestination.DASHBOARD, AppDestination.SETTINGS, AppDestination.APPEARANCE), stack.destinations)
        assertTrue(stack.canGoBack)
    }
}
