package com.example.weatherapp.domain.model

/**
 * How the application decides between its light and dark colour scheme.
 *
 * [SYSTEM] follows the Android setting, [LIGHT] and [DARK] pin the app to one scheme until the
 * user picks another mode. The stored representation is the enum name, and anything that cannot
 * be read back falls back to [SYSTEM] rather than guessing.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /**
         * Reads a stored value. Unknown, blank or missing values are treated as "never stored"
         * and resolve to [SYSTEM], which is the documented default.
         */
        fun fromStoredValue(value: String?): ThemeMode {
            val candidate = value?.trim()?.uppercase() ?: return SYSTEM
            return entries.firstOrNull { it.name == candidate } ?: SYSTEM
        }
    }
}

/**
 * Whether the dark colour scheme is used for [mode] given the current system setting.
 *
 * A manual choice deliberately ignores the system value, so a device that switches to dark at
 * sunset leaves an app pinned to light exactly as the user asked.
 */
fun ThemeMode.resolveDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
