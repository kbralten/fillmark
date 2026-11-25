package com.fillmark.app

import android.app.Application
import com.fillmark.app.core.ShortcutHelper
import com.fillmark.app.widget.MidnightAlarmScheduler
import com.fillmark.app.widget.WidgetRefreshScheduler
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FillmarkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MidnightAlarmScheduler.scheduleMidnightReset(this)

        CoroutineScope(Dispatchers.Default).launch {
            WidgetRefreshScheduler.syncSchedule(this@FillmarkApplication)
            WidgetUpdateHelper.updateAllWidgets(this@FillmarkApplication)
            ShortcutHelper.updateShortcuts(this@FillmarkApplication)
        }
    }
}
