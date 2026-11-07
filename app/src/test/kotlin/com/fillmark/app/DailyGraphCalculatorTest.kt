package com.fillmark.app

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.domain.DailyGraphCalculator
import com.fillmark.app.domain.DailyGraphMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DailyGraphCalculatorTest {

    private val zoneId = ZoneId.of("UTC")
    private val testDate = LocalDate.of(2026, 11, 7)

    private fun timeToMillis(hour: Int, minute: Int): Long {
        return testDate.atTime(LocalTime.of(hour, minute))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun createRecord(id: String, hour: Int, minute: Int, volumeMl: Double): HydrationRecordEntity {
        return HydrationRecordEntity(
            id = id,
            timestamp = timeToMillis(hour, minute),
            volumeMl = volumeMl,
            hydrationFactor = 1.0,
            drinkType = "Water",
            iconKey = "water_drop",
            lastModified = timeToMillis(hour, minute)
        )
    }

    @Test
    fun testCumulativeMode_withEntries() {
        val records = listOf(
            createRecord("1", 9, 0, 300.0),
            createRecord("2", 12, 0, 500.0),
            createRecord("3", 16, 0, 400.0)
        )

        val result = DailyGraphCalculator.calculate(
            records = records,
            date = testDate,
            zoneId = zoneId,
            targetMl = 2400.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = true,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.CUMULATIVE
        )

        assertTrue(result.hasTarget)
        assertEquals(2400.0, result.targetVolumeMl, 0.01)
        assertEquals(7.0f, result.wakingHoursStart ?: 0f, 0.01f)
        assertEquals(23.0f, result.wakingHoursEnd ?: 0f, 0.01f)
        assertEquals(1200.0, result.totalIntakeMl, 0.01)
        assertTrue(result.hasEntries)

        // Points: starts at 0.0, then 3 entries
        assertEquals(4, result.points.size)
        // Point 0: 0h, 0ml
        assertEquals(0.0f, result.points[0].hourOfDay, 0.01f)
        assertEquals(0.0, result.points[0].value, 0.01)
        // Point 1: 9h, 300ml
        assertEquals(9.0f, result.points[1].hourOfDay, 0.01f)
        assertEquals(300.0, result.points[1].value, 0.01)
        // Point 2: 12h, 800ml
        assertEquals(12.0f, result.points[2].hourOfDay, 0.01f)
        assertEquals(800.0, result.points[2].value, 0.01)
        // Point 3: 16h, 1200ml
        assertEquals(16.0f, result.points[3].hourOfDay, 0.01f)
        assertEquals(1200.0, result.points[3].value, 0.01)
    }

    @Test
    fun testCumulativeMode_noEntries() {
        val result = DailyGraphCalculator.calculate(
            records = emptyList(),
            date = testDate,
            zoneId = zoneId,
            targetMl = 2500.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = true,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.CUMULATIVE
        )

        assertTrue(result.hasTarget)
        assertFalse(result.hasEntries)
        assertEquals(1, result.points.size)
        assertEquals(0.0f, result.points[0].hourOfDay, 0.01f)
        assertEquals(0.0, result.points[0].value, 0.01)
    }

    @Test
    fun testCumulativeMode_noWakingHours() {
        val result = DailyGraphCalculator.calculate(
            records = emptyList(),
            date = testDate,
            zoneId = zoneId,
            targetMl = 2400.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = false,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.CUMULATIVE
        )

        assertTrue(result.hasTarget)
        assertNull(result.wakingHoursStart)
        assertNull(result.wakingHoursEnd)
        // Without waking pacing, duration is 24 hours -> targetRate = 2400 / 24 = 100 ml/h
        assertEquals(100.0, result.targetRatePerHour, 0.01)
    }

    @Test
    fun testCumulativeMode_noTarget() {
        val records = listOf(createRecord("1", 10, 0, 500.0))
        val result = DailyGraphCalculator.calculate(
            records = records,
            date = testDate,
            zoneId = zoneId,
            targetMl = 0.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = true,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.CUMULATIVE
        )

        assertFalse(result.hasTarget)
        assertEquals(0.0, result.targetVolumeMl, 0.01)
        assertEquals(0.0, result.targetRatePerHour, 0.01)
    }

    @Test
    fun testRateMode_withEntries() {
        // Waking hours: 07:00 to 23:00 (16 hours)
        // Target: 2400 ml -> 2400 / 16 = 150 ml/h target rate
        val records = listOf(
            createRecord("1", 9, 0, 300.0),  // 2h from wake (7:00) -> 300 / 2 = 150 ml/h
            createRecord("2", 12, 0, 600.0), // 3h from 9:00 -> 600 / 3 = 200 ml/h
            createRecord("3", 16, 0, 200.0)  // 4h from 12:00 -> 200 / 4 = 50 ml/h
        )

        val result = DailyGraphCalculator.calculate(
            records = records,
            date = testDate,
            zoneId = zoneId,
            targetMl = 2400.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = true,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.RATE
        )

        assertTrue(result.hasTarget)
        assertEquals(150.0, result.targetRatePerHour, 0.01)

        // Points:
        // Baseline: 7.0h, 0.0 ml/h
        // Point 1: 9.0h, 150.0 ml/h
        // Point 2: 12.0h, 200.0 ml/h
        // Point 3: 16.0h, 50.0 ml/h
        assertEquals(4, result.points.size)
        assertEquals(7.0f, result.points[0].hourOfDay, 0.01f)
        assertEquals(0.0, result.points[0].value, 0.01)

        assertEquals(9.0f, result.points[1].hourOfDay, 0.01f)
        assertEquals(150.0, result.points[1].value, 0.01)

        assertEquals(12.0f, result.points[2].hourOfDay, 0.01f)
        assertEquals(200.0, result.points[2].value, 0.01)

        assertEquals(16.0f, result.points[3].hourOfDay, 0.01f)
        assertEquals(50.0, result.points[3].value, 0.01)
    }

    @Test
    fun testRateMode_noWakingHours() {
        // No waking hours -> targetRate = target / 24
        val records = listOf(
            createRecord("1", 8, 0, 400.0) // 8h from 00:00 -> 400 / 8 = 50 ml/h
        )

        val result = DailyGraphCalculator.calculate(
            records = records,
            date = testDate,
            zoneId = zoneId,
            targetMl = 2400.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = false,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.RATE
        )

        assertTrue(result.hasTarget)
        assertEquals(100.0, result.targetRatePerHour, 0.01) // 2400 / 24

        assertEquals(2, result.points.size)
        assertEquals(0.0f, result.points[0].hourOfDay, 0.01f)
        assertEquals(0.0, result.points[0].value, 0.01)
        assertEquals(8.0f, result.points[1].hourOfDay, 0.01f)
        assertEquals(50.0, result.points[1].value, 0.01)
    }

    @Test
    fun testRateMode_clusteringWithin5Minutes() {
        val records = listOf(
            createRecord("1", 10, 0, 250.0),
            createRecord("2", 10, 2, 250.0) // 2 minutes later -> clustered to 500ml at 10:02
        )

        val result = DailyGraphCalculator.calculate(
            records = records,
            date = testDate,
            zoneId = zoneId,
            targetMl = 2400.0,
            wakingHoursStart = "07:00",
            wakingHoursEnd = "23:00",
            wakingPacingEnabled = true,
            displayUnit = DisplayUnit.METRIC_ML,
            mode = DailyGraphMode.RATE
        )

        // Only 1 clustered drink event + 1 baseline
        assertEquals(2, result.points.size)
        assertEquals(500.0, result.totalIntakeMl, 0.01)
    }
}

