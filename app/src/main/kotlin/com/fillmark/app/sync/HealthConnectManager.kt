package com.fillmark.app.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
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
import com.fillmark.app.data.local.entity.PendingHealthConnectDeletionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class HealthConnectAvailabilityUi {
    AVAILABLE,
    INSTALL_OR_UPDATE_REQUIRED,
    UNAVAILABLE
}

data class HealthConnectPermissionState(
    val hasRead: Boolean,
    val hasWrite: Boolean
) {
    val hasAll: Boolean get() = hasRead && hasWrite
    val hasAny: Boolean get() = hasRead || hasWrite
}

data class OutboundSyncResult(
    val syncedCount: Int = 0,
    val deletedCount: Int = 0,
    val failedCount: Int = 0,
    val skipped: Boolean = false,
    val securityError: Boolean = false
) {
    val succeeded: Boolean get() = !skipped && !securityError && failedCount == 0
}

data class InboundSyncResult(
    val importedCount: Int = 0,
    val skipped: Boolean = false,
    val succeeded: Boolean = true,
    val securityError: Boolean = false
)

internal data class GatewayChangesPage(
    val changes: List<Any>,
    val nextChangesToken: String,
    val hasMore: Boolean,
    val changesTokenExpired: Boolean
)

internal data class GatewayRecordsPage(
    val records: List<HydrationRecord>,
    val pageToken: String?
)

internal interface HealthConnectGateway {
    fun isAvailable(): Boolean
    suspend fun getGrantedPermissions(): Set<String>
    suspend fun insertRecord(record: HydrationRecord): String?
    suspend fun updateRecord(record: HydrationRecord)
    suspend fun deleteRecords(recordIds: List<String>)
    suspend fun getChangesToken(): String
    suspend fun getChanges(token: String): GatewayChangesPage
    suspend fun readHydrationRecordsAfter(startInstant: Instant, pageToken: String?): GatewayRecordsPage
}

internal interface HealthConnectSyncStorage {
    suspend fun getUnsyncedRecords(): List<HydrationRecordEntity>
    suspend fun getModifiedRecords(): List<HydrationRecordEntity>
    suspend fun updateHealthConnectSyncInfo(id: String, hcId: String, lastSynced: Long, lastModified: Long)
    suspend fun updateLastSynced(id: String, lastSynced: Long)
    suspend fun getRecordByHealthConnectId(hcId: String): HydrationRecordEntity?
    suspend fun insertRecord(record: HydrationRecordEntity)
    suspend fun updateRecord(record: HydrationRecordEntity)
    suspend fun deleteRecordById(id: String)
    suspend fun getPendingDeletions(): List<PendingHealthConnectDeletionEntity>
    suspend fun removePendingDeletion(hcId: String)
    suspend fun getChangesToken(): String?
    suspend fun setChangesToken(token: String?)
}

internal class AndroidHealthConnectGateway(private val context: Context) : HealthConnectGateway {
    private val client: HealthConnectClient? by lazy {
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null
    }

    override fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    override suspend fun getGrantedPermissions(): Set<String> {
        return client?.permissionController?.getGrantedPermissions() ?: emptySet()
    }

    override suspend fun insertRecord(record: HydrationRecord): String? {
        val hc = client ?: return null
        return hc.insertRecords(listOf(record)).recordIdsList.firstOrNull()
    }

    override suspend fun updateRecord(record: HydrationRecord) {
        val hc = client ?: return
        hc.updateRecords(listOf(record))
    }

    override suspend fun deleteRecords(recordIds: List<String>) {
        val hc = client ?: return
        hc.deleteRecords(
            recordType = HydrationRecord::class,
            recordIdsList = recordIds,
            clientRecordIdsList = emptyList()
        )
    }

    override suspend fun getChangesToken(): String {
        val hc = client ?: error("Health Connect unavailable")
        return hc.getChangesToken(
            ChangesTokenRequest(recordTypes = setOf(HydrationRecord::class))
        )
    }

    override suspend fun getChanges(token: String): GatewayChangesPage {
        val hc = client ?: error("Health Connect unavailable")
        val response = hc.getChanges(token)
        return GatewayChangesPage(
            changes = response.changes,
            nextChangesToken = response.nextChangesToken,
            hasMore = response.hasMore,
            changesTokenExpired = response.changesTokenExpired
        )
    }

