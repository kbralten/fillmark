package com.fillmark.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.fillmark.app.widget.UndoWidgetLogActionCallback
import com.fillmark.app.widget.WidgetMidnightReceiver
import com.fillmark.app.widget.WidgetSystemEventReceiver
import com.fillmark.app.widget.WidgetTimingHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

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
            Intent.ACTION_BOOT_COMPLETED
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
}
