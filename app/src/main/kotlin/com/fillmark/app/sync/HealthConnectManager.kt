package com.fillmark.app.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

class HealthConnectManager(private val context: Context) {
    companion object {
        private const val TAG = "HealthConnectManager"
        private const val UNIQUE_PERIODIC_SYNC_NAME = "HealthConnectPeriodicSync"
        private const val UNIQUE_IMMEDIATE_SYNC_NAME = "HealthConnectOutboundSync"

        fun schedulePeriodicSync(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(
                    15, TimeUnit.MINUTES
                ).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_SYNC_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure {
                Log.w(TAG, "Failed to schedule periodic Health Connect sync", it)
            }
        }

        fun cancelPeriodicSync(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_SYNC_NAME)
            }.onFailure {
                Log.w(TAG, "Failed to cancel periodic Health Connect sync", it)
            }
        }

        fun enqueueImmediateSync(context: Context, replace: Boolean = false) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
                val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_IMMEDIATE_SYNC_NAME,
                    policy,
                    request
                )
            }.onFailure {
                Log.w(TAG, "Failed to enqueue Health Connect sync", it)
            }
        }

        /**
         * Clears Health Connect sync metadata when restoring a locally deleted record via Undo
         * so it will be re-uploaded to Health Connect cleanly.
         */
        fun prepareRestoredEntityForResync(
            record: HydrationRecordEntity,
            nowMillis: Long = System.currentTimeMillis()
        ): HydrationRecordEntity {
            return record.copy(
                healthConnectId = null,
                lastSynced = 0L,
                lastModified = nowMillis
            )
        }
    }

    val healthConnectClient: HealthConnectClient? by lazy {
        if (isAvailable()) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class)
    )

    fun getSdkStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    fun isAvailable(): Boolean {
        return getSdkStatus() == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * Performs an outbound sync of all local records that have not yet been synced or were modified locally.
     */
    suspend fun syncOutbound(database: FillmarkDatabase): Int = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext 0
        if (!hasAllPermissions()) return@withContext 0

        var syncedCount = 0

        // 1. Newly created unsynced records
        val unsynced = database.hydrationDao().getUnsyncedRecords()
        for (record in unsynced) {
            try {
                if (record.volumeMl <= 0.0) continue
                val endTime = Instant.ofEpochMilli(record.timestamp)
                val startTime = endTime.minusSeconds(1)
                val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)
                val hcRecord = HydrationRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    volume = Volume.milliliters(record.volumeMl),
                    metadata = Metadata.manualEntry(
                        clientRecordId = record.id,
                        clientRecordVersion = record.lastModified
                    )
                )

                val response = client.insertRecords(listOf(hcRecord))
                val generatedId = response.recordIdsList.firstOrNull()
                if (generatedId != null) {
                    val now = System.currentTimeMillis()
                    database.hydrationDao().updateHealthConnectSyncInfo(
                        id = record.id,
                        hcId = generatedId,
                        lastSynced = now,
                        lastModified = now
                    )
                    syncedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing record ${record.id} to Health Connect", e)
            }
        }

        // 2. Previously synced records that have been modified locally
        val modified = database.hydrationDao().getModifiedRecords()
        for (record in modified) {
            val hcId = record.healthConnectId ?: continue
            try {
                if (record.volumeMl <= 0.0) continue
                val endTime = Instant.ofEpochMilli(record.timestamp)
                val startTime = endTime.minusSeconds(1)
                val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)
                val hcRecord = HydrationRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    volume = Volume.milliliters(record.volumeMl),
                    metadata = Metadata.manualEntryWithId(id = hcId)
                )

                try {
                    client.updateRecords(listOf(hcRecord))
                    database.hydrationDao().updateLastSynced(
                        id = record.id,
                        lastSynced = System.currentTimeMillis()
                    )
                    syncedCount++
                } catch (updateEx: Exception) {
                    Log.w(TAG, "Update failed for record ${record.id}, attempting insert as fallback", updateEx)
                    val newHcRecord = HydrationRecord(
                        startTime = startTime,
                        startZoneOffset = zoneOffset,
                        endTime = endTime,
                        endZoneOffset = zoneOffset,
                        volume = Volume.milliliters(record.volumeMl),
                        metadata = Metadata.manualEntry(
                            clientRecordId = record.id,
                            clientRecordVersion = record.lastModified
                        )
                    )
                    val response = client.insertRecords(listOf(newHcRecord))
                    val newHcId = response.recordIdsList.firstOrNull()
                    if (newHcId != null) {
                        val now = System.currentTimeMillis()
                        database.hydrationDao().updateHealthConnectSyncInfo(
                            id = record.id,
                            hcId = newHcId,
                            lastSynced = now,
                            lastModified = now
                        )
                        syncedCount++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating modified record ${record.id} to Health Connect", e)
            }
        }

        syncedCount
    }

    /**
     * Deletes a record from Health Connect by its Health Connect record ID.
     */
    suspend fun deleteRecord(hcId: String) = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext
        if (!hasAllPermissions()) return@withContext
        try {
            client.deleteRecords(
                recordType = HydrationRecord::class,
                recordIdsList = listOf(hcId),
                clientRecordIdsList = emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting record $hcId from Health Connect", e)
        }
    }

    /**
     * Performs an inbound sync from Health Connect, pulling records authored by other apps
     * (with a 30-day initial backfill on first sync) and applying upstream updates/deletions
     * while filtering out this app's own records to avoid echo loops.
     */
    suspend fun syncInbound(database: FillmarkDatabase): Int = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext 0
        if (!hasAllPermissions()) return@withContext 0

        var importedCount = 0
        val prefsRepository = UserPreferencesRepository(context)

        try {
            val token = prefsRepository.getHealthConnectChangesToken()
            if (token == null) {
                // Obtain token first, then backfill last 30 days of external HydrationRecords
                val initialToken = client.getChangesToken(
                    ChangesTokenRequest(
                        recordTypes = setOf(HydrationRecord::class)
                    )
                )
                importedCount += backfillRecentRecords(client, database)
                prefsRepository.setHealthConnectChangesToken(initialToken)
                return@withContext importedCount
            }

            var currentToken: String = token
            var hasMore = true
            while (hasMore) {
                val changesResponse = client.getChanges(currentToken)
                if (changesResponse.changesTokenExpired) {
                    val freshToken = client.getChangesToken(
                        ChangesTokenRequest(recordTypes = setOf(HydrationRecord::class))
                    )
                    importedCount += backfillRecentRecords(client, database)
                    prefsRepository.setHealthConnectChangesToken(freshToken)
                    return@withContext importedCount
                }

                for (change in changesResponse.changes) {
                    when (change) {
                        is UpsertionChange -> {
                            val record = change.record
                            if (record is HydrationRecord && record.metadata.dataOrigin.packageName != context.packageName) {
                                if (upsertExternalRecord(database, record)) {
                                    importedCount++
                                }
                            }
                        }
                        is DeletionChange -> {
                            val existing = database.hydrationDao().getRecordByHealthConnectId(change.recordId)
                            if (existing != null) {
                                database.hydrationDao().deleteRecordById(existing.id)
                                importedCount++
                            }
                        }
                    }
                }
                currentToken = changesResponse.nextChangesToken
                hasMore = changesResponse.hasMore
            }
            prefsRepository.setHealthConnectChangesToken(currentToken)
        } catch (e: Exception) {
            Log.e(TAG, "Error during Health Connect inbound sync", e)
            runCatching { prefsRepository.setHealthConnectChangesToken(null) }
        }
        importedCount
    }

    private suspend fun backfillRecentRecords(
        client: HealthConnectClient,
        database: FillmarkDatabase
    ): Int {
        var count = 0
        var pageToken: String? = null
        val startInstant = Instant.now().minus(30, ChronoUnit.DAYS)
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HydrationRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(startInstant),
                    pageToken = pageToken
                )
            )
            for (record in response.records) {
                if (record.metadata.dataOrigin.packageName != context.packageName) {
                    if (upsertExternalRecord(database, record)) {
                        count++
                    }
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return count
    }

    private suspend fun upsertExternalRecord(
        database: FillmarkDatabase,
        record: HydrationRecord
    ): Boolean {
        val hcUid = record.metadata.id
        val existing = database.hydrationDao().getRecordByHealthConnectId(hcUid)
        val now = System.currentTimeMillis()
        val recordMillis = record.endTime.toEpochMilli()
        val volumeMl = record.volume.inMilliliters
        if (volumeMl <= 0.0) return false

        return if (existing == null) {
            val newEntity = HydrationRecordEntity(
                id = UUID.randomUUID().toString(),
                timestamp = recordMillis,
                volumeMl = volumeMl,
                hydrationFactor = 1.0,
                drinkType = "WATER",
                iconKey = null,
                healthConnectId = hcUid,
                lastModified = now,
                lastSynced = now
            )
            database.hydrationDao().insertRecord(newEntity)
            true
        } else if (existing.volumeMl != volumeMl || existing.timestamp != recordMillis) {
            database.hydrationDao().updateRecord(
                existing.copy(
                    timestamp = recordMillis,
                    volumeMl = volumeMl,
                    lastModified = now,
                    lastSynced = now
                )
            )
            true
        } else {
            false
        }
    }
}
