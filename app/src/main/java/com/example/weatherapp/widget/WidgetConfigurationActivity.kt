package com.example.weatherapp.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.weatherapp.R
import com.example.weatherapp.WeatherApplication
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.resolveDarkTheme
import com.example.weatherapp.ui.components.InfoNote
import com.example.weatherapp.ui.components.SecondaryScreenHeader
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

    private val viewModel by viewModels<WidgetConfigurationViewModel> {
        viewModelFactory {
            initializer {
                WidgetConfigurationViewModel(
                    widgetId = widgetId,
                    locationRepository = container.locationRepository,
                    widgetConfigRepository = container.widgetConfigRepository,
                    widgetConfigured = container.widgetRefreshCoordinator::onWidgetConfigured,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        if (provider?.packageName != packageName || WidgetRegistry.widgetFor(provider.className) == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val themeMode by container.appearanceViewModel.themeMode.collectAsStateWithLifecycle()
            WeatherAppTheme(darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                BackHandler(enabled = state.isSaving || state.search.query.isNotEmpty()) {
                    if (!state.isSaving) viewModel.clearSearch()
                }
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
                    onBack = { if (!state.isSaving) finish() },
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
    val focusManager = LocalFocusManager.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val compactHeight = maxHeight < 500.dp * LocalDensity.current.fontScale
            Column {
                SecondaryScreenHeader(
                    title = stringResource(R.string.widget_configure_title),
                    onBack = onBack,
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp),
                )
                if (!compactHeight) ConfigurationModes(state, onModeSelected)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (compactHeight) ConfigurationModes(state, onModeSelected, inset = false)
                    when (state.mode) {
                        WidgetLocationMode.HOME -> if (!state.hasHomeLocation) {
                            InfoNote(text = stringResource(R.string.widget_configure_no_home))
                        }
                        WidgetLocationMode.SELECTED -> if (!state.hasSelectedLocation) {
                            InfoNote(text = stringResource(R.string.widget_configure_no_selected))
                        }
                        WidgetLocationMode.FIXED -> FixedLocationSection(state, onQueryChange) { location ->
                            focusManager.clearFocus()
                            onLocationPicked(location)
                        }
                    }
                }
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        state.fixedLocation?.takeIf { state.mode == WidgetLocationMode.FIXED }?.let {
                            Text(
                                text = stringResource(R.string.widget_configure_fixed_selected, it.name),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (state.saveFailed) InfoNote(text = stringResource(R.string.widget_configure_failed))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            OutlinedButton(onClick = onBack, enabled = !state.isSaving) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(onClick = onSave, enabled = state.canSave) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigurationModes(
    state: WidgetConfigurationUiState,
    onModeSelected: (WidgetLocationMode) -> Unit,
    inset: Boolean = true,
) {
    Column(
        modifier = Modifier.selectableGroup().padding(horizontal = if (inset) 16.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WidgetLocationMode.entries.forEach { mode ->
            ModeRow(
                mode = mode,
                selected = state.mode == mode,
                available = when (mode) {
                    WidgetLocationMode.HOME -> state.hasHomeLocation
                    WidgetLocationMode.SELECTED -> state.hasSelectedLocation
                    WidgetLocationMode.FIXED -> true
                },
                enabled = state.isReady && !state.isSaving,
                onSelect = { onModeSelected(mode) },
            )
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
    LocationSearchField(query = state.search.query, onQueryChange = onQueryChange)
    LocationSearchResults(search = state.search, onItemClick = onLocationPicked)
    if (state.fixedLocation == null && state.search.query.isBlank()) {
        InfoNote(text = stringResource(R.string.widget_configure_no_fixed_location))
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
    }
}

/** One mode row: radio button, name and the sentence that explains what the widget will do. */
@Composable
private fun ModeRow(
    mode: WidgetLocationMode,
    selected: Boolean,
    available: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                Text(
                    text = stringResource(if (available) mode.summaryRes() else R.string.widget_configure_mode_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
