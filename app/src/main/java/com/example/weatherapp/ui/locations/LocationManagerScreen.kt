package com.example.weatherapp.ui.locations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import com.example.weatherapp.ui.messageRes
import com.example.weatherapp.ui.weather.messageRes

/**
 * Full screen location management: the title and the search field stay fixed at the top, the
 * saved locations live in a list that scrolls on its own, and search suggestions are shown in a
 * dropdown that floats over that list. The layout therefore never grows or shifts when favourites
 * or results are added, which the previous bottom sheet did. A floating button returns to the
 * dashboard.
 */
@Composable
fun LocationManagerScreen(
    settings: LocationSettings,
    search: SearchUiState,
    message: UserMessage?,
    onSelect: (String) -> Unit,
    onSetHome: (SavedLocation) -> Unit,
    onAddFavorite: (SavedLocation) -> Unit,
    onRemove: (String) -> Unit,
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

    // Back closes the suggestions first and only then the screen itself.
    BackHandler {
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
            .onGloballyPositioned { coordinates -> containerBounds = coordinates.boundsInRoot() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.locations_screen_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.locations_add_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            LocationSearchField(
                query = search.query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates -> fieldBounds = coordinates.boundsInRoot() },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.locations_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            // The list sits directly on the painted background rather than inside a Material
            // surface, so the content colour is provided here: otherwise widgets in the rows
            // would inherit Compose's default black and disappear in dark mode.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                SavedLocationsList(
                    settings = settings,
                    selectedId = selectedId,
                    onSelect = onSelect,
                    onSetHome = onSetHome,
                    onRemove = onRemove,
                    onMove = onMove,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                    onAddFavorite(location)
                    onQueryChange("")
                },
            )
        }

        // The floating button is hidden while the keyboard is up so that the search field and the
        // suggestions stay easy to reach; it comes back as soon as the keyboard closes.
        if (imeBottom == 0) {
            FloatingActionButton(
                onClick = {
                    if (suggestionsOpen) onQueryChange("")
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, bottom = 20.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_to_weather),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                // Feedback such as "already saved" appears while the keyboard is still open, so the
                // message has to sit above it instead of behind it.
                .imePadding()
                .padding(bottom = 16.dp, start = 84.dp, end = 8.dp),
        )
    }
}

/** Home location plus favourites, in a list that scrolls independently of the search field. */
@Composable
private fun SavedLocationsList(
    settings: LocationSettings,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onSetHome: (SavedLocation) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier,
        state = listState,
        // Bottom padding keeps the last favourite clear of the floating button: it is scrollable
        // content space, not a permanent empty footer.
        contentPadding = PaddingValues(bottom = FAVORITES_BOTTOM_SPACE),
    ) {
        item { SectionLabel(stringResource(R.string.locations_home)) }
        item(key = "home") {
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
                                    tint = locationActionColor(
                                        MaterialTheme.colorScheme,
                                        LocationActionEmphasis.DESTRUCTIVE,
                                        enabled = true,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }

        item(key = "favorites-header") {
            Column {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                SectionLabel(stringResource(R.string.locations_favorites))
                Spacer(Modifier.height(4.dp))
                if (settings.favorites.isEmpty()) {
                    InfoNote(text = stringResource(R.string.locations_no_favorites))
                }
            }
        }

        itemsIndexed(settings.favorites, key = { _, location -> location.id }) { index, favorite ->
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
}

/** Small primary coloured label used for the "Startseite" and "Favoriten" sections. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
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
        // Four icon buttons: on narrow screens or with a large system font the row moves them
        // below the text instead of squeezing the name into a few characters.
        trailingReserve = FAVORITE_ACTIONS_WIDTH,
        trailing = {
            val scheme = MaterialTheme.colorScheme
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.action_move_up),
                        tint = locationActionColor(scheme, LocationActionEmphasis.STANDARD, !isFirst),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_move_down),
                        tint = locationActionColor(scheme, LocationActionEmphasis.STANDARD, !isLast),
                    )
                }
                IconButton(onClick = onSetHome) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.action_set_home),
                        tint = locationActionColor(scheme, LocationActionEmphasis.EMPHASISED, true),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_remove_location),
                        tint = locationActionColor(scheme, LocationActionEmphasis.DESTRUCTIVE, true),
                    )
                }
            }
        },
    )
}

/**
 * Search suggestions in a floating Material 3 surface directly under the search field. It is drawn
 * over the favourites list, so showing or hiding it never moves the rest of the screen, and it
 * stays inside the space between the field and the keyboard.
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
                    trailing = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_add_favorite),
                            tint = locationActionColor(
                                MaterialTheme.colorScheme,
                                LocationActionEmphasis.EMPHASISED,
                                enabled = true,
                            ),
                        )
                    },
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

/** Scrollable bottom space that keeps the last favourite clear of the floating button. */
private val FAVORITES_BOTTOM_SPACE = 96.dp

/** Four 48 dp icon buttons plus the gap in front of them, as needed by [LocationListItem]. */
private val FAVORITE_ACTIONS_WIDTH = 48.dp * 4 + 8.dp
private val SUGGESTION_GAP = 8.dp
private const val MAX_SUGGESTION_HEIGHT_FRACTION = 0.45f
