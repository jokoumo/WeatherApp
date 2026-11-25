package com.example.weatherapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherapp.domain.millisUntilNextHour
import com.example.weatherapp.ui.about.AboutContent
import com.example.weatherapp.ui.about.AboutScreen
import com.example.weatherapp.ui.about.LicenceDetailScreen
import com.example.weatherapp.ui.dashboard.DashboardScreen
import com.example.weatherapp.ui.locations.LocationManagerScreen
import com.example.weatherapp.ui.locations.LocationSetupScreen
import com.example.weatherapp.ui.navigation.AppBackStack
import com.example.weatherapp.ui.navigation.AppDestination
import com.example.weatherapp.ui.settings.AppearanceScreen
import com.example.weatherapp.ui.settings.AppearanceViewModel
import com.example.weatherapp.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant

/**
 * Chooses between the initial setup flow and the dashboard, and owns the navigation between the
 * dashboard and the secondary screens.
 *
 * The dashboard stays composed while a secondary screen is on top of it, so its scroll positions
 * and the hourly strip keep their state; all weather and location state lives in [MainViewModel]
 * anyway. The back stack is saveable, so rotation or a theme switch returns to the same screen.
 */
@Composable
fun WeatherRoot(
    viewModel: MainViewModel,
    appearanceViewModel: AppearanceViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by appearanceViewModel.themeMode.collectAsStateWithLifecycle()
    val themePersistenceFailed by appearanceViewModel.persistenceFailed.collectAsStateWithLifecycle()

    var backStack by rememberSaveable(stateSaver = AppBackStack.Saver) {
        mutableStateOf(AppBackStack.atDashboard())
    }
    var licenceId by rememberSaveable { mutableStateOf<String?>(null) }

    val current = backStack.current
    val goBack: () -> Unit = { backStack = backStack.pop() }
    val open: (AppDestination) -> Unit = { destination -> backStack = backStack.push(destination) }

    // System back and the back gesture follow the same stack. The location manager adds its own
    // handler, which closes an open suggestion list first and only then leaves the screen.
    BackHandler(enabled = backStack.canGoBack, onBack = goBack)

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
                // setHomeLocation also selects the new home location, so no extra selection
                // call is needed here.
                viewModel.setHomeLocation(location)
            },
        )

        else -> Box(modifier = Modifier.fillMaxSize()) {
            val onDashboard = current == AppDestination.DASHBOARD
            DashboardScreen(
                state = state.copy(message = if (onDashboard) state.message else null),
                onRefresh = viewModel::refresh,
                onOpenLocations = { open(AppDestination.LOCATIONS) },
                onOpenSettings = { open(AppDestination.SETTINGS) },
                onMessageShown = viewModel::consumeMessage,
                modifier = if (onDashboard) Modifier else Modifier.semantics { hideFromAccessibility() },
            )

            when (current) {
                AppDestination.LOCATIONS -> LocationManagerScreen(
                    settings = state.settings,
                    search = state.search,
                    message = state.message,
                    onSelect = viewModel::selectLocation,
                    onSetHome = viewModel::setHomeLocation,
                    onAddFavorite = viewModel::addFavorite,
                    onRemove = viewModel::removeLocation,
                    onMove = viewModel::moveFavorite,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onMessageShown = viewModel::consumeMessage,
                    onDismiss = goBack,
                )

                AppDestination.SETTINGS -> SettingsScreen(
                    onBack = goBack,
                    onOpenAppearance = { open(AppDestination.APPEARANCE) },
                    onOpenAbout = { open(AppDestination.ABOUT) },
                )

                AppDestination.APPEARANCE -> AppearanceScreen(
                    selectedMode = themeMode,
                    persistenceFailed = themePersistenceFailed,
                    onSelectMode = appearanceViewModel::setThemeMode,
                    onBack = goBack,
                )

                AppDestination.ABOUT -> AboutScreen(
                    onBack = goBack,
                    onOpenLicence = { id ->
                        licenceId = id
                        open(AppDestination.LICENSE)
                    },
                )

                AppDestination.LICENSE -> LicenceDetailScreen(
                    entry = AboutContent.licenceById(licenceId) ?: AboutContent.licences.first(),
                    onBack = goBack,
                )

                AppDestination.DASHBOARD -> Unit
            }
        }
    }
}
