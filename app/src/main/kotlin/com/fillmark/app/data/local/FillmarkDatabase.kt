package com.fillmark.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fillmark.app.R
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.data.local.dao.DailyTargetDao
import com.fillmark.app.data.local.dao.HydrationDao
import com.fillmark.app.data.local.dao.PendingHealthConnectDeletionDao
import com.fillmark.app.data.local.dao.PresetDao
import com.fillmark.app.data.local.entity.DailyTargetEntity
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PendingHealthConnectDeletionEntity
import com.fillmark.app.data.local.entity.PresetEntity
import java.util.UUID

@Database(
    entities = [
        HydrationRecordEntity::class,
        PresetEntity::class,
        DailyTargetEntity::class,
        PendingHealthConnectDeletionEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class FillmarkDatabase : RoomDatabase() {
    abstract fun hydrationDao(): HydrationDao
    abstract fun presetDao(): PresetDao
    abstract fun dailyTargetDao(): DailyTargetDao
    abstract fun pendingHealthConnectDeletionDao(): PendingHealthConnectDeletionDao

    companion object {
        @Volatile
        private var INSTANCE: FillmarkDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE presets ADD COLUMN drink_type TEXT NOT NULL DEFAULT 'WATER'")
                db.execSQL("ALTER TABLE presets ADD COLUMN color_hex TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hydration_records ADD COLUMN last_synced INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_hc_deletions` (`health_connect_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`health_connect_id`))"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hydration_records ADD COLUMN label TEXT")
            }
        }

        fun getInstance(context: Context): FillmarkDatabase {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    appContext,
                    FillmarkDatabase::class.java,
                    "fillmark_database"
                )
                    .addCallback(DatabaseCallback(appContext))
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun defaultPresets(context: Context): List<PresetEntity> {
            return listOf(
                PresetEntity(
                    id = UUID.randomUUID().toString(),
                    label = context.getString(R.string.preset_default_glass),
                    volumeMl = 250.0,
                    iconKey = PresetIcon.GLASS.id,
                    hydrationFactor = 1.0,
                    displayOrder = 0,
                    drinkType = "WATER",
                    colorHex = "#2196F3"
                ),
                PresetEntity(
                    id = UUID.randomUUID().toString(),
                    label = context.getString(R.string.preset_default_coffee_mug),
                    volumeMl = 350.0,
                    iconKey = PresetIcon.MUG.id,
                    hydrationFactor = 0.85,
                    displayOrder = 1,
                    drinkType = "COFFEE",
                    colorHex = "#795548"
                ),
                PresetEntity(
                    id = UUID.randomUUID().toString(),
                    label = context.getString(R.string.preset_default_water_bottle),
                    volumeMl = 500.0,
                    iconKey = PresetIcon.WATER_BOTTLE.id,
                    hydrationFactor = 1.0,
                    displayOrder = 2,
                    drinkType = "WATER",
                    colorHex = "#03A9F4"
                ),
                PresetEntity(
                    id = UUID.randomUUID().toString(),
                    label = context.getString(R.string.preset_default_tumbler),
                    volumeMl = 750.0,
                    iconKey = PresetIcon.TUMBLER.id,
                    hydrationFactor = 0.90,
                    displayOrder = 3,
                    drinkType = "TEA",
                    colorHex = "#4CAF50"
                ),
                PresetEntity(
                    id = UUID.randomUUID().toString(),
                    label = context.getString(R.string.preset_default_large_bottle),
                    volumeMl = 1000.0,
                    iconKey = PresetIcon.WATER_BOTTLE.id,
                    hydrationFactor = 1.0,
                    displayOrder = 4,
                    drinkType = "WATER",
                    colorHex = "#00BCD4"
                )
            )
        }

        val DEFAULT_PRESETS = listOf(
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Glass",
                volumeMl = 250.0,
                iconKey = PresetIcon.GLASS.id,
                hydrationFactor = 1.0,
                displayOrder = 0,
                drinkType = "WATER",
                colorHex = "#2196F3"
            ),
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Coffee Mug",
                volumeMl = 350.0,
                iconKey = PresetIcon.MUG.id,
                hydrationFactor = 0.85,
                displayOrder = 1,
                drinkType = "COFFEE",
                colorHex = "#795548"
            ),
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Water Bottle",
                volumeMl = 500.0,
                iconKey = PresetIcon.WATER_BOTTLE.id,
                hydrationFactor = 1.0,
                displayOrder = 2,
                drinkType = "WATER",
                colorHex = "#03A9F4"
            ),
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Tumbler",
                volumeMl = 750.0,
                iconKey = PresetIcon.TUMBLER.id,
                hydrationFactor = 0.90,
                displayOrder = 3,
                drinkType = "TEA",
                colorHex = "#4CAF50"
            ),
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Large Bottle",
                volumeMl = 1000.0,
                iconKey = PresetIcon.WATER_BOTTLE.id,
                hydrationFactor = 1.0,
                displayOrder = 4,
                drinkType = "WATER",
                colorHex = "#00BCD4"
            )
        )

        private class DatabaseCallback(private val appContext: Context) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                defaultPresets(appContext).forEach { preset ->
                    val values = ContentValues().apply {
                        put("id", preset.id)
                        put("label", preset.label)
                        put("volume_ml", preset.volumeMl)
                        put("icon_key", preset.iconKey)
                        put("hydration_factor", preset.hydrationFactor)
                        put("display_order", preset.displayOrder)
                        put("drink_type", preset.drinkType)
                        put("color_hex", preset.colorHex)
                    }
                    db.insert("presets", SQLiteDatabase.CONFLICT_REPLACE, values)
                }
            }
        }
    }
}

