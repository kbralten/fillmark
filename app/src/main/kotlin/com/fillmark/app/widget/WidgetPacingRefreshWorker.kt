package com.fillmark.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetPacingRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val active = WidgetRefreshScheduler.syncSchedule(applicationContext)
        if (!active) {
            return Result.success()
        }
        MidnightAlarmScheduler.scheduleMidnightReset(applicationContext)
        WidgetUpdateHelper.updatePacingWidgets(applicationContext, awaitRender = true)
        return Result.success()
    }
}
