package com.example.weatherapp.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette: calm sky blues with a warm accent, tuned for both themes.
val SkyBlue = Color(0xFF1B6FA8)
val SkyBlueLight = Color(0xFF8DC8F3)
val SkyBlueContainer = Color(0xFFCDE7FA)
val SkyBlueOnContainer = Color(0xFF062A40)
val TealSecondary = Color(0xFF4C7286)
val TealSecondaryLight = Color(0xFFA9CFE0)
val SandTertiary = Color(0xFF6F5828)
val SandTertiaryLight = Color(0xFFE3C89A)

val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundLight = Color(0xFFF1F5F9)
val SurfaceVariantLight = Color(0xFFE1E9F1)
val OnSurfaceLight = Color(0xFF10202C)
val OnSurfaceVariantLight = Color(0xFF465563)
val OutlineLight = Color(0xFFB6C3CF)

val SurfaceDark = Color(0xFF131C25)
val BackgroundDark = Color(0xFF0C131A)
val SurfaceVariantDark = Color(0xFF1E2A36)
val OnSurfaceDark = Color(0xFFE4EAF1)
val OnSurfaceVariantDark = Color(0xFFB4C2CF)
val OutlineDark = Color(0xFF3A4A58)

/** Text colour used on top of the coloured header gradient. */
val OnHeader = Color(0xFFFFFFFF)
val OnHeaderMuted = Color(0xCCFFFFFF)

/**
 * Colour of the storm wind indicator. It is deliberately a clear red in both themes rather than
 * the scheme's error colour, whose dark variant is a pale red that would not read as a warning.
 * The neutral strong-wind indicator uses [OnSurfaceVariantLight] and [OnSurfaceVariantDark].
 */
val StormRedLight = Color(0xFFC62828)
val StormRedDark = Color(0xFFEF5350)

/**
 * Header gradients. The colours sit in the mid range so that white text keeps enough
 * contrast in the light and in the dark theme.
 */
val HeaderClearDay = Pair(Color(0xFF1B6FA8), Color(0xFF3E91CC))
val HeaderClearNight = Pair(Color(0xFF22304F), Color(0xFF3C4E77))
val HeaderCloudy = Pair(Color(0xFF4A5C6C), Color(0xFF6E8296))
val HeaderRain = Pair(Color(0xFF2F5670), Color(0xFF4E7A97))
val HeaderSnow = Pair(Color(0xFF3F6580), Color(0xFF6C93AC))
val HeaderThunder = Pair(Color(0xFF453A66), Color(0xFF6A5C92))
val HeaderFog = Pair(Color(0xFF53606B), Color(0xFF7A8794))
