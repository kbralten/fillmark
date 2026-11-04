package com.fillmark.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "hydration_records",
    indices = [
        Index(value = ["health_connect_id"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class HydrationRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    @ColumnInfo(name = "volume_ml")
    val volumeMl: Double,
    @ColumnInfo(name = "hydration_factor", defaultValue = "1.0")
    val hydrationFactor: Double = 1.0,
    @ColumnInfo(name = "drink_type", defaultValue = "'WATER'")
    val drinkType: String = "WATER",
    @ColumnInfo(name = "icon_key")
    val iconKey: String? = null,
    @ColumnInfo(name = "health_connect_id")
    val healthConnectId: String? = null,
    @ColumnInfo(name = "last_modified")
    val lastModified: Long = System.currentTimeMillis()
) {
    val effectiveVolumeMl: Double
        get() = volumeMl * hydrationFactor
}

