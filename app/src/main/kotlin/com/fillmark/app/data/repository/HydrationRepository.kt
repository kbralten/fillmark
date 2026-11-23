package com.fillmark.app.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.DailyTargetEntity
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.sync.HealthConnectSyncWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class DayHydrationSummary(
    val dateIso: String,
    val effectiveVolumeMl: Double,
    val targetMl: Double,
    val records: List<HydrationRecordEntity>,
    val isTargetOverridden: Boolean = false,
    val defaultTargetMl: Double = 2500.0,
    val hydrationFactorsEnabled: Boolean = true
)

class HydrationRepository(
    private val context: Context,
    private val database: FillmarkDatabase = FillmarkDatabase.getInstance(context),
    private val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(context)
) {
    private val hydrationDao = database.hydrationDao()
    private val presetDao = database.presetDao()
    private val targetDao = database.dailyTargetDao()

    val preferencesFlow = preferencesRepository.preferencesFlow

    fun observePresets(): Flow<List<PresetEntity>> = presetDao.observeAllPresets()

    suspend fun getPresets(): List<PresetEntity> = presetDao.getAllPresets()

    suspend fun addPreset(preset: PresetEntity) = presetDao.insertPreset(preset)

    suspend fun updatePreset(preset: PresetEntity) = presetDao.updatePreset(preset)

    suspend fun reorderPresets(orderedPresets: List<PresetEntity>) {
        val updated = orderedPresets.mapIndexed { index, preset ->
            preset.copy(displayOrder = index)
        }
        presetDao.updatePresets(updated)
    }

    suspend fun deletePreset(id: String) = presetDao.deletePresetById(id)

    /**
     * Observes today's hydration records and total effective volume.
     */
    fun observeTodaySummary(zoneId: ZoneId = ZoneId.systemDefault()): Flow<DayHydrationSummary> {
        return observeSummaryForDate(LocalDate.now(zoneId), zoneId)
    }

    /**
     * Observes a specific date's hydration records, target, and total effective volume.
     */
    fun observeSummaryForDate(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<DayHydrationSummary> {
        val startMillis = TimeUtils.startOfDayMillis(date, zoneId)
        val endMillis = TimeUtils.endOfDayMillis(date, zoneId)
        val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val recordsFlow = hydrationDao.observeRecordsBetween(startMillis, endMillis)
        val targetFlow = targetDao.observeTargetForDate(dateIso)
        val prefsFlow = preferencesRepository.preferencesFlow

        return combine(recordsFlow, targetFlow, prefsFlow) { records, targetEntity, prefs ->
            val effectiveTotal = records.sumOf { it.countedVolumeMl(prefs.hydrationFactorsEnabled) }
            val activeTarget = targetEntity?.targetMl ?: prefs.defaultDailyTargetMl
            val isOverridden = targetEntity != null && targetEntity.targetMl != prefs.defaultDailyTargetMl
            DayHydrationSummary(
                dateIso = dateIso,
                effectiveVolumeMl = effectiveTotal,
                targetMl = activeTarget,
                records = records,
                isTargetOverridden = isOverridden,
                defaultTargetMl = prefs.defaultDailyTargetMl,
                hydrationFactorsEnabled = prefs.hydrationFactorsEnabled
            )
        }
    }

    /**
     * Observes all hydration records, ordered descending by timestamp.
     */
    fun observeAllRecords(): Flow<List<HydrationRecordEntity>> = hydrationDao.observeAllRecords()

    suspend fun getRecordById(id: String): HydrationRecordEntity? = hydrationDao.getRecordById(id)

    /**
     * Inserts a record and triggers Health Connect outbound sync worker.
     */
    suspend fun insertRecord(record: HydrationRecordEntity) {
        hydrationDao.insertRecord(record)
        triggerHealthConnectSync()
    }

    /**
     * Quick-logs a preset by ID, creating a new record at the current timestamp.
     * Returns the created record entity (for undo capability).
     */
    suspend fun logPreset(preset: PresetEntity): HydrationRecordEntity {
        val record = HydrationRecordEntity(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            volumeMl = preset.volumeMl,
            hydrationFactor = preset.hydrationFactor,
            drinkType = preset.drinkType,
            iconKey = preset.iconKey,
            lastModified = System.currentTimeMillis()
        )
        insertRecord(record)
        return record
    }

    suspend fun updateRecord(record: HydrationRecordEntity) {
        val updated = record.copy(lastModified = System.currentTimeMillis())
        hydrationDao.updateRecord(updated)
        triggerHealthConnectSync()
    }

    suspend fun deleteRecordById(id: String) {
        val record = hydrationDao.getRecordById(id)
        hydrationDao.deleteRecordById(id)
        if (record?.healthConnectId != null) {
            try {
                val hcManager = HealthConnectManager(context)
                if (hcManager.isAvailable() && hcManager.hasAllPermissions()) {
                    hcManager.deleteRecord(record.healthConnectId)
                }
            } catch (e: Exception) {
                // Ignore failure on HC delete
            }
        }
        triggerHealthConnectSync()
    }

    /**
     * Observes the target for a specific date, falling back to default preference.
     */
    fun observeTargetForDate(dateIso: String): Flow<Double> {
        val targetFlow = targetDao.observeTargetForDate(dateIso)
        val prefsFlow = preferencesRepository.preferencesFlow
        return combine(targetFlow, prefsFlow) { entity, prefs ->
            entity?.targetMl ?: prefs.defaultDailyTargetMl
        }
    }

    fun observeAllTargets(): Flow<List<DailyTargetEntity>> = targetDao.observeAllTargets()

    suspend fun setTargetForDate(dateIso: String, targetMl: Double) {
        targetDao.upsertTarget(DailyTargetEntity(dateString = dateIso, targetMl = targetMl))
    }

    suspend fun clearTargetOverrideForDate(dateIso: String) {
        targetDao.deleteTargetForDate(dateIso)
    }

    suspend fun hasAnyRecords(): Boolean = hydrationDao.getEarliestRecordTimestamp() != null

    suspend fun clearAllData() {
        hydrationDao.deleteAllRecords()
        targetDao.deleteAllTargets()
        presetDao.deleteAllPresets()
        presetDao.insertPresets(FillmarkDatabase.DEFAULT_PRESETS)
        preferencesRepository.resetPreferencesKeepOnboarding()
    }

    /**
     * Updates the daily target for today and future days while freezing unrecorded
     * historical days at the previous target so past compliance is preserved.
     */
    suspend fun updateDailyTargetPreservingHistory(
        newTargetMl: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val oldTargetMl = preferencesRepository.preferencesFlow.first().defaultDailyTargetMl
        val today = LocalDate.now(zoneId)
        val todayIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (oldTargetMl != newTargetMl) {
            val earliestTs = hydrationDao.getEarliestRecordTimestamp()
            val earliestDate = earliestTs?.let { TimeUtils.toLocalDate(it, zoneId) }
            val pastDates = computeHistoricalTargetFreezeDates(earliestDate, today)
            if (pastDates.isNotEmpty()) {
                val pastTargets = pastDates.map { dateIso ->
                    DailyTargetEntity(dateString = dateIso, targetMl = oldTargetMl)
                }
                targetDao.insertTargetsIgnore(pastTargets)
            }
        }

        targetDao.upsertTarget(DailyTargetEntity(dateString = todayIso, targetMl = newTargetMl))
        preferencesRepository.updateDefaultDailyTargetMl(newTargetMl)
    }

    companion object {
        fun computeHistoricalTargetFreezeDates(
            earliestDate: LocalDate?,
            today: LocalDate
        ): List<String> {
            val start = earliestDate ?: return emptyList()
            if (!start.isBefore(today)) return emptyList()
            val dates = mutableListOf<String>()
            var cursor = start
            while (cursor.isBefore(today)) {
                dates.add(cursor.format(DateTimeFormatter.ISO_LOCAL_DATE))
                cursor = cursor.plusDays(1)
            }
            return dates
        }
    }

    suspend fun getRecordsBetween(startMillis: Long, endMillis: Long): List<HydrationRecordEntity> {
        return hydrationDao.getRecordsBetween(startMillis, endMillis)
    }

    private fun triggerHealthConnectSync() {
        try {
            val syncRequest = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "HealthConnectOutboundSync",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        } catch (e: Exception) {
            // Worker scheduling safe fallback
        }
    }
}

