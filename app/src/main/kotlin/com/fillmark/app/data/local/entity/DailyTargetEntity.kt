package com.fillmark.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_targets")
data class DailyTargetEntity(
    @PrimaryKey
    @ColumnInfo(name = "date_string")
    val dateString: String,
    @ColumnInfo(name = "target_ml")
    val targetMl: Double
)

