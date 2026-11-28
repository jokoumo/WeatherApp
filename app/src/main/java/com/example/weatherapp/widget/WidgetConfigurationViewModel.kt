package com.example.weatherapp.widget

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.ui.SearchUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a widget is showing while it is being set up. */
data class WidgetConfigurationUiState(
    val mode: WidgetLocationMode = WidgetLocationMode.HOME,
    val fixedLocation: SavedLocation? = null,
    /** Places the app already knows, offered so a common case needs no search request. */
    val storedLocations: List<SavedLocation> = emptyList(),
    /** False on a fresh install: a "Hauptstandort" widget would then have nothing to show. */
    val hasHomeLocation: Boolean = false,
    val hasSelectedLocation: Boolean = false,
    val search: SearchUiState = SearchUiState(),
    /** Set once the configuration is in storage; the activity closes the screen on it. */
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
    val locationChangedDuringSave: Boolean = false,
    val isSaving: Boolean = false,
    val isReady: Boolean = true,
) {
    val canSave: Boolean
        get() = isReady && !isSaving && !saved && hasValidLocation

    internal val hasValidLocation: Boolean
        get() = when (mode) {
            WidgetLocationMode.HOME -> hasHomeLocation
            WidgetLocationMode.SELECTED -> hasSelectedLocation
            WidgetLocationMode.FIXED -> fixedLocation.isValidWidgetLocation()
        }

    internal fun withSettings(settings: LocationSettings): WidgetConfigurationUiState = copy(
        storedLocations = settings.selectableLocations.filter { it.isValidWidgetLocation() },
        hasHomeLocation = settings.homeLocation.isValidWidgetLocation(),
        hasSelectedLocation = settings.resolveSelectedLocation().isValidWidgetLocation(),
    )
}

private fun SavedLocation?.isValidWidgetLocation(): Boolean =
    this != null && id.isNotBlank() && name.isNotBlank() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0

/**
 * Owns the state of the widget configuration screen for exactly one App Widget id.
 *
 * The screen writes nothing before the user confirms: opening it, browsing the search results and
 * pressing back all leave the previous configuration untouched, and cancelling the add removes the
 * widget itself. That matters because a half-written configuration would make a widget show a place
 * nobody chose.
 */
class WidgetConfigurationViewModel(
    private val widgetId: Int,
    private val locationRepository: LocationRepository,
    private val widgetConfigRepository: WidgetConfigRepository,
    private val widgetConfigured: suspend (Int) -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WidgetConfigurationUiState(isReady = false))
    val uiState = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val configuration = widgetConfigRepository.configuration(widgetId)
            _uiState.update { state ->
                state.copy(mode = configuration?.mode ?: state.mode, fixedLocation = configuration?.fixedLocation)
            }
            locationRepository.settings.collect { settings ->
                _uiState.update { it.withSettings(settings).copy(isReady = true) }
            }
        }
    }

    fun onModeSelected(mode: WidgetLocationMode) {
        if (!_uiState.value.isReady || _uiState.value.isSaving) return
        if (mode != WidgetLocationMode.FIXED) clearSearch()
        _uiState.update { it.copy(mode = mode, saveFailed = false, locationChangedDuringSave = false) }
    }

    /**
     * Searches for a place, with the same debounce and the same minimum length as the search inside
     * the app, so the two screens answer a query in the same way.
     */
    fun onSearchQueryChange(rawQuery: String) {
        val query = rawQuery.trim()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                search = SearchUiState(
                    query = rawQuery,
                    queryTooShort = query.isNotEmpty() && query.length < LocationRepository.MIN_SEARCH_QUERY_LENGTH,
                ),
            )
        }
        if (query.length < LocationRepository.MIN_SEARCH_QUERY_LENGTH) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _uiState.update { it.copy(search = it.search.copy(isSearching = true)) }
            val outcome = locationRepository.searchLocations(query)
            if (_uiState.value.search.query.trim() != outcome.query) return@launch
            _uiState.update {
                it.copy(
                    search = SearchUiState(
                        query = rawQuery,
                        results = outcome.locations,
                        failure = outcome.failure,
                    ),
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(search = SearchUiState()) }
    }

    /**
     * Chooses the place a fixed-location widget shows. Selecting it also selects the fixed mode,
     * because picking a place only ever means "this widget shows this place, whatever the app does".
     */
    fun onLocationPicked(location: SavedLocation) {
        if (_uiState.value.isSaving || !location.isValidWidgetLocation()) return
        clearSearch()
        _uiState.update {
            it.copy(
                mode = WidgetLocationMode.FIXED,
                fixedLocation = location,
                saveFailed = false,
                locationChangedDuringSave = false,
            )
        }
    }

    /**
     * Writes the configuration for [widgetId] and asks the coordinator to refresh the widget.
     *
     * The write and the first refresh are kept together on purpose: a saved configuration whose
     * widget still shows yesterday's values would look like a broken widget, and a widget that shows
     * a place whose configuration was never stored would look like a broken setting.
     */
    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, saveFailed = false, locationChangedDuringSave = false) }
        viewModelScope.launch {
            try {
                val validated = state.withSettings(locationRepository.currentSettings())
                if (!validated.hasValidLocation) {
                    _uiState.update { it.copy(isSaving = false, locationChangedDuringSave = true) }
                    return@launch
                }
                widgetConfigRepository.save(
                    WidgetConfiguration(
                        widgetId = widgetId,
                        mode = state.mode,
                        fixedLocation = state.fixedLocation.takeIf { state.mode == WidgetLocationMode.FIXED },
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Could not save widget configuration", failure)
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
                return@launch
            }
            try {
                widgetConfigured(widgetId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Could not schedule the initial widget refresh", failure)
            }
            _uiState.update { it.copy(saved = true, isSaving = false) }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
        const val TAG = "WidgetConfiguration"
    }
}
