package com.fillmark.app

import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.ui.settings.DailyTargetInputState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAndStartupTest {

    @Test
    fun bug9_dailyTargetInput_doesNotCommitOnEveryKeystroke_andCommitsOnceOnApply() {
        val state = DailyTargetInputState(
            initialTargetMl = 2500.0,
            initialUnit = DisplayUnit.METRIC_ML,
            initialHasTarget = true
        )
        val committedValues = mutableListOf<Double>()

        state.onTextChanged("3", DisplayUnit.METRIC_ML) { committedValues.add(it) }
        state.onTextChanged("30", DisplayUnit.METRIC_ML) { committedValues.add(it) }
        state.onTextChanged("300", DisplayUnit.METRIC_ML) { committedValues.add(it) }
        state.onTextChanged("3000", DisplayUnit.METRIC_ML) { committedValues.add(it) }

        assertTrue(
            "Typing mid-edit should NOT commit on every keystroke, but committed: $committedValues",
            committedValues.isEmpty()
        )
        assertTrue(state.isDirty)

        val committed = state.commitIfValid(
            displayUnit = DisplayUnit.METRIC_ML,
            savedTargetMl = 2500.0
        ) { committedValues.add(it) }

        assertTrue(committed)
        assertEquals(listOf(3000.0), committedValues)
        assertFalse(state.isDirty)
    }

    @Test
    fun bug9_dailyTargetInput_refreshesWhenExternalTargetChangesAfterRestoreOrClear() {
        val state = DailyTargetInputState(
            initialTargetMl = 2500.0,
            initialUnit = DisplayUnit.METRIC_ML,
            initialHasTarget = true
        )
        assertEquals("2500", state.text)

        // Simulate JSON restore changing defaultTargetMl from 2500 -> 3200 in the same unit
        val refreshedAfterRestore = state.syncFromExternal(
            defaultTargetMl = 3200.0,
            displayUnit = DisplayUnit.METRIC_ML,
            hasTarget = true,
            dataRevision = 1
        )
        assertTrue("Expected target input to refresh after backup restore", refreshedAfterRestore)
        assertEquals("3200", state.text)

        // Simulate Clear All Local Data resetting defaultTargetMl 3200 -> 2500
        val refreshedAfterClear = state.syncFromExternal(
            defaultTargetMl = 2500.0,
            displayUnit = DisplayUnit.METRIC_ML,
            hasTarget = true,
            dataRevision = 2
        )
        assertTrue("Expected target input to refresh after clearAllData", refreshedAfterClear)
        assertEquals("2500", state.text)
    }

    @Test
    fun bug11_initialCompositionPrefs_doesNotRaceWithWrongUnitOrOnboardingFlag() = runTest {
        val usFreshPrefs = UserPreferences(
            displayUnit = DisplayUnit.IMPERIAL_FL_OZ,
            onboardingCompleted = false
        )
        val resolved = MainActivity.resolveInitialPreferences(
            loadedPrefs = usFreshPrefs,
            hasAnyRecords = { false },
            markOnboardingCompleted = {}
        )
        val firstComposed = MainActivity.initialCompositionPrefs(resolved)

        assertFalse(
            "Fresh install must not compose MainNavigation with onboardingCompleted=true",
            firstComposed.onboardingCompleted
        )
        assertEquals(
            "US locale fresh install must preserve IMPERIAL_FL_OZ on first composition",
            DisplayUnit.IMPERIAL_FL_OZ,
            firstComposed.displayUnit
        )

        var markedCompleted = false
        val upgradingPrefs = MainActivity.resolveInitialPreferences(
            loadedPrefs = usFreshPrefs,
            hasAnyRecords = { true },
            markOnboardingCompleted = { markedCompleted = true }
        )
        assertTrue(markedCompleted)
        assertTrue(upgradingPrefs.onboardingCompleted)
    }
}
