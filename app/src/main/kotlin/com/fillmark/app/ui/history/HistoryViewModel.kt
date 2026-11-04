package com.fillmark.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DayGroup(
    val dateIso: String,
    val friendlyDate: String,
    val totalEffectiveVolumeMl: Double,
    val records: List<HydrationRecordEntity>
)

data class HistoryUiState(
    val dayGroups: List<DayGroup> = emptyList(),
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val editingRecord: HydrationRecordEntity? = null,
    val recentlyDeletedRecord: HydrationRecordEntity? = null,
    val snackbarMessage: String? = null
)

class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: HydrationRepository = HydrationRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            android.util.Log.d("HistoryViewModel", "Starting flow observation...")
            combine(
                repository.observeAllRecords(),
                repository.preferencesFlow
            ) { records, prefs ->
                android.util.Log.d("HistoryViewModel", "Emitted: records count=${records.size}, unit=${prefs.displayUnit}")
                val grouped = records
                    .groupBy { TimeUtils.toIsoDate(it.timestamp) }
                    .map { (dateIso, groupRecords) ->
                        DayGroup(
                            dateIso = dateIso,
                            friendlyDate = TimeUtils.formatFriendlyDate(dateIso),
                            totalEffectiveVolumeMl = groupRecords.sumOf { it.effectiveVolumeMl },
                            records = groupRecords.sortedByDescending { it.timestamp }
                        )
                    }
                    .sortedByDescending { it.dateIso }

                _uiState.update {
                    it.copy(
                        dayGroups = grouped,
                        displayUnit = prefs.displayUnit
                    )
                }
            }.collect {
                android.util.Log.d("HistoryViewModel", "Collected state update")
            }
        }
    }

    fun startEditing(record: HydrationRecordEntity) {
        _uiState.update { it.copy(editingRecord = record) }
    }

    fun dismissEditing() {
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
            _uiState.update {
                it.copy(
                    recentlyDeletedRecord = record,
                    snackbarMessage = "Record deleted"
                )
            }
        }
    }

    fun undoDelete() {
        val record = _uiState.value.recentlyDeletedRecord ?: return
        viewModelScope.launch {
            repository.insertRecord(record)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update { it.copy(recentlyDeletedRecord = null, snackbarMessage = null) }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

