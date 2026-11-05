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
import com.fillmark.app.sync.HealthConnectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        val record = HydrationRecordEntity(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            volumeMl = preset.volumeMl,
            hydrationFactor = preset.hydrationFactor,
            drinkType = preset.drinkType,
            iconKey = preset.iconKey,
            lastModified = System.currentTimeMillis()
        )
        database.hydrationDao().insertRecord(record)

        // Show instant visual confirmation feedback (Item 8)
        val prefsRepo = UserPreferencesRepository(context)
        val prefs = prefsRepo.preferencesFlow.first()
        val volumeStr = VolumeConverter.formatVolume(preset.volumeMl, prefs.displayUnit)

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "💧 Logged +$volumeStr (${preset.label})",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Trigger Health Connect sync asynchronously if enabled
        if (prefs.healthConnectSyncEnabled) {
            CoroutineScope(Dispatchers.IO).launch {
                val hc = HealthConnectManager(context)
                if (hc.isAvailable() && hc.hasAllPermissions()) {
                    hc.syncOutbound(database)
                }
            }
        }

        // Reschedule midnight alarm to be sure
        MidnightAlarmScheduler.scheduleMidnightReset(context)

        // Immediately update this widget instance directly (Item 9)
        runCatching { FullDashboardWidget().update(context, glanceId) }
        runCatching { ProgressMeterWidget().update(context, glanceId) }
        runCatching { QuickLogBarWidget().update(context, glanceId) }

        // Notify all widgets to refresh immediately
        WidgetUpdateHelper.updateAllWidgets(context)
    }
}

