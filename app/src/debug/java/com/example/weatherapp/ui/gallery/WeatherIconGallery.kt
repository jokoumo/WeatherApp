package com.example.weatherapp.ui.gallery

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.weatherapp.domain.model.PrecipitationClassifier
import com.example.weatherapp.domain.model.PrecipitationStyle
import com.example.weatherapp.domain.model.WeatherCondition
import com.example.weatherapp.domain.model.WindWarning
import com.example.weatherapp.domain.model.WindWarningCause
import com.example.weatherapp.domain.model.WindWarningLevel
import com.example.weatherapp.ui.components.WeatherIcon
import com.example.weatherapp.ui.theme.HeaderCloudy
import com.example.weatherapp.ui.theme.HeaderRain
import com.example.weatherapp.ui.theme.HeaderWindIndicatorColors
import com.example.weatherapp.ui.theme.LocalWindIndicatorColors
import com.example.weatherapp.ui.theme.OnHeader
import com.example.weatherapp.ui.theme.OnSurfaceDark
import com.example.weatherapp.ui.theme.OnSurfaceLight
import com.example.weatherapp.ui.theme.SurfaceDark
import com.example.weatherapp.ui.theme.SurfaceLight
import com.example.weatherapp.ui.theme.WindIndicatorColors
import com.example.weatherapp.ui.theme.WeatherAppTheme
import com.example.weatherapp.ui.weather.descriptionRes

/**
 * Development-only overview of the complete Flat Color weather icon family.
 *
 * It lives in the debug source set, so it is neither part of the release binary nor part of the
 * production navigation. Every symbol is shown at the sizes the application really uses (24 and
 * 30 dp in the compact rows, 44 dp in the day card, 46 dp in the hourly detail sheet, 76 dp in
 * the hero card) and on the light card, the dark card and the header gradient, which is what
 * makes proportion, visual weight and colour contrast comparable across the family.
 */

/** One row of the gallery: a condition, its classified precipitation style and a wind warning. */
internal data class GalleryEntry(
    val condition: WeatherCondition,
    val style: PrecipitationStyle,
    val windWarning: WindWarning,
)

private fun entry(
    condition: WeatherCondition,
    windWarning: WindWarning = WindWarning.NONE,
): GalleryEntry = GalleryEntry(
    condition = condition,
    style = PrecipitationClassifier.classify(condition),
    windWarning = windWarning,
)

private val strongWind = WindWarning(WindWarningLevel.STRONG, WindWarningCause.SUSTAINED_WIND)
private val stormWind = WindWarning(WindWarningLevel.STORM, WindWarningCause.SUSTAINED_WIND)
private val gustStrong = WindWarning(WindWarningLevel.STRONG, WindWarningCause.GUSTS)
private val gustStorm = WindWarning(WindWarningLevel.STORM, WindWarningCause.GUSTS)

/**
 * The whole family: one row per weather condition, so a new condition can never be left out of
 * the review, followed by the rows that combine a symbol with the wind indicator.
 */
internal val galleryEntries: List<GalleryEntry> =
    WeatherCondition.entries.map { entry(it) } + listOf(
        entry(WeatherCondition.CLEAR_DAY, strongWind),
        entry(WeatherCondition.PARTLY_CLOUDY_NIGHT, gustStrong),
        entry(WeatherCondition.RAIN, strongWind),
        entry(WeatherCondition.HEAVY_RAIN, stormWind),
        entry(WeatherCondition.HEAVY_SNOW, gustStorm),
        entry(WeatherCondition.FOG, stormWind),
        entry(WeatherCondition.OVERCAST, strongWind),
    )

/** The four comparison sizes of the gallery, from the smallest row to the hero card. */
internal val gallerySizes: List<Dp> = listOf(24.dp, 32.dp, 48.dp, 72.dp)

private val galleryLabelWidth = 148.dp
private val galleryGap = 12.dp

/** How far one review page of the gallery advances. */
private const val PAGE_STEP_DP = 700

