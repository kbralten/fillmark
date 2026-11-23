package com.fillmark.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetMidnightReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MIDNIGHT_RESET = "com.fillmark.app.action.MIDNIGHT_RESET"
        private const val TAG = "WidgetMidnightReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        handleWidgetRefreshBroadcast(context, intent, TAG)
    }
}

class WidgetSystemEventReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WidgetSystemEventReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        handleWidgetRefreshBroadcast(context, intent, TAG)
    }
}

private fun BroadcastReceiver.handleWidgetRefreshBroadcast(
    context: Context,
    intent: Intent?,
    tag: String
) {
    val action = intent?.action
    Log.d(tag, "Received broadcast action: $action")

    // Reschedule alarm for next midnight
    MidnightAlarmScheduler.scheduleMidnightReset(context)

    // Asynchronously update all widgets
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            WidgetUpdateHelper.updateAllWidgets(context)
        } catch (e: Exception) {
            Log.w(tag, "Error updating widgets on broadcast", e)
        } finally {
            pendingResult.finish()
        }
    }
}
