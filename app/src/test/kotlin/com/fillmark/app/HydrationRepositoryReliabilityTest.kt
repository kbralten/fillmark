package com.fillmark.app

import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.dao.DailyTargetDao
import com.fillmark.app.data.local.dao.HydrationDao
import com.fillmark.app.data.local.dao.PendingHealthConnectDeletionDao
import com.fillmark.app.data.local.dao.PresetDao
import com.fillmark.app.data.local.entity.DailyTargetEntity
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PendingHealthConnectDeletionEntity
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.data.repository.HydrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationRepositoryReliabilityTest {

    private class FakeHydrationDao : HydrationDao {
        val items = mutableListOf<HydrationRecordEntity>()
        var deleteAllCalled = false

        override suspend fun insertRecord(record: HydrationRecordEntity) {
            items.removeAll { it.id == record.id }
            items.add(record)
        }

        override suspend fun insertRecords(records: List<HydrationRecordEntity>) {
            records.forEach { insertRecord(it) }
        }

        override suspend fun updateRecord(record: HydrationRecordEntity) {
            insertRecord(record)
        }

        override suspend fun deleteRecord(record: HydrationRecordEntity) {
            items.removeAll { it.id == record.id }
        }

        override suspend fun deleteRecordById(id: String) {
            items.removeAll { it.id == id }
        }

        override suspend fun getRecordById(id: String): HydrationRecordEntity? =
            items.firstOrNull { it.id == id }

        override suspend fun getRecordByHealthConnectId(hcId: String): HydrationRecordEntity? =
            items.firstOrNull { it.healthConnectId == hcId }

        override fun observeRecordsBetween(startTime: Long, endTime: Long): Flow<List<HydrationRecordEntity>> =
            MutableStateFlow(emptyList())

        override suspend fun getRecordsBetween(startTime: Long, endTime: Long): List<HydrationRecordEntity> =
            items.filter { it.timestamp in startTime..endTime }

        override fun observeAllRecords(): Flow<List<HydrationRecordEntity>> =
            MutableStateFlow(items.toList())

        override suspend fun getAllRecords(): List<HydrationRecordEntity> = items.toList()
        override suspend fun getUnsyncedRecords(): List<HydrationRecordEntity> = emptyList()
        override suspend fun getModifiedRecords(): List<HydrationRecordEntity> = emptyList()
        override suspend fun updateHealthConnectSyncInfo(id: String, hcId: String, lastSynced: Long, lastModified: Long) {}
        override suspend fun updateLastSynced(id: String, lastSynced: Long) {}
        override suspend fun updateHealthConnectId(id: String, hcId: String, lastModified: Long) {}
        override fun observeTotalEffectiveMlBetween(startTime: Long, endTime: Long): Flow<Double?> =
            MutableStateFlow(null)

        override suspend fun getEarliestRecordTimestamp(): Long? = items.minOfOrNull { it.timestamp }
        override suspend fun deleteAllRecords() {
            deleteAllCalled = true
            items.clear()
        }
    }

    private class FakePresetDao : PresetDao {
        val items = mutableListOf<PresetEntity>()
        override fun observeAllPresets(): Flow<List<PresetEntity>> = MutableStateFlow(items.toList())
        override suspend fun getAllPresets(): List<PresetEntity> = items.toList()
        override suspend fun getPresetById(id: String): PresetEntity? = items.firstOrNull { it.id == id }
        override suspend fun insertPreset(preset: PresetEntity) {
            items.add(preset)
        }

        override suspend fun insertPresets(presets: List<PresetEntity>) {
            items.addAll(presets)
        }

        override suspend fun updatePreset(preset: PresetEntity) {}
        override suspend fun updatePresets(presets: List<PresetEntity>) {}
        override suspend fun deletePreset(preset: PresetEntity) {}
        override suspend fun deletePresetById(id: String) {}
        override suspend fun countPresets(): Int = items.size
        override suspend fun deleteAllPresets() {
            items.clear()
        }
    }

    private class FakeTargetDao : DailyTargetDao {
        val items = mutableListOf<DailyTargetEntity>()
        override fun observeTargetForDate(dateString: String): Flow<DailyTargetEntity?> = MutableStateFlow(null)
        override suspend fun getTargetForDate(dateString: String): DailyTargetEntity? = null
        override fun observeAllTargets(): Flow<List<DailyTargetEntity>> = MutableStateFlow(items.toList())
        override suspend fun getAllTargets(): List<DailyTargetEntity> = items.toList()
        override suspend fun upsertTarget(target: DailyTargetEntity) {}
        override suspend fun insertTargets(targets: List<DailyTargetEntity>) {
            items.addAll(targets)
        }

        override suspend fun insertTargetsIgnore(targets: List<DailyTargetEntity>) {}
        override suspend fun deleteTargetForDate(dateString: String) {}
        override suspend fun deleteAllTargets() {
            items.clear()
        }
    }

    private class FakePendingDeletionDao : PendingHealthConnectDeletionDao {
        val items = mutableListOf<PendingHealthConnectDeletionEntity>()
        override suspend fun insert(entity: PendingHealthConnectDeletionEntity) {
            items.removeAll { it.healthConnectId == entity.healthConnectId }
            items.add(entity)
        }

        override suspend fun getAll(): List<PendingHealthConnectDeletionEntity> = items.toList()
        override suspend fun deleteByHcId(hcId: String) {
            items.removeAll { it.healthConnectId == hcId }
        }

        override suspend fun deleteAll() {
            items.clear()
        }
    }

    @Test
    fun bug3_deleteRecordById_persistsTombstoneWhenInlineDeleteFails_andRemovesWhenSucceeded() = runTest {
        val hydrationDao = FakeHydrationDao().apply {
            items.add(
                HydrationRecordEntity(
                    id = "r1",
                    timestamp = 1700000000000L,
                    volumeMl = 500.0,
                    healthConnectId = "hc-1"
                )
            )
            items.add(
                HydrationRecordEntity(
                    id = "r2",
                    timestamp = 1700000001000L,
                    volumeMl = 250.0,
                    healthConnectId = "hc-2"
                )
            )
        }
        val pendingDao = FakePendingDeletionDao()
        var inlineDeleteOutcome = false
        var syncEnqueuedCount = 0

        val repo = HydrationRepository(
            hydrationDao = hydrationDao,
            presetDao = FakePresetDao(),
            targetDao = FakeTargetDao(),
            pendingDeletionDao = pendingDao,
            preferencesFlow = MutableStateFlow(UserPreferences()),
            updateDefaultDailyTargetMlFn = {},
            resetPreferencesKeepOnboardingFn = {},
            runInTransaction = { block -> block() },
            deleteFromHealthConnect = {
                if (!inlineDeleteOutcome) throw RuntimeException("Transient failure")
                true
            },
            enqueueHealthConnectSync = { syncEnqueuedCount++ },
            cancelHealthConnectSync = {}
        )

        repo.deleteRecordById("r1")
        assertEquals(listOf("hc-1"), pendingDao.getAll().map { it.healthConnectId })
        assertEquals(1, syncEnqueuedCount)

        inlineDeleteOutcome = true
        repo.deleteRecordById("r2")
        assertEquals(listOf("hc-1"), pendingDao.getAll().map { it.healthConnectId })
        assertEquals(2, syncEnqueuedCount)
    }

    @Test
    fun bug8_clearAllData_runsInsideTransaction_andCancelsHealthConnectSyncWork() = runTest {
        val hydrationDao = FakeHydrationDao().apply {
            items.add(HydrationRecordEntity(id = "r1", timestamp = 1700000000000L, volumeMl = 250.0))
        }
        val presetDao = FakePresetDao()
        val targetDao = FakeTargetDao().apply {
            items.add(DailyTargetEntity("2026-11-23", 2500.0))
        }
        val pendingDao = FakePendingDeletionDao().apply {
            items.add(PendingHealthConnectDeletionEntity("hc-1", 1000L))
        }

        var transactionCount = 0
        var insideTransaction = false
        var daoClearsOutsideTransaction = false
        var cancelSyncCalled = false
        var prefsResetCalled = false

        val repo = HydrationRepository(
            hydrationDao = hydrationDao,
            presetDao = presetDao,
            targetDao = targetDao,
            pendingDeletionDao = pendingDao,
            preferencesFlow = MutableStateFlow(UserPreferences()),
            updateDefaultDailyTargetMlFn = {},
            resetPreferencesKeepOnboardingFn = { prefsResetCalled = true },
            runInTransaction = { block ->
                transactionCount++
                insideTransaction = true
                try {
                    block()
                } finally {
                    insideTransaction = false
                }
            },
            deleteFromHealthConnect = { true },
            enqueueHealthConnectSync = {},
            cancelHealthConnectSync = { cancelSyncCalled = true }
        )

        val wrappedHydrationDao = object : HydrationDao by hydrationDao {
            override suspend fun deleteAllRecords() {
                if (!insideTransaction) daoClearsOutsideTransaction = true
                hydrationDao.deleteAllRecords()
            }
        }
        val wrappedRepo = HydrationRepository(
            hydrationDao = wrappedHydrationDao,
            presetDao = presetDao,
            targetDao = targetDao,
            pendingDeletionDao = pendingDao,
            preferencesFlow = MutableStateFlow(UserPreferences()),
            updateDefaultDailyTargetMlFn = {},
            resetPreferencesKeepOnboardingFn = { prefsResetCalled = true },
            runInTransaction = { block ->
                transactionCount++
                insideTransaction = true
                try {
                    block()
                } finally {
                    insideTransaction = false
                }
            },
            deleteFromHealthConnect = { true },
            enqueueHealthConnectSync = {},
            cancelHealthConnectSync = { cancelSyncCalled = true }
        )

        wrappedRepo.clearAllData()

        assertEquals(1, transactionCount)
        assertTrue("Expected DAO deletes/inserts inside database transaction", !daoClearsOutsideTransaction)
        assertTrue("Expected Health Connect periodic/immediate sync to be cancelled", cancelSyncCalled)
        assertTrue(prefsResetCalled)
        assertTrue(hydrationDao.items.isEmpty())
        assertTrue(targetDao.items.isEmpty())
        assertTrue(pendingDao.items.isEmpty())
        assertEquals(FillmarkDatabase.DEFAULT_PRESETS.size, presetDao.items.size)
    }
}
