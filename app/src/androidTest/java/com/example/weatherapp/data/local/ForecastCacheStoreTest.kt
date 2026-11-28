package com.example.weatherapp.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.weatherapp.data.remote.ForecastMapper
import com.example.weatherapp.data.repository.WeatherRepository
import com.example.weatherapp.testing.OfflineForecastApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

/**
 * Exercises the Room backed forecast cache with a real (in-memory) database: storing,
 * replacing, reading and deleting one entry per location, and the two steps the cache
 * reconciliation performs - listing every stored key and removing a batch of them.
 */
@RunWith(AndroidJUnit4::class)
class ForecastCacheStoreTest {

    private lateinit var database: WeatherDatabase
    private lateinit var store: RoomForecastCacheStore

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, WeatherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomForecastCacheStore(database.forecastCacheDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun unknownLocationHasNoCacheEntry() = runBlocking {
        assertNull(store.find("does-not-exist"))
    }

    @Test
    fun storedForecastIsReturnedWithItsTimestampAndPayload() = runBlocking {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        val payload = """{"timezone":"Europe/Berlin","hourly":{"time":["2026-11-22T08:00"]}}"""
        store.save("2911298", fetchedAt, payload)

        val entry = requireNotNull(store.find("2911298"))
        assertEquals("2911298", entry.locationId)
        assertEquals(fetchedAt, entry.fetchedAt)
        assertEquals(payload, entry.payloadJson)
    }

    @Test
    fun savingAgainForTheSameLocationReplacesTheEntry() = runBlocking {
        store.save("2911298", Instant.parse("2026-11-22T11:00:00Z"), "old payload")
        val newer = Instant.parse("2026-11-22T12:00:00Z")
        store.save("2911298", newer, "new payload")

        val entry = requireNotNull(store.find("2911298"))
        assertEquals("new payload", entry.payloadJson)
        assertEquals(newer, entry.fetchedAt)
        assertEquals(1, database.forecastCacheDao().observeAll().first().size)
    }

    @Test
    fun rejectedCacheReadCannotDeleteAReplacementAtTheSameTimestamp() = runBlocking {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        store.save("2911298", fetchedAt, "unusable payload")
        val unusable = requireNotNull(store.find("2911298"))
        store.save("2911298", fetchedAt, "replacement")

        store.deleteIfUnchanged(unusable)

        assertEquals("replacement", store.find("2911298")?.payloadJson)
        store.deleteIfUnchanged(requireNotNull(store.find("2911298")))
        assertNull(store.find("2911298"))
    }

    @Test
    fun deletingOneLocationKeepsTheOthers() = runBlocking {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        store.save("2911298", fetchedAt, "hamburg")
        store.save("2950280", fetchedAt, "berlin")

        store.delete("2911298")

        assertNull(store.find("2911298"))
        assertNotNull(store.find("2950280"))
    }

    /** The reconciliation pass needs the keys of all rows without reading their payloads. */
    @Test
    fun locationIdsListsEveryStoredLocation() = runBlocking {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        store.save("2911298", fetchedAt, "hamburg")
        store.save("2950280", fetchedAt, "berlin")

        assertEquals(listOf("2911298", "2950280"), store.locationIds().sorted())
    }

    /**
     * Batch removal for the reconciliation pass. An empty list has to delete nothing at all: it
     * would otherwise reach the database as an empty "IN ()" and fail there.
     */
    @Test
    fun deletingSeveralLocationsKeepsTheUnlistedOnesAndAnEmptyListDeletesNothing() = runBlocking {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        store.save("2911298", fetchedAt, "hamburg")
        store.save("2950280", fetchedAt, "berlin")
        store.save("1850144", fetchedAt, "tokio")

        store.deleteAll(emptyList())
        assertEquals(3, store.locationIds().size)

        store.deleteAll(listOf("2911298", "1850144"))

        assertEquals(listOf("2950280"), store.locationIds())
    }

    /**
     * The same two steps the view model performs once per app start, against a real database: list
     * the keys, remove the ones no location role refers to any more. Reading the keys through the
     * repository also proves that the query works in Room and not only in the in-memory double.
     */
    @Test
    fun theRepositoryRemovesExactlyTheRowsThatAreNotRetained() = runBlocking {
        val repository = WeatherRepository(
            OfflineForecastApi(),
            store,
            ForecastMapper(),
            Clock.systemUTC(),
        )
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        store.save("2911298", fetchedAt, "hamburg")
        store.save("2950280", fetchedAt, "berlin")
        store.save("1850144", fetchedAt, "tokio")

        assertEquals(listOf("1850144", "2911298", "2950280"), repository.cachedLocationIds().sorted())

        repository.dropCaches(listOf("1850144", "2950280"))

        assertEquals(listOf("2911298"), repository.cachedLocationIds())
        assertNotNull(store.find("2911298"))
        assertNull(store.find("1850144"))
    }
}
