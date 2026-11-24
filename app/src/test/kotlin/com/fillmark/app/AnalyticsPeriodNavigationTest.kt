package com.fillmark.app

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.domain.DailyGraphCalculator
import com.fillmark.app.domain.DailyGraphMode
import com.fillmark.app.ui.analytics.AnalyticsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AnalyticsPeriodNavigationTest {

    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 7)

    @Test
    fun testPastDayCalculation() {
        val yesterday = today.minusDays(1)
        val yesterdayRecord = HydrationRecordEntity(
            id = "yest1",
            timestamp = yesterday.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli(),
            volumeMl = 500.0,
            hydrationFactor = 1.0,
            drinkType = "Water",
            iconKey = "water_drop",
            lastModified = yesterday.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
        )

        val result = DailyGraphCalculator.calculate(
            records = listOf(yesterdayRecord),
            date = yesterday,
            zoneId = zoneId,
            targetMl = 2500.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = true,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.CUMULATIVE
        )

        assertTrue(result.hasEntries)
        assertFalse(result.isToday)
        assertEquals(500.0, result.totalIntakeMl, 0.01)
        assertEquals(2, result.points.size)
        assertEquals(0.0, result.points[0].value, 0.01)
        assertEquals(500.0, result.points[1].value, 0.01)
        assertEquals(10.0f, result.points[1].hourOfDay, 0.01f)
    }

    @Test
    fun testTodayExcludesPastDayRecords() {
        val yesterday = today.minusDays(1)
        val yesterdayRecord = HydrationRecordEntity(
            id = "yest1",
            timestamp = yesterday.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli(),
            volumeMl = 500.0,
            hydrationFactor = 1.0,
            drinkType = "Water",
            iconKey = "water_drop",
            lastModified = yesterday.atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
        )

        val result = DailyGraphCalculator.calculate(
            records = listOf(yesterdayRecord),
            date = today,
            zoneId = zoneId,
            targetMl = 2500.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = true,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.CUMULATIVE
        )

        // Today has 0 entries because the record was from yesterday
        assertEquals(0.0, result.totalIntakeMl, 0.01)
        assertEquals(1, result.points.size) // only baseline (0,0)
    }

    @Test
    fun testIsTodayFlagForCurrentDate() {
        val realToday = LocalDate.now(zoneId)
        val result = DailyGraphCalculator.calculate(
            records = emptyList(),
            date = realToday,
            zoneId = zoneId,
            targetMl = 2500.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = true,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.CUMULATIVE
        )
        assertTrue(result.isToday)
    }

    @Test
    fun testMultiWeekSummaryCountsActualDaysGoalMetAndPerDayTargets() {
        // 1 week period ending on 2026-11-07 (2026-11-01 through 2026-11-07)
        // Default target 2000 ml, with Nov 3 overridden to 1500 ml
        fun recordOn(date: LocalDate, ml: Double) = HydrationRecordEntity(
            id = date.toString(),
            timestamp = date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli(),
            volumeMl = ml,
            hydrationFactor = 1.0,
            drinkType = "WATER",
            iconKey = null,
            lastModified = 1000L
        )

        val records = listOf(
            recordOn(LocalDate.of(2026, 11, 1), 2200.0), // meets 2000 -> YES
            recordOn(LocalDate.of(2026, 11, 2), 2000.0), // meets 2000 -> YES
            recordOn(LocalDate.of(2026, 11, 3), 1600.0), // meets custom 1500 -> YES
            recordOn(LocalDate.of(2026, 11, 4), 1000.0)  // below 2000 -> NO
        )

        val targetsMap = mapOf(
            LocalDate.of(2026, 11, 3).format(DateTimeFormatter.ISO_LOCAL_DATE) to 1500.0
        )

        val summary = AnalyticsViewModel.computeMultiWeekSummary(
            records = records,
            numWeeks = 1,
            offset = 0,
            today = today,
            zoneId = zoneId,
            defaultTargetMl = 2000.0,
            allTargetsMap = targetsMap
        )

        // Previously `met * 7` would have reported 0 or 7 instead of actual 3 days
        assertEquals(3, summary.daysGoalMet)
        assertEquals(7, summary.totalDays)
        assertEquals(6800.0, summary.totalVolumeMl, 0.01)
        assertEquals(6800.0 / 7.0, summary.averageDailyVolumeMl, 0.01)
        assertEquals(1, summary.bars.size)
        // Average target for the week = (6 * 2000 + 1500) / 7
        assertEquals((6 * 2000.0 + 1500.0) / 7.0, summary.bars[0].targetMl, 0.01)
    }

    @Test
    fun `52-week multi-week summary aggregates ~5000 records accurately`() {
        val zone = java.time.ZoneId.of("UTC")
        val today = java.time.LocalDate.of(2026, 11, 22) // Sunday -> exact 52-week alignment
        val numWeeks = 52
        val totalDays = numWeeks * 7 // 364 days
        val startDate = today.minusDays((totalDays - 1).toLong())

        // Generate 14 records per day (~5,096 records total): alternating 150ml and 160ml -> 2170ml/day on even days, 14*100=1400ml on odd days
        val records = ArrayList<com.fillmark.app.data.local.entity.HydrationRecordEntity>(totalDays * 14)
        var expectedTotalMl = 0.0
        var expectedGoalMetDays = 0
        for (dayIdx in 0 until totalDays) {
            val date = startDate.plusDays(dayIdx.toLong())
            val isGoalMetDay = dayIdx % 2 == 0
            val perRecordMl = if (isGoalMetDay) 155.0 else 100.0 // 14 * 155 = 2170 >= 2000; 14 * 100 = 1400 < 2000
            if (isGoalMetDay) expectedGoalMetDays++
            val baseMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            for (r in 0 until 14) {
                records.add(
                    com.fillmark.app.data.local.entity.HydrationRecordEntity(
                        id = "rec-$dayIdx-$r",
                        timestamp = baseMillis + (r + 1) * 3_600_000L,
                        volumeMl = perRecordMl,
                        hydrationFactor = 1.0,
                        drinkType = "WATER"
                    )
                )
                expectedTotalMl += perRecordMl
            }
        }

        val summary = com.fillmark.app.ui.analytics.AnalyticsViewModel.computeMultiWeekSummary(
            records = records,
            numWeeks = numWeeks,
            offset = 0,
            today = today,
            zoneId = zone,
            defaultTargetMl = 2000.0,
            allTargetsMap = emptyMap(),
            hydrationFactorsEnabled = true
        )

        assertEquals(totalDays, summary.totalDays)
        assertEquals(expectedGoalMetDays, summary.daysGoalMet)
        assertEquals(expectedTotalMl, summary.totalVolumeMl, 0.01)
        assertEquals(expectedTotalMl / totalDays.toDouble(), summary.averageDailyVolumeMl, 0.01)
        assertEquals(numWeeks, summary.bars.size)
        summary.bars.forEach { bar ->
            assertEquals(2000.0, bar.targetMl, 0.01)
        }
    }
}

