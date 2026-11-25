package com.fillmark.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.glance.appwidget.AppWidgetId
import com.fillmark.app.widget.UndoWidgetLogActionCallback
import com.fillmark.app.widget.WidgetMidnightReceiver
import com.fillmark.app.widget.WidgetPacingRefreshReceiver
import com.fillmark.app.widget.WidgetRefreshScheduler
import com.fillmark.app.widget.WidgetSystemEventReceiver
import com.fillmark.app.widget.WidgetTimingHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetReliabilityTest {

    @Test
    fun bug4_undoWindowHelper_enforces30SecondBoundary() {
        assertTrue(WidgetTimingHelper.isUndoAvailable("rec-1", 10_000L, nowMillis = 10_000L))
        assertTrue(WidgetTimingHelper.isUndoAvailable("rec-1", 10_000L, nowMillis = 40_000L))
        assertFalse(WidgetTimingHelper.isUndoAvailable("rec-1", 10_000L, nowMillis = 40_001L))
        assertFalse(WidgetTimingHelper.isUndoAvailable("rec-1", 10_000L, nowMillis = 9_999L))
        assertFalse(WidgetTimingHelper.isUndoAvailable(null, 10_000L, nowMillis = 10_000L))
        assertEquals(12_000L, WidgetTimingHelper.remainingUndoMillis("rec-1", 10_000L, nowMillis = 28_000L))
        assertEquals(0L, WidgetTimingHelper.remainingUndoMillis("rec-1", 10_000L, nowMillis = 45_000L))
    }

    @Test
    fun bug4_observeCanUndo_emitsFalseAfter30Seconds() = runTest {
        var now = 1_000L
        val recordedDelays = mutableListOf<Long>()
        val emissions = WidgetTimingHelper.observeCanUndo(
            lastRecordId = "rec-1",
            lastLogTimeMillis = 1_000L,
            nowMillisProvider = { now },
            delayFn = { ms ->
                recordedDelays.add(ms)
                now += ms
            }
        ).toList()

        assertEquals(listOf(true, false), emissions)
        assertTrue(recordedDelays.isNotEmpty())
        assertTrue(recordedDelays.first() >= 30_000L)
    }

    @Test
    fun bug4_undoActionCallback_rejectsExpiredUndoWithoutDeletingRecord() = runTest {
        var deletedId: String? = null
        var clearedPrefs = false
        var widgetsUpdated = false

        val undone = UndoWidgetLogActionCallback.executeUndo(
            lastRecordId = "rec-expired",
            lastLogTimeMillis = 10_000L,
            nowMillis = 45_000L,
            deleteRecord = { deletedId = it },
            clearLastWidgetLog = { clearedPrefs = true },
            cancelCleanupWork = {},
            updateWidgets = { widgetsUpdated = true }
        )

        assertFalse("Expired undo (>30s) must be rejected", undone)
        assertEquals("Expired undo must NOT delete record", null, deletedId)
        assertTrue("Stale undo metadata should still be cleared", clearedPrefs)
        assertTrue(widgetsUpdated)
    }

    @Test
    fun bug4_undoActionCallback_deletesWithin30Seconds() = runTest {
        var deletedId: String? = null
        var clearedPrefs = false

        val undone = UndoWidgetLogActionCallback.executeUndo(
            lastRecordId = "rec-fresh",
            lastLogTimeMillis = 10_000L,
            nowMillis = 25_000L,
            deleteRecord = { deletedId = it },
            clearLastWidgetLog = { clearedPrefs = true },
            cancelCleanupWork = {},
            updateWidgets = {}
        )

        assertTrue(undone)
        assertEquals("rec-fresh", deletedId)
        assertTrue(clearedPrefs)
    }

    @Test
    fun bug5_widgetClockFlow_emitsMinuteTicksWithinSameLocalDate() = runTest {
        var currentMillis = 1_700_000_010_000L // arbitrary offset within minute
        val times = listOf(
            LocalDateTime.of(2026, 11, 23, 10, 0, 10),
            LocalDateTime.of(2026, 11, 23, 10, 1, 0),
            LocalDateTime.of(2026, 11, 23, 10, 2, 0)
        )
        var idx = 0

        val emitted = withTimeoutOrNull(300_000L) {
            WidgetTimingHelper.activeClockFlow(
                nowDateTimeProvider = { times[idx] },
                nowMillisProvider = { currentMillis },
                delayFn = { ms ->
                    delay(ms)
                    currentMillis += ms
                    if (idx < times.lastIndex) idx++
                }
            ).take(3).toList()
        }

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 11, 23, 10, 0),
                LocalDateTime.of(2026, 11, 23, 10, 1),
                LocalDateTime.of(2026, 11, 23, 10, 2)
            ),
            emitted
        )
    }

    private class RecordingContext : ContextWrapper(null) {
        var alarmServiceRequested = false

        override fun getApplicationContext(): Context = this

        override fun getSystemService(name: String): Any? {
            if (name == Context.ALARM_SERVICE) {
                alarmServiceRequested = true
            }
            return null
        }
    }

    private fun intentWithAction(actionValue: String?): Intent {
        return object : Intent() {
            override fun getAction(): String? = actionValue
        }
    }

    @Test
    fun bug10_systemEventReceiver_rejectsSpoofedOrNullActions_andAcceptsAllowedActions() {
        val receiver = WidgetSystemEventReceiver()

        val spoofedCtx = RecordingContext()
        receiver.onReceive(spoofedCtx, intentWithAction("com.evil.SPOOFED_ACTION"))
        assertFalse("Spoofed action must be ignored", spoofedCtx.alarmServiceRequested)

        val nullCtx = RecordingContext()
        receiver.onReceive(nullCtx, intentWithAction(null))
        assertFalse("Null action must be ignored", nullCtx.alarmServiceRequested)

        val validActions = listOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        for (validAction in validActions) {
            val validCtx = RecordingContext()
            receiver.onReceive(validCtx, intentWithAction(validAction))
            assertTrue("Expected allowed action $validAction to trigger refresh", validCtx.alarmServiceRequested)
        }
    }

    @Test
    fun bug10_midnightReceiver_rejectsUnexpectedActions() {
        val receiver = WidgetMidnightReceiver()
        val spoofedCtx = RecordingContext()
        receiver.onReceive(spoofedCtx, intentWithAction("com.evil.SPOOFED"))
        assertFalse(spoofedCtx.alarmServiceRequested)

        val validCtx = RecordingContext()
        receiver.onReceive(validCtx, intentWithAction(WidgetMidnightReceiver.ACTION_MIDNIGHT_RESET))
        assertTrue(validCtx.alarmServiceRequested)
    }

    @Test
    fun pacingRefreshReceiver_rejectsUnexpectedActions_andAcceptsPacingAction() {
        val receiver = WidgetPacingRefreshReceiver()
        val spoofedCtx = RecordingContext()
        receiver.onReceive(spoofedCtx, intentWithAction("com.evil.SPOOFED"))
        assertFalse(spoofedCtx.alarmServiceRequested)

        val nullCtx = RecordingContext()
        receiver.onReceive(nullCtx, intentWithAction(null))
        assertFalse(nullCtx.alarmServiceRequested)

        val validCtx = RecordingContext()
        receiver.onReceive(validCtx, intentWithAction(WidgetPacingRefreshReceiver.ACTION_PACING_REFRESH))
        assertTrue(validCtx.alarmServiceRequested)
    }

    @Test
    fun calculateNextPacingRefreshMillis_normalWakingWindowCases() {
        val zone = ZoneId.of("America/New_York")

        // Inside window: 14:08:36 -> 14:10:00
        val midAfternoon = ZonedDateTime.of(2026, 11, 25, 14, 8, 36, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 14, 10, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(midAfternoon, "07:00", "23:00")
        )

        // Within < 60s of boundary: 14:09:30 -> 14:20:00
        val nearSlot = ZonedDateTime.of(2026, 11, 25, 14, 9, 30, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 14, 20, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(nearSlot, "07:00", "23:00")
        )

        // Clamps to sleep time right before sleep
        val beforeSleep = ZonedDateTime.of(2026, 11, 25, 22, 55, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 23, 0, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(beforeSleep, "07:00", "23:00")
        )
        val secondsBeforeSleep = ZonedDateTime.of(2026, 11, 25, 22, 59, 30, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 23, 0, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(secondsBeforeSleep, "07:00", "23:00")
        )

        // Before wake time -> today 07:00
        val earlyMorning = ZonedDateTime.of(2026, 11, 25, 5, 30, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(earlyMorning, "07:00", "23:00")
        )

        // At and after sleep time -> tomorrow 07:00
        val atSleep = ZonedDateTime.of(2026, 11, 25, 23, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 26, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(atSleep, "07:00", "23:00")
        )
        val afterSleep = ZonedDateTime.of(2026, 11, 25, 23, 15, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 26, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(afterSleep, "07:00", "23:00")
        )
    }

    @Test
    fun calculateNextPacingRefreshMillis_overnightAnd24HourWindows() {
        val zone = ZoneId.of("America/New_York")

        // Overnight window 20:00 -> 04:00: post-midnight active (02:15 -> 02:20)
        val postMidnight = ZonedDateTime.of(2026, 11, 25, 2, 15, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 2, 20, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(postMidnight, "20:00", "04:00")
        )

        // Overnight window: daytime sleep gap (12:00 -> today 20:00)
        val daytimeSleep = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 20, 0, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(daytimeSleep, "20:00", "04:00")
        )

        // Overnight window: pre-midnight active (22:15 -> 22:20)
        val preMidnight = ZonedDateTime.of(2026, 11, 25, 22, 15, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 22, 20, 0, 0, zone).toInstant().toEpochMilli(),
            WidgetRefreshScheduler.calculateNextPacingRefreshMillis(preMidnight, "20:00", "04:00")
        )

        // Equal wake/sleep (07:00 -> 07:00) treated as 24h waking window
        val equalWindow = ZonedDateTime.of(2026, 11, 25, 3, 12, 0, 0, zone)
        val nextTick = WidgetRefreshScheduler.calculateNextPacingRefreshMillis(equalWindow, "07:00", "07:00")
        assertEquals(
            ZonedDateTime.of(2026, 11, 25, 3, 20, 0, 0, zone),
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextTick), zone)
        )
    }

    @Test
    fun widgetTimingHelper_notifyClockRefreshAndAwaitWidgetsComposed() = runTest {
        val v0 = WidgetTimingHelper.clockRefreshVersion.value
        val v1 = WidgetTimingHelper.notifyClockRefresh()
        val v2 = WidgetTimingHelper.notifyClockRefresh()
        assertEquals(v0 + 1L, v1)
        assertEquals(v0 + 2L, v2)

        val id1 = AppWidgetId(9001)
        val id2 = AppWidgetId(9002)

        assertTrue(
            WidgetTimingHelper.awaitWidgetsComposed(
                expectedIds = emptyList(),
                targetVersion = v2,
                timeoutMillis = 100L,
                postComposeGraceMillis = 0L
            )
        )

        // Mark only one widget; await should time out and return false
        WidgetTimingHelper.markWidgetComposed(id1, v2)
        val timedOut = WidgetTimingHelper.awaitWidgetsComposed(
            expectedIds = listOf(id1, id2),
            targetVersion = v2,
            timeoutMillis = 50L,
            postComposeGraceMillis = 0L
        )
        assertFalse(timedOut)

        // Now mark the second widget concurrently and verify await returns true
        val waiter = launch {
            val completed = WidgetTimingHelper.awaitWidgetsComposed(
                expectedIds = listOf(id1, id2),
                targetVersion = v2,
                timeoutMillis = 2_000L,
                postComposeGraceMillis = 0L
            )
            assertTrue(completed)
        }
        WidgetTimingHelper.markWidgetComposed(id2, v2)
        waiter.join()
    }
}
