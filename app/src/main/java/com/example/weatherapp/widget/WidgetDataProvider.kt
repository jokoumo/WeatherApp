package com.example.weatherapp.widget

import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.data.repository.SnapshotSource
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.domain.CachePolicy
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.domain.zoneIdOrDefault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Everything a widget needs in order to render one location.
 *
 * [snapshot] can be null while [location] is not: a location is known before its weather has
 * arrived, and a widget has to name the place it belongs to even in the loading and the error state.
 * [failure] says why a refresh did not work; cached values may still be on screen next to it.
 */
data class WidgetData(
    val location: SavedLocation,
    val snapshot: WeatherSnapshot?,
    val source: SnapshotSource? = null,
    val failure: DataLoadFailure? = null,
    val now: Instant,
    val awaitingFirstForecast: Boolean = false,
) {
    val hasData: Boolean get() = snapshot != null && !snapshot.isEmpty

    /** True when data is on screen but the last refresh failed, so it may be outdated. */
    val isShowingStaleData: Boolean get() = hasData && failure != null

    /** Time zone of the location, falling back to the stored id of the location itself. */
    val timeZone: ZoneId get() = snapshot?.timeZone
        ?: zoneIdOrDefault(location.timeZoneId, SavedLocation.DEFAULT_TIME_ZONE_ID)

    val fetchedAt: Instant? get() = snapshot?.fetchedAt

    /** Cached values older than the policy's lifetime are presented as possibly outdated. */
    val isProbablyOutdated: Boolean
        get() = fetchedAt?.let { CachePolicy.isProbablyOutdated(it, now) } ?: false
}

/**
 * Loads the weather a widget instance should show.
 *
 * The path is the one the dashboard already uses - cached data first, the existing repository for
 * everything else - which is why a widget never needs its own request, its own cache or its own
 * freshness rules. [loadForWidget] turns a widget id into location plus data; [loadForLocation] does
 * the same for the background refresh, where several widgets may name the same place.
 *
 * Requests for one location are serialised per location id. Two widgets of the same city that both
 * find a stale cache therefore produce one network call: the first one writes the fresh row, the
 * second one reads it back and does not touch the network at all. Serialising instead of sharing a
 * deferred result keeps this simple and leaves no half-finished request behind when a widget update
 * is cancelled.
 */
