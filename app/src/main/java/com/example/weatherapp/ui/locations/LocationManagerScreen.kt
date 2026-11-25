package com.example.weatherapp.ui.locations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.weatherapp.R
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.SearchUiState
import com.example.weatherapp.ui.UserMessage
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.components.SecondaryScreenHeader
import com.example.weatherapp.ui.messageRes
import com.example.weatherapp.ui.weather.messageRes

/**
 * Full screen location management: the navigation header and the search field stay fixed at the
 * top, the saved locations live in a list that scrolls on its own, and search suggestions are
 * shown in a dropdown that floats over that list. Navigation uses the same top-left back arrow as
 * every other secondary screen.
 *
 * The list is ordered by role rather than by age: **Hauptstandort**, then **Favoriten**, then the
 * **Verlauf** of places that were only looked at. A house marks the home location, a star marks a
 * favourite, and the star of a history entry offers to make it one. Tapping a row - of a
 * suggestion, a favourite or a history entry - selects that location and leaves the screen; every
 * other control in a row performs only its own action.
 */
@Composable
fun LocationManagerScreen(
    settings: LocationSettings,
    search: SearchUiState,
    message: UserMessage?,
    onSelect: (String) -> Unit,
    onSelectSearched: (SavedLocation) -> Unit,
    onToggleFavorite: (SavedLocation) -> Unit,
    onSetHome: (SavedLocation) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onMove: (String, Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onMessageShown: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val selectedId = settings.resolveSelectedLocation()?.id
    val suggestionsOpen = search.query.isNotBlank()
    val imeBottom = WindowInsets.ime.getBottom(density)

    // Anchor of the suggestion dropdown. Measuring the field and the container lets the dropdown
    // float over the list instead of pushing it down.
    var containerBounds by remember { mutableStateOf<Rect?>(null) }
    var fieldBounds by remember { mutableStateOf<Rect?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    // Back closes the suggestions first and only then the screen itself. While the confirmation
    // dialog is open the dialog owns the back key, so a press there only closes the dialog.
    BackHandler(enabled = !confirmClearHistory) {
        if (suggestionsOpen) onQueryChange("") else onDismiss()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = if (message != null) stringResource(message.messageRes()) else null
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { }) }
            .onGloballyPositioned { coordinates -> containerBounds = coordinates.boundsInRoot() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            SecondaryScreenHeader(
                title = stringResource(R.string.locations_screen_title),
                onBack = onDismiss,
                modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.locations_add_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))
            LocationSearchField(
                query = search.query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .onGloballyPositioned { coordinates -> fieldBounds = coordinates.boundsInRoot() },
            )
            Spacer(Modifier.height(16.dp))
            // The list sits directly on the painted background rather than inside a Material
            // surface, so the content colour is provided here: otherwise widgets in the rows
            // would inherit Compose's default black and disappear in dark mode.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                SavedLocationsList(
                    settings = settings,
                    selectedId = selectedId,
                    onSelect = { id ->
                        onSelect(id)
                        onDismiss()
                    },
                    onToggleFavorite = onToggleFavorite,
                    onSetHome = onSetHome,
                    onRemove = onRemove,
                    onRemoveRecent = onRemoveRecent,
                    onClearRecent = { confirmClearHistory = true },
                    onMove = onMove,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                )
            }
        }

        val container = containerBounds
        val anchor = fieldBounds
        if (suggestionsOpen && container != null && anchor != null) {
            SearchSuggestionsDropdown(
                search = search,
                anchor = anchor,
                container = container,
                imeBottomPx = imeBottom,
                onItemClick = { location ->
                    // Choosing a suggestion shows that place straight away; it does not save it.
                    onSelectSearched(location)
                    onDismiss()
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                // Feedback such as "already saved" appears while the keyboard is still open, so the
                // message has to sit above it instead of behind it.
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 16.dp),
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    onClearRecent()
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Home location, favourites and history in one list that scrolls independently of the search
 * field. The three sections are separated by a divider and a small label, and the history header
 * carries the compact "Alle löschen" action.
 */
@Composable
private fun SavedLocationsList(
    settings: LocationSettings,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onToggleFavorite: (SavedLocation) -> Unit,
    onSetHome: (SavedLocation) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = listState,
        // A little scrollable space below the last entry so it never sits flush against the bottom
        // edge; it is content space, not a permanent empty footer.
        contentPadding = PaddingValues(bottom = LIST_BOTTOM_SPACE),
    ) {
        item(key = "home-header") { SectionLabel(stringResource(R.string.locations_home)) }
        item(key = "home") {
            val home = settings.homeLocation
            if (home == null) {
                InfoNote(text = stringResource(R.string.no_home_location))
            } else {
                HomeLocationRow(
                    location = home,
                    isSelected = home.id == selectedId,
                    onSelect = { onSelect(home.id) },
                    onRemove = { onRemove(home.id) },
                )
            }
        }

        item(key = "favorites-header") {
            SectionDivider()
            SectionLabel(stringResource(R.string.locations_favorites))
            Spacer(Modifier.height(4.dp))
            if (settings.favorites.isEmpty()) {
                InfoNote(text = stringResource(R.string.locations_no_favorites))
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }

        itemsIndexed(settings.favorites, key = { _, location -> location.id }) { index, favorite ->
            FavoriteRow(
                location = favorite,
                isSelected = favorite.id == selectedId,
                isFirst = index == 0,
                isLast = index == settings.favorites.lastIndex,
                onSelect = { onSelect(favorite.id) },
                onToggleFavorite = { onToggleFavorite(favorite) },
                onSetHome = { onSetHome(favorite) },
                onMoveUp = { onMove(favorite.id, -1) },
                onMoveDown = { onMove(favorite.id, 1) },
            )
        }

        item(key = "history-header") {
            SectionDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.locations_history),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                // Only offered when there is something to clear, and deliberately quieter than the
                // section label it belongs to.
                if (settings.recentLocations.isNotEmpty()) {
                    TextButton(
                        onClick = onClearRecent,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_clear_history),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (settings.recentLocations.isEmpty()) {
                InfoNote(text = stringResource(R.string.locations_no_history))
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }

        itemsIndexed(settings.recentLocations, key = { _, location -> location.id }) { _, recent ->
            HistoryRow(
                location = recent,
                isSelected = recent.id == selectedId,
                onSelect = { onSelect(recent.id) },
                onToggleFavorite = { onToggleFavorite(recent) },
                onRemove = { onRemoveRecent(recent.id) },
                onSetHome = { onSetHome(recent) },
            )
        }
    }
}

/** Divider plus the space that separates two sections of the list. */
@Composable
private fun SectionDivider() {
    Column {
        Spacer(Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
    }
}

/** Small primary coloured label used for the "Hauptstandort" and "Favoriten" sections. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The home location: recognisable by the house in front of its name, never by a star. It cannot be
 * un-saved by accident, which is why the only action it offers sits in the overflow menu.
 */
@Composable
private fun HomeLocationRow(
    location: SavedLocation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    LocationListItem(
        location = location,
        onClick = onSelect,
        isSelected = isSelected,
        selectedLabel = if (isSelected) stringResource(R.string.locations_currently_shown) else null,
        leading = {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = stringResource(R.string.content_description_home_location),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp),
            )
        },
        trailingReserve = OVERFLOW_ACTIONS_WIDTH,
        trailing = {
            OverflowMenu(
                actions = listOf(
                    RowAction(
                        label = stringResource(R.string.action_remove_home_location),
                        icon = Icons.Default.Home,
                        destructive = true,
                        onClick = onRemove,
                    ),
                ),
            )
        },
    )
}

/**
 * A favourite: the filled star removes it from the favourites, everything less frequent - making
 * it the home location and reordering - lives in the overflow menu so the row stays readable on a
 * narrow phone.
 */
@Composable
private fun FavoriteRow(
    location: SavedLocation,
    isSelected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetHome: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    LocationListItem(
        location = location,
        onClick = onSelect,
        isSelected = isSelected,
        selectedLabel = if (isSelected) stringResource(R.string.locations_currently_shown) else null,
        trailingReserve = FAVORITE_ACTIONS_WIDTH,
        trailing = {
            val scheme = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.action_remove_favorite),
                        tint = locationActionColor(scheme, LocationActionEmphasis.EMPHASISED, true),
                    )
                }
                OverflowMenu(
                    actions = listOf(
                        RowAction(
                            label = stringResource(R.string.action_set_home),
                            icon = Icons.Default.Home,
                            onClick = onSetHome,
                        ),
                        RowAction(
                            label = stringResource(R.string.action_move_up),
                            icon = Icons.Default.KeyboardArrowUp,
                            enabled = !isFirst,
                            onClick = onMoveUp,
                        ),
                        RowAction(
                            label = stringResource(R.string.action_move_down),
                            icon = Icons.Default.KeyboardArrowDown,
                            enabled = !isLast,
                            onClick = onMoveDown,
                        ),
                    ),
                )
            }
        },
    )
}

