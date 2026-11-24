package com.fillmark.app.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fillmark.app.data.local.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class WidgetUndoCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WidgetUndoCleanupWorker"
        private const val UNIQUE_WORK_NAME = "WidgetUndoCleanup"

        fun schedule(
            context: Context,
            delayMillis: Long = WidgetTimingHelper.UNDO_WINDOW_MS + 1_000L
        ) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<WidgetUndoCleanupWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }.onFailure {
                Log.w(TAG, "Failed to schedule widget undo cleanup", it)
            }
        }

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            }.onFailure {
                Log.w(TAG, "Failed to cancel widget undo cleanup", it)
            }
        }
    }

    override suspend fun doWork(): Result {
        val prefsRepo = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepo.preferencesFlow.first()
        val recordId = prefs.lastWidgetLogRecordId ?: return Result.success()
        if (!WidgetTimingHelper.isUndoAvailable(recordId, prefs.lastWidgetLogTimeMillis)) {
            prefsRepo.setLastWidgetLog(null, null, 0L)
            WidgetUpdateHelper.updateAllWidgets(applicationContext)
        }
        return Result.success()
    }
}
