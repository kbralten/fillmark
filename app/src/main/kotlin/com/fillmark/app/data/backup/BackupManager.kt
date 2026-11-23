package com.fillmark.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.ShortcutHelper
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.DailyTargetEntity
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class FillmarkBackup(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long,
    val records: List<BackupRecord> = emptyList(),
    val presets: List<BackupPreset> = emptyList(),
    val dailyTargets: List<BackupTarget> = emptyList(),
    val preferences: BackupPreferences = BackupPreferences()
)

@Serializable
data class BackupRecord(
    val id: String,
    val timestamp: Long,
    val volumeMl: Double,
    val hydrationFactor: Double = 1.0,
    val drinkType: String = "WATER",
    val iconKey: String? = null,
    val lastModified: Long = 0L
)

@Serializable
data class BackupPreset(
    val id: String,
    val label: String,
    val volumeMl: Double,
    val iconKey: String,
    val hydrationFactor: Double = 1.0,
    val displayOrder: Int = 0,
    val drinkType: String = "WATER",
    val colorHex: String? = null
)

@Serializable
data class BackupTarget(
    val dateString: String,
    val targetMl: Double
)

@Serializable
data class BackupPreferences(
    val displayUnit: String = DisplayUnit.METRIC_ML.name,
    val defaultDailyTargetMl: Double = 2500.0,
    val wakingHoursStart: String = "07:00",
    val wakingHoursEnd: String = "23:00",
    val wakingPacingEnabled: Boolean = true,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val dynamicColorEnabled: Boolean = false,
    val hydrationFactorsEnabled: Boolean = true
)

data class RestoreResult(
    val recordsCount: Int,
    val presetsCount: Int,
    val targetsCount: Int
)

object BackupManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeBackupJson(backup: FillmarkBackup): String {
        return json.encodeToString(FillmarkBackup.serializer(), backup)
    }

    fun decodeBackupJson(rawJson: String): FillmarkBackup {
        val parsed = json.decodeFromString(FillmarkBackup.serializer(), rawJson)
        require(parsed.schemaVersion >= 1) { "Unsupported backup schema version" }
        return parsed
    }

    fun encodeRecordsCsv(
        records: List<HydrationRecordEntity>,
        hydrationFactorsEnabled: Boolean = true,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("id,date,time,timestamp_ms,drink_type,volume_ml,hydration_factor,effective_volume_ml")
        val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
        for (record in records.sortedByDescending { it.timestamp }) {
            val zdt = Instant.ofEpochMilli(record.timestamp).atZone(zoneId)
            val dateStr = zdt.toLocalDate().format(dateFmt)
            val timeStr = zdt.toLocalTime().format(timeFmt)
            val counted = record.countedVolumeMl(hydrationFactorsEnabled)
            sb.append(csvEscape(record.id)).append(',')
                .append(csvEscape(dateStr)).append(',')
                .append(csvEscape(timeStr)).append(',')
                .append(record.timestamp).append(',')
                .append(csvEscape(record.drinkType)).append(',')
                .append(String.format(Locale.US, "%.2f", record.volumeMl)).append(',')
                .append(String.format(Locale.US, "%.2f", record.hydrationFactor)).append(',')
                .append(String.format(Locale.US, "%.2f", counted))
                .appendLine()
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    suspend fun exportBackupToUri(context: Context, uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val db = FillmarkDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)
        val prefs = prefsRepo.preferencesFlow.first()
        val records = db.hydrationDao().getAllRecords()
        val presets = db.presetDao().getAllPresets()
        val targets = db.dailyTargetDao().getAllTargets()

        val backup = FillmarkBackup(
            schemaVersion = 1,
            exportedAtMillis = System.currentTimeMillis(),
            records = records.map {
                BackupRecord(
                    id = it.id,
                    timestamp = it.timestamp,
                    volumeMl = it.volumeMl,
                    hydrationFactor = it.hydrationFactor,
                    drinkType = it.drinkType,
                    iconKey = it.iconKey,
                    lastModified = it.lastModified
                )
            },
            presets = presets.map {
                BackupPreset(
                    id = it.id,
                    label = it.label,
                    volumeMl = it.volumeMl,
                    iconKey = it.iconKey,
                    hydrationFactor = it.hydrationFactor,
                    displayOrder = it.displayOrder,
                    drinkType = it.drinkType,
                    colorHex = it.colorHex
                )
            },
            dailyTargets = targets.map {
                BackupTarget(dateString = it.dateString, targetMl = it.targetMl)
            },
            preferences = BackupPreferences(
                displayUnit = prefs.displayUnit.name,
                defaultDailyTargetMl = prefs.defaultDailyTargetMl,
                wakingHoursStart = prefs.wakingHoursStart,
                wakingHoursEnd = prefs.wakingHoursEnd,
                wakingPacingEnabled = prefs.wakingPacingEnabled,
                themeMode = prefs.themeMode.name,
                dynamicColorEnabled = prefs.dynamicColorEnabled,
                hydrationFactorsEnabled = prefs.hydrationFactorsEnabled
            )
        )

        val payload = encodeBackupJson(backup)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload)
            }
        } ?: error("Unable to open destination file")

        RestoreResult(records.size, presets.size, targets.size)
    }

    suspend fun restoreBackupFromUri(context: Context, uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } ?: error("Unable to read backup file")

        val backup = decodeBackupJson(raw)
        val db = FillmarkDatabase.getInstance(context)
        val prefsRepo = UserPreferencesRepository(context)
        val now = System.currentTimeMillis()

        val recordEntities = backup.records.map {
            HydrationRecordEntity(
                id = it.id,
                timestamp = it.timestamp,
                volumeMl = it.volumeMl,
                hydrationFactor = it.hydrationFactor,
                drinkType = it.drinkType,
                iconKey = it.iconKey,
                healthConnectId = null,
                lastModified = if (it.lastModified > 0L) it.lastModified else now,
                lastSynced = 0L
            )
        }
        val presetEntities = if (backup.presets.isNotEmpty()) {
            backup.presets.map {
                PresetEntity(
                    id = it.id,
                    label = it.label,
                    volumeMl = it.volumeMl,
                    iconKey = it.iconKey,
                    hydrationFactor = it.hydrationFactor,
                    displayOrder = it.displayOrder,
                    drinkType = it.drinkType,
                    colorHex = it.colorHex
                )
            }
        } else {
            FillmarkDatabase.DEFAULT_PRESETS
        }
        val targetEntities = backup.dailyTargets.map {
            DailyTargetEntity(dateString = it.dateString, targetMl = it.targetMl)
        }

        db.withTransaction {
            db.hydrationDao().deleteAllRecords()
            db.dailyTargetDao().deleteAllTargets()
            db.presetDao().deleteAllPresets()
            if (recordEntities.isNotEmpty()) db.hydrationDao().insertRecords(recordEntities)
            if (targetEntities.isNotEmpty()) db.dailyTargetDao().insertTargets(targetEntities)
            db.presetDao().insertPresets(presetEntities)
        }

        val unit = runCatching { DisplayUnit.valueOf(backup.preferences.displayUnit) }
            .getOrDefault(DisplayUnit.METRIC_ML)
        prefsRepo.applyRestoredPreferences(
            displayUnit = unit,
            defaultDailyTargetMl = backup.preferences.defaultDailyTargetMl,
            wakingHoursStart = backup.preferences.wakingHoursStart,
            wakingHoursEnd = backup.preferences.wakingHoursEnd,
            wakingPacingEnabled = backup.preferences.wakingPacingEnabled,
            themeMode = ThemeMode.fromName(backup.preferences.themeMode),
            dynamicColorEnabled = backup.preferences.dynamicColorEnabled,
            hydrationFactorsEnabled = backup.preferences.hydrationFactorsEnabled
        )

        WidgetUpdateHelper.updateAllWidgets(context)
        ShortcutHelper.updateShortcuts(context)

        RestoreResult(recordEntities.size, presetEntities.size, targetEntities.size)
    }

    suspend fun exportCsvToUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val db = FillmarkDatabase.getInstance(context)
        val prefs = UserPreferencesRepository(context).preferencesFlow.first()
        val records = db.hydrationDao().getAllRecords()
        val csv = encodeRecordsCsv(records, prefs.hydrationFactorsEnabled)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(csv)
            }
        } ?: error("Unable to open CSV destination")
        records.size
    }
}
