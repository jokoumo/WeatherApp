package com.example.weatherapp.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherapp.R
import com.example.weatherapp.WeatherApplication
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.resolveDarkTheme
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.components.SecondaryScreenScaffold
import com.example.weatherapp.ui.locations.LocationListItem
import com.example.weatherapp.ui.locations.LocationSearchField
import com.example.weatherapp.ui.locations.LocationSearchResults
import com.example.weatherapp.ui.theme.WeatherAppTheme

/**
 * Configuration screen of every WetterPlus widget, opened by the launcher when a widget is added.
 *
 * Three decisions are made here and nowhere else: which location the widget shows, that the widget is
 * only configured once the user confirms, and that nothing is written when the user backs out. Android
 * removes the widget again when this activity finishes without [RESULT_OK], which is why the result is
 * set to cancelled before any content exists - a crashed screen must never leave a configured widget
 * behind.
 */
class WidgetConfigurationActivity : ComponentActivity() {

    private val container by lazy { (application as WeatherApplication).container }

    /** The App Widget id the launcher asked this screen about. */
    private val widgetId: Int by lazy {
        intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val viewModel by lazy {
        WidgetConfigurationViewModel(
            widgetId = widgetId,
            locationRepository = container.locationRepository,
            widgetConfigRepository = container.widgetConfigRepository,
            widgetRefreshCoordinator = container.widgetRefreshCoordinator,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val themeMode by container.appearanceViewModel.themeMode.collectAsStateWithLifecycle()
            WeatherAppTheme(darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                // The screen closes itself only after the configuration really is in storage.
                LaunchedEffect(state.saved) {
                    if (state.saved) {
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        )
                        finish()
                    }
                }
                WidgetConfigurationScreen(
                    state = state,
                    onBack = { finish() },
                    onModeSelected = viewModel::onModeSelected,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onLocationPicked = viewModel::onLocationPicked,
                    onSave = viewModel::save,
                )
            }
        }
    }
}

/** German label of a widget location mode. */
@StringRes
fun WidgetLocationMode.labelRes(): Int = when (this) {
    WidgetLocationMode.HOME -> R.string.widget_mode_home
    WidgetLocationMode.SELECTED -> R.string.widget_mode_selected
    WidgetLocationMode.FIXED -> R.string.widget_mode_fixed
}

/** German explanation of what choosing this mode means for the widget. */
@StringRes
fun WidgetLocationMode.summaryRes(): Int = when (this) {
    WidgetLocationMode.HOME -> R.string.widget_mode_home_description
    WidgetLocationMode.SELECTED -> R.string.widget_mode_selected_description
    WidgetLocationMode.FIXED -> R.string.widget_mode_fixed_description
}

/**
 * The screen itself, without the activity: the same layout the appearance page uses, so the choice
 * between three modes looks and behaves like a choice the user can already make elsewhere in the app.
 */
@Composable
fun WidgetConfigurationScreen(
    state: WidgetConfigurationUiState,
    onBack: () -> Unit,
    onModeSelected: (WidgetLocationMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onLocationPicked: (SavedLocation) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryScreenScaffold(
        title = stringResource(R.string.widget_configure_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.widget_configure_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WidgetLocationMode.entries.forEach { mode ->
                ModeRow(
                    mode = mode,
                    selected = state.mode == mode,
                    onSelect = { onModeSelected(mode) },
                )
            }
        }
        when (state.mode) {
            WidgetLocationMode.HOME -> if (!state.hasHomeLocation) {
                Spacer(Modifier.height(10.dp))
                InfoNote(text = stringResource(R.string.widget_configure_no_home))
            }

            WidgetLocationMode.SELECTED -> Unit

            WidgetLocationMode.FIXED -> FixedLocationSection(state, onQueryChange, onLocationPicked)
        }
        if (state.saveFailed) {
            Spacer(Modifier.height(10.dp))
            InfoNote(text = stringResource(R.string.widget_configure_failed))
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.width(10.dp))
            Button(onClick = onSave, enabled = state.canSave) { Text(stringResource(R.string.action_save)) }
        }
    }
}

/** The part that only exists for a fixed location: the chosen place, the stored ones, and a search. */
@Composable
private fun FixedLocationSection(
    state: WidgetConfigurationUiState,
    onQueryChange: (String) -> Unit,
    onLocationPicked: (SavedLocation) -> Unit,
) {
    Spacer(Modifier.height(10.dp))
    state.fixedLocation?.let { location ->
        Text(
            text = stringResource(R.string.widget_configure_fixed_selected, location.name),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
    }
    if (state.storedLocations.isNotEmpty()) {
        Text(
            text = stringResource(R.string.widget_configure_stored_locations),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.storedLocations.forEach { location ->
            LocationListItem(
                location = location,
                onClick = { onLocationPicked(location) },
                isSelected = location.id == state.fixedLocation?.id,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
    Text(
        text = stringResource(R.string.widget_configure_other_location),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(6.dp))
    LocationSearchField(query = state.search.query, onQueryChange = onQueryChange)
    Spacer(Modifier.height(8.dp))
    LocationSearchResults(search = state.search, onItemClick = onLocationPicked)
    if (state.fixedLocation == null) {
        InfoNote(text = stringResource(R.string.widget_configure_no_fixed_location))
    }
}

/** One mode row: radio button, name and the sentence that explains what the widget will do. */
@Composable
private fun ModeRow(
    mode: WidgetLocationMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(mode.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(mode.summaryRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
