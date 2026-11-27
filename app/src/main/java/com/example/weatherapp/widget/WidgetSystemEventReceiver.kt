package com.example.weatherapp.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.weatherapp.WeatherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Redraws the widgets when the world underneath them changes.
 *
 * The four actions cover the cases a forecast widget cannot ignore:
 *
 * - [Intent.ACTION_DATE_CHANGED] and [Intent.ACTION_TIMEZONE_CHANGED] move the day boundaries the
 *   day-period widget is built from, and a rolling four-period list is wrong the moment the local date
 *   or the local clock shifts;
 * - [Intent.ACTION_TIME_CHANGED] covers a manual clock change, which can jump across a period boundary;
 * - [Intent.ACTION_MY_PACKAGE_REPLACED] runs after an update, when the stored configuration has to be
 *   reconciled with the widgets the launcher really holds.
 *
 * The system sends all four as protected broadcasts, so this receiver stays unexported and the app
 * needs no permission for it. A cold start of the app or the periodic WorkManager job covers a device
 * that missed the broadcasts while switched off; there is deliberately no boot receiver.
 */
class WidgetSystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as? WeatherApplication)?.container ?: return
        val coordinator = container.widgetRefreshCoordinator
        val packageReplaced = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (packageReplaced) {
                    // An upgrade is the one moment worth comparing stored widget ids with the launcher,
                    // because a restored or partially removed home screen would otherwise keep a stale
                    // configuration - and with it the forecast of a place nobody looks at any more.
                    coordinator.pruneStaleConfigurations()
                }
                coordinator.requestWidgetUpdate()
            } catch (failure: Exception) {
                Log.w(TAG, "Widget refresh after system event failed", failure)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "WidgetSystemEvents"
    }
}
