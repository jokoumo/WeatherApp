package com.example.weatherapp.ui.about

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.ui.components.DetailRow
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.components.SecondaryScreenScaffold
import com.example.weatherapp.ui.settings.SettingsNavigationRow
import com.example.weatherapp.ui.settings.SettingsSectionHeader

/**
 * Opens an external link in the system browser. The application deliberately has no in-app
 * browser and no extra permission, so the only failure mode is a device without any browser;
 * that is logged instead of crashing.
 */
internal fun openExternalLink(url: String, uriHandler: UriHandler) {
    if (url.isBlank()) return
    try {
        uriHandler.openUri(url)
    } catch (activityNotFound: ActivityNotFoundException) {
        Log.w("AboutScreen", "No application available to open $url")
    }
}

/**
 * Application information: version of the installed package, the weather services behind the
 * data, and the licences and attributions that come with them.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicence: (String) -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context) { appVersionInfo(context) }

    SecondaryScreenScaffold(
        title = stringResource(R.string.about_screen_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        SettingsSectionHeader(stringResource(R.string.about_section_application))
        InfoCard {
            Text(
                text = version.applicationName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DetailRow(
                label = stringResource(R.string.about_version_label),
                value = version.versionName,
            )
            DetailRow(
                label = stringResource(R.string.about_build_label),
                value = version.buildNumber,
            )
        }

        SettingsSectionHeader(stringResource(R.string.about_section_sources))
        AboutContent.sources.forEach { entry ->
            ExternalLinkCard(
                title = stringResource(entry.titleRes),
                summary = stringResource(entry.summaryRes),
                onOpen = { openExternalLink(entry.url, uriHandler) },
            )
        }
        TextButton(onClick = { openExternalLink(AboutContent.ATTRIBUTION_URL, uriHandler) }) {
            Text(stringResource(R.string.about_data_attribution))
        }

        SettingsSectionHeader(stringResource(R.string.about_section_licenses))
        AboutContent.licences.forEach { entry ->
            LicenceRow(
                title = stringResource(entry.titleRes),
                licenceName = stringResource(entry.licenceNameRes),
                onOpen = { onOpenLicence(entry.id) },
            )
        }

        SettingsSectionHeader(stringResource(R.string.about_section_privacy))
        SettingsNavigationRow(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_privacy),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            },
            title = stringResource(R.string.about_privacy_title),
            summary = stringResource(R.string.about_privacy_summary),
            onClick = onOpenPrivacy,
        )
    }
}

/** Neutral card used for the grouped information blocks on the information pages. */
@Composable
fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            content()
        }
    }
}

/** A weather service with a short description; the whole card opens the official website. */
@Composable
private fun ExternalLinkCard(
    title: String,
    summary: String,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.about_license_link_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A licence entry; it opens its own readable page instead of expanding in place. */
@Composable
private fun LicenceRow(
    title: String,
    licenceName: String,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = licenceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Readable detail page of one licence: author, licence identifier, a German summary that stays
 * available offline, the components it covers and a link to the full text.
 */
@Composable
fun LicenceDetailScreen(
    entry: LicenceEntry,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    SecondaryScreenScaffold(
        title = stringResource(entry.titleRes),
        onBack = onBack,
        modifier = modifier,
    ) {
        InfoCard {
            Text(
                text = stringResource(R.string.about_license_name, stringResource(entry.licenceNameRes)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.about_license_holder, entry.holder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text = stringResource(entry.summaryRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (entry.components.isNotEmpty()) {
            SettingsSectionHeader(stringResource(R.string.about_license_components_title))
            InfoCard {
                entry.components.forEach { component ->
                    Text(
                        text = component,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
            }
        }
        InfoNote(text = stringResource(R.string.about_license_offline_note))
        if (entry.url.isNotBlank()) {
            Button(onClick = { openExternalLink(entry.url, uriHandler) }) {
                Text(stringResource(R.string.about_license_link_hint))
            }
        }
    }
}
