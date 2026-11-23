package com.fillmark.app

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.backup.BackupManager
import com.fillmark.app.data.backup.BackupPreferences
import com.fillmark.app.data.backup.BackupPreset
import com.fillmark.app.data.backup.BackupRecord
import com.fillmark.app.data.backup.BackupTarget
import com.fillmark.app.data.backup.FillmarkBackup
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class BackupManagerTest {

    @Test
    fun jsonRoundTrip_preservesAllData() {
        val backup = FillmarkBackup(
            schemaVersion = 1,
            exportedAtMillis = 1700000000000L,
            records = listOf(
                BackupRecord(
                    id = "r1",
                    timestamp = 1700000001000L,
                    volumeMl = 350.0,
                    hydrationFactor = 0.85,
                    drinkType = "COFFEE",
                    iconKey = "MUG",
                    lastModified = 1700000002000L,
                    label = "Morning Coffee"
                )
            ),
            presets = listOf(
                BackupPreset(
                    id = "p1",
                    label = "Morning Coffee",
                    volumeMl = 350.0,
                    iconKey = "MUG",
                    hydrationFactor = 0.85,
                    displayOrder = 0,
                    drinkType = "COFFEE",
                    colorHex = "#795548"
                )
            ),
            dailyTargets = listOf(
                BackupTarget(dateString = "2026-11-22", targetMl = 2800.0)
            ),
            preferences = BackupPreferences(
                displayUnit = DisplayUnit.IMPERIAL_FL_OZ.name,
                defaultDailyTargetMl = 2500.0,
                wakingHoursStart = "06:30",
                wakingHoursEnd = "22:30",
                wakingPacingEnabled = true,
                themeMode = ThemeMode.DARK.name,
                dynamicColorEnabled = true,
                hydrationFactorsEnabled = false
            )
        )

        val encoded = BackupManager.encodeBackupJson(backup)
        val decoded = BackupManager.decodeBackupJson(encoded)

        assertEquals(backup, decoded)
    }

    @Test
    fun encodeRecordsCsv_formatsHeaderAndRows() {
        val ts = Instant.parse("2026-11-23T14:30:00Z").toEpochMilli()
        val records = listOf(
            HydrationRecordEntity(
                id = "id-1",
                timestamp = ts,
                volumeMl = 500.0,
                hydrationFactor = 0.85,
                drinkType = "COFFEE",
                iconKey = "MUG",
                lastModified = ts
            )
        )

        val csvWithFactors = BackupManager.encodeRecordsCsv(records, hydrationFactorsEnabled = true, zoneId = ZoneId.of("UTC"))
        val lines = csvWithFactors.trim().lines()
        assertEquals(2, lines.size)
        assertEquals("id,date,time,timestamp_ms,drink_type,volume_ml,hydration_factor,effective_volume_ml", lines[0])
        assertTrue(lines[1].startsWith("id-1,2026-11-23,14:30:00,$ts,COFFEE,500.00,0.85,425.00"))

        val csvRaw = BackupManager.encodeRecordsCsv(records, hydrationFactorsEnabled = false, zoneId = ZoneId.of("UTC"))
        assertTrue(csvRaw.trim().lines()[1].endsWith(",500.00"))
    }

    private fun validBackup(): FillmarkBackup = FillmarkBackup(
        schemaVersion = 1,
        exportedAtMillis = 1700000000000L,
        records = listOf(
            BackupRecord(
                id = "r1",
                timestamp = 1700000001000L,
                volumeMl = 250.0,
                hydrationFactor = 1.0,
                drinkType = "WATER"
            )
        ),
        presets = listOf(
            BackupPreset(
                id = "p1",
                label = "Glass",
                volumeMl = 250.0,
                iconKey = "GLASS"
            )
        ),
        dailyTargets = listOf(
            BackupTarget(dateString = "2026-11-22", targetMl = 2500.0)
        ),
        preferences = BackupPreferences()
    )

    private fun assertInvalidBackupRejected(label: String, backup: FillmarkBackup) {
        val encoded = BackupManager.encodeBackupJson(backup)
        try {
            BackupManager.decodeBackupJson(encoded)
            fail("Expected decodeBackupJson to reject invalid backup: $label")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun bug7a_decodeBackupJson_rejectsUnsupportedSchemaVersions() {
        assertInvalidBackupRejected("schemaVersion=0", validBackup().copy(schemaVersion = 0))
        assertInvalidBackupRejected("future schemaVersion=99", validBackup().copy(schemaVersion = 99))
    }

    @Test
    fun bug7a_decodeBackupJson_rejectsInvalidRecordsPresetsTargetsAndPreferences() {
        val base = validBackup()
        assertInvalidBackupRejected(
            "negative record volume",
            base.copy(records = listOf(base.records.first().copy(volumeMl = -50.0)))
        )
        assertInvalidBackupRejected(
            "blank record id",
            base.copy(records = listOf(base.records.first().copy(id = "   ")))
        )
        assertInvalidBackupRejected(
            "invalid hydrationFactor",
            base.copy(records = listOf(base.records.first().copy(hydrationFactor = 0.0)))
        )
        assertInvalidBackupRejected(
            "invalid target date",
            base.copy(dailyTargets = listOf(BackupTarget(dateString = "2026-02-30", targetMl = 2000.0)))
        )
        assertInvalidBackupRejected(
            "malformed wakingHoursStart",
            base.copy(preferences = base.preferences.copy(wakingHoursStart = "25:99"))
        )
        assertInvalidBackupRejected(
            "excessive record volume",
            base.copy(records = listOf(base.records.first().copy(volumeMl = 100_000.0)))
        )
        assertInvalidBackupRejected(
            "zero record volume",
            base.copy(records = listOf(base.records.first().copy(volumeMl = 0.0)))
        )
    }

    @Test
    fun bug7b_applyBackupRestore_cancelsSyncWork_andResetsHealthConnectState() = runTest {
        var syncWorkCancelled = false
        val prefs = mutablePreferencesOf()
        UserPreferencesRepository.seedHealthConnectStateForTest(
            preferences = prefs,
            syncEnabled = true,
            bidirectional = true,
            changesToken = "stale-token",
            lastSyncMillis = 123456L
        )

        BackupManager.applyBackupRestore(
            backup = validBackup(),
            cancelSyncWork = { syncWorkCancelled = true },
            writeDatabase = { _, _, _ -> },
            applyPreferences = { unit, targetMl, wakeStart, wakeEnd, pacingEnabled, themeMode, dynamicColor, factorsEnabled ->
                UserPreferencesRepository.applyRestoredPreferencesTo(
                    preferences = prefs,
                    displayUnit = unit,
                    defaultDailyTargetMl = targetMl,
                    wakingHoursStart = wakeStart,
                    wakingHoursEnd = wakeEnd,
                    wakingPacingEnabled = pacingEnabled,
                    themeMode = themeMode,
                    dynamicColorEnabled = dynamicColor,
                    hydrationFactorsEnabled = factorsEnabled
                )
            },
            refreshUi = {}
        )

        assertTrue("Expected restore to cancel active/periodic Health Connect sync work", syncWorkCancelled)
        assertFalse(
            "Expected restore to pause Health Connect sync to avoid duplicating records",
            UserPreferencesRepository.isHealthConnectSyncEnabled(prefs)
        )
        assertFalse(
            "Expected restore to disable bidirectional Health Connect sync",
            UserPreferencesRepository.isHealthConnectBidirectional(prefs)
        )
        assertFalse(
            "Expected restore to clear Health Connect changes token",
            UserPreferencesRepository.hasHealthConnectChangesToken(prefs)
        )
        assertEquals(0L, UserPreferencesRepository.lastHealthConnectSyncMillis(prefs))
    }

    @Test
    fun decodeBackupJson_handlesLegacyBackupWithoutRecordLabel() {
        val legacyJson = """
            {
              "schemaVersion": 1,
              "exportedAtMillis": 1700000000000,
              "records": [
                {
                  "id": "r1",
                  "timestamp": 1700000001000,
                  "volumeMl": 250.0,
                  "hydrationFactor": 1.0,
                  "drinkType": "WATER"
                }
              ],
              "presets": [],
              "dailyTargets": [],
              "preferences": {}
            }
        """.trimIndent()
        val decoded = BackupManager.decodeBackupJson(legacyJson)
        assertEquals(null, decoded.records.first().label)
    }
}
