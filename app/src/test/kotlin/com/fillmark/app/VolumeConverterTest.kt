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

    @Test
    fun testFormatInputNumber() {
        assertEquals("500", VolumeConverter.formatInputNumber(500.0, DisplayUnit.METRIC_ML))
        assertEquals("250", VolumeConverter.formatInputNumber(250.0, DisplayUnit.METRIC_ML))
        assertEquals("12", VolumeConverter.formatInputNumber(12.0 * VolumeConverter.ML_PER_FL_OZ, DisplayUnit.IMPERIAL_FL_OZ))
        assertEquals("8.5", VolumeConverter.formatInputNumber(250.0, DisplayUnit.IMPERIAL_FL_OZ))
    }

    @Test
    fun testConvertInputVolume() {
        // ml to fl oz
        assertEquals("8.5", VolumeConverter.convertInputVolume("250", DisplayUnit.METRIC_ML, DisplayUnit.IMPERIAL_FL_OZ))
        assertEquals("16.9", VolumeConverter.convertInputVolume("500", DisplayUnit.METRIC_ML, DisplayUnit.IMPERIAL_FL_OZ))

        // fl oz to ml
        assertEquals("355", VolumeConverter.convertInputVolume("12", DisplayUnit.IMPERIAL_FL_OZ, DisplayUnit.METRIC_ML))
        assertEquals("500", VolumeConverter.convertInputVolume("16.9", DisplayUnit.IMPERIAL_FL_OZ, DisplayUnit.METRIC_ML))

        // round-trip 12 oz -> 355 ml -> 12 oz
        val toMl = VolumeConverter.convertInputVolume("12", DisplayUnit.IMPERIAL_FL_OZ, DisplayUnit.METRIC_ML)
        assertEquals("355", toMl)
        val backToOz = VolumeConverter.convertInputVolume(toMl, DisplayUnit.METRIC_ML, DisplayUnit.IMPERIAL_FL_OZ)
        assertEquals("12", backToOz)

        // edge cases
        assertEquals("", VolumeConverter.convertInputVolume("", DisplayUnit.METRIC_ML, DisplayUnit.IMPERIAL_FL_OZ))
        assertEquals("0", VolumeConverter.convertInputVolume("0", DisplayUnit.METRIC_ML, DisplayUnit.IMPERIAL_FL_OZ))
        assertEquals("abc", VolumeConverter.convertInputVolume("abc", DisplayUnit.METRIC_ML, DisplayUnit.IMPERIAL_FL_OZ))
        assertEquals("250", VolumeConverter.convertInputVolume("250", DisplayUnit.METRIC_ML, DisplayUnit.METRIC_ML))
    }
}

