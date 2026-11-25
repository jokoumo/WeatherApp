package com.example.weatherapp.ui.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.weatherapp.ui.theme.WeatherAppTheme

/**
 * Debug-only host of [WeatherIconGallery]. It is declared in the debug manifest only, so it is
 * absent from a release build and never reachable from the production navigation; the instrumented
 * gallery test starts it through [androidx.test.core.app.ActivityScenario].
 */
class WeatherIconGalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getIntExtra(EXTRA_PAGE, 0).coerceAtLeast(0)
        setContent {
            WeatherAppTheme {
                WeatherIconGallery(
                    modifier = Modifier.fillMaxSize(),
                    page = page,
                )
            }
        }
    }

    companion object {
        /** Zero based review page the screenshot helper asks the gallery to start at. */
        const val EXTRA_PAGE = "com.example.weatherapp.ui.gallery.PAGE"
    }
}
