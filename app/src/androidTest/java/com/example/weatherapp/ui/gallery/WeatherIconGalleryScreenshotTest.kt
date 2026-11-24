package com.example.weatherapp.ui.gallery

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * Development tool, not an assertion: it opens the debug gallery on the connected device and
 * writes one PNG per review page into the files directory of the app, from where
 * `adb exec-out run-as com.example.weatherapp tar -c -C files/icon_gallery .` takes them for a
 * pixel by pixel check of the icon family. The gallery has no production entry point and is not
 * exported, so an in process launch is the only way to see it.
 *
 * Every page is a fresh launch with a scroll offset instead of a gesture, which keeps the review
 * images reproducible: page n always starts at the same place.
 */
@RunWith(AndroidJUnit4::class)
class WeatherIconGalleryScreenshotTest {

    @Test
    fun writeGalleryScreenshotsIntoTheFilesDirectory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val directory = File(target.filesDir, GALLERY_DIRECTORY)
        directory.mkdirs()
        directory.listFiles()?.forEach(File::delete)
        repeat(PAGES) { page ->
            val intent = Intent(target, WeatherIconGalleryActivity::class.java)
                .putExtra(WeatherIconGalleryActivity.EXTRA_PAGE, page)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<WeatherIconGalleryActivity>(intent).use {
                instrumentation.waitForIdleSync()
                Thread.sleep(SETTLE_MILLIS)
                val name = String.format(Locale.ROOT, "gallery_page_%02d.png", page)
                capture(instrumentation, File(directory, name))
            }
        }
    }

    @Suppress("DEPRECATION") // UiAutomation is the screenshot API an instrumentation may use.
    private fun capture(instrumentation: Instrumentation, target: File) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "the device did not return a screenshot"
        }
        target.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 0, stream) }
        bitmap.recycle()
    }

    private companion object {
        const val GALLERY_DIRECTORY = "icon_gallery"
        const val PAGES = 20
        const val SETTLE_MILLIS = 400L
    }
}
