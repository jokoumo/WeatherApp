package com.example.weatherapp.ui.locations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.weather.messageRes
import com.example.weatherapp.ui.SearchUiState

/** Search input used both during setup and inside the location manager. */
@Composable
fun LocationSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
    )
}

/**
 * Renders the result list of the geocoding search, including loading, empty and error states.
 * All text comes from resources so that the search behaves identically in German.
 */
@Composable
fun LocationSearchResults(
    search: SearchUiState,
    modifier: Modifier = Modifier,
    onItemClick: (SavedLocation) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            search.isSearching -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.loading_weather),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            search.queryTooShort -> InfoNote(text = stringResource(R.string.search_short_query))

            search.failure != null -> InfoNote(text = stringResource(search.failure.messageRes()))

            search.results.isEmpty() && search.hasRequestedSearch ->
                InfoNote(text = stringResource(R.string.search_no_results))
        }

        search.results.forEach { location ->
            LocationListItem(
                location = location,
                onClick = { onItemClick(location) },
            )
        }
    }
}

/**
 * One location row: name, region line and an optional trailing action area.
 *
 * [trailingReserve] declares how much width the action buttons need. If the row is too narrow to
 * keep a readable amount of text next to them - small screens, long names or a large system font
 * size - the actions move to their own right aligned line below the text instead of squeezing the
 * name into a few characters. Touch targets and the visual style stay the same either way.
 */
@Composable
fun LocationListItem(
    location: SavedLocation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    selectedLabel: String? = null,
    trailing: @Composable () -> Unit = {},
    trailingReserve: Dp? = null,
) {
    val minTextWidth = MIN_READABLE_TEXT_WIDTH_DP.dp * LocalConfiguration.current.fontScale
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        val actionsBelowText = trailingReserve != null && maxWidth - trailingReserve < minTextWidth
        if (actionsBelowText) {
            Column {
                LocationText(location = location, isSelected = isSelected, selectedLabel = selectedLabel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trailing()
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    LocationText(location = location, isSelected = isSelected, selectedLabel = selectedLabel)
                }
                Spacer(Modifier.width(8.dp))
                Box(contentAlignment = Alignment.CenterEnd) { trailing() }
            }
        }
    }
}

/** Name, region line and the optional "currently shown" marker of a location row. */
@Composable
private fun LocationText(
    location: SavedLocation,
    isSelected: Boolean,
    selectedLabel: String?,
) {
    Text(
        text = location.name,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val subtitle = location.subtitle
    if (subtitle.isNotEmpty()) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (selectedLabel != null) {
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Below this width the name of a location stops being readable next to four action buttons. */
private const val MIN_READABLE_TEXT_WIDTH_DP = 120
