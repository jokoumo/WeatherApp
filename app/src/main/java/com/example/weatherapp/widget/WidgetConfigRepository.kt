package com.example.weatherapp.widget

import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the configuration of every widget instance on the home screen.
 *
 * The rules the rest of the widget layer relies on live here:
 *
 * - one entry per App Widget id, so instances never overwrite each other;
 * - a fixed entry keeps a full [SavedLocation], so it survives the removal of that place from the
 *   favourites and the history;
 * - a cancelled first-time configuration never reaches storage, because writing happens here and
 *   only for a configuration the user confirmed;
 * - [pruneTo] drops entries whose widget id has disappeared, which keeps abandoned ids from
 *   accumulating after a crash or a launcher that never announced a deletion.
 *
 * Mutations are serialised with a mutex for the same reason
 * [com.example.weatherapp.data.repository.LocationRepository] serialises its own: adding one widget
 * and deleting another can happen in the same moment, and each mutation has to see what the previous
 * one wrote.
 */
class WidgetConfigRepository(private val store: WidgetConfigStore) {

    val configurations: Flow<Map<Int, WidgetConfiguration>> = store.configurations

    private val mutationLock = Mutex()

    /** Snapshot of every stored configuration, without collecting the flow. */
    suspend fun currentConfigurations(): Map<Int, WidgetConfiguration> = store.configurations.first()

    /** The entry of one widget instance, or null while it has never been configured. */
    suspend fun configuration(widgetId: Int): WidgetConfiguration? = currentConfigurations()[widgetId]

    suspend fun save(configuration: WidgetConfiguration): Unit = mutate { current ->
        current + (configuration.widgetId to configuration)
    }

    suspend fun remove(widgetIds: Collection<Int>): Unit = mutate { current ->
        current.filterKeys { it !in widgetIds }
    }

    /** Keeps exactly the entries whose id is in [liveWidgetIds]; everything else is dropped. */
    suspend fun pruneTo(liveWidgetIds: Set<Int>): Unit = mutate { current ->
        current.filterKeys { it in liveWidgetIds }
    }

    /**
     * Ids of the places at least one widget instance has pinned down.
     *
     * This is what extends cache retention to widgets: a fixed location is neither a favourite nor a
     * history entry, so without this set its forecast row would look orphaned and be deleted as soon
     * as the place left the app's own lists.
     */
    suspend fun fixedLocationIds(): Set<String> =
        WidgetLocationResolver.fixedLocationIds(currentConfigurations())

    /** The whole location of a fixed widget, used when a tap has to open that place again. */
    suspend fun fixedLocation(widgetId: Int): SavedLocation? = configuration(widgetId)?.fixedLocation

    private suspend fun mutate(
        transform: (Map<Int, WidgetConfiguration>) -> Map<Int, WidgetConfiguration>,
    ) = mutationLock.withLock {
        val current = store.configurations.first()
        val updated = transform(current)
        if (updated == current) return@withLock
        val gone = current.keys - updated.keys
        if (gone.isNotEmpty()) store.remove(gone)
        val written = updated.values.filter { current[it.widgetId] != it }
        store.saveAll(written)
    }
}

/**
 * Decides which location a widget instance shows.
 *
 * Two independent inputs meet here - the persisted configuration of the instance and the location
 * settings of the app - and the whole decision is a pure function. That is what makes "this widget
 * follows the home location" and "this widget stays on its own place" testable properties instead of
 * something that has to be watched in a launcher.
 *
 * An unconfigured instance behaves like [WidgetLocationMode.HOME]: the default is the location the
 * app itself opens with, and it is the safe answer while a configuration has not arrived yet.
 */
object WidgetLocationResolver {

    /**
     * The location to render, or null when the configured role is currently empty. No home location
     * and no selection are real states of the app; the honest answer then is "nothing to show",
     * never an arbitrary place.
     */
    fun resolve(
        configuration: WidgetConfiguration?,
        settings: LocationSettings,
    ): SavedLocation? {
        if (configuration == null) return settings.homeLocation
        return when (configuration.mode) {
            WidgetLocationMode.HOME -> settings.homeLocation
            WidgetLocationMode.SELECTED -> settings.resolveSelectedLocation()
            WidgetLocationMode.FIXED -> configuration.fixedLocation
        }
    }

    /** Ids of the places an active fixed-location widget depends on. */
    fun fixedLocationIds(configurations: Map<Int, WidgetConfiguration>): Set<String> =
        configurations.values.mapNotNull { it.fixedLocationId }.toSet()
}
