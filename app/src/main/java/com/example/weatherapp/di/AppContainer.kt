package com.example.weatherapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.weatherapp.data.local.DataStoreSettingsStore
import com.example.weatherapp.data.local.ForecastCacheStore
import com.example.weatherapp.data.local.RoomForecastCacheStore
import com.example.weatherapp.data.local.SettingsStore
import com.example.weatherapp.data.local.WeatherDatabase
import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.remote.NetworkModule
import com.example.weatherapp.data.remote.OpenMeteoForecastApi
import com.example.weatherapp.data.remote.OpenMeteoGeocodingApi
import com.example.weatherapp.data.repository.LocationRepository
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.ui.MainViewModel
import okhttp3.OkHttpClient
import androidx.room.Room

/**
 * Manual dependency container created once by [com.example.weatherapp.WeatherApplication].
 *
 * A dependency injection framework is not needed for this app size; constructing the object
 * graph here keeps it explicit and keeps every class testable through its constructor.
 */
class AppContainer(private val context: Context) {

    private val httpClient: OkHttpClient by lazy { NetworkModule.createHttpClient() }

    val forecastApi: OpenMeteoForecastApi by lazy {
        NetworkModule.createForecastApi(httpClient, NetworkModule.json)
    }

    val geocodingApi: OpenMeteoGeocodingApi by lazy {
        NetworkModule.createGeocodingApi(httpClient, NetworkModule.json)
    }

    val forecastMapper: ForecastMapper by lazy { ForecastMapper(NetworkModule.json) }

    private val database: WeatherDatabase by lazy {
        Room.databaseBuilder(context.applicationContext, WeatherDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val forecastCacheStore: ForecastCacheStore by lazy {
        RoomForecastCacheStore(database.forecastCacheDao())
    }

    val settingsStore: SettingsStore by lazy {
        DataStoreSettingsStore(preferencesDataStore(), NetworkModule.json)
    }

    val locationRepository: LocationRepository by lazy {
        LocationRepository(settingsStore, geocodingApi, forecastMapper)
    }

    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(forecastApi, forecastCacheStore, forecastMapper)
    }

    val mainViewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory {
            initializer {
                MainViewModel(
                    locationRepository = locationRepository,
                    weatherRepository = weatherRepository,
                )
            }
        }
    }

    private fun preferencesDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.applicationContext.preferencesDataStoreFile(PREFERENCES_NAME) },
        )

    private companion object {
        const val DATABASE_NAME = "weather-cache"
        const val PREFERENCES_NAME = "weather-settings"
    }
}
