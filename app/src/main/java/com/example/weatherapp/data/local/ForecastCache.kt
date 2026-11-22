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

    @Upsert
    suspend fun upsert(entry: ForecastCacheEntity)

    @Query("DELETE FROM forecast_cache WHERE location_id = :locationId")
    suspend fun delete(locationId: String)

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
}
