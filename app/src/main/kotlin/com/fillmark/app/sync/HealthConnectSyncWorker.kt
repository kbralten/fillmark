package com.fillmark.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.first

class HealthConnectSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefsRepository = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepository.preferencesFlow.first()

        if (!prefs.healthConnectSyncEnabled) {
            return Result.success()
        }

        val hcManager = HealthConnectManager(applicationContext)
        if (!hcManager.isAvailable() || !hcManager.hasAllPermissions()) {
            return Result.success()
        }

        val database = FillmarkDatabase.getInstance(applicationContext)

        // Step 1: Outbound sync
        hcManager.syncOutbound(database)

        // Step 2: Inbound sync (if bidirectional sync is enabled)
        var inboundChanged = 0
        if (prefs.healthConnectBidirectional) {
            inboundChanged = hcManager.syncInbound(database)
        }

        prefsRepository.setLastHealthConnectSyncMillis(System.currentTimeMillis())

        if (inboundChanged > 0) {
            WidgetUpdateHelper.updateAllWidgets(applicationContext)
        }

        return Result.success()
    }
}
