package com.example.weatherapp.ui.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import com.example.weatherapp.R

/**
 * Version information as it is actually installed, never as a string written into the layout.
 *
 * A package that cannot be read yields [AppVersionInfo] with the fallback text instead of
 * crashing, so the About screen always renders.
 */
data class AppVersionInfo(
    val applicationName: String,
    val versionName: String,
    val buildNumber: String,
)

/** Reads name, version name and build number from the installed package. */
fun appVersionInfo(context: Context): AppVersionInfo {
    val packageManager = context.packageManager
    val packageName = context.packageName
    val unknown = context.getString(R.string.about_value_unknown)
    val applicationName = runCatching {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    }.getOrDefault(unknown)

    return try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        AppVersionInfo(
            applicationName = applicationName,
            versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: unknown,
            buildNumber = packageInfo.longVersionCode.toString(),
        )
    } catch (nameNotFound: PackageManager.NameNotFoundException) {
        AppVersionInfo(applicationName = applicationName, versionName = unknown, buildNumber = unknown)
    }
}

/** A weather data source or service shown on the About screen, with its official website. */
data class SourceEntry(
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val url: String,
)

/**
 * One licence or attribution entry. The summary is a short German text that is part of the
 * application, so the required notice stays readable offline; the full licence text is opened
 * in the browser through [url].
 */
data class LicenceEntry(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    @param:StringRes val licenceNameRes: Int,
    val holder: String,
    val url: String,
    val components: List<String> = emptyList(),
)

/**
 * The content of the About screen. It is a plain list rather than a layout so that a unit test
 * can check every link and licence, and so the screen and the licence detail page read from the
 * same source.
 */
object AboutContent {

    /** Attribution link that has to sit next to displayed Open-Meteo data. */
    const val ATTRIBUTION_URL = "https://open-meteo.com/"

    /**
     * Official terms and privacy notice of the weather service. Open-Meteo publishes both on one
     * page; there is no separate /privacy address. The privacy page of the app links here because
     * only the operator can describe how it handles the requests it receives.
     */
    const val PROVIDER_PRIVACY_URL = "https://open-meteo.com/en/terms"

    val sources: List<SourceEntry> = listOf(
        SourceEntry(
            titleRes = R.string.about_source_forecast_title,
            summaryRes = R.string.about_source_forecast_summary,
            url = "https://open-meteo.com/en/docs",
        ),
        SourceEntry(
            titleRes = R.string.about_source_geocoding_title,
            summaryRes = R.string.about_source_geocoding_summary,
            url = "https://open-meteo.com/en/docs/geocoding-api",
        ),
        SourceEntry(
            titleRes = R.string.about_source_models_title,
            summaryRes = R.string.about_source_models_summary,
            url = "https://open-meteo.com/en/docs",
        ),
        SourceEntry(
            titleRes = R.string.about_source_geonames_title,
            summaryRes = R.string.about_source_geonames_summary,
            url = "https://www.geonames.org/",
        ),
    )

    /**
     * Licences of everything the shipped application contains: the data it displays, the
     * libraries it is built on and the artwork it ships. Library names are the artifacts that
     * are actually on the runtime classpath.
     */
    val licences: List<LicenceEntry> = listOf(
        LicenceEntry(
            id = "open-meteo-data",
            titleRes = R.string.licence_open_meteo_title,
            summaryRes = R.string.licence_open_meteo_summary,
            licenceNameRes = R.string.licence_open_meteo_name,
            holder = "Open-Meteo.com",
            url = "https://open-meteo.com/en/licence",
        ),
        LicenceEntry(
            id = "geonames-data",
            titleRes = R.string.licence_geonames_title,
            summaryRes = R.string.licence_geonames_summary,
            licenceNameRes = R.string.licence_geonames_name,
            holder = "GeoNames",
            url = "https://www.geonames.org/",
        ),
        LicenceEntry(
            id = "apache-libraries",
            titleRes = R.string.licence_libraries_title,
            summaryRes = R.string.licence_libraries_summary,
            licenceNameRes = R.string.licence_libraries_name,
            holder = "Google LLC, JetBrains s.r.o., Square, Inc. und Mitwirkende",
            url = "https://www.apache.org/licenses/LICENSE-2.0",
            components = listOf(
                "Jetpack Compose (BOM 2026.02.01)",
                "AndroidX Activity, Core KTX, Lifecycle, SavedState",
                "AndroidX DataStore",
                "AndroidX Room und SQLite",
                "AndroidX Material Icons",
                "Kotlin Standard Library",
                "kotlinx.coroutines",
                "kotlinx.serialization",
                "Retrofit und converter-kotlinx-serialization",
                "OkHttp und Okio",
                "JSpecify",
            ),
        ),
        LicenceEntry(
            id = "protobuf",
            titleRes = R.string.licence_protobuf_title,
            summaryRes = R.string.licence_protobuf_summary,
            licenceNameRes = R.string.licence_protobuf_name,
            holder = "Google Inc.",
            url = "https://github.com/protocolbuffers/protobuf",
            components = listOf("androidx.datastore:datastore-preferences-external-protobuf"),
        ),
        LicenceEntry(
            id = "project-artwork",
            titleRes = R.string.licence_icons_title,
            summaryRes = R.string.licence_icons_summary,
            licenceNameRes = R.string.licence_icons_name,
            holder = "dieses Projekt",
            url = "",
            components = listOf(
                "19 Wettersymbole (weather_*.xml)",
                "Startsymbol der App (ic_launcher_*.xml)",
                "Symbol für die Darstellungseinstellung (ic_appearance.xml)",
            ),
        ),
    )

    fun licenceById(id: String?): LicenceEntry? = licences.firstOrNull { it.id == id }
}