class WidgetDataProvider(
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val configRepository: WidgetConfigRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val registryLock = Mutex()
    private val locationLocks = mutableMapOf<String, LocationLock>()
    private val renderRevision = MutableStateFlow(0L)
    private val refreshStatus = MutableStateFlow<Map<String, RefreshStatus>>(emptyMap())

    internal data class Rendering(val configuration: WidgetConfiguration?, val data: WidgetData?)
    private data class RefreshStatus(
        val updating: Boolean = false,
        val failure: DataLoadFailure? = null,
        val revision: Long = 0,
    )
    private data class RenderInput(
        val configuration: WidgetConfiguration?,
        val location: SavedLocation?,
        val status: RefreshStatus?,
        val revision: Long,
    )

    /** Active Glance sessions must observe changes because update() can reuse their composition. */
    internal fun rendering(widgetId: Int) = combine(
        configRepository.configurations.map { it[widgetId] }.distinctUntilChanged(),
        locationRepository.settings,
        renderRevision,
        refreshStatus,
    ) { configuration, settings, revision, statuses ->
        val location = configuration?.let { WidgetLocationResolver.resolve(it, settings) }
        RenderInput(configuration, location, location?.let { statuses[it.id] }, revision)
    }.distinctUntilChanged().map { input ->
        val data = input.location?.let {
            val snapshot = withContext(Dispatchers.IO) { weatherRepository.loadCached(it) }
            val failure = input.status?.failure
            WidgetData(
                location = it,
                snapshot = snapshot,
                source = snapshot?.let { SnapshotSource.CACHED },
                failure = failure,
                now = clock.instant(),
                awaitingFirstForecast = snapshot == null && failure == null && input.status?.updating == true,
            )
        }
        Rendering(input.configuration, data)
    }

    internal fun markRefreshing(locationIds: Collection<String>) {
        refreshStatus.update { previous ->
            previous + locationIds.associateWith { (previous[it] ?: RefreshStatus()).copy(updating = true) }
        }
    }

    /** Wake active compositions for this location and discard status for removed locations. */
    internal fun publishRefresh(locationId: String, failure: DataLoadFailure?, activeLocationIds: Set<String>) {
        refreshStatus.update { previous ->
            val retained = previous.filterKeys { it in activeLocationIds }
            if (locationId !in activeLocationIds) retained else {
                retained + (locationId to RefreshStatus(failure = failure, revision = (previous[locationId]?.revision ?: 0) + 1))
            }
        }
    }

    internal fun forgetInactiveLocations(activeLocationIds: Set<String>) {
        refreshStatus.update { previous -> previous.filterKeys { it in activeLocationIds } }
    }

    internal fun invalidateRendering() {
        renderRevision.update { it + 1 }
    }

    internal suspend fun locationsByWidget(widgetIds: Set<Int>): Map<Int, SavedLocation> {
        val settings = locationRepository.currentSettings()
        return configRepository.currentConfigurations()
            .filterKeys { it in widgetIds }
            .mapNotNull { (id, configuration) ->
                WidgetLocationResolver.resolve(configuration, settings)?.let { id to it }
            }
            .toMap()
    }

    /** Configuration of one widget instance, null while it was never configured. */
    suspend fun configuration(widgetId: Int): WidgetConfiguration? =
        configRepository.configuration(widgetId)

    /**
     * Location and weather of one widget instance.
     *
     * Returns null only when the widget has no location to show at all - no home location, or a
     * fixed entry without a place. The caller then renders the "no data" state instead of guessing.
     */
    suspend fun loadForWidget(
        widgetId: Int,
        forceRefresh: Boolean = false,
    ): WidgetData? {
        val configuration = configRepository.configuration(widgetId)
        return loadForConfiguration(configuration, forceRefresh)
    }

    /** Same as [loadForWidget] for a configuration that is already at hand. */
    suspend fun loadForConfiguration(
        configuration: WidgetConfiguration?,
        forceRefresh: Boolean = false,
    ): WidgetData? {
        val settings = locationRepository.currentSettings()
        val location = WidgetLocationResolver.resolve(configuration, settings) ?: return null
        return loadForLocation(location, forceRefresh)
    }

    /** Location and weather of one place, used by the periodic background refresh. */
    suspend fun loadForLocation(
        location: SavedLocation,
        forceRefresh: Boolean = false,
    ): WidgetData {
        val result = withLocationLock(location.id) {
            weatherRepository.load(location, forceRefresh = forceRefresh)
        }
        return WidgetData(
            location = location,
            snapshot = result.snapshot,
            source = result.source,
            failure = result.failure,
            now = clock.instant(),
        )
    }

    /**
     * Reads the cached forecast of [location] without any network call.
     *
     * This is what a presentation-only refresh uses: the date rolled over or a new day period began,
     * and the labels have to catch up. Nothing is fetched for that, and stale cached values stay
     * usable while they are on screen.
     */
    suspend fun loadCached(location: SavedLocation): WeatherSnapshot? =
        weatherRepository.loadCached(location)

    /**
     * Every place the widgets currently display, without duplicates.
     *
     * Fixed widgets contribute their own location; the home and selected roles come from the app's
     * settings, and a role that is empty simply contributes nothing.
     */
    suspend fun displayedLocations(): List<SavedLocation> {
        val settings = locationRepository.currentSettings()
        val configurations = configRepository.currentConfigurations()
        return configurations.values
            .mapNotNull { WidgetLocationResolver.resolve(it, settings) }
            .distinctBy { it.id }
    }

    /** Runs [block] while no other widget update is loading the same location. */
    private suspend fun <R> withLocationLock(locationId: String, block: suspend () -> R): R {
        val lock = registryLock.withLock {
            locationLocks.getOrPut(locationId, ::LocationLock).also { it.users++ }
        }
        try {
            return lock.mutex.withLock { block() }
        } finally {
            withContext(NonCancellable) {
                registryLock.withLock {
                    lock.users--
                    if (lock.users == 0) locationLocks.remove(locationId)
                }
            }
        }
    }

    private class LocationLock(val mutex: Mutex = Mutex(), var users: Int = 0)
}
