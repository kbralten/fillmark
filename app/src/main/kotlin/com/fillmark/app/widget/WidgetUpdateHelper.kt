package com.fillmark.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll

object WidgetUpdateHelper {
    private const val TAG = "WidgetUpdateHelper"

    suspend fun updateAllWidgets(context: Context) {
        runCatching {
            ProgressMeterWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Failed updating ProgressMeterWidget", it) }

        runCatching {
            QuickLogBarWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Failed updating QuickLogBarWidget", it) }

        runCatching {
            FullDashboardWidget().updateAll(context)
        }.onFailure { Log.w(TAG, "Failed updating FullDashboardWidget", it) }
    }
}

