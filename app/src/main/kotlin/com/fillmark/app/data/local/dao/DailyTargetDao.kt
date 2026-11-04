package com.fillmark.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fillmark.app.data.local.entity.DailyTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTargetDao {
    @Query("SELECT * FROM daily_targets WHERE date_string = :dateString LIMIT 1")
    fun observeTargetForDate(dateString: String): Flow<DailyTargetEntity?>

    @Query("SELECT * FROM daily_targets WHERE date_string = :dateString LIMIT 1")
    suspend fun getTargetForDate(dateString: String): DailyTargetEntity?

    @Query("SELECT * FROM daily_targets ORDER BY date_string DESC")
    fun observeAllTargets(): Flow<List<DailyTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTarget(target: DailyTargetEntity)
}

