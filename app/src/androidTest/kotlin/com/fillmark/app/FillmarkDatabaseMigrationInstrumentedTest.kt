package com.fillmark.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fillmark.app.data.local.FillmarkDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FillmarkDatabaseMigrationInstrumentedTest {

    private val testDbName = "instrumented-migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FillmarkDatabase::class.java
    )

    @Before
    fun cleanUpBefore() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(testDbName)
    }

    @After
    fun cleanUpAfter() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(testDbName)
    }

    @Test
    fun migrateAll1To5_preservesSeededDataOnAndroidSqlite() = runBlocking {
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO hydration_records (id, timestamp, volume_ml, hydration_factor, drink_type, icon_key, health_connect_id, last_modified)
                VALUES ('inst-rec-1', 1700000010000, 250.0, 1.0, 'WATER', 'glass', NULL, 1700000010000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO presets (id, label, volume_ml, icon_key, hydration_factor, display_order)
                VALUES ('inst-preset-1', 'Glass', 250.0, 'glass', 1.0, 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            testDbName,
            5,
            true,
            FillmarkDatabase.MIGRATION_1_2,
            FillmarkDatabase.MIGRATION_2_3,
            FillmarkDatabase.MIGRATION_3_4,
            FillmarkDatabase.MIGRATION_4_5
        ).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FillmarkDatabase::class.java,
            testDbName
        ).addMigrations(
            FillmarkDatabase.MIGRATION_1_2,
            FillmarkDatabase.MIGRATION_2_3,
            FillmarkDatabase.MIGRATION_3_4,
            FillmarkDatabase.MIGRATION_4_5
        ).build()

        try {
            val records = db.hydrationDao().getAllRecords()
            assertEquals(1, records.size)
            assertEquals("inst-rec-1", records[0].id)
            assertEquals(0L, records[0].lastSynced)
            assertNull(records[0].label)

            val presets = db.presetDao().getAllPresets()
            assertEquals(1, presets.size)
            assertEquals("WATER", presets[0].drinkType)
        } finally {
            db.close()
        }
    }
}
