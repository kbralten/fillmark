package com.fillmark.app

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
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
    fun testHydrationRecordWithMetadata() {
        val instant = Instant.ofEpochMilli(1700000000000L)
        val startTime = instant.minusSeconds(1)
        val endTime = instant
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)

        val record = HydrationRecord(
            startTime = startTime,
            startZoneOffset = zoneOffset,
            endTime = endTime,
            endZoneOffset = zoneOffset,
            volume = Volume.milliliters(250.0),
            metadata = Metadata(id = "test-id")
        )

        assertEquals("test-id", record.metadata.id)
    }

    @Test
    fun testClientMethodsReflected() {
        val methods = HealthConnectClient::class.java.methods
        val updateMethod = methods.find { it.name == "updateRecords" }
        val deleteMethod = methods.find { it.name == "deleteRecords" }
        assertNotNull(updateMethod)
        assertNotNull(deleteMethod)
    }

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
    fun testHydrationRecordForPastTime() {
        // Simulating overriding to a past date/time (e.g., 3 days ago)
        val pastInstant = Instant.now().minus(3, ChronoUnit.DAYS)
        val endTime = pastInstant
        val startTime = endTime.minusSeconds(1)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)

        val record = HydrationRecord(
            startTime = startTime,
            startZoneOffset = zoneOffset,
            endTime = endTime,
            endZoneOffset = zoneOffset,
            volume = Volume.milliliters(500.0)
        )

        assertNotNull(record)
        assertEquals(500.0, record.volume.inMilliliters, 0.001)
        assert(record.startTime.isBefore(record.endTime))
        assert(record.endTime.isBefore(Instant.now()))
    }

    @Test
    fun testHydrationRecordUpdateMapping() {
        // Simulating an edit of an existing record that already has a Health Connect ID
        val originalHcId = "hc-uuid-12345"
        val updatedPastInstant = Instant.now().minus(1, ChronoUnit.DAYS)
        val endTime = updatedPastInstant
        val startTime = endTime.minusSeconds(1)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)

        val updatedRecord = HydrationRecord(
            startTime = startTime,
            startZoneOffset = zoneOffset,
            endTime = endTime,
            endZoneOffset = zoneOffset,
            volume = Volume.milliliters(750.0),
            metadata = Metadata(id = originalHcId)
        )

        assertEquals(originalHcId, updatedRecord.metadata.id)
        assertEquals(750.0, updatedRecord.volume.inMilliliters, 0.001)
        assertEquals(updatedPastInstant, updatedRecord.endTime)
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
