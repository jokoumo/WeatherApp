package com.example.weatherapp.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Exercises the Room backed forecast cache with a real (in-memory) database: storing,
 * replacing, reading and deleting one entry per location.
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
    fun deletingOneLocationKeepsTheOthers() = runBlocking {
        val fetchedAt = Instant.parse("2026-11-22T11:00:00Z")
        store.save("2911298", fetchedAt, "hamburg")
        store.save("2950280", fetchedAt, "berlin")

        store.delete("2911298")

        assertNull(store.find("2911298"))
        assertNotNull(store.find("2950280"))
    }
}