    override suspend fun readHydrationRecordsAfter(
        startInstant: Instant,
        pageToken: String?
    ): GatewayRecordsPage {
        val hc = client ?: error("Health Connect unavailable")
        val response = hc.readRecords(
            ReadRecordsRequest(
                recordType = HydrationRecord::class,
                timeRangeFilter = TimeRangeFilter.after(startInstant),
                pageToken = pageToken
            )
        )
        return GatewayRecordsPage(
            records = response.records,
            pageToken = response.pageToken
        )
    }
}

internal class RoomHealthConnectSyncStorage(
    private val database: FillmarkDatabase,
    private val prefsRepository: UserPreferencesRepository
) : HealthConnectSyncStorage {
    override suspend fun getUnsyncedRecords(): List<HydrationRecordEntity> =
        database.hydrationDao().getUnsyncedRecords()

    override suspend fun getModifiedRecords(): List<HydrationRecordEntity> =
        database.hydrationDao().getModifiedRecords()

    override suspend fun updateHealthConnectSyncInfo(
        id: String,
        hcId: String,
        lastSynced: Long,
        lastModified: Long
    ) = database.hydrationDao().updateHealthConnectSyncInfo(id, hcId, lastSynced, lastModified)

    override suspend fun updateLastSynced(id: String, lastSynced: Long) =
        database.hydrationDao().updateLastSynced(id, lastSynced)

    override suspend fun getRecordByHealthConnectId(hcId: String): HydrationRecordEntity? =
        database.hydrationDao().getRecordByHealthConnectId(hcId)

    override suspend fun insertRecord(record: HydrationRecordEntity) =
        database.hydrationDao().insertRecord(record)

    override suspend fun updateRecord(record: HydrationRecordEntity) =
        database.hydrationDao().updateRecord(record)

    override suspend fun deleteRecordById(id: String) =
        database.hydrationDao().deleteRecordById(id)

    override suspend fun getPendingDeletions(): List<PendingHealthConnectDeletionEntity> =
        database.pendingHealthConnectDeletionDao().getAll()

    override suspend fun removePendingDeletion(hcId: String) =
        database.pendingHealthConnectDeletionDao().deleteByHcId(hcId)

    override suspend fun getChangesToken(): String? =
        prefsRepository.getHealthConnectChangesToken()

    override suspend fun setChangesToken(token: String?) =
        prefsRepository.setHealthConnectChangesToken(token)
}

