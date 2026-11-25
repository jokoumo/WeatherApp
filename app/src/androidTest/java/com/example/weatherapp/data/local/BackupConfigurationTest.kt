package com.example.weatherapp.data.local

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.R
import com.example.weatherapp.di.AppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * The declared backup policy compared with the files the application really writes.
 *
 * This is a configuration check, not a backup and restore run: it reads the two rule resources the
 * way the system reads them and makes sure the weather cache is named while the preference file
 * that holds the saved locations is left alone.
 */
@RunWith(AndroidJUnit4::class)
class BackupConfigurationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private data class Rule(val domain: String, val path: String)

    private class Scheme(
        val sections: List<String>,
        val includes: List<Rule>,
        val excludes: List<Rule>,
    )

    private fun parseScheme(resource: Int): Scheme {
        val sections = mutableListOf<String>()
        val includes = mutableListOf<Rule>()
        val excludes = mutableListOf<Rule>()
        val parser = context.resources.getXml(resource)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val domain = parser.getAttributeValue(null, "domain").orEmpty()
            val path = parser.getAttributeValue(null, "path").orEmpty()
            when (parser.name) {
                "include" -> includes += Rule(domain, path)
                "exclude" -> excludes += Rule(domain, path)
                "cloud-backup", "device-transfer" -> sections += parser.name
            }
        }
        return Scheme(sections, includes, excludes)
    }

    /** The database file and its side files as they appear on the device. */
    private val databaseFileName: String
        get() = context.getDatabasePath(AppContainer.DATABASE_NAME).name

    private val cachePaths = setOf(
        "weather-cache",
        "weather-cache-wal",
        "weather-cache-shm",
    )

    @Test
    fun legacyRulesExcludeTheForecastCacheAndNothingElse() {
        val scheme = parseScheme(R.xml.backup_rules)

        assertEquals(emptyList<Rule>(), scheme.includes)
        assertTrue(
            "the forecast cache database has to be excluded",
            scheme.excludes.containsAll(
                cachePaths.map { Rule("database", it) },
            ),
        )
        assertEquals(
            "only the cache files and the debug gallery may be excluded",
            cachePaths.size + 1,
            scheme.excludes.size,
        )
    }

    @Test
    fun extractionRulesCoverCloudBackupAndDeviceTransferIdentically() {
        val scheme = parseScheme(R.xml.data_extraction_rules)

        assertEquals(listOf("cloud-backup", "device-transfer"), scheme.sections)
        assertEquals(emptyList<Rule>(), scheme.includes)
        assertEquals(
            "both sections carry the same four exclusions",
            2 * (cachePaths.size + 1),
            scheme.excludes.size,
        )
        scheme.excludes.chunked(cachePaths.size + 1).forEach { sectionRules ->
            assertTrue(
                "each section excludes the cache database and its side files",
                sectionRules.containsAll(cachePaths.map { Rule("database", it) }),
            )
        }
    }

    /** The rules name the file the application actually opens, not an invented one. */
    @Test
    fun excludedNamesMatchTheFilesOnDisk() {
        val scheme = parseScheme(R.xml.data_extraction_rules)
        val excludedDatabasePaths = scheme.excludes
            .filter { it.domain == "database" }
            .map { it.path }
            .toSet()

        assertEquals(AppContainer.DATABASE_NAME, databaseFileName)
        assertTrue(
            "the real database file is excluded",
            excludedDatabasePaths.contains(databaseFileName),
        )
        assertTrue(
            "the write-ahead log of the database is excluded",
            excludedDatabasePaths.contains("$databaseFileName-wal"),
        )
        assertTrue(
            "the shared memory file of the database is excluded",
            excludedDatabasePaths.contains("$databaseFileName-shm"),
        )
    }

    /**
     * The saved locations must survive: no rule may hide the DataStore file that holds home
     * location, favourites, ordering, selection and appearance.
     */
    @Test
    fun preferencesFileStaysInsideTheBackup() {
        val preferencesFile = context.preferencesDataStoreFile(AppContainer.PREFERENCES_NAME)
        val relativeToDataDir = preferencesFile.absolutePath
            .removePrefix(context.dataDir.absolutePath + "/")

        assertEquals("files/datastore/$preferencesFileName", relativeToDataDir)

        listOf(parseScheme(R.xml.backup_rules), parseScheme(R.xml.data_extraction_rules))
            .forEach { scheme ->
                scheme.excludes.forEach { rule ->
                    val covered = when (rule.domain) {
                        "file" -> relativeToDataDir.startsWith("files/" + rule.path)
                        "root" -> relativeToDataDir.startsWith(rule.path.trimStart('/'))
                        else -> false
                    }
                    assertTrue(
                        "the rule domain=${rule.domain} path=${rule.path} would drop the settings",
                        !covered,
                    )
                }
            }
    }

    private val preferencesFileName get() = "${AppContainer.PREFERENCES_NAME}.preferences_pb"
}
