package com.fillmark.app.core

import java.util.Locale
import kotlin.math.roundToInt

enum class DisplayUnit(val label: String, val symbol: String) {
    METRIC_ML("Milliliters", "ml"),
    IMPERIAL_FL_OZ("Fluid Ounces", "fl oz")
}

object VolumeConverter {
    const val ML_PER_FL_OZ = 29.5735

    fun mlToFlOz(ml: Double): Double {
        return ml / ML_PER_FL_OZ
    }

    fun flOzToMl(flOz: Double): Double {
        return flOz * ML_PER_FL_OZ
    }

    /**
     * Converts an internal milliliter amount to the user's active display unit value.
     */
    fun toDisplayValue(ml: Double, unit: DisplayUnit): Double {
        return when (unit) {
            DisplayUnit.METRIC_ML -> ml
            DisplayUnit.IMPERIAL_FL_OZ -> mlToFlOz(ml)
        }
    }

    /**
     * Converts a user entered value in their active unit to internal milliliters.
     */
    fun toInternalMl(value: Double, unit: DisplayUnit): Double {
        return when (unit) {
            DisplayUnit.METRIC_ML -> value
            DisplayUnit.IMPERIAL_FL_OZ -> flOzToMl(value)
        }
    }

    /**
     * Formats an internal ml value with its unit symbol (e.g. "500 ml" or "16.9 fl oz").
     */
    fun formatVolume(ml: Double, unit: DisplayUnit, decimals: Int = 1): String {
        return when (unit) {
            DisplayUnit.METRIC_ML -> "${ml.roundToInt()} ml"
            DisplayUnit.IMPERIAL_FL_OZ -> {
                val flOz = mlToFlOz(ml)
                String.format(Locale.US, "%.${decimals}f fl oz", flOz)
            }
        }
    }

    fun formatNumberOnly(ml: Double, unit: DisplayUnit, decimals: Int = 1): String {
        return when (unit) {
            DisplayUnit.METRIC_ML -> "${ml.roundToInt()}"
            DisplayUnit.IMPERIAL_FL_OZ -> {
                val flOz = mlToFlOz(ml)
                String.format(Locale.US, "%.${decimals}f", flOz)
            }
        }
    }

    /**
     * Formats a volume number cleanly for text inputs, omitting unnecessary trailing ".0".
     */
    fun formatInputNumber(ml: Double, unit: DisplayUnit): String {
        return when (unit) {
            DisplayUnit.METRIC_ML -> "${ml.roundToInt()}"
            DisplayUnit.IMPERIAL_FL_OZ -> {
                val flOz = mlToFlOz(ml)
                val formatted = String.format(Locale.US, "%.1f", flOz)
                if (formatted.endsWith(".0")) formatted.dropLast(2) else formatted
            }
        }
    }

    /**
     * Converts an entered volume string from one display unit to another, preserving clean input formatting.
     */
    fun convertInputVolume(volumeText: String, fromUnit: DisplayUnit, toUnit: DisplayUnit): String {
        if (fromUnit == toUnit) return volumeText
        val num = volumeText.toDoubleOrNull() ?: return volumeText
        if (num <= 0.0) return volumeText
        val internalMl = toInternalMl(num, fromUnit)
        return formatInputNumber(internalMl, toUnit)
    }
}

