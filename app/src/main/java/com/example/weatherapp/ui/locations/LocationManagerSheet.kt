package com.example.weatherapp.ui.locations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.SearchUiState
import com.example.weatherapp.ui.components.InfoNote

/**
 * Bottom sheet that manages the home location and the favorite locations: select the location
 * to display, promote a favorite to the home location, reorder favorites and remove entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationManagerSheet(
    settings: LocationSettings,
    search: SearchUiState,
    onSelect: (String) -> Unit,
    onSetHome: (SavedLocation) -> Unit,
    onAddFavorite: (SavedLocation) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedId = settings.resolveSelectedLocation()?.id

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.locations_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Text(
                text = stringResource(R.string.locations_add_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.locations_home),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val home = settings.homeLocation
            if (home == null) {
                InfoNote(text = stringResource(R.string.no_home_location))
            } else {
                LocationListItem(
                    location = home,
                    onClick = {
                        onSelect(home.id)
                        onDismiss()
                    },
                    isSelected = home.id == selectedId,
                    selectedLabel = if (home.id == selectedId) {
                        stringResource(R.string.locations_currently_shown)
                    } else {
                        null
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (home.id != selectedId) {
                                TextButton(onClick = {
                                    onSelect(home.id)
                                    onDismiss()
                                }) {
                                    Text(stringResource(R.string.action_select_location))
                                }
                            }
                            IconButton(onClick = { onRemove(home.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_remove_location),
                                )
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.locations_favorites),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            if (settings.favorites.isEmpty()) {
                InfoNote(text = stringResource(R.string.locations_no_favorites))
            } else {
                settings.favorites.forEachIndexed { index, favorite ->
                    FavoriteRow(
                        location = favorite,
                        isSelected = favorite.id == selectedId,
                        isFirst = index == 0,
                        isLast = index == settings.favorites.lastIndex,
                        onSelect = {
                            onSelect(favorite.id)
                            onDismiss()
                        },
                        onSetHome = { onSetHome(favorite) },
                        onRemove = { onRemove(favorite.id) },
                        onMoveUp = { onMove(favorite.id, -1) },
                        onMoveDown = { onMove(favorite.id, 1) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.action_search_location),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            LocationSearchField(query = search.query, onQueryChange = onQueryChange)
            Spacer(Modifier.height(10.dp))
            LocationSearchResults(
                search = search,
                onItemClick = { location ->
                    onAddFavorite(location)
                    onQueryChange("")
                },
            )
        }
    }
}

@Composable
private fun FavoriteRow(
    location: SavedLocation,
    isSelected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onSelect: () -> Unit,
    onSetHome: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    LocationListItem(
        location = location,
        onClick = onSelect,
        isSelected = isSelected,
        selectedLabel = if (isSelected) stringResource(R.string.locations_currently_shown) else null,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.action_move_up),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_move_down),
                    )
                }
                IconButton(onClick = onSetHome) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.action_set_home),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_remove_location),
                    )
                }
            }
        },
    )
}
