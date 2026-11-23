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
}

