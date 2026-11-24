package com.fillmark.app.domain

import kotlin.math.roundToInt

enum class WeightUnit {
    KG,
    LB
}

enum class ActivityLevel(val bonusMl: Double) {
    LOW(0.0),
    MODERATE(350.0),
    HIGH(700.0)
}

object DailyGoalEstimator {
    const val MIN_ESTIMATE_ML = 1200.0
    const val MAX_ESTIMATE_ML = 5000.0
    const val ML_PER_KG = 33.0
    const val WARM_CLIMATE_BONUS_ML = 300.0
    const val KG_PER_LB = 0.45359237

    fun toKg(weight: Double, unit: WeightUnit): Double {
        return when (unit) {
            WeightUnit.KG -> weight
            WeightUnit.LB -> weight * KG_PER_LB
        }
    }

    fun estimateDailyTargetMl(
        weight: Double,
        weightUnit: WeightUnit,
        activityLevel: ActivityLevel,
        warmClimate: Boolean = false
    ): Double {
        if (!weight.isFinite() || weight <= 0.0) return MIN_ESTIMATE_ML
        val weightKg = toKg(weight, weightUnit)
        val rawMl = weightKg * ML_PER_KG +
            activityLevel.bonusMl +
            if (warmClimate) WARM_CLIMATE_BONUS_ML else 0.0
        val roundedTo50 = ((rawMl / 50.0).roundToInt() * 50).toDouble()
        return roundedTo50.coerceIn(MIN_ESTIMATE_ML, MAX_ESTIMATE_ML)
    }
}
