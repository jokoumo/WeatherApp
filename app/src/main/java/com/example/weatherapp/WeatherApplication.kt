package com.example.weatherapp

import android.app.Application
import com.example.weatherapp.di.AppContainer

/** Creates the application wide object graph once, before any UI is shown. */
class WeatherApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
