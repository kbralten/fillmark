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
        val action = intent?.action
        Log.d(TAG, "Received broadcast action: $action")

        // Reschedule alarm for next midnight
        MidnightAlarmScheduler.scheduleMidnightReset(context)

        // Asynchronously update all widgets
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdateHelper.updateAllWidgets(context)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating widgets on broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
