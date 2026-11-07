package com.fillmark.app

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.domain.DailyGraphCalculator
import com.fillmark.app.domain.DailyGraphMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

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
}

