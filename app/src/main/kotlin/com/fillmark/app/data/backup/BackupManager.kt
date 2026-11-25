package com.fillmark.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.ShortcutHelper
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.DailyTargetEntity
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.widget.WidgetRefreshScheduler
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
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
    val lastModified: Long = 0L,
    val label: String? = null
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
    const val CURRENT_SCHEMA_VERSION = 1
    private val TIME_HH_MM_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
    private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val STRICT_ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.US)
        .withResolverStyle(ResolverStyle.STRICT)

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
        validateBackup(parsed)
        return parsed
    }

    fun validateBackup(backup: FillmarkBackup) {
        require(backup.schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
            "Unsupported backup schema version: ${backup.schemaVersion}"
        }
        backup.records.forEachIndexed { index, record ->
            require(record.id.isNotBlank()) { "Record[$index] has blank id" }
            require(record.timestamp > 0L) { "Record[$index] has invalid timestamp" }
            require(
                record.volumeMl.isFinite() &&
                    record.volumeMl > 0.0 &&
                    record.volumeMl <= VolumeConverter.MAX_SINGLE_DRINK_ML
            ) { "Record[$index] has invalid volumeMl: ${record.volumeMl}" }
            require(
                record.hydrationFactor.isFinite() &&
                    record.hydrationFactor > 0.0 &&
                    record.hydrationFactor <= 2.0
            ) { "Record[$index] has invalid hydrationFactor: ${record.hydrationFactor}" }
            require(record.drinkType.isNotBlank()) { "Record[$index] has blank drinkType" }
        }
        backup.presets.forEachIndexed { index, preset ->
            require(preset.id.isNotBlank()) { "Preset[$index] has blank id" }
            require(preset.label.isNotBlank()) { "Preset[$index] has blank label" }
            require(
                preset.volumeMl.isFinite() &&
                    preset.volumeMl > 0.0 &&
                    preset.volumeMl <= VolumeConverter.MAX_SINGLE_DRINK_ML
            ) { "Preset[$index] has invalid volumeMl: ${preset.volumeMl}" }
            require(
                preset.hydrationFactor.isFinite() &&
                    preset.hydrationFactor > 0.0 &&
                    preset.hydrationFactor <= 2.0
            ) { "Preset[$index] has invalid hydrationFactor: ${preset.hydrationFactor}" }
            require(preset.iconKey.isNotBlank()) { "Preset[$index] has blank iconKey" }
            require(preset.drinkType.isNotBlank()) { "Preset[$index] has blank drinkType" }
        }
        backup.dailyTargets.forEachIndexed { index, target ->
            require(ISO_DATE_REGEX.matches(target.dateString)) {
                "DailyTarget[$index] has invalid date format: ${target.dateString}"
            }
            require(
                runCatching { LocalDate.parse(target.dateString, STRICT_ISO_DATE_FORMATTER) }.isSuccess
            ) { "DailyTarget[$index] is not a valid calendar date: ${target.dateString}" }
            require(
                target.targetMl.isFinite() &&
                    target.targetMl >= 0.0 &&
                    target.targetMl <= VolumeConverter.MAX_DAILY_TARGET_ML
            ) { "DailyTarget[$index] has invalid targetMl: ${target.targetMl}" }
        }
        val prefs = backup.preferences
        require(
            prefs.defaultDailyTargetMl.isFinite() &&
                prefs.defaultDailyTargetMl >= 0.0 &&
                prefs.defaultDailyTargetMl <= VolumeConverter.MAX_DAILY_TARGET_ML
        ) { "Preferences defaultDailyTargetMl out of range: ${prefs.defaultDailyTargetMl}" }
        require(TIME_HH_MM_REGEX.matches(prefs.wakingHoursStart)) {
            "Preferences wakingHoursStart invalid: ${prefs.wakingHoursStart}"
        }
        require(TIME_HH_MM_REGEX.matches(prefs.wakingHoursEnd)) {
            "Preferences wakingHoursEnd invalid: ${prefs.wakingHoursEnd}"
        }
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
                    lastModified = it.lastModified,
                    label = it.label
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
        applyBackupRestore(
            backup = backup,
            cancelSyncWork = { HealthConnectManager.cancelAllSyncWork(context) },
            writeDatabase = { records, targets, presets ->
                db.withTransaction {
                    db.hydrationDao().deleteAllRecords()
                    db.dailyTargetDao().deleteAllTargets()
                    db.presetDao().deleteAllPresets()
                    db.pendingHealthConnectDeletionDao().deleteAll()
                    if (records.isNotEmpty()) db.hydrationDao().insertRecords(records)
                    if (targets.isNotEmpty()) db.dailyTargetDao().insertTargets(targets)
                    db.presetDao().insertPresets(presets)
                }
            },
            applyPreferences = { unit, targetMl, wakeStart, wakeEnd, pacingEnabled, themeMode, dynamicColor, factorsEnabled ->
                prefsRepo.applyRestoredPreferences(
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
            refreshUi = {
                WidgetRefreshScheduler.syncSchedule(context)
                WidgetUpdateHelper.updateAllWidgets(context)
                ShortcutHelper.updateShortcuts(context)
            }
        )
    }

    internal suspend fun applyBackupRestore(
        backup: FillmarkBackup,
        nowMillis: Long = System.currentTimeMillis(),
        cancelSyncWork: () -> Unit,
        writeDatabase: suspend (
            records: List<HydrationRecordEntity>,
            targets: List<DailyTargetEntity>,
            presets: List<PresetEntity>
        ) -> Unit,
        applyPreferences: suspend (
            displayUnit: DisplayUnit,
            defaultDailyTargetMl: Double,
            wakingHoursStart: String,
            wakingHoursEnd: String,
            wakingPacingEnabled: Boolean,
            themeMode: ThemeMode,
            dynamicColorEnabled: Boolean,
            hydrationFactorsEnabled: Boolean
        ) -> Unit,
        refreshUi: suspend () -> Unit
    ): RestoreResult {
        cancelSyncWork()

        val recordEntities = backup.records.map {
            HydrationRecordEntity(
                id = it.id,
                timestamp = it.timestamp,
                volumeMl = it.volumeMl,
                hydrationFactor = it.hydrationFactor,
                drinkType = it.drinkType,
                iconKey = it.iconKey,
                healthConnectId = null,
                lastModified = if (it.lastModified > 0L) it.lastModified else nowMillis,
                lastSynced = 0L,
                label = it.label?.trim()?.takeIf { lbl -> lbl.isNotBlank() }
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

        writeDatabase(recordEntities, targetEntities, presetEntities)

        val unit = runCatching { DisplayUnit.valueOf(backup.preferences.displayUnit) }
            .getOrDefault(DisplayUnit.METRIC_ML)
        applyPreferences(
            unit,
            backup.preferences.defaultDailyTargetMl,
            backup.preferences.wakingHoursStart,
            backup.preferences.wakingHoursEnd,
            backup.preferences.wakingPacingEnabled,
            ThemeMode.fromName(backup.preferences.themeMode),
            backup.preferences.dynamicColorEnabled,
            backup.preferences.hydrationFactorsEnabled
        )

        refreshUi()
        return RestoreResult(recordEntities.size, presetEntities.size, targetEntities.size)
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
