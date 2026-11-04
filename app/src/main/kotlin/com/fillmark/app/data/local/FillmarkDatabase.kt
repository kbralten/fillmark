package com.fillmark.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = false
)
abstract class FillmarkDatabase : RoomDatabase() {
    abstract fun hydrationDao(): HydrationDao
    abstract fun presetDao(): PresetDao
    abstract fun dailyTargetDao(): DailyTargetDao

    companion object {
        @Volatile
        private var INSTANCE: FillmarkDatabase? = null

        fun getInstance(context: Context): FillmarkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FillmarkDatabase::class.java,
                    "fillmark_database"
                )
                    .addCallback(DatabaseCallback())
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
                displayOrder = 0
            ),
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Coffee Mug",
                volumeMl = 350.0,
                iconKey = PresetIcon.MUG.id,
                hydrationFactor = 0.85,
                displayOrder = 1
            ),
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Water Bottle",
                volumeMl = 500.0,
                iconKey = PresetIcon.WATER_BOTTLE.id,
                hydrationFactor = 1.0,
                displayOrder = 2
            ),
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Tumbler",
                volumeMl = 750.0,
                iconKey = PresetIcon.TUMBLER.id,
                hydrationFactor = 1.0,
                displayOrder = 3
            ),
            PresetEntity(
                id = UUID.randomUUID().toString(),
                label = "Large Bottle",
                volumeMl = 1000.0,
                iconKey = PresetIcon.WATER_BOTTLE.id,
                hydrationFactor = 1.0,
                displayOrder = 4
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

