package com.fillmark.app

import com.fillmark.app.widget.MidnightAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class MidnightAlarmSchedulerTest {

    @Test
    fun calculateNextMidnightMillis_isTomorrowMidnight() {
        val zoneId = ZoneId.of("America/New_York")
        val midnightMillis = MidnightAlarmScheduler.calculateNextMidnightMillis(zoneId)

        val scheduledZdt = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(midnightMillis),
            zoneId
        )

        val today = LocalDate.now(zoneId)
        val expectedTomorrow = today.plusDays(1)

        assertEquals(expectedTomorrow, scheduledZdt.toLocalDate())
        assertEquals(0, scheduledZdt.hour)
        assertEquals(0, scheduledZdt.minute)
        assertEquals(0, scheduledZdt.second)
        assertTrue(midnightMillis > System.currentTimeMillis())
    }
}
