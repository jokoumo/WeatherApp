package com.example.weatherapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * One cached forecast per location. The payload is the serialised Open-Meteo response, so
 * the cache survives app updates without schema changes and never needs a second request.
 */
@Entity(tableName = "forecast_cache")
data class ForecastCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "location_id")
    val locationId: String,
    @ColumnInfo(name = "fetched_at_epoch_milli")
    val fetchedAtEpochMilli: Long,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
)

@Dao
interface ForecastCacheDao {

    @Query("SELECT * FROM forecast_cache WHERE location_id = :locationId")
    suspend fun find(locationId: String): ForecastCacheEntity?

    @Query("SELECT * FROM forecast_cache")
    fun observeAll(): Flow<List<ForecastCacheEntity>>

    /** Only the keys, for the reconciliation pass that looks for rows nobody references any more. */
    @Query("SELECT location_id FROM forecast_cache")
    suspend fun locationIds(): List<String>

    @Upsert
    suspend fun upsert(entry: ForecastCacheEntity)

    @Query("DELETE FROM forecast_cache WHERE location_id = :locationId")
    suspend fun delete(locationId: String)

    @Query(
        "DELETE FROM forecast_cache WHERE location_id = :locationId " +
            "AND fetched_at_epoch_milli = :fetchedAt AND payload_json = :payloadJson",
    )
    suspend fun deleteIfUnchanged(locationId: String, fetchedAt: Long, payloadJson: String)

    @Query("DELETE FROM forecast_cache WHERE location_id IN (:locationIds)")
    suspend fun deleteAll(locationIds: List<String>)

    @Query("DELETE FROM forecast_cache")
    suspend fun clear()
}

@Database(entities = [ForecastCacheEntity::class], version = 1, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
}

/** Location-scoped cache access, independent of Room so repositories stay testable. */
data class CachedForecast(
    val locationId: String,
    val fetchedAt: Instant,
    val payloadJson: String,
)

interface ForecastCacheStore {
    suspend fun find(locationId: String): CachedForecast?
    suspend fun save(locationId: String, fetchedAt: Instant, payloadJson: String)
    suspend fun delete(locationId: String)
    suspend fun deleteIfUnchanged(entry: CachedForecast)

    /** Every location id that currently has a cached forecast. */
    suspend fun locationIds(): List<String>

    /** Removes several locations at once; an empty list deletes nothing. */
    suspend fun deleteAll(locationIds: List<String>)
}

class RoomForecastCacheStore(private val dao: ForecastCacheDao) : ForecastCacheStore {

    override suspend fun find(locationId: String): CachedForecast? =
        dao.find(locationId)?.let {
            CachedForecast(
                locationId = it.locationId,
                fetchedAt = Instant.ofEpochMilli(it.fetchedAtEpochMilli),
                payloadJson = it.payloadJson,
            )
        }

    override suspend fun save(locationId: String, fetchedAt: Instant, payloadJson: String) {
        dao.upsert(
            ForecastCacheEntity(
                locationId = locationId,
                fetchedAtEpochMilli = fetchedAt.toEpochMilli(),
                payloadJson = payloadJson,
            ),
        )
    }

    override suspend fun delete(locationId: String) {
        dao.delete(locationId)
    }

    override suspend fun deleteIfUnchanged(entry: CachedForecast) {
        dao.deleteIfUnchanged(entry.locationId, entry.fetchedAt.toEpochMilli(), entry.payloadJson)
    }

    override suspend fun locationIds(): List<String> = dao.locationIds()

    override suspend fun deleteAll(locationIds: List<String>) {
        // An empty list would expand to "IN ()", which SQLite rejects, and deleting nothing is
        // exactly what an empty list asks for.
        if (locationIds.isEmpty()) return
        dao.deleteAll(locationIds)
    }
}
