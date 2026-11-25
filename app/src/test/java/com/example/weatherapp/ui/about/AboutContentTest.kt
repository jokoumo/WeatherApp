package com.example.weatherapp.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The information the About screen shows has to be right, not just present: real links, a licence
 * for every shipped component and no invented attribution. These checks keep the list honest
 * without needing a device.
 */
class AboutContentTest {

    @Test
    fun licenceIdsAreUniqueAndResolvable() {
        val ids = AboutContent.licences.map { it.id }
        assertEquals("licence ids must be unique", ids.size, ids.distinct().size)

        ids.forEach { id -> assertNotNull(AboutContent.licenceById(id)) }
        assertEquals(null, AboutContent.licenceById("does-not-exist"))
        assertEquals(null, AboutContent.licenceById(null))
    }

    /** Every text of the About screen comes from a resource, so it is translatable and offline. */
    @Test
    fun everyEntryCarriesResourceBackedText() {
        AboutContent.sources.forEach { entry ->
            assertTrue("title resource of ${entry.url}", entry.titleRes != 0)
            assertTrue("summary resource of ${entry.url}", entry.summaryRes != 0)
        }
        AboutContent.licences.forEach { entry ->
            assertTrue("title resource of ${entry.id}", entry.titleRes != 0)
            assertTrue("summary resource of ${entry.id}", entry.summaryRes != 0)
            assertTrue("licence name resource of ${entry.id}", entry.licenceNameRes != 0)
            assertTrue("holder of ${entry.id}", entry.holder.isNotBlank())
        }
    }

    /**
     * Links are opened in the browser, so they have to be https URLs with a host. The one entry
     * without a link is the project's own artwork, which has no upstream page.
     */
    @Test
    fun sourceLinksAreHttpsUrls() {
        AboutContent.sources.forEach { entry -> assertHttpsUrl(entry.url) }
        AboutContent.licences.forEach { entry ->
            if (entry.url.isEmpty()) {
                assertEquals("only the own artwork may stay without a link", "project-artwork", entry.id)
            } else {
                assertHttpsUrl(entry.url)
            }
        }
        assertEquals("https://open-meteo.com/", AboutContent.ATTRIBUTION_URL)
    }

    private fun assertHttpsUrl(url: String) {
        assertTrue("url '$url' is not https", url.startsWith("https://"))
        val host = url.removePrefix("https://").substringBefore('/')
        assertTrue("url '$url' has no usable host", host.isNotBlank() && host.contains('.'))
    }

    /**
     * The weather data attribution Open-Meteo requires, the location database behind the search,
     * and the two APIs the app really calls.
     */
    @Test
    fun weatherSourcesAndTheirOperatorsAreNamed() {
        val sourceUrls = AboutContent.sources.map { it.url }
        assertTrue("forecast API", sourceUrls.any { it == "https://open-meteo.com/en/docs" })
        assertTrue("geocoding API", sourceUrls.any { it.contains("geocoding") })
        assertTrue("location database", sourceUrls.any { it.contains("geonames.org") })

        val licenceIds = AboutContent.licences.map { it.id }
        assertTrue("weather data licence", licenceIds.contains("open-meteo-data"))
        assertTrue("location data licence", licenceIds.contains("geonames-data"))
    }

    /**
     * The library list mirrors the artifacts on the runtime classpath. If a dependency is added or
     * removed, this test is the place that has to be updated together with the About screen.
     */
    @Test
    fun shippedLibrariesAreListedWithTheirLicence() {
        val apache = AboutContent.licenceById("apache-libraries") ?: error("apache entry missing")
        val components = apache.components.joinToString("\n")

        listOf(
            "Compose",
            "Activity",
            "Lifecycle",
            "DataStore",
            "Room",
            "Kotlin Standard Library",
            "kotlinx.coroutines",
            "kotlinx.serialization",
            "Retrofit",
            "OkHttp",
        ).forEach { library ->
            assertTrue("'$library' is missing from the licence list", components.contains(library))
        }
        assertTrue("protobuf is not Apache licensed", !components.contains("protobuf"))
    }

    /**
     * Not everything is Apache: the protobuf runtime inside AndroidX DataStore is BSD licensed,
     * and the artwork of this app is not a third-party asset at all.
     */
    @Test
    fun nonApacheComponentsAreListedSeparately() {
        val protobuf = AboutContent.licenceById("protobuf") ?: error("protobuf entry missing")
        assertTrue("protobuf component", protobuf.components.any { it.contains("datastore") })

        val artwork = AboutContent.licenceById("project-artwork") ?: error("artwork entry missing")
        assertEquals("", artwork.url)
        assertTrue("own artwork is not a third-party asset", artwork.components.any { it.contains("weather_") })
    }
}