/** One background the family has to work on: a card surface or a header gradient. */
internal data class GallerySectionSpec(
    val title: String,
    val background: Brush,
    val labelColor: Color,
    val windIndicatorColors: WindIndicatorColors?,
)

internal val gallerySections: List<GallerySectionSpec> = listOf(
    GallerySectionSpec("Helle Karte", SolidColor(SurfaceLight), OnSurfaceLight, null),
    GallerySectionSpec("Dunkle Karte", SolidColor(SurfaceDark), OnSurfaceDark, null),
    GallerySectionSpec(
        title = "Kopfverlauf, Regen",
        background = Brush.verticalGradient(listOf(HeaderRain.first, HeaderRain.second)),
        labelColor = OnHeader,
        windIndicatorColors = HeaderWindIndicatorColors,
    ),
    GallerySectionSpec(
        title = "Kopfverlauf, bewölkt",
        background = Brush.verticalGradient(listOf(HeaderCloudy.first, HeaderCloudy.second)),
        labelColor = OnHeader,
        windIndicatorColors = HeaderWindIndicatorColors,
    ),
)

@Composable
fun WeatherIconGallery(
    modifier: Modifier = Modifier,
    page: Int = 0,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // The screenshot helper restarts the screen once per page instead of injecting gestures, so
    // every review image starts at a known offset.
    LaunchedEffect(page, scrollState.maxValue) {
        if (page > 0) {
            val step = with(density) { (page * PAGE_STEP_DP).dp.toPx() }
            scrollState.scrollTo(step.toInt().coerceAtMost(scrollState.maxValue))
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(12.dp),
    ) {
        Text(
            text = "Wettersymbole",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Flat-Color-Familie mit geteilter 48×48-Geometrie. Jede Zeile zeigt dasselbe " +
                "Symbol in 24, 32, 48 und 72 dp.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        gallerySections.forEach { section ->
            Spacer(Modifier.height(14.dp))
            GallerySection(section)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** One background of the gallery, with every entry rendered on it. */
@Composable
private fun GallerySection(section: GallerySectionSpec) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(section.background)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            color = section.labelColor,
        )
        Spacer(Modifier.height(6.dp))
        SizeHeader(section.labelColor)
        galleryEntries.forEach { entry ->
            GalleryRow(
                entry = entry,
                labelColor = section.labelColor,
                windIndicatorColors = section.windIndicatorColors,
            )
        }
    }
}

/** The dp value of every column, so the same symbol can be compared across sizes. */
@Composable
private fun SizeHeader(labelColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Zustand",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier.width(galleryLabelWidth),
        )
        gallerySizes.forEach { size ->
            Box(modifier = Modifier.width(size), contentAlignment = Alignment.Center) {
                Text(
                    text = size.value.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                )
            }
            Spacer(Modifier.width(galleryGap))
        }
    }
}

@Composable
private fun GalleryRow(
    entry: GalleryEntry,
    labelColor: Color,
    windIndicatorColors: WindIndicatorColors?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = stringResource(entry.condition.descriptionRes()),
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 2,
            modifier = Modifier.width(galleryLabelWidth),
        )
        gallerySizes.forEach { size ->
            Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
                WeatherIcon(
                    condition = entry.condition,
                    precipitation = entry.style,
                    windWarning = entry.windWarning,
                    size = size,
                    windIndicatorColors = windIndicatorColors
                        ?: LocalWindIndicatorColors.current,
                )
            }
            Spacer(Modifier.width(galleryGap))
        }
    }
}

@Preview(name = "Icon gallery light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 420, heightDp = 900)
@Composable
private fun WeatherIconGalleryLightPreview() {
    WeatherAppTheme(darkTheme = false) {
        WeatherIconGallery()
    }
}

@Preview(name = "Icon gallery dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 420, heightDp = 900)
@Composable
private fun WeatherIconGalleryDarkPreview() {
    WeatherAppTheme(darkTheme = true) {
        WeatherIconGallery()
    }
}
