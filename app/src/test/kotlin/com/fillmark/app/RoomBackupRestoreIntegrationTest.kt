package com.fillmark.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.backup.BackupManager
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.DailyTargetEntity
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PendingHealthConnectDeletionEntity
import com.fillmark.app.data.local.entity.PresetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomBackupRestoreIntegrationTest {

    private lateinit var context: Context
    private lateinit var backupFile: File

    @Before
    fun setup() {
        FillmarkDatabase.resetInstanceForTesting()
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("fillmark_database")
        backupFile = File(context.cacheDir, "test-fillmark-backup.json")
        if (backupFile.exists()) backupFile.delete()
    }

    @After
    fun tearDown() {
        if (::backupFile.isInitialized && backupFile.exists()) {
            backupFile.delete()
        }
        FillmarkDatabase.resetInstanceForTesting()
        context.deleteDatabase("fillmark_database")
    }

    @Test
    fun exportAndRestoreBackup_roundTripsRoomTablesAndDataStorePreferences() = runBlocking {
        val db = FillmarkDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)

        // Seed Room tables with representative custom data
        db.hydrationDao().deleteAllRecords()
        db.presetDao().deleteAllPresets()
        db.dailyTargetDao().deleteAllTargets()
        db.pendingHealthConnectDeletionDao().deleteAll()

        val seededRecords = listOf(
            HydrationRecordEntity(
                id = "rec-matcha",
                timestamp = 1700001000000L,
                volumeMl = 300.0,
                hydrationFactor = 1.15,
                drinkType = "TEA",
                iconKey = "mug",
                healthConnectId = "hc-orig-1",
                lastModified = 1700001005000L,
                lastSynced = 1700001005000L,
                label = "Matcha Latte"
            ),
            HydrationRecordEntity(
                id = "rec-water",
                timestamp = 1700002000000L,
                volumeMl = 500.0,
                hydrationFactor = 1.0,
                drinkType = "WATER",
                iconKey = "water_bottle",
                healthConnectId = null,
                lastModified = 1700002005000L,
                lastSynced = 0L,
                label = null
            )
        )
        val seededPresets = listOf(
            PresetEntity(
                id = "preset-matcha",
                label = "Matcha Bowl",
                volumeMl = 300.0,
                iconKey = "mug",
                hydrationFactor = 1.15,
                displayOrder = 0,
                drinkType = "TEA",
                colorHex = "#4CAF50"
            )
        )
        val seededTargets = listOf(
            DailyTargetEntity(dateString = "2026-11-22", targetMl = 2200.0),
            DailyTargetEntity(dateString = "2026-11-23", targetMl = 2800.0)
        )

        db.hydrationDao().insertRecords(seededRecords)
        db.presetDao().insertPresets(seededPresets)
        db.dailyTargetDao().insertTargets(seededTargets)

        prefsRepo.updateDisplayUnit(DisplayUnit.IMPERIAL_FL_OZ)
        prefsRepo.updateDefaultDailyTargetMl(2800.0)
        prefsRepo.updateWakingSchedule("06:30", "22:30", pacingEnabled = false)
        prefsRepo.updateThemeMode(ThemeMode.DARK)
        prefsRepo.updateDynamicColorEnabled(true)
        prefsRepo.updateHydrationFactorsEnabled(false)

        val uri = Uri.fromFile(backupFile)
        val exportResult = BackupManager.exportBackupToUri(context, uri)
        assertEquals(2, exportResult.recordsCount)
        assertEquals(1, exportResult.presetsCount)
        assertEquals(2, exportResult.targetsCount)
        assertTrue(backupFile.exists() && backupFile.length() > 0L)

        // Wipe and mutate local DB & prefs to verify restore actually replaces everything
        db.hydrationDao().deleteAllRecords()
        db.presetDao().deleteAllPresets()
        db.dailyTargetDao().deleteAllTargets()
        db.pendingHealthConnectDeletionDao().insert(
            PendingHealthConnectDeletionEntity(healthConnectId = "stale-hc-del", createdAt = 123456L)
        )
        prefsRepo.updateDisplayUnit(DisplayUnit.METRIC_ML)
        prefsRepo.updateDefaultDailyTargetMl(1500.0)
        prefsRepo.updateThemeMode(ThemeMode.LIGHT)
        prefsRepo.updateHydrationFactorsEnabled(true)

        val restoreResult = BackupManager.restoreBackupFromUri(context, uri)
        assertEquals(2, restoreResult.recordsCount)
        assertEquals(1, restoreResult.presetsCount)
        assertEquals(2, restoreResult.targetsCount)

        val restoredRecords = db.hydrationDao().getAllRecords().associateBy { it.id }
        assertEquals(2, restoredRecords.size)
        val matcha = restoredRecords.getValue("rec-matcha")
        assertEquals("Matcha Latte", matcha.label)
        assertEquals(1.15, matcha.hydrationFactor, 0.001)
        assertEquals("TEA", matcha.drinkType)
        // Restored records must clear HC ID & lastSynced so outbound sync can safely re-sync
        assertNull(matcha.healthConnectId)
        assertEquals(0L, matcha.lastSynced)

        val restoredPresets = db.presetDao().getAllPresets()
        assertEquals(1, restoredPresets.size)
        assertEquals("Matcha Bowl", restoredPresets[0].label)
        assertEquals(1.15, restoredPresets[0].hydrationFactor, 0.001)

        val restoredTargets = db.dailyTargetDao().getAllTargets().associateBy { it.dateString }
        assertEquals(2200.0, restoredTargets["2026-11-22"]?.targetMl ?: 0.0, 0.001)
        assertEquals(2800.0, restoredTargets["2026-11-23"]?.targetMl ?: 0.0, 0.001)

        assertTrue(db.pendingHealthConnectDeletionDao().getAll().isEmpty())

        val restoredPrefs = prefsRepo.preferencesFlow.first()
        assertEquals(DisplayUnit.IMPERIAL_FL_OZ, restoredPrefs.displayUnit)
        assertEquals(2800.0, restoredPrefs.defaultDailyTargetMl, 0.001)
        assertEquals("06:30", restoredPrefs.wakingHoursStart)
        assertEquals("22:30", restoredPrefs.wakingHoursEnd)
        assertEquals(false, restoredPrefs.wakingPacingEnabled)
        assertEquals(ThemeMode.DARK, restoredPrefs.themeMode)
        assertEquals(true, restoredPrefs.dynamicColorEnabled)
        assertEquals(false, restoredPrefs.hydrationFactorsEnabled)
    }
}
