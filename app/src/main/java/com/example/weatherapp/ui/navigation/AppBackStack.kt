package com.example.weatherapp.ui.navigation

import androidx.compose.runtime.saveable.Saver

/**
 * Every full screen the application can show.
 *
 * The dashboard is the root; location management and settings hang directly below it, and
 * appearance, about, a licence detail and the privacy page are sub-pages further down that tree.
 */
enum class AppDestination {
    DASHBOARD,
    LOCATIONS,
    SETTINGS,
    APPEARANCE,
    ABOUT,
    LICENSE,

    /** Privacy information, opened from the information page. */
    PRIVACY,
}

/**
 * Type-safe back stack for the secondary screens.
 *
 * The dashboard is always the bottom entry, so popping can never leave the application and no
 * screen can be pushed twice in a row. A navigation library is not needed for a tree this small:
 * the whole rule set is the two operations below, which keeps the hierarchy testable without any
 * UI.
 */
data class AppBackStack(val destinations: List<AppDestination>) {

    init {
        require(destinations.isNotEmpty()) { "a back stack always holds at least the dashboard" }
        require(destinations.first() == AppDestination.DASHBOARD) {
            "the dashboard is the root of every navigation path"
        }
    }

    val current: AppDestination get() = destinations.last()

    val canGoBack: Boolean get() = destinations.size > 1

    /** Opens [destination] on top of the current screen. */
    fun push(destination: AppDestination): AppBackStack =
        copy(destinations = destinations + destination)

    /** Returns to the parent screen; on the dashboard this is a no-op. */
    fun pop(): AppBackStack =
        if (canGoBack) copy(destinations = destinations.dropLast(1)) else this

    /** Stored form of the stack, used by the [Saver] below and by its tests. */
    fun toStoredNames(): List<String> = destinations.map { it.name }

    companion object {
        /** A fresh stack: the dashboard. */
        fun atDashboard(): AppBackStack = AppBackStack(listOf(AppDestination.DASHBOARD))

        /**
         * Restores a persisted stack. Unknown entries are dropped instead of crashing, and if
         * nothing usable is left the stack falls back to the dashboard.
         */
        fun fromStoredNames(names: List<String?>): AppBackStack {
            val restored = names.mapNotNull { name -> AppDestination.entries.firstOrNull { it.name == name } }
            return when {
                restored.isEmpty() || restored.first() != AppDestination.DASHBOARD -> atDashboard()
                else -> AppBackStack(restored)
            }
        }

        /** Keeps the current screen across configuration changes such as rotation. */
        val Saver: Saver<AppBackStack, List<String>> = Saver(
            save = { backStack -> backStack.toStoredNames() },
            restore = { names -> fromStoredNames(names) },
        )
    }
}
