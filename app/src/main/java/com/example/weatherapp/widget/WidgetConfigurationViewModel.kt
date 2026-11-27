package com.example.weatherapp.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.ui.SearchUiState
import kotlinx.coroutines.Job
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
    val search: SearchUiState = SearchUiState(),
    /** Set once the configuration is in storage; the activity closes the screen on it. */
    val saved: Boolean = false,
    val saveFailed: Boolean = false,
) {
    /** A fixed-location widget without a place has nothing to show, so it cannot be saved yet. */
    val canSave: Boolean
        get() = mode != WidgetLocationMode.FIXED || fixedLocation != null
}

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
    private val widgetRefreshCoordinator: WidgetRefreshCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WidgetConfigurationUiState())
    val uiState = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = locationRepository.currentSettings()
            _uiState.update {
                it.copy(
                    storedLocations = settings.selectableLocations,
                    hasHomeLocation = settings.homeLocation != null,
                )
            }
        }
    }

    fun onModeSelected(mode: WidgetLocationMode) {
        _uiState.update { it.copy(mode = mode, saveFailed = false) }
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
        _uiState.update {
            it.copy(mode = WidgetLocationMode.FIXED, fixedLocation = location, saveFailed = false)
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
        if (!state.canSave) {
            _uiState.update { it.copy(saveFailed = true) }
            return
        }
        viewModelScope.launch {
            runCatching {
                widgetConfigRepository.save(
                    WidgetConfiguration(
                        widgetId = widgetId,
                        mode = state.mode,
                        fixedLocation = state.fixedLocation.takeIf { state.mode == WidgetLocationMode.FIXED },
                    ),
                )
            }.onSuccess {
                // Handed over before the screen closes: the activity finishes as soon as this state
                // says "saved", and viewModelScope ends with the ViewModel.
                widgetRefreshCoordinator.onWidgetConfigured(widgetId)
                _uiState.update { it.copy(saved = true) }
            }.onFailure {
                _uiState.update { it.copy(saveFailed = true) }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
