package com.example.weatherapp

import android.content.Intent
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.weatherapp.domain.model.resolveDarkTheme
import com.example.weatherapp.ui.MainViewModel
import com.example.weatherapp.ui.WeatherRoot
import com.example.weatherapp.ui.theme.WeatherAppTheme
import com.example.weatherapp.widget.WidgetLaunch
import com.example.weatherapp.widget.WidgetLocationMode

/**
 * Single activity host.
 *
 * The colour scheme follows the stored [com.example.weatherapp.domain.model.ThemeMode]: the
 * system setting while "Systemeinstellung" is selected, and a pinned light or dark scheme
 * otherwise. The system bars are re-coloured with the same decision, so a forced dark app on a
 * light system still gets light status bar icons.
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as WeatherApplication).container }

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this, container.mainViewModelFactory)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySystemBarAppearance(container.appearanceViewModel.themeMode.value.resolveDarkTheme(isSystemDark()))
        setContent {
            val themeMode by container.appearanceViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = themeMode.resolveDarkTheme(isSystemInDarkTheme())
            LaunchedEffect(darkTheme) { applySystemBarAppearance(darkTheme) }
            WeatherAppTheme(darkTheme = darkTheme) {
                WeatherRoot(
                    viewModel = viewModel,
                    appearanceViewModel = container.appearanceViewModel,
                )
            }
        }
        handleWidgetLaunch(intent)
    }

    /** A widget tap while this activity is still on screen arrives here instead of in [onCreate]. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetLaunch(intent)
    }

    /**
     * Opens the app at the location the tapped widget shows.
     *
     * A plain launch and a tap on a "aktueller Ort" widget need no action - the app already shows that
     * place. Everything else is decided by the view model, which never writes a home location and never
     * favourite anything because a widget was tapped.
     */
    private fun handleWidgetLaunch(intent: Intent?) {
        when (WidgetLaunch.locationModeFrom(intent)) {
            null, WidgetLocationMode.SELECTED -> Unit
            WidgetLocationMode.HOME -> viewModel.showHomeLocation()
            WidgetLocationMode.FIXED ->
                WidgetLaunch.locationFrom(intent)?.let { viewModel.showFixedLocation(it) }
        }
    }

    /** Refreshes stale data when the app returns to the foreground. */
    override fun onResume() {
        super.onResume()
        viewModel.onForeground()
    }

    /** True while the Android system itself is in dark mode. */
    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Keeps the status and navigation bar icons readable for the scheme that is actually drawn. */
    private fun applySystemBarAppearance(darkTheme: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}