/**
 * A history entry: an outlined star turns it into a favourite, the remove icon drops just this
 * entry, and the overflow menu offers the one less frequent action.
 */
@Composable
private fun HistoryRow(
    location: SavedLocation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
    onSetHome: () -> Unit,
) {
    LocationListItem(
        location = location,
        onClick = onSelect,
        isSelected = isSelected,
        selectedLabel = if (isSelected) stringResource(R.string.locations_currently_shown) else null,
        trailingReserve = HISTORY_ACTIONS_WIDTH,
        trailing = {
            val scheme = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star_outline),
                        contentDescription = stringResource(R.string.action_add_favorite),
                        tint = locationActionColor(scheme, LocationActionEmphasis.STANDARD, true),
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_remove_recent),
                        tint = locationActionColor(scheme, LocationActionEmphasis.DESTRUCTIVE, true),
                    )
                }
                OverflowMenu(
                    actions = listOf(
                        RowAction(
                            label = stringResource(R.string.action_set_home),
                            icon = Icons.Default.Home,
                            onClick = onSetHome,
                        ),
                    ),
                )
            }
        },
    )
}

/** One entry of an overflow menu; only the actions that apply to a row are ever built. */
private data class RowAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

/**
 * Compact contextual menu of a location row. It holds the actions that do not deserve a permanent
 * icon, and it opens on its own: pressing it never selects the location and never leaves the
 * screen.
 */
