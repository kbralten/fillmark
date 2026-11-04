package com.fillmark.app

import com.fillmark.app.domain.PacingCalculator
import com.fillmark.app.domain.PacingStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class PacingCalculatorTest {

    private val wake = LocalTime.of(7, 0)
    private val sleep = LocalTime.of(23, 0)

    @Test
    fun testPacingAtWakeTime() {
        val pacing = PacingCalculator.calculateTargetPacingPercent(wake, wake, sleep)
        assertEquals(0f, pacing, 0.01f)
    }

    @Test
    fun testPacingAtSleepTime() {
        val pacing = PacingCalculator.calculateTargetPacingPercent(sleep, wake, sleep)
        assertEquals(100f, pacing, 0.01f)
    }

    @Test
    fun testPacingMidday() {
        // 7:00 to 23:00 is 16 hours. Midpoint is 7 + 8 = 15:00
        val midday = LocalTime.of(15, 0)
        val pacing = PacingCalculator.calculateTargetPacingPercent(midday, wake, sleep)
        assertEquals(50f, pacing, 0.01f)
    }

    @Test
    fun testPacingBeforeWakeTime() {
        val early = LocalTime.of(5, 30)
        val pacing = PacingCalculator.calculateTargetPacingPercent(early, wake, sleep)
        assertEquals(0f, pacing, 0.01f)
    }

    @Test
    fun testPacingStatusAhead() {
        // At 15:00 (50% expected), target is 2000ml, actual is 1300ml (65% -> +15% ahead)
        val result = PacingCalculator.evaluatePacing(
            currentTime = LocalTime.of(15, 0),
            wakeTimeString = "07:00",
            sleepTimeString = "23:00",
            currentVolumeMl = 1300.0,
            dailyTargetMl = 2000.0
        )
        assertEquals(PacingStatus.AHEAD, result.status)
        assertEquals(50f, result.targetPacingPercent, 0.01f)
        assertEquals(65f, result.actualProgressPercent, 0.01f)
    }

    @Test
    fun testPacingStatusBehind() {
        // At 15:00 (50% expected = 1000ml), actual is 600ml (deficit = 400ml)
        val result = PacingCalculator.evaluatePacing(
            currentTime = LocalTime.of(15, 0),
            wakeTimeString = "07:00",
            sleepTimeString = "23:00",
            currentVolumeMl = 600.0,
            dailyTargetMl = 2000.0
        )
        assertEquals(PacingStatus.BEHIND, result.status)
        assertEquals("Behind pace by 400 ml", result.statusText)
    }

    @Test
    fun testPacingStatusOnTrack() {
        // At 15:00 (50% expected), target is 2000ml, actual is 1020ml (51% -> on track)
        val result = PacingCalculator.evaluatePacing(
            currentTime = LocalTime.of(15, 0),
            wakeTimeString = "07:00",
            sleepTimeString = "23:00",
            currentVolumeMl = 1020.0,
            dailyTargetMl = 2000.0
        )
        assertEquals(PacingStatus.ON_TRACK, result.status)
    }

    @Test
    fun testPacingOver100Percent() {
        // Target is 2000ml, actual is 2500ml (125%)
        val result = PacingCalculator.evaluatePacing(
            currentTime = LocalTime.of(20, 0),
            wakeTimeString = "07:00",
            sleepTimeString = "23:00",
            currentVolumeMl = 2500.0,
            dailyTargetMl = 2000.0
        )
        assertEquals(125f, result.actualProgressPercent, 0.01f)
        assertEquals(PacingStatus.AHEAD, result.status)
    }

    @Test
    fun testPacingNoTarget() {
        val result = PacingCalculator.evaluatePacing(
            currentTime = LocalTime.of(15, 0),
            wakeTimeString = "07:00",
            sleepTimeString = "23:00",
            currentVolumeMl = 1500.0,
            dailyTargetMl = 0.0
        )
        assertEquals(0f, result.actualProgressPercent, 0.01f)
    }

    @Test
    fun testTimeUtilsFriendlyDate() {
        val now = System.currentTimeMillis()
        val iso = com.fillmark.app.core.TimeUtils.toIsoDate(now)
        val friendly = com.fillmark.app.core.TimeUtils.formatFriendlyDate(iso)
        assertEquals("Today", friendly)
    }
}

