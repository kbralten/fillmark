package com.fillmark.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_hc_deletions")
data class PendingHealthConnectDeletionEntity(
    @PrimaryKey
    @ColumnInfo(name = "health_connect_id")
    val healthConnectId: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
