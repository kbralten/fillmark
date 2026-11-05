package com.fillmark.app

import android.app.Application
import com.fillmark.app.widget.MidnightAlarmScheduler
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FillmarkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Schedule midnight reset alarm
        MidnightAlarmScheduler.scheduleMidnightReset(this)

        // Refresh all widgets on app process start
        CoroutineScope(Dispatchers.Default).launch {
            WidgetUpdateHelper.updateAllWidgets(this@FillmarkApplication)
        }
    }
}
