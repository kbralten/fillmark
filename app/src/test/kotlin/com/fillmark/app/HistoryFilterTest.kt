package com.fillmark.app

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.ui.history.HistoryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HistoryFilterTest {

    private val zone = ZoneId.of("UTC")

    private fun recordAt(
        id: String,
        date: LocalDate,
        hour: Int,
        volumeMl: Double,
        drinkType: String,
        label: String? = null,
        hydrationFactor: Double = 1.0
    ): HydrationRecordEntity {
        val ts = date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()
        return HydrationRecordEntity(
            id = id,
            timestamp = ts,
            volumeMl = volumeMl,
            hydrationFactor = hydrationFactor,
            drinkType = drinkType,
            label = label
        )
    }

    @Test
    fun filterAndGroupRecords_searchesByLabelDrinkTypeAndVolume() {
        val d1 = LocalDate.of(2026, 11, 23)
        val d2 = LocalDate.of(2026, 11, 22)
        val records = listOf(
            recordAt("1", d1, 8, 350.0, "TEA", label = "Morning Matcha", hydrationFactor = 0.9),
            recordAt("2", d1, 10, 1183.0, "WATER", label = "Stanley 40oz"),
            recordAt("3", d2, 14, 250.0, "COFFEE", label = "Espresso")
        )

        val byLabel = HistoryViewModel.filterAndGroupRecords(
            records = records,
            searchQuery = "matcha",
            selectedDrinkType = null,
            selectedDateIso = null,
            displayUnit = DisplayUnit.METRIC_ML,
            hydrationFactorsEnabled = true,
            zoneId = zone
        )
        assertEquals(1, byLabel.size)
        assertEquals(listOf("1"), byLabel.first().records.map { it.id })

        val byCategory = HistoryViewModel.filterAndGroupRecords(
            records = records,
            searchQuery = "coffee",
            selectedDrinkType = null,
            selectedDateIso = null,
            displayUnit = DisplayUnit.METRIC_ML,
            hydrationFactorsEnabled = true,
            zoneId = zone
        )
        assertEquals(1, byCategory.size)
        assertEquals("3", byCategory.first().records.first().id)

        val byVolume = HistoryViewModel.filterAndGroupRecords(
            records = records,
            searchQuery = "1183",
            selectedDrinkType = null,
            selectedDateIso = null,
            displayUnit = DisplayUnit.METRIC_ML,
            hydrationFactorsEnabled = true,
            zoneId = zone
        )
        assertEquals(1, byVolume.size)
        assertEquals("2", byVolume.first().records.first().id)
    }

    @Test
    fun filterAndGroupRecords_filtersByDrinkTypeAndDateIso() {
        val d1 = LocalDate.of(2026, 11, 23)
        val d2 = LocalDate.of(2026, 11, 22)
        val records = listOf(
            recordAt("1", d1, 8, 250.0, "WATER"),
            recordAt("2", d1, 9, 300.0, "COFFEE"),
            recordAt("3", d2, 11, 500.0, "WATER")
        )

        val waterOnly = HistoryViewModel.filterAndGroupRecords(
            records = records,
            searchQuery = "",
            selectedDrinkType = "WATER",
            selectedDateIso = null,
            displayUnit = DisplayUnit.METRIC_ML,
            hydrationFactorsEnabled = true,
            zoneId = zone
        )
        assertEquals(2, waterOnly.size)
        assertEquals(listOf("1"), waterOnly[0].records.map { it.id })
        assertEquals(listOf("3"), waterOnly[1].records.map { it.id })

        val dateFiltered = HistoryViewModel.filterAndGroupRecords(
            records = records,
            searchQuery = "",
            selectedDrinkType = "WATER",
            selectedDateIso = "2026-11-22",
            displayUnit = DisplayUnit.METRIC_ML,
            hydrationFactorsEnabled = true,
            zoneId = zone
        )
        assertEquals(1, dateFiltered.size)
        assertEquals("2026-11-22", dateFiltered.first().dateIso)
        assertEquals("3", dateFiltered.first().records.first().id)

        val noMatches = HistoryViewModel.filterAndGroupRecords(
            records = records,
            searchQuery = "nonexistent",
            selectedDrinkType = null,
            selectedDateIso = null,
            displayUnit = DisplayUnit.METRIC_ML,
            hydrationFactorsEnabled = true,
            zoneId = zone
        )
        assertTrue(noMatches.isEmpty())
    }
}
