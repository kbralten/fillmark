package com.fillmark.app

import com.fillmark.app.domain.ActivityLevel
import com.fillmark.app.domain.DailyGoalEstimator
import com.fillmark.app.domain.GoalMilestoneTracker
import com.fillmark.app.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyGoalEstimatorTest {

    @Test
    fun estimateDailyTargetMl_calculatesAndRoundsToNearest50() {
        // 70kg * 33 = 2310 -> rounds to 2300 ml
        assertEquals(
            2300.0,
            DailyGoalEstimator.estimateDailyTargetMl(70.0, WeightUnit.KG, ActivityLevel.LOW),
            0.001
        )
        // 70kg * 33 + 350 + 300 = 2960 -> rounds to 2950 ml
        assertEquals(
            2950.0,
            DailyGoalEstimator.estimateDailyTargetMl(
                weight = 70.0,
                weightUnit = WeightUnit.KG,
                activityLevel = ActivityLevel.MODERATE,
                warmClimate = true
            ),
            0.001
        )
    }

    @Test
    fun estimateDailyTargetMl_supportsPoundsAndClampsBounds() {
        val lbsEstimate = DailyGoalEstimator.estimateDailyTargetMl(
            weight = 155.0,
            weightUnit = WeightUnit.LB,
            activityLevel = ActivityLevel.HIGH
        )
        assertTrue(lbsEstimate in DailyGoalEstimator.MIN_ESTIMATE_ML..DailyGoalEstimator.MAX_ESTIMATE_ML)
        assertEquals(0.0, lbsEstimate % 50.0, 0.001)

        assertEquals(
            DailyGoalEstimator.MIN_ESTIMATE_ML,
            DailyGoalEstimator.estimateDailyTargetMl(10.0, WeightUnit.KG, ActivityLevel.LOW),
            0.001
        )
        assertEquals(
            DailyGoalEstimator.MAX_ESTIMATE_ML,
            DailyGoalEstimator.estimateDailyTargetMl(250.0, WeightUnit.KG, ActivityLevel.HIGH, warmClimate = true),
            0.001
        )
    }

    @Test
    fun goalMilestoneTracker_detectsGoalReachedAndCrossing() {
        assertFalse(GoalMilestoneTracker.isGoalReached(2500.0, 0.0))
        assertFalse(GoalMilestoneTracker.isGoalReached(2499.0, 2500.0))
        assertTrue(GoalMilestoneTracker.isGoalReached(2500.0, 2500.0))

        assertTrue(GoalMilestoneTracker.didCrossDailyGoal(2250.0, 2500.0, 2500.0))
        assertFalse(GoalMilestoneTracker.didCrossDailyGoal(2500.0, 2750.0, 2500.0))
        assertFalse(GoalMilestoneTracker.didCrossDailyGoal(1800.0, 2000.0, 2500.0))
    }
}
