package com.fillmark.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.fillmark.app.data.local.FillmarkDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FillmarkDatabaseMigrationTest {

    private val testDbName = "migration-test-db"

    // Room's SupportSQLiteDriver checks databaseName via substringAfterLast('/'), which fails on Windows
    // Robolectric paths using '\' unless databaseName returns the full expected path.
    private val crossPlatformOpenHelperFactory = SupportSQLiteOpenHelper.Factory { configuration ->
        val delegate = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val expectedPath = configuration.name?.let { name ->
            configuration.context.getDatabasePath(name).path
        }
        object : SupportSQLiteOpenHelper by delegate {
            override val databaseName: String?
                get() = expectedPath ?: delegate.databaseName
        }
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FillmarkDatabase::class.java,
        emptyList(),
        crossPlatformOpenHelperFactory
    )

    @Before
    fun cleanUpBefore() {
        FillmarkDatabase.resetInstanceForTesting()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(testDbName)
    }

    @After
    fun cleanUpAfter() {
        FillmarkDatabase.resetInstanceForTesting()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(testDbName)
    }

    @Test
    fun migrate1To2_addsPresetDrinkTypeAndColorHex() {
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO hydration_records (id, timestamp, volume_ml, hydration_factor, drink_type, icon_key, health_connect_id, last_modified)
                VALUES ('rec-1', 1700000000000, 250.0, 1.0, 'WATER', 'glass', NULL, 1700000000000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO presets (id, label, volume_ml, icon_key, hydration_factor, display_order)
                VALUES ('preset-1', 'Glass', 250.0, 'glass', 1.0, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO daily_targets (date_string, target_ml)
                VALUES ('2026-11-23', 2500.0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(testDbName, 2, true, FillmarkDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT id, label, drink_type, color_hex FROM presets WHERE id = 'preset-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Glass", cursor.getString(1))
                assertEquals("WATER", cursor.getString(2))
                assertTrue(cursor.isNull(3))
            }
        }
    }

    @Test
    fun migrate2To3_addsLastSyncedColumn() {
        helper.createDatabase(testDbName, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO hydration_records (id, timestamp, volume_ml, hydration_factor, drink_type, icon_key, health_connect_id, last_modified)
                VALUES ('rec-2', 1700000001000, 350.0, 0.85, 'COFFEE', 'mug', 'hc-2', 1700000001000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(testDbName, 3, true, FillmarkDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT id, last_synced FROM hydration_records WHERE id = 'rec-2'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("rec-2", cursor.getString(0))
                assertEquals(0L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun migrate3To4_createsPendingHealthConnectDeletionsTable() {
        helper.createDatabase(testDbName, 3).close()

        helper.runMigrationsAndValidate(testDbName, 4, true, FillmarkDatabase.MIGRATION_3_4).use { db ->
            db.execSQL(
                "INSERT INTO pending_hc_deletions (health_connect_id, created_at) VALUES ('hc-del-1', 1700000002000)"
            )
            db.query("SELECT health_connect_id, created_at FROM pending_hc_deletions").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("hc-del-1", cursor.getString(0))
                assertEquals(1700000002000L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun migrate4To5_addsNullableLabelColumn() {
        helper.createDatabase(testDbName, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO hydration_records (id, timestamp, volume_ml, hydration_factor, drink_type, icon_key, health_connect_id, last_modified, last_synced)
                VALUES ('rec-4', 1700000003000, 500.0, 1.0, 'WATER', 'water_bottle', NULL, 1700000003000, 1700000003000)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(testDbName, 5, true, FillmarkDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT id, label FROM hydration_records WHERE id = 'rec-4'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(1))
            }
            db.execSQL("UPDATE hydration_records SET label = 'Large Bottle' WHERE id = 'rec-4'")
            db.query("SELECT label FROM hydration_records WHERE id = 'rec-4'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Large Bottle", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrateAll1To5_preservesSeededDataAndOpensRoomDatabase() = runBlocking {
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO hydration_records (id, timestamp, volume_ml, hydration_factor, drink_type, icon_key, health_connect_id, last_modified)
                VALUES ('rec-v1', 1700000005000, 350.0, 0.85, 'COFFEE', 'mug', 'hc-v1', 1700000005000)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO presets (id, label, volume_ml, icon_key, hydration_factor, display_order)
                VALUES ('preset-v1', 'Morning Mug', 350.0, 'mug', 0.85, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO daily_targets (date_string, target_ml)
                VALUES ('2026-11-20', 2200.0)
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

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, FillmarkDatabase::class.java, testDbName)
            .addMigrations(
                FillmarkDatabase.MIGRATION_1_2,
                FillmarkDatabase.MIGRATION_2_3,
                FillmarkDatabase.MIGRATION_3_4,
                FillmarkDatabase.MIGRATION_4_5
            )
            .allowMainThreadQueries()
            .build()

        try {
            val records = db.hydrationDao().getAllRecords()
            assertEquals(1, records.size)
            assertEquals("rec-v1", records[0].id)
            assertEquals(350.0, records[0].volumeMl, 0.001)
            assertEquals(0L, records[0].lastSynced)
            assertNull(records[0].label)

            val presets = db.presetDao().getAllPresets()
            assertEquals(1, presets.size)
            assertEquals("Morning Mug", presets[0].label)
            assertEquals("WATER", presets[0].drinkType)
            assertNull(presets[0].colorHex)

            val target = db.dailyTargetDao().getTargetForDate("2026-11-20")
            assertEquals(2200.0, target?.targetMl ?: 0.0, 0.001)
        } finally {
            db.close()
        }
    }
}
