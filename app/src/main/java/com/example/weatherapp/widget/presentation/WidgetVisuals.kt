package com.example.weatherapp.widget.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.domain.model.WindWarningLevel
import com.example.weatherapp.ui.weather.labelRes
import com.example.weatherapp.ui.theme.OnSurfaceDark
import com.example.weatherapp.ui.theme.OnSurfaceLight
import com.example.weatherapp.ui.theme.OnSurfaceVariantDark
import com.example.weatherapp.ui.theme.OnSurfaceVariantLight
import com.example.weatherapp.ui.theme.SkyBlue
import com.example.weatherapp.ui.theme.SkyBlueLight
import com.example.weatherapp.ui.theme.StormRedDark
import com.example.weatherapp.ui.theme.StormRedLight
import com.example.weatherapp.ui.theme.SurfaceDark
import com.example.weatherapp.ui.theme.SurfaceVariantDark
import com.example.weatherapp.ui.theme.SurfaceVariantLight
import com.example.weatherapp.ui.theme.WidgetTileLight

/**
 * Colours, corners and text sizes of the widgets.
 *
 * A widget is drawn by the launcher from a `RemoteViews` description, so the app's Compose theme is not
 * there. Colours are given to Glance as **day/night providers**: each one carries the light and the dark
 * value, and Glance resolves the pair against the night mode of the device while it builds the widget.
 * A widget therefore follows the system's light or dark mode on its own, while the in-app appearance
 * setting stays a decision about the app's own screens.
 *
 * The values are the app's own palette rather than a second one: the widgets sit on the same surface
 * variants, name things in the same on-surface colours and warn with the same storm red, so a widget
 * next to the app cannot drift into different shades of the same weather. (Glance also has a
 * `ColorProvider` for a colour resource; it is restricted to the Glance libraries precisely because the
 * launcher process and the app process can disagree about which resource folder applies - the explicit
 * pair is the supported way for an application to do this.)
 *
 * The sizes are deliberately smaller than in the app: a widget shares its screen with a wallpaper and is
 * read from a distance, so one compact line beats two decorative ones.
 */
object WidgetPalette {
    val background = ColorProvider(SurfaceVariantLight, SurfaceDark)
    val tile = ColorProvider(WidgetTileLight, SurfaceVariantDark)
    val onSurface = ColorProvider(OnSurfaceLight, OnSurfaceDark)
    val muted = ColorProvider(OnSurfaceVariantLight, OnSurfaceVariantDark)
    val accent = ColorProvider(SkyBlue, SkyBlueLight)

    /**
     * Storm marker. The neutral strong-wind marker uses [muted], exactly as the app uses its own
     * on-surface-variant there; only the storm level is coloured, and its meaning is spelled out by the
     * wind glyph's content description rather than by colour alone.
     */
    val windStorm = ColorProvider(StormRedLight, StormRedDark)

    /** Corner radius of the widget surface and of the tiles inside it. */
    val outerCornerRadius: Dp = 20.dp
    val tileCornerRadius: Dp = 10.dp

    /** Surface padding, kept large enough that no text touches the rounded corner. */
    val outerPadding: Dp = 8.dp

    /** Typography, in sp so the system font scale is respected. */
    val headlineSize: TextUnit = 14.sp
    val bodySize: TextUnit = 13.sp
    val labelSize: TextUnit = 10.sp
    val footnoteSize: TextUnit = 10.sp

    /** Location name of the header: the one text a widget always has room for. */
    fun headerTitleSize(sizeClass: WidgetSizeClass): TextUnit = when (sizeClass) {
        WidgetSizeClass.COMPACT -> 14.sp
        WidgetSizeClass.EXPANDED -> 16.sp
    }

    /** Value text of a row or a tile. */
    fun bodySizeFor(sizeClass: WidgetSizeClass): TextUnit = when (sizeClass) {
        WidgetSizeClass.COMPACT -> 12.sp
        WidgetSizeClass.EXPANDED -> 13.sp
    }

    /** Small secondary text: dates, time ranges, the update line. */
    fun secondarySizeFor(sizeClass: WidgetSizeClass): TextUnit = when (sizeClass) {
        WidgetSizeClass.COMPACT -> 11.sp
        WidgetSizeClass.EXPANDED -> 11.sp
    }

    fun temperatureSize(sizeClass: WidgetSizeClass): TextUnit = when (sizeClass) {
        WidgetSizeClass.COMPACT -> 29.sp
        WidgetSizeClass.EXPANDED -> 34.sp
    }

    /** Large symbol of the current weather widget. */
    fun iconSize(sizeClass: WidgetSizeClass): Dp = when (sizeClass) {
        WidgetSizeClass.COMPACT -> 38.dp
        WidgetSizeClass.EXPANDED -> 56.dp
    }

    /** Symbol inside a row of the period and three-day layouts. */
    fun rowIconSize(sizeClass: WidgetSizeClass): Dp = when (sizeClass) {
        WidgetSizeClass.COMPACT -> 22.dp
        WidgetSizeClass.EXPANDED -> 28.dp
    }
}

/**
 * German text of a string resource, resolved inside the widget composition.
 *
 * Glance has no `stringResource`; the context it composes in belongs to this application, which is
 * exactly where the app's German resources live. Every word a widget shows still comes from
 * `strings.xml`.
 */
