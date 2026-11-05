package com.fillmark.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

object MidnightAlarmScheduler {
    private const val TAG = "MidnightAlarmScheduler"
    private const val REQUEST_CODE_MIDNIGHT = 1001

    fun calculateNextMidnightMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val tomorrow = LocalDate.now(zoneId).plusDays(1)
        val midnight = tomorrow.atStartOfDay(zoneId)
        return midnight.toInstant().toEpochMilli()
    }

    fun scheduleMidnightReset(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WidgetMidnightReceiver::class.java).apply {
            action = WidgetMidnightReceiver.ACTION_MIDNIGHT_RESET
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MIDNIGHT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = calculateNextMidnightMillis()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled midnight reset alarm for $triggerAtMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Failed scheduling midnight reset alarm", e)
        }
    }
}