class HealthConnectManager internal constructor(
    private val context: Context?,
    private val packageName: String,
    private val gateway: HealthConnectGateway,
    private val defaultStorageFactory: (() -> HealthConnectSyncStorage)? = null
) {
    constructor(context: Context) : this(
        context = context.applicationContext,
        packageName = context.packageName,
        gateway = AndroidHealthConnectGateway(context.applicationContext),
        defaultStorageFactory = {
            RoomHealthConnectSyncStorage(
                FillmarkDatabase.getInstance(context.applicationContext),
                UserPreferencesRepository(context.applicationContext)
            )
        }
    )

    companion object {
        private const val TAG = "HealthConnectManager"
        private const val UNIQUE_PERIODIC_SYNC_NAME = "HealthConnectPeriodicSync"
        private const val UNIQUE_IMMEDIATE_SYNC_NAME = "HealthConnectOutboundSync"

        val readPermission: String = HealthPermission.getReadPermission(HydrationRecord::class)
        val writePermission: String = HealthPermission.getWritePermission(HydrationRecord::class)

        fun evaluatePermissions(granted: Set<String>): HealthConnectPermissionState {
            return HealthConnectPermissionState(
                hasRead = readPermission in granted,
                hasWrite = writePermission in granted
            )
        }

        fun resolveAvailabilityUi(sdkStatus: Int): HealthConnectAvailabilityUi {
            return when (sdkStatus) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailabilityUi.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    HealthConnectAvailabilityUi.INSTALL_OR_UPDATE_REQUIRED
                else -> HealthConnectAvailabilityUi.UNAVAILABLE
            }
        }

        fun openPlayStore(context: Context) {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                "market://details?id=com.google.android.apps.healthdata".toUri()
            ).apply {
                setPackage("com.android.vending")
            }
            runCatching { context.startActivity(marketIntent) }
                .onFailure {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata".toUri()
                            )
                        )
                    }
                }
        }

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

        fun cancelAllSyncWork(context: Context) {
            runCatching {
                val wm = WorkManager.getInstance(context)
                wm.cancelUniqueWork(UNIQUE_PERIODIC_SYNC_NAME)
                wm.cancelUniqueWork(UNIQUE_IMMEDIATE_SYNC_NAME)
            }.onFailure {
                Log.w(TAG, "Failed to cancel Health Connect sync work", it)
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
        val ctx = context ?: return@lazy null
        if (isAvailable()) HealthConnectClient.getOrCreate(ctx) else null
    }

    val readPermission: String get() = Companion.readPermission
    val writePermission: String get() = Companion.writePermission
    val permissions = setOf(Companion.readPermission, Companion.writePermission)

    fun getSdkStatus(): Int {
        val ctx = context ?: return HealthConnectClient.SDK_UNAVAILABLE
        return HealthConnectClient.getSdkStatus(ctx)
    }

    fun isAvailable(): Boolean {
        return gateway.isAvailable()
    }

    suspend fun getPermissionState(): HealthConnectPermissionState {
        if (!gateway.isAvailable()) return HealthConnectPermissionState(hasRead = false, hasWrite = false)
        return evaluatePermissions(gateway.getGrantedPermissions())
    }

    suspend fun hasWritePermission(): Boolean = getPermissionState().hasWrite

    suspend fun hasReadPermission(): Boolean = getPermissionState().hasRead

    suspend fun hasAllPermissions(): Boolean = getPermissionState().hasAll

    suspend fun syncOutbound(database: FillmarkDatabase): OutboundSyncResult {
        val ctx = context ?: error("Context required when no storage provided")
        return syncOutbound(RoomHealthConnectSyncStorage(database, UserPreferencesRepository(ctx)))
    }

    internal suspend fun syncOutbound(storage: HealthConnectSyncStorage): OutboundSyncResult =
        withContext(Dispatchers.IO) {
            if (!gateway.isAvailable()) return@withContext OutboundSyncResult(skipped = true)
            if (!hasWritePermission()) return@withContext OutboundSyncResult(skipped = true)

            var syncedCount = 0
            var deletedCount = 0
            var failedCount = 0
            var securityError = false

            // 1. Drain pending Health Connect deletion tombstones
            val pendingDeletions = storage.getPendingDeletions()
            for (pending in pendingDeletions) {
                try {
                    gateway.deleteRecords(listOf(pending.healthConnectId))
                    storage.removePendingDeletion(pending.healthConnectId)
                    deletedCount++
                } catch (se: SecurityException) {
                    Log.w(TAG, "SecurityException deleting record ${pending.healthConnectId} from Health Connect", se)
                    securityError = true
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting pending record ${pending.healthConnectId} from Health Connect", e)
                    failedCount++
                }
            }

            if (securityError) {
                return@withContext OutboundSyncResult(
                    syncedCount = syncedCount,
                    deletedCount = deletedCount,
                    failedCount = failedCount,
                    securityError = true
                )
            }

            // 2. Newly created unsynced records
            val unsynced = storage.getUnsyncedRecords()
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

                    val generatedId = gateway.insertRecord(hcRecord)
                    if (generatedId != null) {
                        val now = System.currentTimeMillis()
                        storage.updateHealthConnectSyncInfo(
                            id = record.id,
                            hcId = generatedId,
                            lastSynced = now,
                            lastModified = now
                        )
                        syncedCount++
                    }
                } catch (se: SecurityException) {
                    Log.w(TAG, "SecurityException syncing record ${record.id} to Health Connect", se)
                    securityError = true
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing record ${record.id} to Health Connect", e)
                    failedCount++
                }
            }

            if (securityError) {
                return@withContext OutboundSyncResult(
                    syncedCount = syncedCount,
                    deletedCount = deletedCount,
                    failedCount = failedCount,
                    securityError = true
                )
            }

            // 3. Previously synced records that have been modified locally
            val modified = storage.getModifiedRecords()
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
                        gateway.updateRecord(hcRecord)
                        storage.updateLastSynced(
                            id = record.id,
                            lastSynced = System.currentTimeMillis()
                        )
                        syncedCount++
                    } catch (updateEx: Exception) {
                        if (updateEx is SecurityException) throw updateEx
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
                        val newHcId = gateway.insertRecord(newHcRecord)
                        if (newHcId != null) {
                            val now = System.currentTimeMillis()
                            storage.updateHealthConnectSyncInfo(
                                id = record.id,
                                hcId = newHcId,
                                lastSynced = now,
                                lastModified = now
                            )
                            syncedCount++
                        }
                    }
                } catch (se: SecurityException) {
                    Log.w(TAG, "SecurityException updating modified record ${record.id} to Health Connect", se)
                    securityError = true
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating modified record ${record.id} to Health Connect", e)
                    failedCount++
                }
            }

            OutboundSyncResult(
                syncedCount = syncedCount,
                deletedCount = deletedCount,
                failedCount = failedCount,
                securityError = securityError
            )
        }

    suspend fun deleteRecord(hcId: String): Boolean = withContext(Dispatchers.IO) {
        if (!gateway.isAvailable()) return@withContext false
        if (!hasWritePermission()) return@withContext false
        try {
            gateway.deleteRecords(listOf(hcId))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting record $hcId from Health Connect", e)
            false
        }
    }

    suspend fun syncInbound(database: FillmarkDatabase): InboundSyncResult {
        val ctx = context ?: error("Context required when no storage provided")
        return syncInbound(RoomHealthConnectSyncStorage(database, UserPreferencesRepository(ctx)))
    }

    internal suspend fun syncInbound(storage: HealthConnectSyncStorage): InboundSyncResult =
        withContext(Dispatchers.IO) {
            if (!gateway.isAvailable()) return@withContext InboundSyncResult(skipped = true, succeeded = false)
            if (!hasReadPermission()) return@withContext InboundSyncResult(skipped = true, succeeded = false)

            var importedCount = 0

            try {
                val token = storage.getChangesToken()
                if (token == null) {
                    val initialToken = gateway.getChangesToken()
                    importedCount += backfillRecentRecords(storage)
                    storage.setChangesToken(initialToken)
                    return@withContext InboundSyncResult(importedCount = importedCount, succeeded = true)
                }

                var currentToken: String = token
                var hasMore = true
                while (hasMore) {
                    val changesResponse = gateway.getChanges(currentToken)
                    if (changesResponse.changesTokenExpired) {
                        val freshToken = gateway.getChangesToken()
                        importedCount += backfillRecentRecords(storage)
                        storage.setChangesToken(freshToken)
                        return@withContext InboundSyncResult(importedCount = importedCount, succeeded = true)
                    }

                    for (change in changesResponse.changes) {
                        when (change) {
                            is UpsertionChange -> {
                                val record = change.record
                                if (record is HydrationRecord && record.metadata.dataOrigin.packageName != packageName) {
                                    if (upsertExternalRecord(storage, record)) {
                                        importedCount++
                                    }
                                }
                            }
                            is DeletionChange -> {
                                val existing = storage.getRecordByHealthConnectId(change.recordId)
                                if (existing != null) {
                                    storage.deleteRecordById(existing.id)
                                    importedCount++
                                }
                            }
                        }
                    }
                    currentToken = changesResponse.nextChangesToken
                    storage.setChangesToken(currentToken)
                    hasMore = changesResponse.hasMore
                }
                InboundSyncResult(importedCount = importedCount, succeeded = true)
            } catch (se: SecurityException) {
                Log.w(TAG, "Health Connect read rejected (preserving changes token)", se)
                InboundSyncResult(importedCount = importedCount, succeeded = false, securityError = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error during Health Connect inbound sync (preserving changes token)", e)
                InboundSyncResult(importedCount = importedCount, succeeded = false, securityError = false)
            }
        }

    private suspend fun backfillRecentRecords(storage: HealthConnectSyncStorage): Int {
        var count = 0
        var pageToken: String? = null
        val startInstant = Instant.now().minus(30, ChronoUnit.DAYS)
        do {
            val response = gateway.readHydrationRecordsAfter(startInstant, pageToken)
            for (record in response.records) {
                if (record.metadata.dataOrigin.packageName != packageName) {
                    if (upsertExternalRecord(storage, record)) {
                        count++
                    }
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return count
    }

    private suspend fun upsertExternalRecord(
        storage: HealthConnectSyncStorage,
        record: HydrationRecord
    ): Boolean {
        val hcUid = record.metadata.id
        val existing = storage.getRecordByHealthConnectId(hcUid)
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
            storage.insertRecord(newEntity)
            true
        } else if (existing.volumeMl != volumeMl || existing.timestamp != recordMillis) {
            storage.updateRecord(
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
