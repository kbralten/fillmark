package com.fillmark.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.data.local.dao.DailyTargetDao
import com.fillmark.app.data.local.dao.HydrationDao
import com.fillmark.app.data.local.dao.PresetDao
import com.fillmark.app.data.local.entity.DailyTargetEntity
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PresetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        HydrationRecordEntity::class,
        PresetEntity::class,
        DailyTargetEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FillmarkDatabase : RoomDatabase() {
    abstract fun hydrationDao(): HydrationDao
    abstract fun presetDao(): PresetDao
    abstract fun dailyTargetDao(): DailyTargetDao

    companion object {
        @Volatile
        private var INSTANCE: FillmarkDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE presets ADD COLUMN drink_type TEXT NOT NULL DEFAULT 'WATER'")
                db.execSQL("ALTER TABLE presets ADD COLUMN color_hex TEXT")
            }
        }

        fun getInstance(context: Context): FillmarkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FillmarkDatabase::class.java,
                    "fillmark_database"
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
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

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        database.presetDao().insertPresets(DEFAULT_PRESETS)
                    }
                }
            }
        }
    }
}

