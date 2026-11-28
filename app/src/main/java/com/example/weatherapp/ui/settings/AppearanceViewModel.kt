package com.example.weatherapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherapp.data.local.ThemeStore
import com.example.weatherapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the appearance preference for the whole application.
 *
 * The container owns a single instance, so the theme survives configuration changes and the
 * activity can read the same state it hands to the appearance screen. Selecting a mode applies
 * it immediately and persists it afterwards; a failed write leaves the choice active for the
 * running session and reports [persistenceFailed] instead of pretending the value is stored.
 */
class AppearanceViewModel(
    private val themeStore: ThemeStore,
) : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _persistenceFailed = MutableStateFlow(false)
    val persistenceFailed: StateFlow<Boolean> = _persistenceFailed.asStateFlow()
    private val writes = Mutex()
    private var selectionRevision = 0L

    init {
        viewModelScope.launch {
            val storedMode = themeStore.themeMode.first()
            if (selectionRevision == 0L) _themeMode.value = storedMode
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        val revision = ++selectionRevision
        _themeMode.value = mode
        _persistenceFailed.value = false
        viewModelScope.launch {
            writes.withLock {
                val saved = themeStore.save(mode)
                if (revision == selectionRevision) _persistenceFailed.value = !saved
            }
        }
    }
}
