package com.example.weatherapp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherapp.R
import com.example.weatherapp.domain.HourlyWindowEntry
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.MainUiState
import com.example.weatherapp.ui.WeatherUiState
import com.example.weatherapp.ui.components.LoadingPlaceholder
import com.example.weatherapp.ui.components.SectionHeader
import com.example.weatherapp.ui.messageRes
import com.example.weatherapp.ui.weather.messageRes
import java.time.Instant
import java.time.LocalDate

/**
 * Main screen: current conditions, the rolling 24 hour forecast, the next two days in detail
 * and the compact outlook for the remaining forecast days. Content is refreshed by pulling
 * down or by the toolbar button.
 *
 * The toolbar carries the application name, not the location: the selected location is already
 * the headline of the current weather card, so the toolbar stays free for the three actions
 * refresh, locations and settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weather = state.weather
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedEntry by remember { mutableStateOf<HourlyWindowEntry?>(null) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    val message = state.message
    val messageText = if (message != null) stringResource(message.messageRes()) else null

    // Feedback belongs to the screen that is visible: the caller only passes a message when the
    // dashboard is on top, so a message shown by the location manager never appears twice.
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            // One step above the titleLarge of the secondary screens: the name of
                            // the application is the most prominent heading in the app. The title
                            // keeps its own column, so it can never reach into the action icons.
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        // Explicit 48 dp boxes: the icon itself stays 24 dp, but the clickable
                        // area meets the minimum touch target on every screen density.
                        IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
                            )
                        }
                        IconButton(onClick = onOpenLocations, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = stringResource(R.string.action_locations),
                            )
                        }
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.action_settings),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            },
        ) { contentPadding ->
            PullToRefreshBox(
                isRefreshing = weather.isRefreshing && weather.hasData,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val location = weather.location
                    when {
                        // A place is known, so its name, region and star stay on the hero card
                        // while its weather is loading and also when loading failed. The state
                        // carries exactly one location, so nothing of the previous one is left.
                        location != null -> WeatherContent(
                            location = location,
                            weather = weather,
                            isFavorite = state.displayedLocationIsFavorite,
                            isHomeLocation = state.displayedLocationIsHome,
                            onRefresh = onRefresh,
                            onToggleFavorite = onToggleFavorite,
                            onHourClick = { selectedEntry = it },
                            onDayClick = { selectedDay = it },
                        )

                        state.isInitialLoading -> LoadingPlaceholder()

                        else -> ErrorState(failure = weather.failure, onRetry = onRefresh)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        selectedEntry?.let { entry ->
            HourlyDetailSheet(entry = entry, onDismiss = { selectedEntry = null })
        }

        val dayZone = weather.timeZone
        selectedDay?.let { date ->
            // The periods are calendar periods of the location, so without its time zone there is
            // nothing honest to show and the sheet simply stays closed.
            if (dayZone != null) {
                DayForecastDetailSheet(
                    date = date,
                    hours = weather.sections.allHours,
                    zone = dayZone,
                    onDismiss = { selectedDay = null },
                )
            }
        }
    }
}

/**
 * Everything below the toolbar for the selected [location].
 *
 * The hero card is rendered in all three situations - values available, values still loading, load
 * failed - because identifying the place the user picked does not depend on the weather response.
 * Only the forecast sections need data, and a failed load ends in [ErrorState], which offers the
 * retry without leaving the screen.
 */
@Composable
private fun WeatherContent(
    location: SavedLocation,
    weather: WeatherUiState,
    isFavorite: Boolean,
    isHomeLocation: Boolean,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHourClick: (HourlyWindowEntry) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val zone = weather.timeZone
    val sections = weather.sections
    val status = when {
        weather.hasData -> WeatherCardStatus.READY
        weather.isRefreshing -> WeatherCardStatus.LOADING
        else -> WeatherCardStatus.FAILED
    }

    CurrentWeatherCard(
        location = location,
        current = weather.current,
        todayMin = weather.todayMin,
        todayMax = weather.todayMax,
        dataInstant = weather.displayInstant,
        zoneId = zone ?: java.time.ZoneId.systemDefault(),
        isFavorite = isFavorite,
        isHomeLocation = isHomeLocation,
        onToggleFavorite = onToggleFavorite,
        status = status,
    )

    if (!weather.hasData) {
        // While loading there is nothing to add below the card; a failed load explains itself
        // right here, next to the retry button.
        if (!weather.isRefreshing) {
            ErrorState(failure = weather.failure, onRetry = onRefresh)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        weather.fetchedAt?.let { fetchedAt ->
            Text(
                text = stringResource(
                    R.string.last_updated,
                    WeatherFormatter.relativeTime(fetchedAt, Instant.now()),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.section_hourly),
            note = weather.today?.let { WeatherFormatter.weekdayAndDate(it) },
        )
        // Keyed by the location so the strip begins again at the current hour after a switch,
        // while a window that only advanced by one hour keeps the position the user scrolled to.
        key(location.id) {
            HourlyForecast(
                entries = sections.hourlyEntries,
                onItemClick = onHourClick,
            )
        }
    }

    if (sections.detailedDays.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = stringResource(R.string.section_daily))
            sections.detailedDays.forEach { day ->
                DetailedDayCard(
                    day = day,
                    overline = dayOverline(day.date, weather.today),
                    onClick = { onDayClick(day.date) },
                )
            }
        }
    }

    if (sections.extendedDays.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = stringResource(R.string.section_extended),
                note = stringResource(R.string.extended_hint),
            )
            ExtendedDayList(days = sections.extendedDays)
        }
    }
}

/** "Morgen" and "Übermorgen" as small labels above the two detailed day cards. */
@Composable
private fun dayOverline(date: LocalDate, today: LocalDate?): String? = when {
    today == null -> null
    date == today.plusDays(1) -> stringResource(R.string.tomorrow)
    date == today.plusDays(2) -> stringResource(R.string.day_after_tomorrow)
    else -> null
}

@Composable
private fun ErrorState(failure: DataLoadFailure?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(failure?.messageRes() ?: R.string.error_no_data),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Row {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
