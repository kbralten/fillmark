package com.fillmark.app

import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectTest {
    @Test
    fun testHydrationRecordValidInterval() {
        val instant = Instant.ofEpochMilli(1700000000000L)
        val startTime = instant.minusSeconds(1)
        val endTime = instant
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)

        val record = HydrationRecord(
            startTime = startTime,
            startZoneOffset = zoneOffset,
            endTime = endTime,
            endZoneOffset = zoneOffset,
            volume = Volume.milliliters(250.0)
        )

        assertNotNull(record)
        assertEquals(250.0, record.volume.inMilliliters, 0.001)
        assertEquals(startTime, record.startTime)
        assertEquals(endTime, record.endTime)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testHydrationRecordThrowsWhenStartTimeEqualsEndTime() {
        val instant = Instant.ofEpochMilli(1700000000000L)
        HydrationRecord(
            startTime = instant,
            startZoneOffset = null,
            endTime = instant,
            endZoneOffset = null,
            volume = Volume.milliliters(250.0)
        )
    }

    @Test
    fun testReadRecordsRequest() {
        val request = ReadRecordsRequest(
            recordType = HydrationRecord::class,
            timeRangeFilter = TimeRangeFilter.after(Instant.now().minus(30, ChronoUnit.DAYS))
        )
        assertNotNull(request)
    }
}
