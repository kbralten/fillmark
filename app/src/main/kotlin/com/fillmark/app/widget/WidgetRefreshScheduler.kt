package com.fillmark.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

object WidgetRefreshScheduler {
    const val PACING_ALARM_INTERVAL_MINUTES = 10L
    const val MIN_ALARM_LEAD_MS = 60_000L
    const val PERIODIC_WORK_INTERVAL_MINUTES = 15L
    private const val REQUEST_CODE_PACING = 1002
    internal const val UNIQUE_PERIODIC_WORK_NAME = "WidgetPacingPeriodicRefresh"
    private const val TAG = "WidgetRefreshScheduler"

    fun hasActivePacingWidgets(context: Context): Boolean {
        return runCatching {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
            val fullIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, FullDashboardWidgetReceiver::class.java)
            )
            val meterIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ProgressMeterWidgetReceiver::class.java)
            )
            fullIds.isNotEmpty() || meterIds.isNotEmpty()
        }.getOrDefault(false)
    }

    fun calculateNextPacingRefreshMillis(
        now: ZonedDateTime = ZonedDateTime.now(),
        wakeTimeString: String,
        sleepTimeString: String,
        intervalMinutes: Long = PACING_ALARM_INTERVAL_MINUTES
    ): Long {
        val wakeTime = TimeUtils.parseTimeString(wakeTimeString)
        val sleepTime = TimeUtils.parseTimeString(sleepTimeString)
        val date = now.toLocalDate()
        val zone = now.zone

        val activeWindowEnd: ZonedDateTime = when {
            wakeTime < sleepTime -> {
                val todayStart = date.atTime(wakeTime).atZone(zone)
                val todayEnd = date.atTime(sleepTime).atZone(zone)
                if (now.isBefore(todayStart)) {
                    return todayStart.toInstant().toEpochMilli()
                }
                if (!now.isBefore(todayEnd)) {
                    return date.plusDays(1).atTime(wakeTime).atZone(zone).toInstant().toEpochMilli()
                }
                todayEnd
            }
            wakeTime == sleepTime -> {
                val todayBoundary = date.atTime(wakeTime).atZone(zone)
                if (now.isBefore(todayBoundary)) {
                    todayBoundary
                } else {
                    date.plusDays(1).atTime(wakeTime).atZone(zone)
                }
            }
            else -> {
                val todayWake = date.atTime(wakeTime).atZone(zone)
                val todaySleep = date.atTime(sleepTime).atZone(zone)
                when {
                    now.isBefore(todaySleep) -> todaySleep
                    now.isBefore(todayWake) -> return todayWake.toInstant().toEpochMilli()
                    else -> date.plusDays(1).atTime(sleepTime).atZone(zone)
                }
            }
        }

        val safeInterval = intervalMinutes.coerceAtLeast(1L)
        val nowMinute = now.truncatedTo(ChronoUnit.MINUTES)
        val nextSlotDelta = safeInterval - (nowMinute.minute % safeInterval)
        var candidate = nowMinute.plusMinutes(nextSlotDelta)
        val leadMillis = Duration.between(now.toInstant(), candidate.toInstant()).toMillis()
        if (leadMillis < MIN_ALARM_LEAD_MS) {
            candidate = candidate.plusMinutes(safeInterval)
        }
        if (candidate.isAfter(activeWindowEnd)) {
            candidate = activeWindowEnd
        }

        val candidateMillis = candidate.toInstant().toEpochMilli()
        val nowMillis = now.toInstant().toEpochMilli()
        return maxOf(candidateMillis, nowMillis + 1_000L)
    }

    private fun buildPacingPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetPacingRefreshReceiver::class.java).apply {
            action = WidgetPacingRefreshReceiver.ACTION_PACING_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PACING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleNextPacingAlarm(
        context: Context,
        wakeTimeString: String,
        sleepTimeString: String,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = calculateNextPacingRefreshMillis(
            now = now,
            wakeTimeString = wakeTimeString,
            sleepTimeString = sleepTimeString
        )
        try {
            val pendingIntent = buildPacingPendingIntent(context)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                triggerAtMillis,
                pendingIntent
            )
            Log.d(TAG, "Scheduled pacing refresh alarm for $triggerAtMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Failed scheduling pacing refresh alarm", e)
        }
    }

    fun ensurePeriodicWorkScheduled(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<WidgetPacingRefreshWorker>(
                PERIODIC_WORK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }.onFailure {
            Log.w(TAG, "Failed scheduling periodic widget refresh work", it)
        }
    }

    fun cancelPacingRefresh(context: Context) {
        runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(buildPacingPendingIntent(context))
        }.onFailure {
            Log.w(TAG, "Failed canceling pacing refresh alarm", it)
        }

        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        }.onFailure {
            Log.w(TAG, "Failed canceling periodic widget refresh work", it)
        }
    }

    suspend fun syncSchedule(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        if (!hasActivePacingWidgets(appContext)) {
            cancelPacingRefresh(appContext)
            return false
        }
        val prefs = runCatching {
            UserPreferencesRepository(appContext).preferencesFlow.first()
        }.getOrNull() ?: return false

        if (!prefs.wakingPacingEnabled) {
            cancelPacingRefresh(appContext)
            return false
        }

        scheduleNextPacingAlarm(appContext, prefs.wakingHoursStart, prefs.wakingHoursEnd)
        ensurePeriodicWorkScheduled(appContext)
        return true
    }
}

class WidgetPacingRefreshReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PACING_REFRESH = "com.fillmark.app.action.PACING_REFRESH"
        private const val TAG = "WidgetPacingRefreshReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_PACING_REFRESH) {
            Log.w(TAG, "Ignoring unexpected action: ${intent?.action}")
            return
        }

        MidnightAlarmScheduler.scheduleMidnightReset(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val active = WidgetRefreshScheduler.syncSchedule(context)
                if (active) {
                    WidgetUpdateHelper.updatePacingWidgets(context, awaitRender = true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error refreshing pacing widgets", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
