package com.fillmark.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HydrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HydrationRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<HydrationRecordEntity>)

    @Update
    suspend fun updateRecord(record: HydrationRecordEntity)

    @Delete
    suspend fun deleteRecord(record: HydrationRecordEntity)

    @Query("DELETE FROM hydration_records WHERE id = :id")
    suspend fun deleteRecordById(id: String)

    @Query("SELECT * FROM hydration_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: String): HydrationRecordEntity?

    @Query("SELECT * FROM hydration_records WHERE health_connect_id = :hcId LIMIT 1")
    suspend fun getRecordByHealthConnectId(hcId: String): HydrationRecordEntity?

    @Query("SELECT * FROM hydration_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun observeRecordsBetween(startTime: Long, endTime: Long): Flow<List<HydrationRecordEntity>>

    @Query("SELECT * FROM hydration_records WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getRecordsBetween(startTime: Long, endTime: Long): List<HydrationRecordEntity>

    @Query("SELECT * FROM hydration_records ORDER BY timestamp DESC")
    fun observeAllRecords(): Flow<List<HydrationRecordEntity>>

    @Query("SELECT * FROM hydration_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<HydrationRecordEntity>

    @Query("SELECT * FROM hydration_records WHERE health_connect_id IS NULL")
    suspend fun getUnsyncedRecords(): List<HydrationRecordEntity>

    @Query("SELECT * FROM hydration_records WHERE health_connect_id IS NOT NULL AND last_modified > last_synced")
    suspend fun getModifiedRecords(): List<HydrationRecordEntity>

    @Query("UPDATE hydration_records SET health_connect_id = :hcId, last_modified = :lastModified WHERE id = :id")
    suspend fun updateHealthConnectId(id: String, hcId: String, lastModified: Long = System.currentTimeMillis())

    @Query("UPDATE hydration_records SET health_connect_id = :hcId, last_synced = :lastSynced, last_modified = :lastModified WHERE id = :id")
    suspend fun updateHealthConnectSyncInfo(id: String, hcId: String, lastSynced: Long, lastModified: Long)

    @Query("UPDATE hydration_records SET last_synced = :lastSynced WHERE id = :id")
    suspend fun updateLastSynced(id: String, lastSynced: Long)

    @Query("SELECT SUM(volume_ml * hydration_factor) FROM hydration_records WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun observeTotalEffectiveMlBetween(startTime: Long, endTime: Long): Flow<Double?>
}

