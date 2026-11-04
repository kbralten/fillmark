package com.fillmark.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.domain.PacingCalculator
import com.fillmark.app.domain.PacingResult
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

data class DashboardUiState(
    val effectiveVolumeMl: Double = 0.0,
    val targetMl: Double = 2500.0,
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val presets: List<PresetEntity> = emptyList(),
    val todayRecords: List<HydrationRecordEntity> = emptyList(),
    val pacingResult: PacingResult? = null,
    val isManualLogSheetOpen: Boolean = false,
    val lastLoggedRecord: HydrationRecordEntity? = null,
    val snackbarMessage: String? = null,
    val editingPreset: PresetEntity? = null,
    val isPresetEditorOpen: Boolean = false
)

class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: HydrationRepository = HydrationRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                repository.observeTodaySummary(),
                repository.observePresets(),
                repository.preferencesFlow
            ) { summary, presets, prefs ->
                val pacing = if (prefs.wakingPacingEnabled) {
                    PacingCalculator.evaluatePacing(
                        currentTime = LocalTime.now(),
                        wakeTimeString = prefs.wakingHoursStart,
                        sleepTimeString = prefs.wakingHoursEnd,
                        currentVolumeMl = summary.effectiveVolumeMl,
                        dailyTargetMl = summary.targetMl,
                        displayUnit = prefs.displayUnit
                    )
                } else {
                    null
                }

                _uiState.update { current ->
                    current.copy(
                        effectiveVolumeMl = summary.effectiveVolumeMl,
                        targetMl = summary.targetMl,
                        displayUnit = prefs.displayUnit,
                        presets = presets,
                        todayRecords = summary.records,
                        pacingResult = pacing
                    )
                }
            }.collect {}
        }
    }

    fun logPreset(preset: PresetEntity) {
        viewModelScope.launch {
            val record = repository.logPreset(preset)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            showUndoSnackbar(record, "Added ${preset.label}")
        }
    }

    fun logCustom(volumeMl: Double, hydrationFactor: Double, drinkType: String, timestamp: Long) {
        viewModelScope.launch {
            val record = HydrationRecordEntity(
                timestamp = timestamp,
                volumeMl = volumeMl,
                hydrationFactor = hydrationFactor,
                drinkType = drinkType,
                iconKey = null
            )
            repository.insertRecord(record)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update { it.copy(isManualLogSheetOpen = false) }
            showUndoSnackbar(record, "Logged ${drinkType.lowercase().replaceFirstChar { it.uppercase() }}")
        }
    }

    fun undoLastLog() {
        val last = _uiState.value.lastLoggedRecord ?: return
        viewModelScope.launch {
            repository.deleteRecordById(last.id)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update { it.copy(lastLoggedRecord = null, snackbarMessage = null) }
        }
    }

    private fun showUndoSnackbar(record: HydrationRecordEntity, message: String) {
        undoJob?.cancel()
        _uiState.update { it.copy(lastLoggedRecord = record, snackbarMessage = message) }

        undoJob = viewModelScope.launch {
            delay(4000) // 4 seconds undo window
            _uiState.update {
                if (it.lastLoggedRecord?.id == record.id) {
                    it.copy(lastLoggedRecord = null, snackbarMessage = null)
                } else {
                    it
                }
            }
        }
    }

    fun openManualLogSheet() {
        _uiState.update { it.copy(isManualLogSheetOpen = true) }
    }

    fun closeManualLogSheet() {
        _uiState.update { it.copy(isManualLogSheetOpen = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun openAddPreset() {
        _uiState.update { it.copy(isPresetEditorOpen = true, editingPreset = null) }
    }

    fun openEditPreset(preset: PresetEntity) {
        _uiState.update { it.copy(isPresetEditorOpen = true, editingPreset = preset) }
    }

    fun dismissPresetEditor() {
        _uiState.update { it.copy(isPresetEditorOpen = false, editingPreset = null) }
    }

    fun savePreset(preset: PresetEntity) {
        viewModelScope.launch {
            val existing = _uiState.value.presets.any { it.id == preset.id }
            if (existing) {
                repository.updatePreset(preset)
            } else {
                repository.addPreset(preset)
            }
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            dismissPresetEditor()
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            repository.deletePreset(presetId)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            dismissPresetEditor()
        }
    }
}

