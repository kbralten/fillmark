package com.fillmark.app

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeConverterTest {

    @Test
    fun testMlToFlOzConversion() {
        val ml = 29.5735
        val flOz = VolumeConverter.mlToFlOz(ml)
        assertEquals(1.0, flOz, 0.0001)

        val bottle500 = VolumeConverter.mlToFlOz(500.0)
        assertEquals(16.907, bottle500, 0.01)
    }

    @Test
    fun testFlOzToMlConversion() {
        val flOz = 1.0
        val ml = VolumeConverter.flOzToMl(flOz)
        assertEquals(29.5735, ml, 0.0001)

        val flOz16_9 = 16.907
        val mlBack = VolumeConverter.flOzToMl(flOz16_9)
        assertEquals(500.0, mlBack, 0.1)
    }

    @Test
    fun testFormatting() {
        val metricFormatted = VolumeConverter.formatVolume(500.0, DisplayUnit.METRIC_ML)
        assertEquals("500 ml", metricFormatted)

        val imperialFormatted = VolumeConverter.formatVolume(500.0, DisplayUnit.IMPERIAL_FL_OZ, decimals = 1)
        assertEquals("16.9 fl oz", imperialFormatted)
    }

    @Test
    fun testToInternalMl() {
        val mlFromMetric = VolumeConverter.toInternalMl(500.0, DisplayUnit.METRIC_ML)
        assertEquals(500.0, mlFromMetric, 0.001)

        val mlFromImperial = VolumeConverter.toInternalMl(10.0, DisplayUnit.IMPERIAL_FL_OZ)
        assertEquals(295.735, mlFromImperial, 0.001)
    }
}

