package com.example.weatherapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherapp.domain.millisUntilNextHour
import com.example.weatherapp.ui.dashboard.DashboardScreen
import com.example.weatherapp.ui.locations.LocationSetupScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant

/**
 * Chooses between the initial setup flow and the dashboard, and passes the state held by
 * [MainViewModel] down to the screens.
 */
@Composable
fun WeatherRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Keeps the rolling hourly window aligned with the current hour. The effect only runs
    // while the UI is composed, so nothing ticks in the background, and the view model rebuilds
    // the window from the snapshot it already has instead of loading the forecast again.
    val hourlyZone = state.weather.timeZone
    LaunchedEffect(hourlyZone) {
        val zone = hourlyZone ?: return@LaunchedEffect
        viewModel.onClockTick()
        while (isActive) {
            delay(millisUntilNextHour(Instant.now(), zone))
            viewModel.onClockTick()
        }
    }

    when {
        !state.settingsLoaded -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        !state.settings.hasAnyLocation -> LocationSetupScreen(
            search = state.search,
            onQueryChange = viewModel::onSearchQueryChange,
            onLocationChosen = { location ->
                viewModel.setHomeLocation(location)
                viewModel.selectLocation(location.id)
            },
        )

        else -> DashboardScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onSelectLocation = viewModel::selectLocation,
            onSetHomeLocation = viewModel::setHomeLocation,
            onAddFavorite = viewModel::addFavorite,
            onRemoveLocation = viewModel::removeLocation,
            onMoveFavorite = viewModel::moveFavorite,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onMessageShown = viewModel::consumeMessage,
        )
    }
}
