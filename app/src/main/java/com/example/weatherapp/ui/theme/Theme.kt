package com.example.weatherapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The two shipped colour schemes. They are internal rather than private so that the colour
 * contrast tests can assert against the palette the application really uses.
 */
internal val LightColorScheme = lightColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = SkyBlueOnContainer,
    secondary = TealSecondary,
    onSecondary = Color.White,
    tertiary = SandTertiary,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

internal val DarkColorScheme = darkColorScheme(
    primary = SkyBlueLight,
    onPrimary = Color(0xFF063049),
    primaryContainer = Color(0xFF17456A),
    onPrimaryContainer = Color(0xFFCFE9FF),
    secondary = TealSecondaryLight,
    onSecondary = Color(0xFF11303E),
    tertiary = SandTertiaryLight,
    onTertiary = Color(0xFF33260D),
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

/**
 * App theme following the Android system setting for light and dark mode.
 *
 * Dynamic colours are intentionally not used: the weather palette also carries the header
 * gradients and the condition colours, and both themes need guaranteed contrast.
 */
@Composable
fun WeatherAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWindIndicatorColors provides if (darkTheme) DarkWindIndicatorColors else LightWindIndicatorColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = WeatherTypography,
            shapes = Shapes(),
            content = content,
        )
    }
}

/**
 * Colours of the supplementary wind indicator. Strong wind stays neutral, storm turns red, and
 * both are chosen per theme so that they keep their contrast on the shipped backgrounds.
 */
data class WindIndicatorColors(val strong: Color, val storm: Color)

val LightWindIndicatorColors = WindIndicatorColors(
    strong = OnSurfaceVariantLight,
    storm = StormRedLight,
)

val DarkWindIndicatorColors = WindIndicatorColors(
    strong = OnSurfaceVariantDark,
    storm = StormRedDark,
)

/** Wind indicator palette of the theme that is currently active. */
val LocalWindIndicatorColors = staticCompositionLocalOf { LightWindIndicatorColors }
