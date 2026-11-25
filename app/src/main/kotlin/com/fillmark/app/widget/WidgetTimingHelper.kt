package com.fillmark.app.widget

import androidx.glance.GlanceId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object WidgetTimingHelper {
    const val UNDO_WINDOW_MS = 30_000L

    private val _clockRefreshVersion = MutableStateFlow(0L)
    val clockRefreshVersion: StateFlow<Long> = _clockRefreshVersion.asStateFlow()

    private val _composedVersions = MutableStateFlow<Map<GlanceId, Long>>(emptyMap())
    val composedVersions: StateFlow<Map<GlanceId, Long>> = _composedVersions.asStateFlow()

    fun notifyClockRefresh(): Long {
        return _clockRefreshVersion.updateAndGet { it + 1L }
    }

    fun markWidgetComposed(glanceId: GlanceId, refreshVersion: Long) {
        _composedVersions.update { current ->
            val existing = current[glanceId] ?: -1L
            val next = maxOf(existing, refreshVersion)
            if (existing == next && current.containsKey(glanceId)) {
                current
            } else {
                current + (glanceId to next)
            }
        }
    }

    suspend fun awaitWidgetsComposed(
        expectedIds: Collection<GlanceId>,
        targetVersion: Long,
        timeoutMillis: Long = 3_000L,
        postComposeGraceMillis: Long = 250L
    ): Boolean {
        if (expectedIds.isEmpty()) return true
        val reached = withTimeoutOrNull(timeoutMillis) {
            composedVersions.first { map ->
                expectedIds.all { id -> (map[id] ?: -1L) >= targetVersion }
            }
        } != null
        if (reached && postComposeGraceMillis > 0L) {
            delay(postComposeGraceMillis)
        }
        return reached
    }

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
