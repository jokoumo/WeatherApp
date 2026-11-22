package com.example.weatherapp.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherapp.R
import com.example.weatherapp.data.repository.SnapshotSource
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.domain.CachePolicy
import com.example.weatherapp.domain.buildForecastSections
import com.example.weatherapp.domain.currentHourIndex
import com.example.weatherapp.domain.model.CurrentWeather
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.domain.ForecastSections
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One-shot feedback message rendered as a snackbar; text lives in resources. */
enum class UserMessage {
    LOCATION_ADDED,
    LOCATION_DUPLICATE,
    HOME_LOCATION_SET,
}

/** German snackbar text for a [UserMessage]. */
@StringRes
fun UserMessage.messageRes(): Int = when (this) {
    UserMessage.LOCATION_ADDED -> R.string.message_location_added
    UserMessage.LOCATION_DUPLICATE -> R.string.message_location_duplicate
    UserMessage.HOME_LOCATION_SET -> R.string.message_home_location_set
}

/** Weather state of the location that is currently displayed. */
data class WeatherUiState(
    val location: SavedLocation? = null,
    val current: CurrentWeather? = null,
    val sections: ForecastSections = ForecastSections.EMPTY,
    val hourlyAnchorIndex: Int = 0,
    val snapshotSource: SnapshotSource? = null,
    val fetchedAt: Instant? = null,
    val timeZone: ZoneId? = null,
    val today: LocalDate? = null,
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val failure: DataLoadFailure? = null,
) {
    val hasData: Boolean
        get() = current != null ||
            sections.todayHours.isNotEmpty() ||
            sections.detailedDays.isNotEmpty() ||
            sections.extendedDays.isNotEmpty()

    /** Temperature range of the hours belonging to today. */
    val todayMin: Double? get() = sections.todayHours.mapNotNull { it.temperatureCelsius }.minOrNull()
    val todayMax: Double? get() = sections.todayHours.mapNotNull { it.temperatureCelsius }.maxOrNull()

    /** Temperature range across the compact forecast list, used for its range bars. */
    val extendedMin: Double?
        get() = sections.extendedDays.mapNotNull { it.minTemperatureCelsius }.minOrNull()
    val extendedMax: Double?
        get() = sections.extendedDays.mapNotNull { it.maxTemperatureCelsius }.maxOrNull()

    /** True when data is shown but the last refresh failed, so it may be outdated. */
    val isShowingStaleData: Boolean
        get() = hasData && failure != null
}

/** State of the location search field used during setup and when adding favorites. */
data class SearchUiState(
    val query: String = "",
    val results: List<SavedLocation> = emptyList(),
    val isSearching: Boolean = false,
    val failure: DataLoadFailure? = null,
    val queryTooShort: Boolean = false,
) {
    val hasRequestedSearch: Boolean
        get() = query.isNotBlank() && !isSearching && !queryTooShort
}

data class MainUiState(
    val settingsLoaded: Boolean = false,
    val settings: LocationSettings = LocationSettings(),
    val weather: WeatherUiState = WeatherUiState(),
    val search: SearchUiState = SearchUiState(),
    val message: UserMessage? = null,
) {
    /** True while the dashboard has no data at all and is still loading. */
    val isInitialLoading: Boolean
        get() = settingsLoaded && settings.hasAnyLocation && !weather.hasData && weather.isRefreshing
}

/**
 * Coordinates location selection, forecast loading and search for the whole app.
 *
 * Only one load runs at a time: switching locations cancels the previous load, and results
 * are dropped when the user has already moved on to another location.
 */
