package com.fillmark.app.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.units.Volume
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class HealthConnectManager(private val context: Context) {
    companion object {
        private const val TAG = "HealthConnectManager"
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
                // Health Connect enforces startTime < endTime strictly for interval records.
                // For point-in-time hydration events, span 1 second.
                val startTime = endTime.minusSeconds(1)
                val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)
                val hcRecord = HydrationRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    volume = Volume.milliliters(record.volumeMl)
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
                    metadata = Metadata(id = hcId)
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
                        volume = Volume.milliliters(record.volumeMl)
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
                val initialToken = client.getChangesToken(
                    ChangesTokenRequest(
                        recordTypes = setOf(HydrationRecord::class)
                    )
                )
                prefsRepository.setHealthConnectChangesToken(initialToken)
                return@withContext 0
            }

            var currentToken: String = token
            var hasMore = true
            while (hasMore) {
                val changesResponse = client.getChanges(currentToken)
                for (change in changesResponse.changes) {
                    if (change is androidx.health.connect.client.changes.UpsertionChange) {
                        val record = change.record
                        if (record is HydrationRecord) {
                            // Echo protection: ignore records authored by our own package
                            val originPackage = record.metadata.dataOrigin.packageName
                            if (originPackage != context.packageName) {
                                val hcUid = record.metadata.id
                                val existing = database.hydrationDao().getRecordByHealthConnectId(hcUid)
                                if (existing == null) {
                                    val newEntity = HydrationRecordEntity(
                                        id = UUID.randomUUID().toString(),
                                        timestamp = record.startTime.toEpochMilli(),
                                        volumeMl = record.volume.inMilliliters,
                                        hydrationFactor = 1.0,
                                        drinkType = "WATER",
                                        iconKey = null,
                                        healthConnectId = hcUid,
                                        lastModified = System.currentTimeMillis(),
                                        lastSynced = System.currentTimeMillis()
                                    )
                                    database.hydrationDao().insertRecord(newEntity)
                                    importedCount++
                                }
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
            // If token expired or errored, clear token to start fresh next time
            runCatching { prefsRepository.setHealthConnectChangesToken(null) }
        }
        importedCount
    }
}
