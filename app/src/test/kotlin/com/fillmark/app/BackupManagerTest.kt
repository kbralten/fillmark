package com.fillmark.app

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.backup.BackupManager
import com.fillmark.app.data.backup.BackupPreferences
import com.fillmark.app.data.backup.BackupPreset
import com.fillmark.app.data.backup.BackupRecord
import com.fillmark.app.data.backup.BackupTarget
import com.fillmark.app.data.backup.FillmarkBackup
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    lastModified = 1700000002000L
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
}
