package com.fillmark.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import kotlin.math.abs

internal class DailyTargetInputState(
    initialTargetMl: Double,
    initialUnit: DisplayUnit,
    initialHasTarget: Boolean = initialTargetMl > 0.0,
    initialRevision: Int = 0
) {
    var text: String by mutableStateOf(formatTargetForInput(initialTargetMl, initialUnit))
        private set
    var isDirty: Boolean by mutableStateOf(false)
        private set

    private var lastObservedUnit: DisplayUnit = initialUnit
    private var lastObservedHasTarget: Boolean = initialHasTarget
    private var lastObservedTargetMl: Double = initialTargetMl
    private var lastObservedRevision: Int by mutableIntStateOf(initialRevision)

    fun onTextChanged(
        raw: String,
        displayUnit: DisplayUnit,
        onCommit: (Double) -> Unit
    ) {
        text = VolumeConverter.sanitizeDecimalInput(raw)
        isDirty = true
    }

    fun commitIfValid(
        displayUnit: DisplayUnit,
        savedTargetMl: Double,
        onCommit: (Double) -> Unit
    ): Boolean {
        val num = VolumeConverter.parseDecimalInput(text) ?: return false
        val ml = VolumeConverter.toInternalMl(num, displayUnit)
        if (ml <= 0.0 || ml > VolumeConverter.MAX_DAILY_TARGET_ML) return false
        isDirty = false
        if (abs(ml - savedTargetMl) >= 0.001) {
            onCommit(ml)
        }
        return true
    }

    fun markExternalTargetChange(hasTarget: Boolean) {
        lastObservedHasTarget = hasTarget
        isDirty = false
    }

    fun setDisplayText(newText: String) {
        text = newText
        isDirty = false
    }

    fun syncFromExternal(
        defaultTargetMl: Double,
        displayUnit: DisplayUnit,
        hasTarget: Boolean,
        dataRevision: Int = 0
    ): Boolean {
        val unitChanged = lastObservedUnit != displayUnit
        val hasTargetChanged = lastObservedHasTarget != hasTarget
        val targetValueChanged = abs(lastObservedTargetMl - defaultTargetMl) >= 0.001
        val revisionChanged = lastObservedRevision != dataRevision

        if (unitChanged || hasTargetChanged || targetValueChanged || revisionChanged) {
            if (hasTarget) {
                text = VolumeConverter.formatInputNumber(defaultTargetMl, displayUnit)
            }
            lastObservedUnit = displayUnit
            lastObservedHasTarget = hasTarget
            lastObservedTargetMl = defaultTargetMl
            lastObservedRevision = dataRevision
            isDirty = false
            return true
        }
        return false
    }

    companion object {
        fun formatTargetForInput(targetMl: Double, unit: DisplayUnit): String {
            return if (targetMl > 0) {
                VolumeConverter.formatInputNumber(targetMl, unit)
            } else {
                if (unit == DisplayUnit.METRIC_ML) "2500" else "85"
            }
        }
    }
}
