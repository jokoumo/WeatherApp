package com.example.weatherapp.widget

import androidx.lifecycle.viewModelScope
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.domain.model.LocationSettings
import com.example.weatherapp.testing.FakeGeocodingApi
import com.example.weatherapp.testing.FakeSettingsStore
import com.example.weatherapp.testing.geocoderResult
import com.example.weatherapp.testing.hamburg
import com.example.weatherapp.testing.paris
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetConfigurationViewModelTest {
    private val settings = FakeSettingsStore()
    private val store = WidgetDataProviderTest.FakeWidgetConfigStore()
    private val configurations = WidgetConfigRepository(store)
    private val scheduled = mutableListOf<WidgetConfiguration>()

    private fun withViewModel(
        initial: WidgetConfiguration? = null,
        body: suspend TestScope.(WidgetConfigurationViewModel) -> Unit,
    ) = runTest {
        initial?.let { configurations.save(it) }
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val api = FakeGeocodingApi(results = listOf(geocoderResult(2911298, "Hamburg", 53.55, 9.99)))
        val viewModel = WidgetConfigurationViewModel(42, LocationRepository(settings, api), configurations) { id ->
            scheduled.add(requireNotNull(configurations.configuration(id)))
        }
        try {
            advanceUntilIdle()
            body(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `location requirements react when app settings change`() = withViewModel { model ->
        assertFalse(model.uiState.value.canSave)
        model.onModeSelected(WidgetLocationMode.SELECTED)
        model.save()
        advanceUntilIdle()
        assertNull(configurations.configuration(42))

        settings.save(LocationSettings(recentLocations = listOf(paris), selectedLocationId = paris.id))
        advanceUntilIdle()
        assertTrue(model.uiState.value.canSave)
        model.onModeSelected(WidgetLocationMode.HOME)
        assertFalse(model.uiState.value.canSave)

        settings.save(LocationSettings(homeLocation = hamburg))
        advanceUntilIdle()
        assertTrue(model.uiState.value.canSave)
    }

    @Test
    fun `search works before app setup and save persists before it schedules the exact id`() = withViewModel { model ->
        model.onModeSelected(WidgetLocationMode.FIXED)
        assertFalse(model.uiState.value.canSave)
        model.onSearchQueryChange("Hamburg")
        advanceUntilIdle()
        val result = model.uiState.value.search.results.single()
        assertEquals("Hamburg", result.name)

        model.onLocationPicked(result)
        assertTrue(model.uiState.value.search.results.isEmpty())
        assertTrue(model.uiState.value.search.query.isEmpty())
        assertTrue(model.uiState.value.canSave)
        assertNull(configurations.configuration(42))

        model.save()
        model.save()
        advanceUntilIdle()
        val expected = WidgetConfiguration(42, WidgetLocationMode.FIXED, result)
        assertEquals(expected, configurations.configuration(42))
        assertEquals(listOf(expected), scheduled)
        assertTrue(model.uiState.value.saved)
        assertEquals(0, settings.saveCount)
    }

    @Test
    fun `leaving reconfiguration without saving preserves its original location`() =
        withViewModel(initial = WidgetConfiguration(42, WidgetLocationMode.FIXED, paris)) { model ->
            assertEquals(paris, model.uiState.value.fixedLocation)
            model.onLocationPicked(hamburg)
            assertEquals(WidgetConfiguration(42, WidgetLocationMode.FIXED, paris), configurations.configuration(42))
            assertTrue(scheduled.isEmpty())
        }

    @Test
    fun `a home location disappearing during save unlocks the screen and can be retried`() = withViewModel { model ->
        settings.save(LocationSettings(homeLocation = hamburg))
        advanceUntilIdle()
        model.save()
        assertTrue(model.uiState.value.isSaving)
        settings.save(LocationSettings())
        advanceUntilIdle()

        assertFalse(model.uiState.value.isSaving)
        assertTrue(model.uiState.value.locationChangedDuringSave)
        assertNull(configurations.configuration(42))
        assertTrue(scheduled.isEmpty())

        settings.save(LocationSettings(homeLocation = hamburg))
        advanceUntilIdle()
        assertTrue(model.uiState.value.canSave)
        model.save()
        advanceUntilIdle()
        assertEquals(listOf(WidgetConfiguration(42, WidgetLocationMode.HOME)), scheduled)
    }

    @Test
    fun `selected location can be saved without a home location`() = withViewModel { model ->
        settings.save(LocationSettings(recentLocations = listOf(paris), selectedLocationId = paris.id))
        advanceUntilIdle()
        model.onModeSelected(WidgetLocationMode.SELECTED)
        assertTrue(model.uiState.value.canSave)
        model.save()
        advanceUntilIdle()
        assertEquals(listOf(WidgetConfiguration(42, WidgetLocationMode.SELECTED)), scheduled)
    }

    @Test
    fun `failed reconfiguration preserves the existing record and allows a single retry`() =
        withViewModel(initial = WidgetConfiguration(42, WidgetLocationMode.FIXED, paris)) { model ->
            model.onLocationPicked(hamburg)
            store.writeFailure = IOException("Write failed")
            model.save()
            advanceUntilIdle()
            assertFalse(model.uiState.value.isSaving)
            assertTrue(model.uiState.value.canSave)
            assertTrue(model.uiState.value.saveFailed)
            assertEquals(paris, configurations.configuration(42)?.fixedLocation)
            assertTrue(scheduled.isEmpty())

            store.writeFailure = null
            model.save()
            model.save()
            advanceUntilIdle()
            assertEquals(listOf(WidgetConfiguration(42, WidgetLocationMode.FIXED, hamburg)), scheduled)
            assertTrue(model.uiState.value.saved)
        }
}
