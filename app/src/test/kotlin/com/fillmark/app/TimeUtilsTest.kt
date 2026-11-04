package com.fillmark.app

import com.fillmark.app.core.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimeUtilsTest {

    @Test
    fun testDayBoundaries() {
        val date = LocalDate.of(2026, 11, 4)
        val zone = ZoneId.of("UTC")

        val start = TimeUtils.startOfDayMillis(date, zone)
        val end = TimeUtils.endOfDayMillis(date, zone)

        // 24 hours in millis = 86,400,000 ms. Difference between end and start should be 86,399,999 ms
        assertEquals(86400000L - 1, end - start)
    }

    @Test
    fun testIsoDateConversion() {
        val date = LocalDate.of(2026, 11, 4)
        val zone = ZoneId.of("UTC")
        val millis = TimeUtils.startOfDayMillis(date, zone)

        val iso = TimeUtils.toIsoDate(millis, zone)
        assertEquals("2026-11-04", iso)
    }

    @Test
    fun testParseTimeString() {
        val time = TimeUtils.parseTimeString("08:45")
        assertEquals(8, time.hour)
        assertEquals(45, time.minute)
    }
}

