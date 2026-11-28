package com.example.weatherapp.ui.settings

import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.local.ThemeStore
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.domain.model.ThemeMode
import com.example.weatherapp.testing.FakeForecastApi
import com.example.weatherapp.testing.FakeForecastCacheStore
import com.example.weatherapp.testing.FakeGeocodingApi
import com.example.weatherapp.testing.FakeSettingsStore
import com.example.weatherapp.testing.FakeThemeStore
import com.example.weatherapp.testing.MutableClock
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Appearance preference behaviour: the default, restoring a stored mode, applying a choice
 * immediately, and what happens when the write fails. The last group of tests also proves that
 * changing the theme never touches the saved locations or the loaded weather.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    /**
     * Runs [body] with an appearance view model on the test dispatcher, so `advanceUntilIdle`
     * also drives the store write.
     */
    private fun withAppearance(
        storedMode: ThemeMode = ThemeMode.SYSTEM,
        failSave: Boolean = false,
        body: suspend TestScope.(AppearanceViewModel, FakeThemeStore) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeThemeStore(storedMode, failSave)
            val model = AppearanceViewModel(store)
            advanceUntilIdle()
            body(model, store)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun theLatestThemeRemainsVisibleDuringEarlierSlowWrites() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            val store = object : ThemeStore {
                override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
                override suspend fun save(mode: ThemeMode): Boolean {
                    if (mode == ThemeMode.LIGHT) first.await() else second.await()
                    themeMode.value = mode
                    return true
                }
            }
            val model = AppearanceViewModel(store)
            runCurrent()
            model.setThemeMode(ThemeMode.LIGHT)
            runCurrent()
            model.setThemeMode(ThemeMode.DARK)
            runCurrent()
            first.complete(Unit)
            runCurrent()

            assertEquals(ThemeMode.DARK, model.themeMode.value)
            assertFalse(model.persistenceFailed.value)
            second.complete(Unit)
            advanceUntilIdle()
            assertEquals(ThemeMode.DARK, store.themeMode.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun systemIsTheDefaultWhenNothingWasStored() = withAppearance { model, _ ->
        assertEquals(ThemeMode.SYSTEM, model.themeMode.value)
        assertFalse(model.persistenceFailed.value)
    }

    @Test
    fun storedModeIsRestoredOnStart() = withAppearance(storedMode = ThemeMode.DARK) { model, _ ->
        assertEquals(ThemeMode.DARK, model.themeMode.value)
    }

    @Test
    fun choosingAModeAppliesItImmediatelyAndStoresIt() = withAppearance { model, store ->
        model.setThemeMode(ThemeMode.LIGHT)

        // The UI switches with the call, not only after the write has finished.
        assertEquals(ThemeMode.LIGHT, model.themeMode.value)
        advanceUntilIdle()
        assertEquals(listOf(ThemeMode.LIGHT), store.savedModes)
        assertEquals(ThemeMode.LIGHT, store.state.value)
        assertFalse(model.persistenceFailed.value)
    }

    @Test
    fun everyOptionIsStoredAsItself() = withAppearance { model, store ->
        ThemeMode.entries.forEach { mode -> model.setThemeMode(mode) }
        advanceUntilIdle()

        assertEquals(ThemeMode.entries, store.savedModes)
        assertEquals(ThemeMode.DARK, model.themeMode.value)
    }

    /**
     * A preferences file that cannot be written must not lose the choice of the running session
     * and must not pretend anything was saved: the mode stays applied and the screen can say that
     * it is only valid until the next start.
     */
    @Test
    fun failedWriteKeepsTheChoiceAndReportsIt() = withAppearance(failSave = true) { model, _ ->
        model.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, model.themeMode.value)
        assertTrue(model.persistenceFailed.value)
    }

    @Test
    fun persistenceWarningIsClearedByTheNextSelection() = withAppearance(failSave = true) { model, _ ->
        model.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()
        assertTrue(model.persistenceFailed.value)

        model.setThemeMode(ThemeMode.SYSTEM)
        assertFalse(model.persistenceFailed.value)
    }

    /**
     * The theme lives in its own store. Selecting a mode must not rewrite the saved locations and
     * must not reload or reset the weather, which is why both stores are wired together here.
     */
    @Test
    fun themeChangesLeaveLocationsAndWeatherAlone() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val now = Instant.parse("2026-11-22T11:00:00Z")
            val clock = MutableClock(now)
            val settingsStore = FakeSettingsStore(
                LocationSettings(homeLocation = hamburg, selectedLocationId = hamburg.id),
            )
            val forecastApi = FakeForecastApi()
            val weatherRepository = WeatherRepository(forecastApi, FakeForecastCacheStore(), ForecastMapper(), clock)
            val mainViewModel = MainViewModel(
                locationRepository = LocationRepository(settingsStore, FakeGeocodingApi()),
                weatherRepository = weatherRepository,
            ) { clock.now }
            val appearanceViewModel = AppearanceViewModel(FakeThemeStore())
            advanceUntilIdle()

            val before = mainViewModel.uiState.value
            assertTrue("the weather was never loaded in this test", before.weather.hasData)
            val settingsWritesBefore = settingsStore.saveCount
            val forecastCallsBefore = forecastApi.callCount

            appearanceViewModel.setThemeMode(ThemeMode.DARK)
            advanceUntilIdle()

            val after = mainViewModel.uiState.value
            assertEquals(ThemeMode.DARK, appearanceViewModel.themeMode.value)
            assertEquals("the theme write touched the location settings", settingsWritesBefore, settingsStore.saveCount)
            assertEquals("the theme change triggered a forecast request", forecastCallsBefore, forecastApi.callCount)
            assertEquals(before.weather.current, after.weather.current)
            assertEquals(before.settings, after.settings)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
