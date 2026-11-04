package com.fillmark.app.core

enum class DrinkType(
    val id: String,
    val displayName: String,
    val defaultHydrationFactor: Double
) {
    WATER("WATER", "Water", 1.0),
    COFFEE("COFFEE", "Coffee", 0.85),
    TEA("TEA", "Tea", 0.90),
    JUICE("JUICE", "Juice", 0.85),
    ELECTROLYTE("ELECTROLYTE", "Electrolyte", 1.10),
    SODA("SODA", "Soda", 0.70),
    MILK("MILK", "Milk", 0.90),
    OTHER("OTHER", "Other", 1.0);

    companion object {
        fun fromId(id: String): DrinkType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WATER
        }
    }
}

