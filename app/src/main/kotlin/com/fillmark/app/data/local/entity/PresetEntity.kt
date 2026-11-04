package com.fillmark.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    @ColumnInfo(name = "volume_ml")
    val volumeMl: Double,
    @ColumnInfo(name = "icon_key")
    val iconKey: String,
    @ColumnInfo(name = "hydration_factor", defaultValue = "1.0")
    val hydrationFactor: Double = 1.0,
    @ColumnInfo(name = "display_order")
    val displayOrder: Int,
    @ColumnInfo(name = "drink_type", defaultValue = "WATER")
    val drinkType: String = "WATER",
    @ColumnInfo(name = "color_hex")
    val colorHex: String? = null
)