@Composable
fun widgetString(@StringRes resId: Int, vararg formatArgs: Any): String =
    LocalContext.current.getString(resId, *formatArgs)

/** The rounded, theme-aware surface every widget is drawn on, with the tap target of the whole widget. */
@Composable
fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WidgetPalette.background)
            .appWidgetBackground()
            .cornerRadius(WidgetPalette.outerCornerRadius)
            .padding(WidgetPalette.outerPadding),
        content = content,
    )
}

/**
 * Location name plus the subtle "last updated" line: the identity of a widget, always visible.
 *
 * The age is the moment the app last *retrieved* the forecast, not the moment the launcher redrew the
 * widget. Once that moment is older than the cache policy allows, the same line says so in words -
 * [data_may_be_outdated] with the age next to it - because a widget cannot show a spinner and a
 * widget that looks fresh while it is not would be worse than one that admits it.
 */
@Composable
fun WidgetHeader(
    locationName: String,
    updatedAgo: String?,
    sizeClass: WidgetSizeClass,
    stale: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = locationName,
            maxLines = 1,
            style = TextStyle(
                color = WidgetPalette.accent,
                fontSize = WidgetPalette.headerTitleSize(sizeClass),
                fontWeight = FontWeight.Bold,
            ),
        )
        updatedAgo?.let {
            Text(
                text = widgetString(
                    if (stale) R.string.widget_last_updated_stale else R.string.widget_last_updated,
                    it,
                ),
                maxLines = 2,
                style = TextStyle(
                    color = WidgetPalette.muted,
                    fontSize = WidgetPalette.secondarySizeFor(sizeClass),
                ),
            )
        }
    }
}

/**
 * The short sentence a widget shows when it has no values: loading, no data, or no location.
 *
 * The name of the place is drawn next to it by the caller, so a widget never shows an empty surface
 * and never shows weather that belongs to another location. While [state] is [WidgetState.READY]
 * nothing is drawn, because a working widget has no reason to explain itself.
 *
 * Callers pass a weighted modifier from their `Column`, which gives the sentence the space the values
 * would have used: a widget that has nothing to show still fills its cell instead of leaving a hole
 * under the header, and the sentence sits in the middle of that space.
 */
@Composable
fun WidgetStateMessage(
    state: WidgetState,
    sizeClass: WidgetSizeClass,
    modifier: GlanceModifier = GlanceModifier,
) {
    state.messageResOrNull()?.let { resId ->
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = widgetString(resId),
                maxLines = 3,
                style = TextStyle(
                    color = WidgetPalette.muted,
                    fontSize = WidgetPalette.bodySizeFor(sizeClass),
                ),
            )
        }
    }
}

/** German sentence for a widget state, or null while the widget has data to show. */
@StringRes
fun WidgetState.messageResOrNull(): Int? = when (this) {
    WidgetState.READY -> null
    WidgetState.LOADING -> R.string.loading_weather
    WidgetState.NO_DATA -> R.string.widget_no_data
    WidgetState.NO_LOCATION -> R.string.widget_no_location
}

/**
 * Weather symbol with the supplementary wind marker.
 *
 * The same rule as in the app: the symbol itself is never replaced, the wind glyph only sits in the
 * free corner of the same footprint, and its colour says whether the wind is merely strong or a
 * storm. The condition stays readable either way, which is why the marker never becomes a disc.
 */
@Composable
fun WidgetWeatherSymbol(
    iconRes: Int,
    windWarning: WindWarning,
    size: Dp,
    contentDescription: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.TopEnd) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(size),
        )
        if (windWarning.isWarning) {
            Image(
                provider = ImageProvider(R.drawable.weather_wind),
                contentDescription = widgetString(windWarning.labelRes()),
                colorFilter = ColorFilter.tint(
                    if (windWarning.level == WindWarningLevel.STORM) {
                        WidgetPalette.windStorm
                    } else {
                        WidgetPalette.muted
                    },
                ),
                modifier = GlanceModifier.size((size.value * WIND_MARKER_RATIO).dp.coerceAtLeast(9.dp)),
            )
        }
    }
}

/** Share of the symbol footprint the wind glyph may occupy - as small as in the app's icon family. */
private const val WIND_MARKER_RATIO = 0.34f

/**
 * One information tile: label above value, on its own rounded surface.
 *
 * The caller passes the weight it wants inside its row, because Glance only lets a child ask for a
 * share of the row's width from the row's own scope.
 */
@Composable
fun WidgetTile(tile: WidgetMetricTile, sizeClass: WidgetSizeClass, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier
            .background(WidgetPalette.tile)
            .cornerRadius(WidgetPalette.tileCornerRadius)
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Text(
            text = widgetString(tile.labelRes),
            maxLines = 2,
            style = TextStyle(
                color = WidgetPalette.muted,
                fontSize = WidgetPalette.secondarySizeFor(sizeClass),
            ),
        )
        Text(
            text = tile.value,
            maxLines = if (tile.metric == WidgetMetric.TODAY_RANGE) 2 else 1,
            style = TextStyle(
                color = WidgetPalette.onSurface,
                fontSize = WidgetPalette.bodySizeFor(sizeClass),
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
