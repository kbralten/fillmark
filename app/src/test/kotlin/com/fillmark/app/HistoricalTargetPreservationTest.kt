package com.fillmark.app

import com.fillmark.app.data.repository.HydrationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoricalTargetPreservationTest {

    @Test
    fun computeHistoricalTargetFreezeDates_nullEarliest_returnsEmpty() {
        val result = HydrationRepository.computeHistoricalTargetFreezeDates(
            earliestDate = null,
            today = LocalDate.of(2026, 11, 23)
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun computeHistoricalTargetFreezeDates_earliestIsToday_returnsEmpty() {
        val today = LocalDate.of(2026, 11, 23)
        val result = HydrationRepository.computeHistoricalTargetFreezeDates(
            earliestDate = today,
            today = today
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun computeHistoricalTargetFreezeDates_pastDays_excludesToday() {
        val earliest = LocalDate.of(2026, 11, 20)
        val today = LocalDate.of(2026, 11, 23)
        val result = HydrationRepository.computeHistoricalTargetFreezeDates(
            earliestDate = earliest,
            today = today
        )
        assertEquals(
            listOf("2026-11-20", "2026-11-21", "2026-11-22"),
            result
        )
    }
}
