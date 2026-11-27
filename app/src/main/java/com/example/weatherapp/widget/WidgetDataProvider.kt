package com.example.weatherapp.widget

import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.data.repository.SnapshotSource
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.domain.CachePolicy
import com.example.weatherapp.domain.model.DataLoadFailure
import com.example.weatherapp.domain.model.SavedLocation
import com.example.weatherapp.domain.model.WeatherSnapshot
import com.example.weatherapp.domain.zoneIdOrDefault
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val locationLocks = mutableMapOf<String, Mutex>()

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
        val mutex = registryLock.withLock { locationLocks.getOrPut(locationId) { Mutex() } }
        try {
            return mutex.withLock { block() }
        } finally {
            // Drop the lock again once nobody is waiting, so the map stays bounded by the number of
            // locations that are loaded at the same moment rather than by everything ever seen.
            registryLock.withLock {
                if (!mutex.isLocked) locationLocks.remove(locationId)
            }
        }
    }
}
