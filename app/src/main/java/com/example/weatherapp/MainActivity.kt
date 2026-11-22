package com.example.weatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.weatherapp.ui.MainViewModel
import com.example.weatherapp.ui.WeatherRoot
import com.example.weatherapp.ui.theme.WeatherAppTheme

/** Single activity host: light and dark theme follow the system setting automatically. */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy {
        val container = (application as WeatherApplication).container
        ViewModelProvider(this, container.mainViewModelFactory)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherAppTheme {
                WeatherRoot(viewModel = viewModel)
            }
        }
    }

    /** Refreshes stale data when the app returns to the foreground. */
    override fun onResume() {
        super.onResume()
        viewModel.onForeground()
    }
}
