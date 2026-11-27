package com.example.weatherapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.weatherapp.WeatherApplication
import com.example.weatherapp.di.AppContainer
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The three widgets, drawn from the data that is currently available.
 *
 * `updateAll` asks Glance for the ids of the instances that really exist, so a call costs nothing
 * while no widget is on any screen and never renders an instance that has been removed. The actual
 * work happens in [GlanceWeatherWidget.provideGlance], which is also where cached data is read.
 */
object WidgetRendering {

    suspend fun updateAllWidgets(context: Context) {
        CurrentWeatherWidget().updateAll(context)
        DayPeriodWidget().updateAll(context)
        ThreeDayWidget().updateAll(context)
    }
}

/**
 * Single scheduling point for everything that keeps the widgets up to date.
 *
 * One unique periodic job serves all widget instances of all three types - not one per widget, which
 * would multiply network requests and wake-ups for the same forecast. Repeat requests are collapsed
 * into that one job as well: [requestWidgetUpdate] replaces a not-yet-started run instead of adding
 * another, so five widgets reacting to one location change still means one refresh.
 *
 * The policy is the app's own cache policy, not a timer: a refresh only reaches the network when the
 * cached forecast is no longer fresh, so the periodic interval is chosen to match that window rather
 * than to poll harder.
 *
 * Android may run the periodic job late - Doze, battery optimisation, manufacturer policies, no
 * network. Nothing here pretends otherwise: the widgets keep showing the last cached forecast, and a
 * later run simply catches up.
 */
