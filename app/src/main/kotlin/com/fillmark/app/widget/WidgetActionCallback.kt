package com.fillmark.app.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.sync.HealthConnectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

class QuickLogActionCallback : ActionCallback {
    companion object {
        val PRESET_ID_KEY = ActionParameters.Key<String>("preset_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val presetId = parameters[PRESET_ID_KEY] ?: return
        val database = FillmarkDatabase.getInstance(context)
        val preset = database.presetDao().getPresetById(presetId) ?: return

        val now = System.currentTimeMillis()
        val record = HydrationRecordEntity(
            id = UUID.randomUUID().toString(),
            timestamp = now,
            volumeMl = preset.volumeMl,
            hydrationFactor = preset.hydrationFactor,
            drinkType = preset.drinkType,
            iconKey = preset.iconKey,
            lastModified = now
        )
        database.hydrationDao().insertRecord(record)

        val prefsRepo = UserPreferencesRepository(context)
        val prefs = prefsRepo.preferencesFlow.first()
        prefsRepo.setLastWidgetLog(record.id, preset.label, now)
        WidgetUndoCleanupWorker.schedule(context)
        val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, prefs.displayUnit)

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Logged +$volumeStr (${preset.label})",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (prefs.healthConnectSyncEnabled) {
            HealthConnectManager.enqueueImmediateSync(context, replace = true)
        }

        MidnightAlarmScheduler.scheduleMidnightReset(context)

        runCatching { FullDashboardWidget().update(context, glanceId) }
        runCatching { ProgressMeterWidget().update(context, glanceId) }
        runCatching { QuickLogBarWidget().update(context, glanceId) }

        WidgetUpdateHelper.updateAllWidgets(context)
    }
}

class UndoWidgetLogActionCallback : ActionCallback {
    internal companion object {
        suspend fun executeUndo(
            lastRecordId: String?,
            lastLogTimeMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            deleteRecord: suspend (String) -> Unit,
            clearLastWidgetLog: suspend () -> Unit,
            cancelCleanupWork: () -> Unit,
            updateWidgets: suspend () -> Unit
        ): Boolean {
            val recordId = lastRecordId ?: return false
            if (!WidgetTimingHelper.isUndoAvailable(recordId, lastLogTimeMillis, nowMillis)) {
                clearLastWidgetLog()
                cancelCleanupWork()
                updateWidgets()
                return false
            }
            deleteRecord(recordId)
            clearLastWidgetLog()
            cancelCleanupWork()
            updateWidgets()
            return true
        }
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefsRepo = UserPreferencesRepository(context)
        val prefs = prefsRepo.preferencesFlow.first()
        val label = prefs.lastWidgetLogLabel ?: "drink"

        val undone = executeUndo(
            lastRecordId = prefs.lastWidgetLogRecordId,
            lastLogTimeMillis = prefs.lastWidgetLogTimeMillis,
            deleteRecord = { recordId -> HydrationRepository(context).deleteRecordById(recordId) },
            clearLastWidgetLog = { prefsRepo.setLastWidgetLog(null, null, 0L) },
            cancelCleanupWork = { WidgetUndoCleanupWorker.cancel(context) },
            updateWidgets = { WidgetUpdateHelper.updateAllWidgets(context) }
        )

        if (undone) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Undid $label", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