class MainViewModel(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val timeProvider: () -> Instant = { Instant.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            locationRepository.settings.collect { settings ->
                _uiState.update { it.copy(settingsLoaded = true, settings = settings) }
                val selected = settings.resolveSelectedLocation()
                when {
                    selected == null -> {
                        loadJob?.cancel()
                        _uiState.update { it.copy(weather = WeatherUiState()) }
                    }

                    selected.id != _uiState.value.weather.location?.id ->
                        loadWeather(selected, forceRefresh = false)
                }
            }
        }
    }

    fun selectLocation(locationId: String) {
        if (locationId == _uiState.value.weather.location?.id) return
        viewModelScope.launch { locationRepository.selectLocation(locationId) }
    }

    /** Manual refresh, for example through pull to refresh or the toolbar button. */
    fun refresh() {
        val location = _uiState.value.weather.location
            ?: _uiState.value.settings.resolveSelectedLocation()
            ?: return
        loadWeather(location, forceRefresh = true)
    }

    /**
     * Called when the app comes back to the foreground. Stale data is refreshed silently
     * while the currently displayed values stay on screen.
     */
    fun onForeground() {
        val weather = _uiState.value.weather
        val location = weather.location ?: return
        val fetchedAt = weather.fetchedAt ?: return
        if (weather.isRefreshing) return
        if (CachePolicy.isStale(fetchedAt, timeProvider())) {
            loadWeather(location, forceRefresh = true)
        }
    }

    fun setHomeLocation(location: SavedLocation) {
        viewModelScope.launch {
            locationRepository.setHomeLocation(location)
            clearSearch()
            _uiState.update { it.copy(message = UserMessage.HOME_LOCATION_SET) }
        }
    }

    fun addFavorite(location: SavedLocation) {
        viewModelScope.launch {
            val added = locationRepository.addFavorite(location)
            _uiState.update {
                it.copy(
                    message = if (added) UserMessage.LOCATION_ADDED else UserMessage.LOCATION_DUPLICATE,
                )
            }
        }
    }

    fun removeLocation(locationId: String) {
        viewModelScope.launch { locationRepository.removeLocation(locationId) }
    }

    fun moveFavorite(locationId: String, delta: Int) {
        viewModelScope.launch { locationRepository.moveFavorite(locationId, delta) }
    }

    fun onSearchQueryChange(rawQuery: String) {
        val query = rawQuery.trim()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                search = SearchUiState(
                    query = rawQuery,
                    queryTooShort = query.isNotEmpty() && query.length < MIN_SEARCH_LENGTH,
                ),
            )
        }
        if (query.length < MIN_SEARCH_LENGTH) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _uiState.update { it.copy(search = it.search.copy(isSearching = true)) }
            val outcome = locationRepository.searchLocations(query)
            // Ignore results of a query the user has already changed.
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

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadWeather(location: SavedLocation, forceRefresh: Boolean) {
        loadJob?.cancel()
        _uiState.update { state ->
            if (state.weather.location?.id == location.id) {
                state.copy(weather = state.weather.copy(isRefreshing = true, failure = null))
            } else {
                state.copy(weather = WeatherUiState(location = location, isRefreshing = true))
            }
        }
        loadJob = viewModelScope.launch {
            // Cached data is shown immediately, then replaced when the refresh finishes.
            val cached = weatherRepository.loadCached(location)
            if (cached != null && isStillSelected(location)) {
                _uiState.update {
                    it.copy(
                        weather = render(
                            location = location,
                            snapshot = cached,
                            source = SnapshotSource.CACHED,
                            failure = null,
                            refreshing = true,
                        ),
                    )
                }
            }
            val result = weatherRepository.load(location, forceRefresh = forceRefresh)
            if (!isStillSelected(location)) return@launch
            _uiState.update {
                it.copy(
                    weather = render(
                        location = location,
                        snapshot = result.snapshot,
                        source = result.source,
                        failure = result.failure,
                        refreshing = false,
                    ),
                )
            }
        }
    }

    private fun isStillSelected(location: SavedLocation): Boolean =
        _uiState.value.weather.location?.id == location.id &&
            _uiState.value.settings.resolveSelectedLocation()?.id == location.id

    private fun render(
        location: SavedLocation,
        snapshot: WeatherSnapshot?,
        source: SnapshotSource?,
        failure: DataLoadFailure?,
        refreshing: Boolean,
    ): WeatherUiState {
        if (snapshot == null) {
            return WeatherUiState(
                location = location,
                isRefreshing = refreshing,
                hasLoadedOnce = true,
                failure = failure,
            )
        }
        val now = timeProvider()
        val sections = buildForecastSections(snapshot, now)
        return WeatherUiState(
            location = location,
            current = snapshot.current,
            sections = sections,
            hourlyAnchorIndex = currentHourIndex(sections.todayHours, now),
            snapshotSource = source,
            fetchedAt = snapshot.fetchedAt,
            timeZone = snapshot.timeZone,
            today = now.atZone(snapshot.timeZone).toLocalDate(),
            isRefreshing = refreshing,
            hasLoadedOnce = true,
            failure = failure,
        )
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MILLIS = 300L
        private const val MIN_SEARCH_LENGTH = 2
    }
}
