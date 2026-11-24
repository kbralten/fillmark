package com.fillmark.app.core

import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

enum class DisplayUnit(val label: String, val symbol: String) {
    METRIC_ML("Milliliters", "ml"),
    IMPERIAL_FL_OZ("Fluid Ounces", "fl oz")
}

object VolumeConverter {
    const val ML_PER_FL_OZ = 29.5735
    const val MAX_SINGLE_DRINK_ML = 10_000.0
    const val MAX_DAILY_TARGET_ML = 20_000.0
    const val MIN_HYDRATION_FACTOR = 0.1
    const val MAX_HYDRATION_FACTOR = 2.0

    fun decimalSeparator(locale: Locale = Locale.getDefault()): Char {
        return DecimalFormatSymbols.getInstance(locale).decimalSeparator
    }

    fun parseDecimalInput(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        return normalized.toDoubleOrNull()
    }

    fun isValidHydrationFactor(factor: Double): Boolean {
        return factor.isFinite() && factor in MIN_HYDRATION_FACTOR..MAX_HYDRATION_FACTOR
    }

    fun formatHydrationFactor(factor: Double, locale: Locale = Locale.getDefault()): String {
        val rounded = (factor * 100.0).roundToInt() / 100.0
        val sep = decimalSeparator(locale)
        val raw = String.format(locale, "%.2f", rounded)
        return if (raw.endsWith("0") && !raw.endsWith("${sep}0")) {
            raw.dropLast(1)
        } else {
            raw
        }
    }

    /**
     * Filters raw user text to digits and at most one locale-aware decimal separator.
     */
    fun sanitizeDecimalInput(raw: String, locale: Locale = Locale.getDefault()): String {
        val sep = decimalSeparator(locale)
        val sb = StringBuilder(raw.length)
        var seenSeparator = false
        for (c in raw) {
            if (c.isDigit()) {
                sb.append(c)
            } else if ((c == '.' || c == ',' || c == sep) && !seenSeparator) {
                sb.append(sep)
                seenSeparator = true
            }
        }
        return sb.toString()
    }

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
    fun formatVolume(
        ml: Double,
        unit: DisplayUnit,
        decimals: Int = 1,
        locale: Locale = Locale.getDefault()
    ): String {
        return when (unit) {
            DisplayUnit.METRIC_ML -> "${ml.roundToInt()} ml"
            DisplayUnit.IMPERIAL_FL_OZ -> {
                val flOz = mlToFlOz(ml)
                String.format(locale, "%.${decimals}f fl oz", flOz)
            }
        }
    }

    fun formatNumberOnly(
        ml: Double,
        unit: DisplayUnit,
        decimals: Int = 1,
        locale: Locale = Locale.getDefault()
    ): String {
        return when (unit) {
            DisplayUnit.METRIC_ML -> "${ml.roundToInt()}"
            DisplayUnit.IMPERIAL_FL_OZ -> {
                val flOz = mlToFlOz(ml)
                String.format(locale, "%.${decimals}f", flOz)
            }
        }
    }

    /**
     * Formats a volume number cleanly for text inputs, omitting unnecessary trailing ".0" / ",0".
     */
    fun formatInputNumber(
        ml: Double,
        unit: DisplayUnit,
        locale: Locale = Locale.getDefault()
    ): String {
        return when (unit) {
            DisplayUnit.METRIC_ML -> "${ml.roundToInt()}"
            DisplayUnit.IMPERIAL_FL_OZ -> {
                val flOz = mlToFlOz(ml)
                val formatted = String.format(locale, "%.1f", flOz)
                val zeroSuffix = "${decimalSeparator(locale)}0"
                if (formatted.endsWith(zeroSuffix)) formatted.dropLast(2) else formatted
            }
        }
    }

    /**
     * Converts an entered volume string from one display unit to another, preserving clean input formatting.
     */
    fun convertInputVolume(
        volumeText: String,
        fromUnit: DisplayUnit,
        toUnit: DisplayUnit,
        locale: Locale = Locale.getDefault()
    ): String {
        if (fromUnit == toUnit) return volumeText
        val num = parseDecimalInput(volumeText) ?: return volumeText
        if (num <= 0.0) return volumeText
        val internalMl = toInternalMl(num, fromUnit)
        return formatInputNumber(internalMl, toUnit, locale)
    }
}
