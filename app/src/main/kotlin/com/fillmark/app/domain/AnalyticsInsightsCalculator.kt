package com.fillmark.app.domain

import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class StreakStats(
    val currentStreak: Int,
    val bestStreak: Int
)

data class DrinkTypeBreakdownItem(
    val drinkType: DrinkType,
    val volumeMl: Double,
    val percentage: Float
)

object AnalyticsInsightsCalculator {

    fun calculateStreaks(
        records: List<HydrationRecordEntity>,
        defaultTargetMl: Double,
        allTargetsMap: Map<String, Double>,
        hydrationFactorsEnabled: Boolean = true,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StreakStats {
        if (records.isEmpty()) return StreakStats(0, 0)

        val dailyTotals = mutableMapOf<LocalDate, Double>()
        var earliestDate: LocalDate? = null
        for (record in records) {
            val date = TimeUtils.toLocalDate(record.timestamp, zoneId)
            if (date.isAfter(today)) continue
            dailyTotals[date] = (dailyTotals[date] ?: 0.0) + record.countedVolumeMl(hydrationFactorsEnabled)
            if (earliestDate == null || date.isBefore(earliestDate)) {
                earliestDate = date
            }
        }

        val start = earliestDate ?: return StreakStats(0, 0)
        var cursor = start
        var runningStreak = 0
        var runningBeforeToday = 0
        var bestStreak = 0
        var todayMet = false

        while (!cursor.isAfter(today)) {
            val dayIso = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val target = allTargetsMap[dayIso] ?: defaultTargetMl
            val total = dailyTotals[cursor] ?: 0.0
            val met = target > 0.0 && total >= target

            if (cursor == today) {
                runningBeforeToday = runningStreak
                todayMet = met
            }

            if (met) {
                runningStreak++
                if (runningStreak > bestStreak) bestStreak = runningStreak
            } else {
                runningStreak = 0
            }
            cursor = cursor.plusDays(1)
        }

        val currentStreak = if (todayMet) runningStreak else runningBeforeToday
        return StreakStats(currentStreak = currentStreak, bestStreak = bestStreak)
    }

    fun calculateDrinkBreakdown(
        records: List<HydrationRecordEntity>,
        hydrationFactorsEnabled: Boolean = true
    ): List<DrinkTypeBreakdownItem> {
        if (records.isEmpty()) return emptyList()

        val totalsByType = mutableMapOf<DrinkType, Double>()
        var grandTotal = 0.0
        for (record in records) {
            val type = DrinkType.fromId(record.drinkType)
            val vol = record.countedVolumeMl(hydrationFactorsEnabled)
            if (vol > 0.0) {
                totalsByType[type] = (totalsByType[type] ?: 0.0) + vol
                grandTotal += vol
            }
        }

        if (grandTotal <= 0.0) return emptyList()
        return totalsByType.entries
            .sortedByDescending { it.value }
            .map { (type, vol) ->
                DrinkTypeBreakdownItem(
                    drinkType = type,
                    volumeMl = vol,
                    percentage = ((vol / grandTotal) * 100.0).toFloat()
                )
            }
    }
}
