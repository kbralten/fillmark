package com.fillmark.app.core

import androidx.annotation.StringRes
import com.fillmark.app.R

enum class DrinkType(
    val id: String,
    val displayName: String,
    val defaultHydrationFactor: Double,
    @param:StringRes val labelRes: Int
) {
    WATER("WATER", "Water", 1.0, R.string.drink_type_water),
    COFFEE("COFFEE", "Coffee", 0.85, R.string.drink_type_coffee),
    TEA("TEA", "Tea", 0.90, R.string.drink_type_tea),
    JUICE("JUICE", "Juice", 0.85, R.string.drink_type_juice),
    ELECTROLYTE("ELECTROLYTE", "Electrolyte", 1.10, R.string.drink_type_electrolyte),
    SODA("SODA", "Soda", 0.70, R.string.drink_type_soda),
    MILK("MILK", "Milk", 0.90, R.string.drink_type_milk),
    OTHER("OTHER", "Other", 1.0, R.string.drink_type_other);

    companion object {
        fun fromId(id: String): DrinkType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WATER
        }
    }
}
