package com.fillmark.app.domain

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class DailyGraphMode(val label: String) {
    CUMULATIVE("Cumulative"),
    RATE("Rate")
}

data class ChartPoint(
    val hourOfDay: Float,      // 0.0f to 24.0f
    val value: Double,         // ml (if cumulative) or ml/h (if rate)
    val displayValue: String   // formatted value string (e.g. "500 ml" or "150 ml/h")
)

data class DailyLineChartData(
    val mode: DailyGraphMode = DailyGraphMode.CUMULATIVE,
    val points: List<ChartPoint> = emptyList(),
    val targetVolumeMl: Double = 0.0,
    val hasTarget: Boolean = false,
    val wakingHoursStart: Float? = null,
    val wakingHoursEnd: Float? = null,
    val targetRatePerHour: Double = 0.0,
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val totalIntakeMl: Double = 0.0,
    val hasEntries: Boolean = false
)

object DailyGraphCalculator {

    /**
     * Clusters records that occur within 5 minutes of each other to treat simultaneous
     * logs as part of the same intake event and prevent division-by-near-zero spikes in rate mode.
     */
    data class ClusteredDrinkEvent(
        val timestamp: Long,
        val hourOfDay: Float,
        val volumeMl: Double
    )

    fun calculate(
        records: List<HydrationRecordEntity>,
        date: LocalDate,
        zoneId: ZoneId,
        targetMl: Double,
        wakingHoursStart: String,
        wakingHoursEnd: String,
        wakingPacingEnabled: Boolean,
        displayUnit: DisplayUnit,
        mode: DailyGraphMode
    ): DailyLineChartData {
        val hasTarget = targetMl > 0.0

        val (wakeHour, sleepHour, wakingDuration) = if (wakingPacingEnabled) {
            val wakeTime = TimeUtils.parseTimeString(wakingHoursStart)
            val sleepTime = TimeUtils.parseTimeString(wakingHoursEnd)
            val wH = wakeTime.hour + wakeTime.minute / 60.0f
            var sH = sleepTime.hour + sleepTime.minute / 60.0f
            if (sH <= wH) {
                sH += 24.0f
            }
            val dur = (sH - wH).toDouble().coerceAtLeast(1.0)
            Triple(wH, sH, dur)
        } else {
            Triple(null, null, 24.0)
        }

        val targetRatePerHour = if (hasTarget) {
            targetMl / wakingDuration
        } else {
            0.0
        }

        val startOfDay = TimeUtils.startOfDayMillis(date, zoneId)
        val endOfDay = TimeUtils.endOfDayMillis(date, zoneId)
        val dayRecords = records
            .filter { it.timestamp in startOfDay..endOfDay }
            .sortedBy { it.timestamp }

        val totalIntakeMl = dayRecords.sumOf { it.effectiveVolumeMl }
        val hasEntries = dayRecords.isNotEmpty()

        // Cluster events within 5 minutes
        val clusteredEvents = mutableListOf<ClusteredDrinkEvent>()
        for (record in dayRecords) {
            val zonedDateTime = Instant.ofEpochMilli(record.timestamp).atZone(zoneId)
            val hourOfDay = zonedDateTime.hour + zonedDateTime.minute / 60.0f + zonedDateTime.second / 3600.0f
            if (clusteredEvents.isNotEmpty() && (record.timestamp - clusteredEvents.last().timestamp) <= 5 * 60 * 1000L) {
                val last = clusteredEvents.removeAt(clusteredEvents.size - 1)
                clusteredEvents.add(
                    ClusteredDrinkEvent(
                        timestamp = record.timestamp,
                        hourOfDay = hourOfDay,
                        volumeMl = last.volumeMl + record.effectiveVolumeMl
                    )
                )
            } else {
                clusteredEvents.add(
                    ClusteredDrinkEvent(
                        timestamp = record.timestamp,
                        hourOfDay = hourOfDay,
                        volumeMl = record.effectiveVolumeMl
                    )
                )
            }
        }

        val points = when (mode) {
            DailyGraphMode.CUMULATIVE -> {
                val pts = mutableListOf<ChartPoint>()
                // Cumulative mode starts at zero at the beginning of the day (hour 0.0)
                pts.add(
                    ChartPoint(
                        hourOfDay = 0.0f,
                        value = 0.0,
                        displayValue = VolumeConverter.formatVolume(0.0, displayUnit)
                    )
                )
                var runningTotal = 0.0
                for (event in clusteredEvents) {
                    runningTotal += event.volumeMl
                    pts.add(
                        ChartPoint(
                            hourOfDay = event.hourOfDay,
                            value = runningTotal,
                            displayValue = VolumeConverter.formatVolume(runningTotal, displayUnit)
                        )
                    )
                }
                pts
            }

            DailyGraphMode.RATE -> {
                if (clusteredEvents.isEmpty()) {
                    emptyList()
                } else {
                    val pts = mutableListOf<ChartPoint>()
                    val firstEvent = clusteredEvents[0]

                    // Prior point for first entry is waking time (if before first entry) or midnight (0.0)
                    val baseHour = if (wakeHour != null && wakeHour < firstEvent.hourOfDay) {
                        wakeHour
                    } else {
                        0.0f
                    }

                    // Delta time for first entry (minimum 15 mins to avoid infinite spikes)
                    val deltaFirst = (firstEvent.hourOfDay - baseHour).coerceAtLeast(0.25f)
                    val rateFirst = firstEvent.volumeMl / deltaFirst

                    // Initial baseline point at baseHour with 0 rate
                    pts.add(
                        ChartPoint(
                            hourOfDay = baseHour,
                            value = 0.0,
                            displayValue = "${VolumeConverter.formatVolume(0.0, displayUnit)}/h"
                        )
                    )

                    // Point at first entry
                    pts.add(
                        ChartPoint(
                            hourOfDay = firstEvent.hourOfDay,
                            value = rateFirst,
                            displayValue = "${VolumeConverter.formatVolume(rateFirst, displayUnit)}/h"
                        )
                    )

                    // Subsequent entries: average rate between entries
                    for (i in 1 until clusteredEvents.size) {
                        val prev = clusteredEvents[i - 1]
                        val curr = clusteredEvents[i]
                        val delta = (curr.hourOfDay - prev.hourOfDay).coerceAtLeast(0.25f)
                        val rate = curr.volumeMl / delta
                        pts.add(
                            ChartPoint(
                                hourOfDay = curr.hourOfDay,
                                value = rate,
                                displayValue = "${VolumeConverter.formatVolume(rate, displayUnit)}/h"
                            )
                        )
                    }
                    pts
                }
            }
        }

        return DailyLineChartData(
            mode = mode,
            points = points,
            targetVolumeMl = if (hasTarget) targetMl else 0.0,
            hasTarget = hasTarget,
            wakingHoursStart = wakeHour,
            wakingHoursEnd = sleepHour,
            targetRatePerHour = targetRatePerHour,
            displayUnit = displayUnit,
            totalIntakeMl = totalIntakeMl,
            hasEntries = hasEntries
        )
    }
}

