package com.fillmark.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll

object WidgetUpdateHelper {
    private const val TAG = "WidgetUpdateHelper"

    suspend fun updateAllWidgets(context: Context, awaitRender: Boolean = false) {
        val targetVersion = WidgetTimingHelper.notifyClockRefresh()
        val expectedIds = if (awaitRender) collectPacingGlanceIds(context) else emptyList()

        runCatching {
            ProgressMeterWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Failed updating ProgressMeterWidget", it) }

        runCatching {
            QuickLogBarWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Failed updating QuickLogBarWidget", it) }

        runCatching {
            FullDashboardWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Failed updating FullDashboardWidget", it) }

        if (awaitRender && expectedIds.isNotEmpty()) {
            WidgetTimingHelper.awaitWidgetsComposed(expectedIds, targetVersion)
        }
    }

    suspend fun updatePacingWidgets(context: Context, awaitRender: Boolean = false) {
        val targetVersion = WidgetTimingHelper.notifyClockRefresh()
        val expectedIds = if (awaitRender) collectPacingGlanceIds(context) else emptyList()

        runCatching {
            ProgressMeterWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Failed updating ProgressMeterWidget", it) }

        runCatching {
            FullDashboardWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Failed updating FullDashboardWidget", it) }

        if (awaitRender && expectedIds.isNotEmpty()) {
            WidgetTimingHelper.awaitWidgetsComposed(expectedIds, targetVersion)
        }
    }

    private suspend fun collectPacingGlanceIds(context: Context): List<GlanceId> {
        return runCatching {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(ProgressMeterWidget::class.java) +
                manager.getGlanceIds(FullDashboardWidget::class.java)
        }.getOrDefault(emptyList())
    }
}


