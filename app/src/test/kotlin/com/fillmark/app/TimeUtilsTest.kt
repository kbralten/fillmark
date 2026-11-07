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

    @Test
    fun testFormatTimeString() {
        val morning = TimeUtils.formatTimeString("07:00")
        assertEquals("7:00 AM", morning)

        val night = TimeUtils.formatTimeString("23:00")
        assertEquals("11:00 PM", night)
    }

    @Test
    fun testLocalDateAndLocalTimeConversion() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 11, 7)
        val time = java.time.LocalTime.of(14, 30)

        val millis = TimeUtils.toEpochMillis(date, time, zone)
        val extractedDate = TimeUtils.toLocalDate(millis, zone)
        val extractedTime = TimeUtils.toLocalTime(millis, zone)

        assertEquals(date, extractedDate)
        assertEquals(time, extractedTime)
    }

    @Test
    fun testUtcMillisConversion() {
        val date = LocalDate.of(2026, 11, 7)
        val utcMillis = TimeUtils.localDateToUtcMillis(date)
        val reconstructed = TimeUtils.utcMillisToLocalDate(utcMillis)

        assertEquals(date, reconstructed)
    }

    @Test
    fun testFormatDateTime() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val pastDate = LocalDate.of(2025, 5, 12)
        val time = java.time.LocalTime.of(15, 45)

        val todayMillis = TimeUtils.toEpochMillis(today, time, zone)
        val yesterdayMillis = TimeUtils.toEpochMillis(yesterday, time, zone)
        val pastMillis = TimeUtils.toEpochMillis(pastDate, time, zone)

        assertEquals("Today at 3:45 PM", TimeUtils.formatDateTime(todayMillis, zone))
        assertEquals("Yesterday at 3:45 PM", TimeUtils.formatDateTime(yesterdayMillis, zone))
        assertEquals("May 12, 2025 at 3:45 PM", TimeUtils.formatDateTime(pastMillis, zone))
    }
}

