package com.fillmark.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.domain.PacingCalculator
import com.fillmark.app.domain.PacingResult
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class DashboardUiState(
    val dateHeader: String = TimeUtils.formatCurrentDateHeader(),
    val effectiveVolumeMl: Double = 0.0,
    val targetMl: Double = 2500.0,
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val presets: List<PresetEntity> = emptyList(),
    val todayRecords: List<HydrationRecordEntity> = emptyList(),
    val pacingResult: PacingResult? = null,
    val isManualLogSheetOpen: Boolean = false,
    val editingRecord: HydrationRecordEntity? = null,
    val lastLoggedRecord: HydrationRecordEntity? = null,
    val recentlyDeletedRecord: HydrationRecordEntity? = null,
    val snackbarMessage: String? = null,
    val editingPreset: PresetEntity? = null,
    val isPresetEditorOpen: Boolean = false,
    val isCustomizePresetsOpen: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: HydrationRepository = HydrationRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val currentTimeTicker = MutableStateFlow(ZonedDateTime.now())
    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                currentTimeTicker.value = ZonedDateTime.now()
            }
        }

        val todaySummaryFlow = currentTimeTicker
            .map { it.toLocalDate() }
            .distinctUntilChanged()
            .flatMapLatest { date ->
                repository.observeSummaryForDate(date)
            }

        viewModelScope.launch {
            combine(
                todaySummaryFlow,
                repository.observePresets(),
                repository.preferencesFlow,
                currentTimeTicker
            ) { summary, presets, prefs, now ->
                val pacing = if (prefs.wakingPacingEnabled && summary.targetMl > 0) {
                    PacingCalculator.evaluatePacing(
                        currentTime = now.toLocalTime(),
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
                        dateHeader = TimeUtils.formatCurrentDateHeader(now.zone),
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

    fun refreshCurrentTime() {
        currentTimeTicker.value = ZonedDateTime.now()
    }

    fun logPreset(preset: PresetEntity) {
        viewModelScope.launch {
            val record = repository.logPreset(preset)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            showUndoSnackbar(loggedRecord = record, deletedRecord = null, message = "Added ${preset.label}")
        }
    }

    fun logCustom(volumeMl: Double, hydrationFactor: Double, drinkType: String, timestamp: Long) {
        viewModelScope.launch {
            val type = DrinkType.fromId(drinkType)
            val record = HydrationRecordEntity(
                timestamp = timestamp,
                volumeMl = volumeMl,
                hydrationFactor = hydrationFactor,
                drinkType = drinkType,
                iconKey = PresetIcon.defaultForDrinkType(type).id
            )
            repository.insertRecord(record)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update { it.copy(isManualLogSheetOpen = false) }
            showUndoSnackbar(
                loggedRecord = record,
                deletedRecord = null,
                message = "Logged ${drinkType.lowercase().replaceFirstChar { it.uppercase() }}"
            )
        }
    }

    fun startEditingRecord(record: HydrationRecordEntity) {
        _uiState.update { it.copy(editingRecord = record) }
    }

    fun dismissEditingRecord() {
        _uiState.update { it.copy(editingRecord = null) }
    }

    fun saveEditedRecord(record: HydrationRecordEntity) {
        viewModelScope.launch {
            repository.updateRecord(record)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update { it.copy(editingRecord = null) }
        }
    }

    fun deleteRecord(record: HydrationRecordEntity) {
        viewModelScope.launch {
            repository.deleteRecordById(record.id)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update { it.copy(editingRecord = null) }
            showUndoSnackbar(loggedRecord = null, deletedRecord = record, message = "Record deleted")
        }
    }

    fun undoLastLog() {
        val state = _uiState.value
        val logged = state.lastLoggedRecord
        val deleted = state.recentlyDeletedRecord
        viewModelScope.launch {
            if (logged != null) {
                repository.deleteRecordById(logged.id)
            } else if (deleted != null) {
                repository.insertRecord(HealthConnectManager.prepareRestoredEntityForResync(deleted))
            }
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update {
                it.copy(
                    lastLoggedRecord = null,
                    recentlyDeletedRecord = null,
                    snackbarMessage = null
                )
            }
        }
    }

    private fun showUndoSnackbar(
        loggedRecord: HydrationRecordEntity?,
        deletedRecord: HydrationRecordEntity?,
        message: String
    ) {
        undoJob?.cancel()
        val actionId = loggedRecord?.id ?: deletedRecord?.id
        _uiState.update {
            it.copy(
                lastLoggedRecord = loggedRecord,
                recentlyDeletedRecord = deletedRecord,
                snackbarMessage = message
            )
        }

        undoJob = viewModelScope.launch {
            delay(4000)
            _uiState.update {
                val currentActionId = it.lastLoggedRecord?.id ?: it.recentlyDeletedRecord?.id
                if (currentActionId == actionId) {
                    it.copy(
                        lastLoggedRecord = null,
                        recentlyDeletedRecord = null,
                        snackbarMessage = null
                    )
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
        _uiState.update {
            it.copy(
                lastLoggedRecord = null,
                recentlyDeletedRecord = null,
                snackbarMessage = null
            )
        }
    }

    fun openAddPreset() {
        _uiState.update { it.copy(isPresetEditorOpen = true, editingPreset = null) }
    }

    fun openCustomizePresets() {
        _uiState.update { it.copy(isCustomizePresetsOpen = true) }
    }

    fun dismissCustomizePresets() {
        _uiState.update { it.copy(isCustomizePresetsOpen = false) }
    }

    fun reorderPresets(orderedPresets: List<PresetEntity>) {
        viewModelScope.launch {
            repository.reorderPresets(orderedPresets)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
        }
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
