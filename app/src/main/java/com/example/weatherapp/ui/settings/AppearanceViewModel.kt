package com.example.weatherapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherapp.data.local.ThemeStore
import com.example.weatherapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            themeStore.themeMode.collect { mode -> _themeMode.value = mode }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        _persistenceFailed.value = false
        viewModelScope.launch {
            _persistenceFailed.value = !themeStore.save(mode)
        }
    }
}
