package com.fillmark.app

import com.fillmark.app.core.DrinkType
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.domain.AnalyticsInsightsCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsInsightsCalculatorTest {

    private val zoneId = ZoneId.of("UTC")

    private fun record(date: LocalDate, ml: Double, drinkType: String = "WATER", factor: Double = 1.0) =
        HydrationRecordEntity(
            id = "${date}_${drinkType}_$ml",
            timestamp = date.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli(),
            volumeMl = ml,
            hydrationFactor = factor,
            drinkType = drinkType,
            iconKey = null,
            lastModified = 1000L
        )

    @Test
    fun calculateStreaks_keepsYesterdayAliveAndTracksBest() {
        val today = LocalDate.of(2026, 11, 23)
        val records = listOf(
            // Best streak: Nov 15, 16, 17 (3 days)
            record(LocalDate.of(2026, 11, 15), 2500.0),
            record(LocalDate.of(2026, 11, 16), 2600.0),
            record(LocalDate.of(2026, 11, 17), 2500.0),
            // Gap on Nov 18
            // Current streak: Nov 21, 22 (2 days), today Nov 23 is still in progress (500ml)
            record(LocalDate.of(2026, 11, 21), 2500.0),
            record(LocalDate.of(2026, 11, 22), 2500.0),
            record(LocalDate.of(2026, 11, 23), 500.0)
        )

        val stats = AnalyticsInsightsCalculator.calculateStreaks(
            records = records,
            defaultTargetMl = 2500.0,
            allTargetsMap = emptyMap(),
            hydrationFactorsEnabled = true,
            today = today,
            zoneId = zoneId
        )

        assertEquals(2, stats.currentStreak)
        assertEquals(3, stats.bestStreak)
    }

    @Test
    fun calculateStreaks_respectsFactorsToggleAndTodayMet() {
        val today = LocalDate.of(2026, 11, 23)
        val records = listOf(
            // 2000ml soda at 0.7 factor -> 1400 effective, 2000 raw
            record(today, 2000.0, drinkType = "SODA", factor = 0.70)
        )

        val withFactors = AnalyticsInsightsCalculator.calculateStreaks(
            records = records,
            defaultTargetMl = 2000.0,
            allTargetsMap = emptyMap(),
            hydrationFactorsEnabled = true,
            today = today,
            zoneId = zoneId
        )
        assertEquals(0, withFactors.currentStreak)

        val withoutFactors = AnalyticsInsightsCalculator.calculateStreaks(
            records = records,
            defaultTargetMl = 2000.0,
            allTargetsMap = emptyMap(),
            hydrationFactorsEnabled = false,
            today = today,
            zoneId = zoneId
        )
        assertEquals(1, withoutFactors.currentStreak)
    }

    @Test
    fun calculateDrinkBreakdown_sortsAndComputesPercentages() {
        val today = LocalDate.of(2026, 11, 23)
        val records = listOf(
            record(today, 1000.0, drinkType = "WATER", factor = 1.0),
            record(today, 500.0, drinkType = "COFFEE", factor = 0.85) // 425 effective
        )

        val breakdown = AnalyticsInsightsCalculator.calculateDrinkBreakdown(records, hydrationFactorsEnabled = true)
        assertEquals(2, breakdown.size)
        assertEquals(DrinkType.WATER, breakdown[0].drinkType)
        assertEquals(1000.0, breakdown[0].volumeMl, 0.01)
        assertEquals(DrinkType.COFFEE, breakdown[1].drinkType)
        assertEquals(425.0, breakdown[1].volumeMl, 0.01)
        assertEquals(100.0f, breakdown.sumOf { it.percentage.toDouble() }.toFloat(), 0.01f)
    }
}
