package com.fillmark.app

import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetAndFactorTest {

    @Test
    fun displayTitle_prefersPresetOrCustomLabelOverDrinkType() {
        val labeled = HydrationRecordEntity(
            id = "1",
            timestamp = 1700000000000L,
            volumeMl = 350.0,
            drinkType = "TEA",
            label = "Morning Matcha"
        )
        assertEquals("Morning Matcha", labeled.displayTitle())

        val blankLabel = labeled.copy(label = "   ")
        assertEquals("Tea", blankLabel.displayTitle())

        val nullLabel = labeled.copy(label = null, drinkType = "COFFEE")
        assertEquals("Coffee", nullLabel.displayTitle())
    }

    @Test
    fun hydrationFactorValidation_andFormatting_workAsExpected() {
        assertTrue(VolumeConverter.isValidHydrationFactor(0.1))
        assertTrue(VolumeConverter.isValidHydrationFactor(1.0))
        assertTrue(VolumeConverter.isValidHydrationFactor(2.0))
        assertFalse(VolumeConverter.isValidHydrationFactor(0.05))
        assertFalse(VolumeConverter.isValidHydrationFactor(2.5))
        assertFalse(VolumeConverter.isValidHydrationFactor(Double.NaN))

        assertEquals("1.0", VolumeConverter.formatHydrationFactor(1.0))
        assertEquals("0.85", VolumeConverter.formatHydrationFactor(0.85))
        assertEquals("1.1", VolumeConverter.formatHydrationFactor(1.10))
    }
}
