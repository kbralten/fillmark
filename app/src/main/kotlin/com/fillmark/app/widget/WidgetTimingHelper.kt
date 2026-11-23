package com.fillmark.app.widget

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object WidgetTimingHelper {
    const val UNDO_WINDOW_MS = 30_000L

    fun isUndoAvailable(
        lastRecordId: String?,
        lastLogTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastRecordId.isNullOrBlank() || lastLogTimeMillis <= 0L) return false
        val elapsed = nowMillis - lastLogTimeMillis
        return elapsed in 0L..UNDO_WINDOW_MS
    }

    fun remainingUndoMillis(
        lastRecordId: String?,
        lastLogTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (!isUndoAvailable(lastRecordId, lastLogTimeMillis, nowMillis)) return 0L
        return (UNDO_WINDOW_MS - (nowMillis - lastLogTimeMillis)).coerceAtLeast(0L)
    }

    fun millisUntilNextMinute(nowMillis: Long): Long {
        return (60_000L - (nowMillis % 60_000L)).coerceIn(1_000L, 60_000L)
    }

    fun observeCanUndo(
        lastRecordId: String?,
        lastLogTimeMillis: Long,
        nowMillisProvider: () -> Long = { System.currentTimeMillis() },
        delayFn: suspend (Long) -> Unit = { delay(it) }
    ): Flow<Boolean> = flow {
        val remaining = remainingUndoMillis(lastRecordId, lastLogTimeMillis, nowMillisProvider())
        if (remaining <= 0L) {
            emit(false)
            return@flow
        }
        emit(true)
        delayFn(remaining + 100L)
        emit(isUndoAvailable(lastRecordId, lastLogTimeMillis, nowMillisProvider()))
    }

    fun activeClockFlow(
        nowDateTimeProvider: () -> LocalDateTime = { LocalDateTime.now() },
        nowMillisProvider: () -> Long = { System.currentTimeMillis() },
        delayFn: suspend (Long) -> Unit = { delay(it) }
    ): Flow<LocalDateTime> = flow {
        var lastEmitted = nowDateTimeProvider().truncatedTo(ChronoUnit.MINUTES)
        emit(lastEmitted)
        while (true) {
            delayFn(millisUntilNextMinute(nowMillisProvider()))
            val current = nowDateTimeProvider().truncatedTo(ChronoUnit.MINUTES)
            if (current != lastEmitted) {
                lastEmitted = current
                emit(current)
            }
        }
    }
}
