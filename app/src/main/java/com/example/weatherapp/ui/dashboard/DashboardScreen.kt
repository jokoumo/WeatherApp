package com.example.weatherapp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
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
import com.example.weatherapp.data.repository.SnapshotSource
import com.example.weatherapp.domain.WeatherFormatter
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.HourlyWeather
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.MainUiState
import com.example.weatherapp.ui.WeatherUiState
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.components.LoadingPlaceholder
import com.example.weatherapp.ui.components.SectionHeader
import com.example.weatherapp.ui.locations.LocationManagerSheet
import com.example.weatherapp.ui.messageRes
import com.example.weatherapp.ui.weather.messageRes
import java.time.Instant
import java.time.LocalDate

/**
 * Main screen: current conditions, the hours of today, the next two days in detail and the
 * compact outlook for the remaining forecast days. Content is refreshed by pulling down or by
 * the toolbar button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onSelectLocation: (String) -> Unit,
    onSetHomeLocation: (SavedLocation) -> Unit,
    onAddFavorite: (SavedLocation) -> Unit,
    onRemoveLocation: (String) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weather = state.weather
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedHour by remember { mutableStateOf<HourlyWeather?>(null) }
    var showLocationSheet by remember { mutableStateOf(false) }
    val message = state.message
    val messageText = if (message != null) stringResource(message.messageRes()) else null

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = weather.location?.name ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = weather.location?.subtitle.orEmpty()
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                    IconButton(onClick = { showLocationSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = stringResource(R.string.action_locations),
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
                    state.isInitialLoading -> LoadingPlaceholder()

                    location == null || !weather.hasData ->
                        ErrorState(failure = weather.failure, onRetry = onRefresh)

                    else -> WeatherContent(
                        location = location,
                        weather = weather,
                        onHourClick = { selectedHour = it },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    selectedHour?.let { hour ->
        HourlyDetailSheet(hour = hour, onDismiss = { selectedHour = null })
    }

    if (showLocationSheet) {
        LocationManagerSheet(
            settings = state.settings,
            search = state.search,
            onSelect = onSelectLocation,
            onSetHome = onSetHomeLocation,
            onAddFavorite = onAddFavorite,
            onRemove = onRemoveLocation,
            onMove = onMoveFavorite,
            onQueryChange = onSearchQueryChange,
            onDismiss = { showLocationSheet = false },
        )
    }
}

@Composable
private fun WeatherContent(
    location: SavedLocation,
    weather: WeatherUiState,
    onHourClick: (HourlyWeather) -> Unit,
) {
    val zone = weather.timeZone
    val sections = weather.sections

    CurrentWeatherCard(
        location = location,
        current = weather.current,
        todayMin = weather.todayMin,
        todayMax = weather.todayMax,
        dataInstant = weather.current?.instant ?: weather.fetchedAt ?: Instant.EPOCH,
        zoneId = zone ?: java.time.ZoneId.systemDefault(),
    )

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
        // Tells the user whether what is on screen came from the network or from the cache.
        val noteRes = when {
            weather.isShowingStaleData && weather.snapshotSource == SnapshotSource.CACHED ->
                R.string.offline_notice

            weather.isShowingStaleData -> R.string.data_may_be_outdated
            weather.snapshotSource == SnapshotSource.CACHED -> R.string.status_cached
            weather.snapshotSource == SnapshotSource.LIVE -> R.string.status_live
            else -> null
        }
        noteRes?.let { InfoNote(text = stringResource(it)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.section_hourly),
            note = weather.today?.let { WeatherFormatter.weekdayAndDate(it) },
        )
        HourlyForecast(
            hours = sections.todayHours,
            currentHourIndex = weather.hourlyAnchorIndex,
            onItemClick = onHourClick,
        )
    }

    if (sections.detailedDays.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title = stringResource(R.string.section_daily))
            sections.detailedDays.forEach { day ->
                DetailedDayCard(day = day, overline = dayOverline(day.date, weather.today))
            }
        }
    }

    if (sections.extendedDays.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = stringResource(R.string.section_extended),
                note = stringResource(R.string.extended_hint),
            )
            ExtendedDayList(
                days = sections.extendedDays,
                overallMin = weather.extendedMin,
                overallMax = weather.extendedMax,
            )
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
