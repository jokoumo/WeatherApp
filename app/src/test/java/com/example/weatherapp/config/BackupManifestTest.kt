package com.example.weatherapp.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Configuration check for the manifest: the two backup rule files have to be referenced, backup
 * has to be enabled, and the permission list has to stay as small as the privacy page claims.
 *
 * The source manifest is read as text on purpose. The attributes involved are not exposed by
 * [android.content.pm.ApplicationInfo] to applications, so the declaration itself is checked.
 */
class BackupManifestTest {

    private val manifest: String by lazy { readSourceManifest() }

    private val applicationTag: String
        get() {
            val matcher = Pattern.compile("<application[\\s\\S]*?>").matcher(manifest)
            assertTrue("the manifest has an <application> element", matcher.find())
            return matcher.group()
        }

    @Test
    fun backupIsEnabledAndBothRuleFilesAreReferenced() {
        assertTrue("backup is enabled", applicationTag.contains("android:allowBackup=\"true\""))
        assertTrue(
            "the rules for Android 11 and lower are referenced",
            applicationTag.contains("android:fullBackupContent=\"@xml/backup_rules\""),
        )
        assertTrue(
            "the rules for Android 12 and newer are referenced",
            applicationTag.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""),
        )
    }

    /** The application talks to https hosts only, so cleartext traffic stays off. */
    @Test
    fun cleartextTrafficIsNotPermitted() {
        assertFalse(
            "no cleartext traffic",
            manifest.contains("android:usesCleartextTraffic=\"true\""),
        )
    }

    /**
     * The privacy page states that the app never asks for the position of the device. The manifest
     * has to stay in line with that sentence.
     */
    @Test
    fun internetIsTheOnlyPermission() {
        val permissions = Pattern.compile("<uses-permission[^>]*android:name=\"([^\"]+)\"")
            .matcher(manifest)
            .let { matcher ->
                buildList {
                    while (matcher.find()) add(matcher.group(1))
                }
            }

        assertEquals(listOf("android.permission.INTERNET"), permissions)
    }

    /** Gradle runs unit tests from the module directory; the walk up keeps it working elsewhere. */
    private fun readSourceManifest(): String {
        var directory: File? = File(System.getProperty("user.dir"))
        while (directory != null) {
            val candidate = listOf(
                File(directory, "src/main/AndroidManifest.xml"),
                File(directory, "app/src/main/AndroidManifest.xml"),
            ).firstOrNull { it.isFile }
            if (candidate != null) return candidate.readText()
            directory = directory.parentFile
        }
        error("AndroidManifest.xml of the application could not be located for the manifest check")
    }
}