class WidgetRefreshCoordinator(
    private val container: AppContainer,
    /**
     * The widget instances the launcher holds right now. Injectable because an instrumented test
     * cannot create a widget host of its own, and "one job for all widgets" is a rule worth testing.
     */
    private val liveWidgetIdsProvider: suspend (Context) -> Set<Int> = { WidgetRegistry.liveWidgetIds(it) },
) {

    private val context: Context get() = container.applicationContext

    /**
     * Drawing is not allowed to die with the screen that asked for it: the configuration activity
     * finishes the moment its save went through. A supervisor job keeps one failed render from
     * cancelling the next one.
     */
    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A widget has just been configured on the configuration screen.
     *
     * This path deliberately does not ask "are there any widgets": the launcher composes a new widget
     * while the user is still choosing its location, and its id is not in the launcher's list of bound
     * widgets at the moment this screen confirms the choice. Without a refresh right here, the freshly
     * configured instance would keep showing the "no location yet" note until the next periodic run.
     *
     * The redraw is started in the coordinator's own scope rather than the caller's because the
     * configuration screen closes as soon as it has saved, and its scope ends with it.
     */
    suspend fun onWidgetConfigured(widgetId: Int) {
        schedulePeriodicRefresh()
        enqueueUpdate(forceRefresh = false)
        renderScope.launch {
            // The launcher binds the id when this screen returns, so the instance can only be drawn a
            // moment later. Waiting for the scheduled run instead would leave the note on the home
            // screen for as long as JobScheduler needs to get to the job.
            delay(RENDER_DELAY_MILLIS)
            drawConfiguredWidget(widgetId)
            runCatching { WidgetRendering.updateAllWidgets(context) }
                .onFailure { Log.w(TAG, "Widgets could not be drawn right after configuring one", it) }
        }
    }

    /**
     * Draws one instance from the configuration that was just confirmed.
     *
     * `updateAll` only reaches the instances Glance knows about, and a widget that the launcher composed
     * before it was configured is not necessarily among them yet - which is exactly the case this path
     * exists for. Which type the id belongs to comes from the App Widget service.
     */
    private suspend fun drawConfiguredWidget(widgetId: Int) {
        runCatching {
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
            val widget = WidgetRegistry.widgetFor(info?.provider?.className)
            if (widget == null) {
                Log.i(TAG, "Widget $widgetId is not bound to a known widget type yet")
                return
            }
            widget.update(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId))
        }.onFailure {
            Log.i(TAG, "Widget $widgetId could not be drawn right after configuring it", it)
        }
    }

    /** A widget has been deleted: drop what belongs to it and stop the work if nothing is left. */
    suspend fun onWidgetRemoved(widgetIds: Collection<Int>) {
        container.widgetConfigRepository.remove(widgetIds)
        pruneStaleConfigurations()
        cancelIfNoWidgetsRemain()
    }

    /**
     * Asks for one refresh of every widget, without forcing a network request.
     *
     * The worker reads the cache first and only fetches when the stored forecast is no longer fresh,
     * which is what keeps a date rollover or a resize from costing an API call.
     *
     * This is also the entry point that (re)creates the periodic job: a widget added while the app was
     * killed is discovered here, and a periodic job cancelled while no widget existed comes back with
     * the first refresh request of the new one. [ExistingPeriodicWorkPolicy.KEEP] makes sure that
     * happens at most once, whatever the number of widgets.
     */
    suspend fun requestWidgetUpdate(forceRefresh: Boolean = false) {
        if (liveWidgetIds().isEmpty()) return
        schedulePeriodicRefresh()
        enqueueUpdate(forceRefresh)
    }

    /** Hands over the single one-time refresh; the unique name is what collapses repeat requests. */
    private suspend fun enqueueUpdate(forceRefresh: Boolean) {
        withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UPDATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                        .setInputData(workDataOf(WidgetRefreshWorker.INPUT_FORCE_REFRESH to forceRefresh))
                        .setConstraints(networkConstraints())
                        .build(),
                )
            }.onFailure { Log.w(TAG, "Widget refresh could not be scheduled", it) }
        }
    }

    /** Runs the periodic refresh while at least one widget exists and stops it when none does. */
    suspend fun cancelIfNoWidgetsRemain() {
        if (hasWidgets()) return
        withContext(Dispatchers.IO) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME) }
                .onFailure { Log.w(TAG, "Periodic widget refresh could not be cancelled", it) }
        }
    }

    /**
     * Drops configurations whose widget id no longer exists.
     *
     * An empty answer from the App Widget service counts as no answer: deleting every stored
     * configuration because the service was briefly unreachable would be worse than keeping one
     * configuration too many, and a widget that really is gone is handled by the widget's own
     * deletion callback.
     */
    suspend fun pruneStaleConfigurations() {
        val live = liveWidgetIds()
        if (live.isEmpty()) return
        val stored = container.widgetConfigRepository.currentConfigurations().keys
        val stale = stored - live
        if (stale.isNotEmpty()) {
            container.widgetConfigRepository.remove(stale)
            Log.i(TAG, "Removed ${stale.size} abandoned widget configuration(s)")
        }
    }

    /** Schedules the one periodic job; [ExistingPeriodicWorkPolicy.KEEP] keeps it a single instance. */
    private suspend fun schedulePeriodicRefresh() {
        withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                        PERIODIC_REFRESH_MINUTES,
                        TimeUnit.MINUTES,
                    )
                        .setConstraints(networkConstraints())
                        .setBackoffCriteria(
                            androidx.work.BackoffPolicy.EXPONENTIAL,
                            Duration.ofMinutes(BACKOFF_MINUTES),
                        )
                        .build(),
                )
            }.onFailure { Log.w(TAG, "Periodic widget refresh could not be scheduled", it) }
        }
    }

    /** Ids of every widget instance the launcher holds right now. */
    suspend fun liveWidgetIds(): Set<Int> = withContext(Dispatchers.IO) { liveWidgetIdsProvider(context) }

    private suspend fun hasWidgets(): Boolean = liveWidgetIds().isNotEmpty()

    /**
     * A refresh needs a connection, and deferring it while there is none is exactly the behaviour
     * the app wants offline: the widgets keep showing cached values, and the job runs later.
     */
    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    companion object {
        private const val TAG = "WidgetRefresh"

        /** Unique names double as the guarantee that only one job of a kind is ever scheduled. */
        const val PERIODIC_WORK_NAME = "wetterplus-widget-refresh-periodic"
        const val UPDATE_WORK_NAME = "wetterplus-widget-refresh-once"

        /**
         * Interval of the periodic refresh. The cache policy already treats a forecast as stale after
         * 30 minutes, so checking more often than that would only add requests without adding fresh
         * data; the value is also WorkManager's smallest allowed periodic interval.
         */
        const val PERIODIC_REFRESH_MINUTES = 30L

        private const val BACKOFF_MINUTES = 10L

        /**
         * How long to wait before drawing a widget that was just configured. The launcher binds the id
         * when the configuration screen returns, so a redraw of that instance needs the binding first.
         */
        private const val RENDER_DELAY_MILLIS = 700L
    }
}

/**
 * The background work behind every widget refresh.
 *
 * It loads each place the widgets display - once per place, not once per widget - through the
 * existing repository, and then redraws the widgets. A location whose refresh failed keeps its cached
 * forecast in the database, so the widget still shows it and says that the data may be outdated.
 */
class WidgetRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WeatherApplication).container
        val forceRefresh = inputData.getBoolean(INPUT_FORCE_REFRESH, false)
        val provider = container.widgetDataProvider

        val locations = try {
            provider.displayedLocations()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.w(TAG, "Widget locations could not be resolved", failure)
            emptyList()
        }

        if (locations.isEmpty()) {
            // Nothing is displayed any more: the widgets still on screen have no location to show,
            // and no request is made for them.
            WidgetRendering.updateAllWidgets(applicationContext)
            return Result.success()
        }

        var failures = 0
        locations.forEach { location ->
            val data = try {
                provider.loadForLocation(location, forceRefresh)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Log.w(TAG, "Widget weather could not be loaded", failure)
                null
            }
            if (data == null || data.failure != null) failures += 1
        }

        WidgetRendering.updateAllWidgets(applicationContext)

        // Everything failed: the network is probably still unavailable, so let WorkManager retry.
        // Partial success is success - the places that worked are up to date now.
        return if (failures == locations.size) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "WidgetRefreshWorker"
        const val INPUT_FORCE_REFRESH = "force_refresh"
    }
}
