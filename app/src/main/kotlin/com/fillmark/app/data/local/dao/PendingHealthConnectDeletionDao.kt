package com.fillmark.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fillmark.app.data.local.entity.PendingHealthConnectDeletionEntity

@Dao
interface PendingHealthConnectDeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingHealthConnectDeletionEntity)

    @Query("SELECT * FROM pending_hc_deletions ORDER BY created_at ASC")
    suspend fun getAll(): List<PendingHealthConnectDeletionEntity>

    @Query("DELETE FROM pending_hc_deletions WHERE health_connect_id = :hcId")
    suspend fun deleteByHcId(hcId: String)

    @Query("DELETE FROM pending_hc_deletions")
    suspend fun deleteAll()
}
