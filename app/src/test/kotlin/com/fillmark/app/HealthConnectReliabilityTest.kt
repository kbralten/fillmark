package com.fillmark.app

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.records.HydrationRecord
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PendingHealthConnectDeletionEntity
import com.fillmark.app.sync.GatewayChangesPage
import com.fillmark.app.sync.GatewayRecordsPage
import com.fillmark.app.sync.HealthConnectAvailabilityUi
import com.fillmark.app.sync.HealthConnectGateway
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.sync.HealthConnectSyncStorage
import com.fillmark.app.sync.HealthConnectSyncWorker
import com.fillmark.app.sync.InboundSyncResult
import com.fillmark.app.sync.OutboundSyncResult
import com.fillmark.app.sync.WorkerSyncOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HealthConnectReliabilityTest {

    private class FakeGateway(
        var available: Boolean = true,
        var granted: Set<String> = setOf(
            HealthConnectManager.readPermission,
            HealthConnectManager.writePermission
        )
    ) : HealthConnectGateway {
        val insertedRecords = mutableListOf<HydrationRecord>()
        val deletedIds = mutableListOf<String>()
        var nextGeneratedId = "hc-generated-1"
        var changesTokenToReturn = "fresh-token"
        var onGetChanges: ((String) -> GatewayChangesPage)? = null
        var onDeleteRecords: ((List<String>) -> Unit)? = null
        var readPages: List<GatewayRecordsPage> = listOf(GatewayRecordsPage(emptyList(), null))

        override fun isAvailable(): Boolean = available
        override suspend fun getGrantedPermissions(): Set<String> = granted

        override suspend fun insertRecord(record: HydrationRecord): String? {
            insertedRecords.add(record)
            return nextGeneratedId
        }

        override suspend fun updateRecord(record: HydrationRecord) {}

        override suspend fun deleteRecords(recordIds: List<String>) {
            onDeleteRecords?.invoke(recordIds)
            deletedIds.addAll(recordIds)
        }

        override suspend fun getChangesToken(): String = changesTokenToReturn

        override suspend fun getChanges(token: String): GatewayChangesPage {
            return onGetChanges?.invoke(token)
                ?: GatewayChangesPage(emptyList(), "next-$token", hasMore = false, changesTokenExpired = false)
        }

        override suspend fun readHydrationRecordsAfter(
            startInstant: Instant,
            pageToken: String?
        ): GatewayRecordsPage {
            return readPages.first()
        }
    }

    private class FakeStorage(
        initialToken: String? = null
    ) : HealthConnectSyncStorage {
        val records = mutableListOf<HydrationRecordEntity>()
        val pendingDeletions = mutableListOf<PendingHealthConnectDeletionEntity>()
        var changesToken: String? = initialToken
        val setTokenCalls = mutableListOf<String?>()

        override suspend fun getUnsyncedRecords(): List<HydrationRecordEntity> =
            records.filter { it.healthConnectId == null }

        override suspend fun getModifiedRecords(): List<HydrationRecordEntity> =
            records.filter { it.healthConnectId != null && it.lastModified > it.lastSynced }

        override suspend fun updateHealthConnectSyncInfo(
            id: String,
            hcId: String,
            lastSynced: Long,
            lastModified: Long
        ) {
            val idx = records.indexOfFirst { it.id == id }
            if (idx >= 0) {
                records[idx] = records[idx].copy(
                    healthConnectId = hcId,
                    lastSynced = lastSynced,
                    lastModified = lastModified
                )
            }
        }

        override suspend fun updateLastSynced(id: String, lastSynced: Long) {
            val idx = records.indexOfFirst { it.id == id }
            if (idx >= 0) {
                records[idx] = records[idx].copy(lastSynced = lastSynced)
            }
        }

        override suspend fun getRecordByHealthConnectId(hcId: String): HydrationRecordEntity? =
            records.firstOrNull { it.healthConnectId == hcId }

        override suspend fun insertRecord(record: HydrationRecordEntity) {
            records.removeAll { it.id == record.id }
            records.add(record)
        }

        override suspend fun updateRecord(record: HydrationRecordEntity) {
            val idx = records.indexOfFirst { it.id == record.id }
            if (idx >= 0) records[idx] = record
        }

        override suspend fun deleteRecordById(id: String) {
            records.removeAll { it.id == id }
        }

        override suspend fun getPendingDeletions(): List<PendingHealthConnectDeletionEntity> =
            pendingDeletions.toList()

        override suspend fun removePendingDeletion(hcId: String) {
            pendingDeletions.removeAll { it.healthConnectId == hcId }
        }

        override suspend fun getChangesToken(): String? = changesToken

        override suspend fun setChangesToken(token: String?) {
            changesToken = token
            setTokenCalls.add(token)
        }
    }

    @Test
    fun bug1_syncInbound_securityException_doesNotWipeChangesToken() = runTest {
        val gateway = FakeGateway().apply {
            onGetChanges = { throw SecurityException("Background read denied") }
        }
        val storage = FakeStorage(initialToken = "existing-token-123")
        val manager = HealthConnectManager(null, "com.fillmark.app", gateway)

        val result = manager.syncInbound(storage)

        assertFalse(result.succeeded)
        assertTrue(result.securityError)
        assertEquals("existing-token-123", storage.changesToken)
        assertFalse(storage.setTokenCalls.contains(null))
    }

    @Test
    fun bug1_syncInbound_transientError_preservesChangesToken_andDeletesAppliedOnRetry() = runTest {
        var shouldFail = true
        val gateway = FakeGateway().apply {
            onGetChanges = { token ->
                if (shouldFail) throw IllegalStateException("Transient IPC error")
                GatewayChangesPage(
                    changes = listOf(DeletionChange("hc-to-delete")),
                    nextChangesToken = "token-after-delete",
                    hasMore = false,
                    changesTokenExpired = false
                )
            }
        }
        val storage = FakeStorage(initialToken = "token-1").apply {
            records.add(
                HydrationRecordEntity(
                    id = "local-1",
                    timestamp = 1700000000000L,
                    volumeMl = 500.0,
                    healthConnectId = "hc-to-delete"
                )
            )
        }
        val manager = HealthConnectManager(null, "com.fillmark.app", gateway)

        val failedResult = manager.syncInbound(storage)
        assertFalse(failedResult.succeeded)
        assertEquals("token-1", storage.changesToken)

        shouldFail = false
        val retryResult = manager.syncInbound(storage)
        assertTrue(retryResult.succeeded)
        assertEquals(1, retryResult.importedCount)
        assertTrue(storage.records.isEmpty())
        assertEquals("token-after-delete", storage.changesToken)
    }

    @Test
    fun bug1_backgroundWorker_neverRunsInboundSync_andDoesNotAdvanceLastSyncOnFailure() = runTest {
        var inboundCalled = false
        var lastSyncUpdatedTo: Long? = null

        val outcome = HealthConnectSyncWorker.executeBackgroundSync(
            prefs = UserPreferences(
                healthConnectSyncEnabled = true,
                healthConnectBidirectional = true
            ),
            isAvailable = true,
            hasAllPermissions = true,
            hasWritePermission = true,
            runAttemptCount = 0,
            nowMillis = 99_999L,
            syncOutbound = { OutboundSyncResult(syncedCount = 0, failedCount = 1) },
            syncInbound = {
                inboundCalled = true
                InboundSyncResult(importedCount = 1, succeeded = true)
            },
            setLastSyncMillis = { lastSyncUpdatedTo = it },
            updateWidgets = {}
        )

        assertFalse("Background worker must NOT invoke syncInbound()", inboundCalled)
        assertEquals(null, lastSyncUpdatedTo)
        assertEquals(WorkerSyncOutcome.RETRY, outcome)
    }

    @Test
    fun bug1_backgroundWorker_advancesLastSyncOnlyOnCleanOutboundSuccess() = runTest {
        var inboundCalled = false
        var lastSyncUpdatedTo: Long? = null

        val outcome = HealthConnectSyncWorker.executeBackgroundSync(
            prefs = UserPreferences(
                healthConnectSyncEnabled = true,
                healthConnectBidirectional = true
            ),
            isAvailable = true,
            hasAllPermissions = false,
            hasWritePermission = true,
            runAttemptCount = 0,
            nowMillis = 55_555L,
            syncOutbound = { OutboundSyncResult(syncedCount = 2, failedCount = 0) },
            syncInbound = {
                inboundCalled = true
                InboundSyncResult(importedCount = 0, succeeded = true)
            },
            setLastSyncMillis = { lastSyncUpdatedTo = it },
            updateWidgets = {}
        )

        assertFalse(inboundCalled)
        assertEquals(55_555L, lastSyncUpdatedTo)
        assertEquals(WorkerSyncOutcome.SUCCESS, outcome)
    }

    @Test
    fun bug2_writeOnlyPermission_allowsOutboundSyncAndDeletes_whileSkippingInbound() = runTest {
        val gateway = FakeGateway(
            granted = setOf(HealthConnectManager.writePermission)
        )
        val storage = FakeStorage().apply {
            records.add(
                HydrationRecordEntity(
                    id = "rec-write-only",
                    timestamp = 1700000000000L,
                    volumeMl = 300.0,
                    healthConnectId = null
                )
            )
        }
        val manager = HealthConnectManager(null, "com.fillmark.app", gateway)

        assertTrue(manager.hasWritePermission())
        assertFalse(manager.hasReadPermission())
        assertFalse(manager.hasAllPermissions())

        val outbound = manager.syncOutbound(storage)
        assertFalse(outbound.skipped)
        assertEquals(1, outbound.syncedCount)
        assertEquals("hc-generated-1", storage.records.first().healthConnectId)

        assertTrue(manager.deleteRecord("hc-generated-1"))
        assertEquals(listOf("hc-generated-1"), gateway.deletedIds)

        val inbound = manager.syncInbound(storage)
        assertTrue(inbound.skipped)
        assertFalse(inbound.succeeded)
    }

    @Test
    fun bug3_syncOutbound_drainsPendingDeletionTombstones_andRetriesOnFailure() = runTest {
        var failDelete = true
        val gateway = FakeGateway().apply {
            onDeleteRecords = {
                if (failDelete) throw RuntimeException("HC service unavailable")
            }
        }
        val storage = FakeStorage().apply {
            pendingDeletions.add(PendingHealthConnectDeletionEntity("hc-orphan-1", 1000L))
        }
        val manager = HealthConnectManager(null, "com.fillmark.app", gateway)

        val firstAttempt = manager.syncOutbound(storage)
        assertEquals(1, firstAttempt.failedCount)
        assertFalse(firstAttempt.succeeded)
        assertEquals(1, storage.pendingDeletions.size)

        failDelete = false
        val secondAttempt = manager.syncOutbound(storage)
        assertEquals(0, secondAttempt.failedCount)
        assertEquals(1, secondAttempt.deletedCount)
        assertTrue(secondAttempt.succeeded)
        assertTrue(storage.pendingDeletions.isEmpty())
        assertEquals(listOf("hc-orphan-1"), gateway.deletedIds)
    }

    @Test
    fun bug12_resolveAvailabilityUi_surfacesInstallPromptWhenUpdateRequired() {
        assertEquals(
            HealthConnectAvailabilityUi.AVAILABLE,
            HealthConnectManager.resolveAvailabilityUi(HealthConnectClient.SDK_AVAILABLE)
        )
        assertEquals(
            HealthConnectAvailabilityUi.INSTALL_OR_UPDATE_REQUIRED,
            HealthConnectManager.resolveAvailabilityUi(
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
            )
        )
        assertEquals(
            HealthConnectAvailabilityUi.UNAVAILABLE,
            HealthConnectManager.resolveAvailabilityUi(HealthConnectClient.SDK_UNAVAILABLE)
        )
    }
}
