package com.fillmark.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.first

internal enum class WorkerSyncOutcome {
    SUCCESS,
    RETRY,
    FAILURE
}

class HealthConnectSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    internal companion object {
        suspend fun executeBackgroundSync(
            prefs: UserPreferences,
            isAvailable: Boolean,
            hasAllPermissions: Boolean,
            hasWritePermission: Boolean,
            runAttemptCount: Int,
            nowMillis: Long = System.currentTimeMillis(),
            syncOutbound: suspend () -> OutboundSyncResult,
            syncInbound: suspend () -> InboundSyncResult,
            setLastSyncMillis: suspend (Long) -> Unit,
            updateWidgets: suspend () -> Unit
        ): WorkerSyncOutcome {
            if (!prefs.healthConnectSyncEnabled) {
                return WorkerSyncOutcome.SUCCESS
            }
            if (!isAvailable || !hasWritePermission) {
                return WorkerSyncOutcome.SUCCESS
            }

            // Inbound reads are foreground-only so background workers never trigger SecurityException
            // without READ_HEALTH_DATA_IN_BACKGROUND.
            val outboundResult = syncOutbound()
            if (outboundResult.securityError) {
                return WorkerSyncOutcome.SUCCESS
            }
            if (outboundResult.failedCount > 0) {
                return if (runAttemptCount < 3) WorkerSyncOutcome.RETRY else WorkerSyncOutcome.FAILURE
            }
            if (outboundResult.succeeded) {
                setLastSyncMillis(nowMillis)
            }

            return WorkerSyncOutcome.SUCCESS
        }
    }

    override suspend fun doWork(): Result {
        val prefsRepository = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepository.preferencesFlow.first()
        val hcManager = HealthConnectManager(applicationContext)
        val database = FillmarkDatabase.getInstance(applicationContext)

        val outcome = executeBackgroundSync(
            prefs = prefs,
            isAvailable = hcManager.isAvailable(),
            hasAllPermissions = hcManager.hasAllPermissions(),
            hasWritePermission = hcManager.hasWritePermission(),
            runAttemptCount = runAttemptCount,
            syncOutbound = { hcManager.syncOutbound(database) },
            syncInbound = { hcManager.syncInbound(database) },
            setLastSyncMillis = { prefsRepository.setLastHealthConnectSyncMillis(it) },
            updateWidgets = { WidgetUpdateHelper.updateAllWidgets(applicationContext) }
        )

        return when (outcome) {
            WorkerSyncOutcome.SUCCESS -> Result.success()
            WorkerSyncOutcome.RETRY -> Result.retry()
            WorkerSyncOutcome.FAILURE -> Result.failure()
        }
    }
}
