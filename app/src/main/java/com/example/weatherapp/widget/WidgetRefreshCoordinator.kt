package com.example.weatherapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.example.weatherapp.WeatherApplication
import com.example.weatherapp.di.AppContainer
import com.example.weatherapp.domain.model.DataLoadFailure
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object WidgetRendering {
    /** The platform registry includes the configured ID even before Glance learns the provider. */
    suspend fun update(context: Context, widgetId: Int) {
        try {
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) ?: return
            val widget = WidgetRegistry.widgetFor(info.provider.className) ?: return
            widget.update(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("WidgetRendering", "Could not update widget $widgetId", failure)
        }
    }
}

class WidgetRefreshCoordinator(
    private val container: AppContainer,
    private val liveWidgetIdsProvider: suspend (Context) -> Set<Int> = { WidgetRegistry.liveWidgetIds(it) },
) {
    private val context get() = container.applicationContext
    private val workManager get() = WorkManager.getInstance(context)

    /** Android binds the ID before launching configuration. Publish the first update before RESULT_OK. */
    suspend fun onWidgetConfigured(widgetId: Int) {
        val provider = container.widgetDataProvider
        val location = provider.locationsByWidget(setOf(widgetId))[widgetId]
        location?.let { provider.markRefreshing(listOf(it.id)) }
        WidgetRendering.update(context, widgetId)
        try {
            schedulePeriodicRefresh()
            enqueueUpdate(initialWorkName(widgetId), widgetId = widgetId).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Could not schedule the initial widget refresh", failure)
            if (location != null) {
                val active = provider.locationsByWidget(liveWidgetIds() + widgetId).values.map { it.id }.toSet()
                provider.publishRefresh(location.id, DataLoadFailure.INVALID_DATA, active)
                WidgetRendering.update(context, widgetId)
            }
        }
    }

    fun onLauncherUpdate() {
        schedulePeriodicRefresh()
        enqueueUpdate(UPDATE_WORK_NAME)
    }

    suspend fun onWidgetRemoved(widgetIds: Collection<Int>) {
        container.widgetConfigRepository.remove(widgetIds)
        widgetIds.forEach { workManager.cancelUniqueWork(initialWorkName(it)) }
        val provider = container.widgetDataProvider
        provider.forgetInactiveLocations(provider.locationsByWidget(liveWidgetIds()).values.map { it.id }.toSet())
        cancelIfNoWidgetsRemain()
    }

    /** Redraw local dates and clock-dependent labels using the existing forecast. */
    suspend fun redrawFromCache() {
        container.widgetDataProvider.invalidateRendering()
        liveWidgetIds().forEach { WidgetRendering.update(context, it) }
    }

    suspend fun requestWidgetUpdate(forceRefresh: Boolean = false) {
        if (liveWidgetIds().isEmpty()) return
        redrawFromCache()
        schedulePeriodicRefresh()
        enqueueUpdate(UPDATE_WORK_NAME, forceRefresh = forceRefresh)
    }

    private fun enqueueUpdate(
        name: String,
        widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        forceRefresh: Boolean = false,
    ): Operation =
        workManager.enqueueUniqueWork(
            name,
            // A new configuration or explicit refresh must survive an in-flight run.
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID || forceRefresh) {
                ExistingWorkPolicy.REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            },
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInputData(workDataOf(
                    WidgetRefreshWorker.INPUT_WIDGET_ID to widgetId,
                    WidgetRefreshWorker.INPUT_FORCE_REFRESH to forceRefresh,
                ))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build(),
        )

    suspend fun cancelIfNoWidgetsRemain() {
        if (liveWidgetIds().isNotEmpty()) return
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(UPDATE_WORK_NAME)
    }

    suspend fun pruneStaleConfigurations() {
        val live = liveWidgetIds()
        if (live.isEmpty()) return
        val stored = container.widgetConfigRepository.currentConfigurations().keys
        val manager = AppWidgetManager.getInstance(context)
        // A bound instance can be in configuration and absent from the provider's list.
        val stale = (stored - live).filter { manager.getAppWidgetInfo(it) == null }
        if (stale.isNotEmpty()) container.widgetConfigRepository.remove(stale)
    }

    private fun schedulePeriodicRefresh() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            // Also replace the network constraint of schedules saved by WetterPlus 1.2.0.
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(PERIODIC_REFRESH_MINUTES, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build(),
        )
    }

    suspend fun liveWidgetIds(): Set<Int> = withContext(Dispatchers.IO) { liveWidgetIdsProvider(context) }

    companion object {
        private const val TAG = "WidgetRefresh"
        const val PERIODIC_WORK_NAME = "wetterplus-widget-refresh-periodic"
        const val UPDATE_WORK_NAME = "wetterplus-widget-refresh-once"
        const val PERIODIC_REFRESH_MINUTES = 30L
        private const val BACKOFF_MINUTES = 10L

        internal fun initialWorkName(widgetId: Int) = "$UPDATE_WORK_NAME-$widgetId"
    }
}

/** Renders cached data first, then refreshes each distinct live location through the shared cache. */
class WidgetRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = refreshLock.withLock {
        try {
            refresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Widget refresh failed", failure)
            Result.retry()
        }
    }

    private suspend fun refresh(): Result {
        val context = applicationContext
        val container = (context as WeatherApplication).container
        val provider = container.widgetDataProvider
        val widgetId = inputData.getInt(INPUT_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val forceRefresh = inputData.getBoolean(INPUT_FORCE_REFRESH, false)

        fun liveIds(): Set<Int> {
            val ids = WidgetRegistry.liveWidgetIds(context)
            val bound = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
            return if (bound != null && WidgetRegistry.widgetFor(bound.provider.className) != null) {
                ids + widgetId
            } else {
                ids
            }
        }

        val liveIds = liveIds()
        val requested = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) liveIds else liveIds intersect setOf(widgetId)
        val locations = provider.locationsByWidget(requested).values.distinctBy { it.id }
        provider.markRefreshing(locations.map { it.id })
        provider.invalidateRendering()
        requested.forEach { WidgetRendering.update(context, it) }

        var hadFailure = false
        for (location in locations) {
            if (provider.locationsByWidget(liveIds()).values.none { it.id == location.id }) continue
            val failure = try {
                provider.loadForLocation(location, forceRefresh && runAttemptCount == 0).failure
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unexpected: Exception) {
                Log.w(TAG, "Could not load widget forecast", unexpected)
                DataLoadFailure.INVALID_DATA
            }
            val activeLocations = provider.locationsByWidget(liveIds())
            val affected = activeLocations.filterValues { it.id == location.id }.keys
            provider.publishRefresh(location.id, failure, activeLocations.values.map { it.id }.toSet())
            affected.forEach { WidgetRendering.update(context, it) }
            if (failure != null && affected.isNotEmpty()) hadFailure = true
        }
        return if (hadFailure && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "WidgetRefreshWorker"
        private const val MAX_RETRIES = 2
        private val refreshLock = Mutex()
        const val INPUT_WIDGET_ID = "widget_id"
        const val INPUT_FORCE_REFRESH = "force_refresh"
    }
}
