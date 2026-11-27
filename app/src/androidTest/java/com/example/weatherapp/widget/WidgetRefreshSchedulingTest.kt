package com.example.weatherapp.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.weatherapp.WeatherApplication
import com.example.weatherapp.domain.CachePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The scheduling rules of the widgets against the real WorkManager of the device.
 *
 * The unit tests cannot answer this: only WorkManager knows whether five requests collapsed into one
 * job or turned into five. Two properties matter for the battery and for the forecast provider - one
 * single one-time refresh at a time, and exactly one periodic job for all widgets of all three types,
 * which disappears again when the last widget is gone.
 *
 * The widget ids are invented here on purpose: an instrumented test cannot create a widget host, so
 * the coordinator is asked to look at a fixed set of ids instead of the launcher's. The ids are far
 * away from the ones a real launcher hands out, so nothing on the device can be disturbed.
 */
@RunWith(AndroidJUnit4::class)
class WidgetRefreshSchedulingTest {

    private val container
        get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WeatherApplication).container

    private val workManager get() = WorkManager.getInstance(container.applicationContext)

    /** The widget instances the coordinator is told to see. */
    private var widgetIds: Set<Int> = setOf(9001, 9002, 9003)

    private fun coordinator() = WidgetRefreshCoordinator(container) { widgetIds }

    @Before
    fun removeLeftoverWork() = runBlocking { cancelWidgetWork() }

    @After
    fun leaveNoWorkBehind() = runBlocking { cancelWidgetWork() }

    private suspend fun cancelWidgetWork() {
        withContext(Dispatchers.IO) {
            workManager.cancelUniqueWork(WidgetRefreshCoordinator.UPDATE_WORK_NAME)
            workManager.cancelUniqueWork(WidgetRefreshCoordinator.PERIODIC_WORK_NAME)
        }
    }

    private suspend fun infosOf(uniqueName: String): List<WorkInfo> = withContext(Dispatchers.IO) {
        workManager.getWorkInfosForUniqueWork(uniqueName).get()
    }

    /**
     * The work a unique name stands for right now. Cancelling leaves a cancelled record behind under
     * the same name, and a refresh that already ran leaves a succeeded or failed one - none of those
     * is the schedule the widgets are living with, only a pending or running piece of work is.
     */
    private suspend fun liveInfos(uniqueName: String): List<WorkInfo> =
        infosOf(uniqueName).filter {
            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
        }

    @Test
    fun severalWidgetsShareOneRefreshJob() = runBlocking {
        val coordinator = coordinator()

        repeat(3) { coordinator.requestWidgetUpdate() }

        val infos = liveInfos(WidgetRefreshCoordinator.UPDATE_WORK_NAME)
        assertEquals(
            "one refresh job for all widgets, found ${infos.map { it.state }}",
            1,
            infos.size,
        )
    }

    @Test
    fun aRepeatedRequestDoesNotStackUp() = runBlocking {
        val coordinator = coordinator()

        coordinator.requestWidgetUpdate()
        coordinator.requestWidgetUpdate(forceRefresh = true)
        coordinator.requestWidgetUpdate()

        assertEquals(1, liveInfos(WidgetRefreshCoordinator.UPDATE_WORK_NAME).size)
    }

    /** All three widget types are served by one periodic job, never one per type or instance. */
    @Test
    fun exactlyOnePeriodicJobIsScheduled() = runBlocking {
        val coordinator = coordinator()

        coordinator.onWidgetConfigured(9001)
        coordinator.onWidgetConfigured(9002)
        coordinator.requestWidgetUpdate()

        val periodic = liveInfos(WidgetRefreshCoordinator.PERIODIC_WORK_NAME)
        assertEquals(1, periodic.size)
        assertTrue(
            "the periodic job stays scheduled, found ${periodic.map { it.state }}",
            periodic.single().state == WorkInfo.State.ENQUEUED || periodic.single().state == WorkInfo.State.RUNNING,
        )
    }

    /**
     * The periodic interval is the app's cache window, not a polling rate: a refresh that runs before
     * the stored forecast counts as stale could only ask the provider the same question twice.
     */
    @Test
    fun thePeriodicIntervalMatchesTheCacheWindow() {
        assertEquals(
            CachePolicy.freshDuration.toMinutes(),
            WidgetRefreshCoordinator.PERIODIC_REFRESH_MINUTES,
        )
    }

    /** Without a widget on any screen, nothing is scheduled and therefore nothing wakes up. */
    @Test
    fun noWidgetsMeanNoWork() = runBlocking {
        widgetIds = emptySet()

        coordinator().requestWidgetUpdate()

        assertTrue(
            "no refresh may be scheduled, found ${liveInfos(WidgetRefreshCoordinator.UPDATE_WORK_NAME).map { it.state }}",
            liveInfos(WidgetRefreshCoordinator.UPDATE_WORK_NAME).isEmpty(),
        )
        assertTrue(
            "no periodic job may be scheduled, found ${liveInfos(WidgetRefreshCoordinator.PERIODIC_WORK_NAME).map { it.state }}",
            liveInfos(WidgetRefreshCoordinator.PERIODIC_WORK_NAME).isEmpty(),
        )
    }

    /** Deleting the last widget stops the periodic refresh; configuring one brings it back. */
    @Test
    fun thePeriodicRefreshStopsWhenTheLastWidgetIsGone() = runBlocking {
        val coordinator = coordinator()
        coordinator.onWidgetConfigured(9001)
        assertEquals(1, liveInfos(WidgetRefreshCoordinator.PERIODIC_WORK_NAME).size)

        widgetIds = emptySet()
        coordinator.cancelIfNoWidgetsRemain()

        val remaining = liveInfos(WidgetRefreshCoordinator.PERIODIC_WORK_NAME)
        assertTrue(
            "no periodic work may be left, found ${remaining.map { it.state }}",
            remaining.isEmpty(),
        )
    }

    /**
     * A widget whose configuration was just confirmed is refreshed even though the launcher does not
     * list it yet: it was composed before this screen had confirmed anything, and waiting for the next
     * periodic run would leave its "no location yet" note on the home screen for half an hour.
     */
    @Test
    fun aJustConfiguredWidgetIsRefreshedWithoutBeingListedYet() = runBlocking {
        widgetIds = emptySet()

        coordinator().onWidgetConfigured(9001)

        val update = liveInfos(WidgetRefreshCoordinator.UPDATE_WORK_NAME)
        assertEquals(
            "the configured widget is refreshed at once, found ${update.map { it.state }}",
            1,
            update.size,
        )
        assertEquals(1, liveInfos(WidgetRefreshCoordinator.PERIODIC_WORK_NAME).size)
    }
}
