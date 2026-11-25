package com.example.weatherapp.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.components.SecondaryScreenScaffold
import com.example.weatherapp.ui.settings.SettingsSectionHeader

/**
 * Privacy information of the application.
 *
 * Every statement here describes what this code actually does: the two Open-Meteo endpoints the
 * repositories call, the files DataStore and Room write, and the backup rules declared in
 * `res/xml`. It is deliberately a technical notice for a personal, open source application and
 * not a legal opinion.
 */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val localItems = stringArrayResource(R.array.privacy_local_items).toList()
    val requestItems = stringArrayResource(R.array.privacy_request_items).toList()
    val searchItems = stringArrayResource(R.array.privacy_search_items).toList()
    val trackingItems = stringArrayResource(R.array.privacy_tracking_items).toList()
    val backupIncluded = stringArrayResource(R.array.privacy_backup_included).toList()
    val backupExcluded = stringArrayResource(R.array.privacy_backup_excluded).toList()
    val endpoints = remember {
        listOf(
            "api.open-meteo.com  (v1/forecast)",
            "geocoding-api.open-meteo.com  (v1/search)",
        )
    }

    SecondaryScreenScaffold(
        title = stringResource(R.string.privacy_screen_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.privacy_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        PrivacySection(
            title = stringResource(R.string.privacy_section_general),
            body = stringResource(R.string.privacy_general_body),
        )

        PrivacySection(
            title = stringResource(R.string.privacy_section_local),
            body = stringResource(R.string.privacy_local_body),
            bullets = localItems,
        )

        PrivacySection(
            title = stringResource(R.string.privacy_section_requests),
            body = stringResource(R.string.privacy_requests_body),
            bullets = requestItems,
            cardTitle = stringResource(R.string.privacy_endpoints_title),
            cardLines = endpoints,
        )

        PrivacySection(
            title = stringResource(R.string.privacy_section_search),
            body = stringResource(R.string.privacy_search_body),
            bullets = searchItems,
        )

        PrivacySection(
            title = stringResource(R.string.privacy_section_tracking),
            body = stringResource(R.string.privacy_tracking_body),
            bullets = trackingItems,
        )

        PrivacySection(
            title = stringResource(R.string.privacy_section_backup),
            body = stringResource(R.string.privacy_backup_body),
            cardTitle = stringResource(R.string.privacy_backup_included_title),
            cardLines = backupIncluded,
            secondCardTitle = stringResource(R.string.privacy_backup_excluded_title),
            secondCardLines = backupExcluded,
        )

        PrivacySection(
            title = stringResource(R.string.privacy_section_provider),
            body = stringResource(R.string.privacy_provider_body),
        )

        Button(onClick = { openExternalLink(AboutContent.PROVIDER_PRIVACY_URL, uriHandler) }) {
            Text(stringResource(R.string.privacy_provider_link_hint))
        }

        InfoNote(text = stringResource(R.string.privacy_offline_note))
    }
}

/** One privacy topic: heading, paragraph, optional bullet list and optional detail cards. */
@Composable
private fun PrivacySection(
    title: String,
    body: String,
    bullets: List<String> = emptyList(),
    cardTitle: String = "",
    cardLines: List<String> = emptyList(),
    secondCardTitle: String = "",
    secondCardLines: List<String> = emptyList(),
) {
    SettingsSectionHeader(title)
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    if (bullets.isNotEmpty()) {
        InfoCard {
            bullets.forEach { item -> PrivacyBullet(item) }
        }
    }
    if (cardLines.isNotEmpty()) {
        PrivacyDetailCard(title = cardTitle, lines = cardLines)
    }
    if (secondCardLines.isNotEmpty()) {
        PrivacyDetailCard(title = secondCardTitle, lines = secondCardLines)
    }
}

/**
 * A list entry with a drawn dot instead of a bullet character: the mark follows the active theme,
 * and no typographic symbol has to survive the encoding of the source file.
 */
@Composable
private fun PrivacyBullet(item: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = item,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Card with a small heading and one line per address, file name or stored value. */
@Composable
private fun PrivacyDetailCard(title: String, lines: List<String>) {
    InfoCard {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
