package com.example.weatherapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherapp.ui.dashboard.DashboardScreen
import com.example.weatherapp.ui.locations.LocationSetupScreen

/**
 * Chooses between the initial setup flow and the dashboard, and passes the state held by
 * [MainViewModel] down to the screens.
 */
@Composable
fun WeatherRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
