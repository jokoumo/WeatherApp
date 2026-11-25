package com.example.weatherapp.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherapp.R
import com.example.weatherapp.data.repository.FavoriteToggle
import com.example.weatherapp.data.repository.SnapshotSource
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.domain.CachePolicy
import com.example.weatherapp.domain.buildForecastSections
import com.example.weatherapp.domain.zoneIdOrDefault
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
    LOCATION_REMOVED_FROM_FAVORITES,
    HOME_LOCATION_SET,
    HOME_LOCATION_PROTECTED,
    HISTORY_CLEARED,
}

/** German snackbar text for a [UserMessage]. */
@StringRes
fun UserMessage.messageRes(): Int = when (this) {
    UserMessage.LOCATION_ADDED -> R.string.message_location_added
    UserMessage.LOCATION_DUPLICATE -> R.string.message_location_duplicate
    UserMessage.LOCATION_REMOVED_FROM_FAVORITES -> R.string.message_location_unfavorited
    UserMessage.HOME_LOCATION_SET -> R.string.message_home_location_set
    UserMessage.HOME_LOCATION_PROTECTED -> R.string.message_home_location_protected
    UserMessage.HISTORY_CLEARED -> R.string.message_history_cleared
}

/**
 * Snackbar text for what a favorite toggle actually did.
 *
 * The outcome comes from the repository, so the message describes the transition that really
 * happened rather than the one the interface expected when the tap arrived.
 */
private fun FavoriteToggle.toUserMessage(): UserMessage = when (this) {
    FavoriteToggle.ADDED -> UserMessage.LOCATION_ADDED
    FavoriteToggle.REMOVED -> UserMessage.LOCATION_REMOVED_FROM_FAVORITES
    FavoriteToggle.HOME_LOCATION -> UserMessage.HOME_LOCATION_PROTECTED
    FavoriteToggle.UNCHANGED -> UserMessage.LOCATION_DUPLICATE
}

/** Weather state of the location that is currently displayed. */
data class WeatherUiState(
    val location: SavedLocation? = null,
    val current: CurrentWeather? = null,
    val sections: ForecastSections = ForecastSections.EMPTY,
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
            sections.hourlyEntries.isNotEmpty() ||
            sections.detailedDays.isNotEmpty() ||
            sections.extendedDays.isNotEmpty()

    /** Temperature range of the hours belonging to today. */
    val todayMin: Double? get() = sections.todayHours.mapNotNull { it.temperatureCelsius }.minOrNull()
    val todayMax: Double? get() = sections.todayHours.mapNotNull { it.temperatureCelsius }.maxOrNull()

    /**
     * Timestamp shown on the hero card, or null when no honest timestamp exists.
     *
     * The observation time reported by the API is preferred. With no current weather block at
     * all the retrieval time of the snapshot is shown, because that is what actually happened;
     * when a current block exists but its timestamp is missing or invalid nothing is shown, so
     * an unknown observation time is never disguised as a known one.
     */
    val displayInstant: Instant?
        get() = when {
            current == null -> fetchedAt
            else -> current.instant
        }

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
        get() = settingsLoaded &&
            settings.hasSelectableLocation &&
            !weather.hasData &&
            weather.isRefreshing

    /**
     * Star state of the location shown on the dashboard.
     *
     * The home location counts as saved as well, so its star is filled too; [isHomeLocation]
     * tells the UI why, and the star of that location cannot be switched off.
     */
    val displayedLocationIsFavorite: Boolean
        get() = displayedLocationId?.let { settings.isFavorite(it) } ?: false

    val displayedLocationIsHome: Boolean
        get() = displayedLocationId?.let { settings.isHomeLocation(it) } ?: false

    val displayedLocationIsRecent: Boolean
        get() = displayedLocationId?.let { settings.isRecent(it) } ?: false

    private val displayedLocationId: String?
        get() = weather.location?.id ?: settings.resolveSelectedLocation()?.id
}

