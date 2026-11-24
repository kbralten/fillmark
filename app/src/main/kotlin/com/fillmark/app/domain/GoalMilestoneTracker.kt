package com.fillmark.app.domain

object GoalMilestoneTracker {
    fun isGoalReached(effectiveVolumeMl: Double, targetMl: Double): Boolean {
        return targetMl > 0.0 && effectiveVolumeMl >= targetMl
    }

    fun didCrossDailyGoal(
        previousVolumeMl: Double,
        currentVolumeMl: Double,
        targetMl: Double
    ): Boolean {
        return targetMl > 0.0 && previousVolumeMl < targetMl && currentVolumeMl >= targetMl
    }
}
