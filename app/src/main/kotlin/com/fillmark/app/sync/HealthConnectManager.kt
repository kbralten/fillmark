package com.fillmark.app.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class HealthConnectManager(private val context: Context) {
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
     * Performs an outbound sync of all local records that have not yet been synced to Health Connect.
     */
    suspend fun syncOutbound(database: FillmarkDatabase): Int = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext 0
        if (!hasAllPermissions()) return@withContext 0

        val unsynced = database.hydrationDao().getUnsyncedRecords()
        var syncedCount = 0

        for (record in unsynced) {
            try {
                val instant = Instant.ofEpochMilli(record.timestamp)
                val hcRecord = HydrationRecord(
                    startTime = instant,
                    startZoneOffset = null,
                    endTime = instant,
                    endZoneOffset = null,
                    volume = Volume.milliliters(record.volumeMl)
                )

                val response = client.insertRecords(listOf(hcRecord))
                val generatedId = response.recordIdsList.firstOrNull()
                if (generatedId != null) {
                    database.hydrationDao().updateHealthConnectId(
                        id = record.id,
                        hcId = generatedId,
                        lastModified = System.currentTimeMillis()
                    )
                    syncedCount++
                }
            } catch (e: Exception) {
                // Log and continue with next record
            }
        }
        syncedCount
    }

    /**
     * Performs an inbound sync from Health Connect, pulling records authored by other apps
     * while filtering out this app's own records to avoid echo loops.
     */
    suspend fun syncInbound(database: FillmarkDatabase): Int = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext 0
        if (!hasAllPermissions()) return@withContext 0

        var importedCount = 0
        try {
            val token = client.getChangesToken(
                ChangesTokenRequest(
                    recordTypes = setOf(HydrationRecord::class)
                )
            )

            val changes = client.getChanges(token)
            for (change in changes.changes) {
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
                                    lastModified = System.currentTimeMillis()
                                )
                                database.hydrationDao().insertRecord(newEntity)
                                importedCount++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently handle sync exceptions to maintain local-first reliability
        }
        importedCount
    }
}