/**
 * Coordinates location selection, forecast loading and search for the whole app.
 *
 * Only one load runs at a time: switching locations cancels the previous load, and results
 * are dropped when the user has already moved on to another location. The last snapshot is
 * kept so that the rolling hourly window can be rebuilt from cached data whenever the clock
 * moves into the next hour, without requesting anything from the network.
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

    /** Snapshot currently on screen, used to rebuild the hourly window without loading again. */
    private var displayedSnapshot: WeatherSnapshot? = null

    /** Local date for which a day-change refresh was already attempted. */
    private var dayChangeRefreshedOn: LocalDate? = null

    init {
        // Once per app start: cache rows that no stored location refers to any more - left behind
        // by an earlier version or by a history entry that was evicted - are removed here. Listing
        // the rows before reading the settings is deliberate, so a location that is added in the
        // meantime counts as retained and keeps its forecast.
        viewModelScope.launch { reconcileOrphanedCache() }

        viewModelScope.launch {
            locationRepository.settings.collect { settings ->
                _uiState.update { it.copy(settingsLoaded = true, settings = settings) }
                val selected = settings.resolveSelectedLocation()
                when {
                    selected == null -> {
                        loadJob?.cancel()
                        displayedSnapshot = null
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
        viewModelScope.launch {
            applyLocationChange { locationRepository.selectLocation(locationId) }
        }
    }

    /** Manual refresh, for example through pull to refresh or the toolbar button. */
    fun refresh() {
        val location = _uiState.value.weather.location
            ?: _uiState.value.settings.resolveSelectedLocation()
            ?: return
        loadWeather(location, forceRefresh = true)
    }

    /**
     * Rebuilds the visible sections from the last snapshot without touching the network, so the
     * rolling hourly window follows the clock as hours pass. Called when the next local hour
     * begins and whenever the app comes back to the foreground; a window that simply moved on
     * by one hour never causes a request.
     *
     * A cached snapshot whose local calendar day has passed is refreshed in the background,
     * once per day, while the current data stays on screen.
     */
    fun onClockTick() {
        val snapshot = displayedSnapshot ?: return
        val stateLocation = _uiState.value.weather.location ?: return
        if (stateLocation.id != snapshot.locationId) return
        val now = timeProvider()
        _uiState.update { state ->
            val weather = state.weather
            if (weather.location?.id != snapshot.locationId) {
                state
            } else {
                state.copy(
                    weather = render(
                        location = weather.location ?: stateLocation,
                        snapshot = snapshot,
                        source = weather.snapshotSource,
                        failure = weather.failure,
                        refreshing = weather.isRefreshing,
                    ),
                )
            }
        }
        refreshAfterLocalDayChange(snapshot, now)
    }

    /**
     * Called when the app comes back to the foreground. Stale data is refreshed silently
     * while the currently displayed values stay on screen. The window is rebuilt first, which
     * also covers a device clock that moved while the app was in the background.
     */
    fun onForeground() {
        onClockTick()
        val weather = _uiState.value.weather
        val location = weather.location ?: return
        val fetchedAt = weather.fetchedAt ?: return
        if (weather.isRefreshing) return
        val zone = weather.timeZone ?: zoneIdOrDefault(location.timeZoneId, SavedLocation.DEFAULT_TIME_ZONE_ID)
        if (CachePolicy.isStale(fetchedAt, timeProvider(), zone)) {
            loadWeather(location, forceRefresh = true)
        }
    }

    fun setHomeLocation(location: SavedLocation) {
        viewModelScope.launch {
            applyLocationChange { locationRepository.setHomeLocation(location) }
            clearSearch()
            _uiState.update { it.copy(message = UserMessage.HOME_LOCATION_SET) }
        }
    }

    /**
     * Switches the star of [location] between saved and remembered.
     *
     * The view model does not decide what the tap means: the repository reads the current role and
     * applies the transition inside one serialized mutation, and the reported outcome is what the
     * snackbar shows. Two rapid taps therefore produce two real state changes instead of both
     * taps adding the same place, and the home location stays protected no matter how the UI state
     * happened to look when the tap arrived.
     */
    fun toggleFavorite(location: SavedLocation) {
        viewModelScope.launch {
            val result = applyLocationChange { locationRepository.toggleFavorite(location) }
            _uiState.update { it.copy(message = result.change.toUserMessage()) }
        }
    }

    fun addFavorite(location: SavedLocation) {
        viewModelScope.launch {
            val added = applyLocationChange { locationRepository.addFavorite(location) }
            _uiState.update {
                it.copy(
                    message = if (added) UserMessage.LOCATION_ADDED else UserMessage.LOCATION_DUPLICATE,
                )
            }
        }
    }

    /**
     * Shows the location of a tapped search suggestion.
     *
     * The repository selects it and remembers it in the history; nothing is added to the favorites,
     * so browsing a place never saves it. Going through [applyLocationChange] is what lets the
     * forecast of a history entry that this very selection evicted be dropped again.
     */
    fun selectSearchedLocation(location: SavedLocation) {
        clearSearch()
        viewModelScope.launch {
            applyLocationChange { locationRepository.selectLocation(location) }
        }
    }

    /** Removes one entry from the history. */
    fun removeRecentLocation(locationId: String) {
        viewModelScope.launch {
            applyLocationChange { locationRepository.removeRecentLocation(locationId) }
        }
    }

    /** Empties the history; favorites, the home location and the appearance stay untouched. */
    fun clearRecentLocations() {
        viewModelScope.launch {
            applyLocationChange { locationRepository.clearRecentLocations() }
            _uiState.update { it.copy(message = UserMessage.HISTORY_CLEARED) }
        }
    }

    /**
     * Forgets a favorite or a history entry completely. The repository keeps its hands off the home
     * location, so this can never empty the app of its protected place.
     */
    fun removeLocation(locationId: String) {
        viewModelScope.launch {
            applyLocationChange { locationRepository.removeLocation(locationId) }
        }
    }

    fun moveFavorite(locationId: String, delta: Int) {
        // Reordering cannot add or remove a location, so no cache reconciliation is needed.
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
            if (!isStillSelected(location)) {
                // The response was already written to the cache when the user moved on. If no role
                // refers to that place any more, the row is orphaned and goes away again; a place
                // that is still kept somewhere holds on to its forecast.
                dropCacheIfNoLongerNeeded(location.id)
                return@launch
            }
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

    /**
     * Runs a location mutation and afterwards removes the cached forecasts that stopped being
     * referenced.
     *
     * The repository decides on its own which locations a mutation drops - a history entry that
     * falls off the list, a place that moves between roles, the entry a search pushed out - so the
     * view model never has to guess. Comparing the retained ids before and after the mutation finds
     * every candidate, and [dropOrphanedCache] decides which of them are really gone.
     */
    private suspend fun <R> applyLocationChange(mutate: suspend () -> R): R {
        val before = locationRepository.currentSettings()
        val result = mutate()
        dropOrphanedCache(before, locationRepository.currentSettings())
        return result
    }

    /**
     * Deletes the cache rows of locations that stopped being referenced.
     *
     * The candidates are re-checked against the newest settings immediately before the delete, so a
     * location that was added again while this was running keeps its forecast. A lost race can
     * therefore only ever leave a row in place until the next reconciliation; it can never delete
     * the data of a location that is still in use. That is the consistency model: cleanup is
     * best-effort, retention is guaranteed.
     */
    private suspend fun dropOrphanedCache(before: LocationSettings, after: LocationSettings) {
        val candidates = before.retainedLocationIds - after.retainedLocationIds
        if (candidates.isEmpty()) return
        val stillRetained = locationRepository.currentSettings().retainedLocationIds
        weatherRepository.dropCaches(candidates.filterNot { it in stillRetained })
    }

    /**
     * Removes cache rows that no stored location references any more. Runs once per app start, on a
     * table bounded by the number of locations the app can know about, so it is cheap and never part
     * of a recomposition.
     */
    private suspend fun reconcileOrphanedCache() {
        val cachedIds = weatherRepository.cachedLocationIds()
        if (cachedIds.isEmpty()) return
        val retained = locationRepository.currentSettings().retainedLocationIds
        weatherRepository.dropCaches(cachedIds.filterNot { it in retained })
    }

    /**
     * Drops the cached forecast of [locationId] only after the settings say that no role refers to
     * it any more.
     *
     * The cache is keyed by location id, so a row of a place nobody keeps can never be read again
     * and would stay in the database forever. A place that merely lost its favorite status, on the
     * other hand, is still selected or still sits in the history: its forecast stays, which is what
     * keeps the dashboard responsive and the app usable offline.
     */
    private suspend fun dropCacheIfNoLongerNeeded(locationId: String) {
        if (locationRepository.currentSettings().retainedLocationIds.contains(locationId)) return
        weatherRepository.dropCache(locationId)
    }

    /**
     * Silently refreshes a snapshot that stopped being valid because the location's calendar day
     * changed. Bounded to one attempt per local date, so a failing network cannot turn the
     * hourly tick into a request per hour. The ordinary cache lifetime is deliberately not a
     * trigger here: a window that only moved on by one hour must never cause a request, and the
     * regular refresh paths (foreground, pull to refresh, button) keep handling the cache age.
     */
    private fun refreshAfterLocalDayChange(snapshot: WeatherSnapshot, now: Instant) {
        val today = now.atZone(snapshot.timeZone).toLocalDate()
        if (dayChangeRefreshedOn == today) return
        if (snapshot.fetchedAt.atZone(snapshot.timeZone).toLocalDate() == today) return
        if (_uiState.value.weather.isRefreshing) return
        val location = _uiState.value.weather.location ?: return
        dayChangeRefreshedOn = today
        loadWeather(location, forceRefresh = false)
    }

    private fun render(
        location: SavedLocation,
        snapshot: WeatherSnapshot?,
        source: SnapshotSource?,
        failure: DataLoadFailure?,
        refreshing: Boolean,
    ): WeatherUiState {
        if (snapshot == null) {
            displayedSnapshot = null
            return WeatherUiState(
                location = location,
                isRefreshing = refreshing,
                hasLoadedOnce = true,
                failure = failure,
            )
        }
        displayedSnapshot = snapshot
        val now = timeProvider()
        val sections = buildForecastSections(snapshot, now)
        return WeatherUiState(
            location = location,
            current = snapshot.current,
            sections = sections,
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
