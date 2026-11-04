package com.fillmark.app.domain

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PacingStatus {
    AHEAD,
    ON_TRACK,
    BEHIND
}

data class PacingResult(
    val targetPacingPercent: Float,
    val actualProgressPercent: Float,
    val differencePercent: Float,
    val volumeDifferenceMl: Double,
    val status: PacingStatus,
    val statusText: String
)

object PacingCalculator {
    const val THRESHOLD_PERCENT = 5.0f

    /**
     * Calculates the target pacing percentage based on current time within the waking window.
     * Formula: min(100, max(0, (t_current - t_wake) / (t_sleep - t_wake))) * 100
     */
    fun calculateTargetPacingPercent(
        currentTime: LocalTime,
        wakeTime: LocalTime,
        sleepTime: LocalTime
    ): Float {
        val currentMinutes = currentTime.hour * 60 + currentTime.minute
        val wakeMinutes = wakeTime.hour * 60 + wakeTime.minute
        var sleepMinutes = sleepTime.hour * 60 + sleepTime.minute

        // Handle case where sleep time is past midnight (e.g., wake at 08:00, sleep at 01:00 next day)
        if (sleepMinutes <= wakeMinutes) {
            sleepMinutes += 24 * 60
        }

        var adjustedCurrent = currentMinutes
        if (adjustedCurrent < wakeMinutes && sleepMinutes > 24 * 60) {
            adjustedCurrent += 24 * 60
        }

        if (adjustedCurrent <= wakeMinutes) return 0f
        if (adjustedCurrent >= sleepMinutes) return 100f

        val totalDuration = (sleepMinutes - wakeMinutes).toFloat()
        val elapsed = (adjustedCurrent - wakeMinutes).toFloat()

        return (elapsed / totalDuration * 100f).coerceIn(0f, 100f)
    }

    fun evaluatePacing(
        currentTime: LocalTime,
        wakeTimeString: String,
        sleepTimeString: String,
        currentVolumeMl: Double,
        dailyTargetMl: Double,
        displayUnit: DisplayUnit = DisplayUnit.METRIC_ML
    ): PacingResult {
        val wakeTime = TimeUtils.parseTimeString(wakeTimeString)
        val sleepTime = TimeUtils.parseTimeString(sleepTimeString)

        val targetPacingPercent = calculateTargetPacingPercent(currentTime, wakeTime, sleepTime)
        val actualProgressPercent = if (dailyTargetMl > 0) {
            ((currentVolumeMl / dailyTargetMl) * 100.0).toFloat().coerceIn(0f, 200f)
        } else {
            0f
        }

        val expectedVolumeMl = dailyTargetMl * (targetPacingPercent / 100.0)
        val volumeDiffMl = currentVolumeMl - expectedVolumeMl

        val diffPercent = actualProgressPercent - targetPacingPercent
        val status = when {
            diffPercent >= THRESHOLD_PERCENT -> PacingStatus.AHEAD
            diffPercent <= -THRESHOLD_PERCENT -> PacingStatus.BEHIND
            else -> PacingStatus.ON_TRACK
        }

        val behindFormatted = VolumeConverter.formatVolume(abs(volumeDiffMl), displayUnit)
        val aheadFormatted = VolumeConverter.formatVolume(volumeDiffMl, displayUnit)

        val statusText = when (status) {
            PacingStatus.AHEAD -> "Ahead of pace (+$aheadFormatted)"
            PacingStatus.ON_TRACK -> "On track with daily goal"
            PacingStatus.BEHIND -> "Behind pace by $behindFormatted"
        }

        return PacingResult(
            targetPacingPercent = targetPacingPercent,
            actualProgressPercent = actualProgressPercent,
            differencePercent = diffPercent,
            volumeDifferenceMl = volumeDiffMl,
            status = status,
            statusText = statusText
        )
    }
}