@Composable
private fun OverflowMenu(actions: List<RowAction>) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.action_more_actions),
                tint = locationActionColor(scheme, LocationActionEmphasis.STANDARD, true),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    enabled = action.enabled,
                    leadingIcon = action.icon?.let { vector ->
                        { Icon(vector, contentDescription = null) }
                    },
                    colors = if (action.destructive) {
                        MenuDefaults.itemColors(
                            textColor = scheme.error,
                            leadingIconColor = scheme.error,
                        )
                    } else {
                        MenuDefaults.itemColors()
                    },
                )
            }
        }
    }
}

/**
 * Search suggestions in a floating Material 3 surface directly under the search field. It is drawn
 * over the list, so showing or hiding it never moves the rest of the screen, and it stays inside
 * the space between the field and the keyboard. Tapping a suggestion shows that place immediately;
 * saving it stays an explicit decision the user makes with the star.
 */
@Composable
private fun SearchSuggestionsDropdown(
    search: SearchUiState,
    anchor: Rect,
    container: Rect,
    imeBottomPx: Int,
    onItemClick: (SavedLocation) -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { SUGGESTION_GAP.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val spaceBelow = container.bottom - imeBottomPx - anchor.bottom - gapPx - marginPx
    val heightPx = minOf(container.height * MAX_SUGGESTION_HEIGHT_FRACTION, spaceBelow).coerceAtLeast(0f)

    Surface(
        modifier = Modifier
            .offset { IntOffset(anchor.left.roundToInt(), (anchor.bottom + gapPx).roundToInt()) }
            .width(with(density) { anchor.width.toDp() })
            .heightIn(max = with(density) { heightPx.toDp() }),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (search.isSearching) {
                item(key = "search-loading") {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.loading_weather),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (search.queryTooShort) {
                item(key = "search-short") {
                    DropdownNote(stringResource(R.string.search_short_query))
                }
            }
            if (search.failure != null) {
                item(key = "search-failure") {
                    DropdownNote(stringResource(search.failure.messageRes()))
                }
            }
            if (
                search.results.isEmpty() &&
                search.hasRequestedSearch &&
                !search.queryTooShort &&
                search.failure == null
            ) {
                item(key = "search-empty") {
                    DropdownNote(stringResource(R.string.search_no_results))
                }
            }
            itemsIndexed(search.results, key = { _, location -> location.id }) { _, location ->
                LocationListItem(
                    location = location,
                    onClick = { onItemClick(location) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DropdownNote(text: String) {
    InfoNote(
        text = text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** How prominent an enabled row action is, which decides the theme colour it is painted with. */
internal enum class LocationActionEmphasis {
    STANDARD,
    EMPHASISED,
    DESTRUCTIVE,
}

/**
 * Paint colour for the action icons of the location rows.
 *
 * The colour is always taken from the active [ColorScheme], never from an inherited content
 * colour, so the icons keep their contrast in the light and in the dark theme. Disabled actions
 * use a clearly reduced emphasis instead of the colour of an enabled action, and they stay far
 * away from being invisible. The function is deliberately free of Compose so that a plain unit
 * test can assert the contrast of both states in both themes.
 */
internal fun locationActionColor(
    scheme: ColorScheme,
    emphasis: LocationActionEmphasis,
    enabled: Boolean,
): Color {
    if (!enabled) return scheme.onSurfaceVariant.copy(alpha = DISABLED_ACTION_ALPHA)
    return when (emphasis) {
        LocationActionEmphasis.STANDARD -> scheme.onSurfaceVariant
        LocationActionEmphasis.EMPHASISED -> scheme.primary
        LocationActionEmphasis.DESTRUCTIVE -> scheme.error
    }
}

/** Reduced emphasis for actions the user cannot trigger at the moment, such as "move up" on the
 * first favourite. */
internal const val DISABLED_ACTION_ALPHA = 0.38f

/** Small scrollable bottom space so the last entry never sits flush against the edge. */
private val LIST_BOTTOM_SPACE = 24.dp

/**
 * Widths the row layout reserves for the action buttons, so that a long location name is moved
 * above the actions instead of being squeezed into a few characters.
 */
private val OVERFLOW_ACTIONS_WIDTH = 48.dp
private val FAVORITE_ACTIONS_WIDTH = 48.dp * 2 + 8.dp
private val HISTORY_ACTIONS_WIDTH = 48.dp * 3 + 8.dp
private val SUGGESTION_GAP = 8.dp
private const val MAX_SUGGESTION_HEIGHT_FRACTION = 0.45f
