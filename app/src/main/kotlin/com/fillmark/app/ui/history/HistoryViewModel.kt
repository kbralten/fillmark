package com.fillmark.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class DayGroup(
    val dateIso: String,
    val friendlyDate: String,
    val totalEffectiveVolumeMl: Double,
    val records: List<HydrationRecordEntity>
)

data class HistoryUiState(
    val dayGroups: List<DayGroup> = emptyList(),
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val hydrationFactorsEnabled: Boolean = true,
    val searchQuery: String = "",
    val selectedDrinkType: String? = null,
    val selectedDateIso: String? = null,
    val hasAnyRecords: Boolean = false,
    val isManualLogSheetOpen: Boolean = false,
    val editingRecord: HydrationRecordEntity? = null,
    val pendingDeleteRecord: HydrationRecordEntity? = null,
    val recentlyDeletedRecord: HydrationRecordEntity? = null,
    val snackbarMessage: String? = null
)

class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: HydrationRepository = HydrationRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val currentDateFlow = MutableStateFlow(LocalDate.now())
    private val searchQueryFlow = MutableStateFlow("")
    private val selectedDrinkTypeFlow = MutableStateFlow<String?>(null)
    private val selectedDateIsoFlow = MutableStateFlow<String?>(null)

    private data class FilterCriteria(
        val searchQuery: String,
        val selectedDrinkType: String?,
        val selectedDateIso: String?
    )

    companion object {
        fun filterAndGroupRecords(
            records: List<HydrationRecordEntity>,
            searchQuery: String,
            selectedDrinkType: String?,
            selectedDateIso: String?,
            displayUnit: DisplayUnit,
            hydrationFactorsEnabled: Boolean,
            todayLabel: String = "Today",
            yesterdayLabel: String = "Yesterday",
            zoneId: ZoneId = ZoneId.systemDefault()
        ): List<DayGroup> {
            val trimmedQuery = searchQuery.trim()
            val filtered = records.filter { record ->
                val matchesType = selectedDrinkType == null ||
                    record.drinkType.equals(selectedDrinkType, ignoreCase = true)
                if (!matchesType) return@filter false

                val recordDateIso = TimeUtils.toIsoDate(record.timestamp, zoneId)
                val matchesDate = selectedDateIso == null || recordDateIso == selectedDateIso
                if (!matchesDate) return@filter false

                if (trimmedQuery.isEmpty()) return@filter true

                val categoryName = DrinkType.fromId(record.drinkType).displayName
                val volumeText = VolumeConverter.formatVolume(
                    record.countedVolumeMl(hydrationFactorsEnabled),
                    displayUnit
                )
                val rawVolumeText = VolumeConverter.formatVolume(record.volumeMl, displayUnit)

                record.label?.contains(trimmedQuery, ignoreCase = true) == true ||
                    record.drinkType.contains(trimmedQuery, ignoreCase = true) ||
                    categoryName.contains(trimmedQuery, ignoreCase = true) ||
                    volumeText.contains(trimmedQuery, ignoreCase = true) ||
                    rawVolumeText.contains(trimmedQuery, ignoreCase = true) ||
                    recordDateIso.contains(trimmedQuery, ignoreCase = true)
            }

            return filtered
                .groupBy { TimeUtils.toIsoDate(it.timestamp, zoneId) }
                .map { (dateIso, groupRecords) ->
                    DayGroup(
                        dateIso = dateIso,
                        friendlyDate = TimeUtils.formatFriendlyDate(
                            dateString = dateIso,
                            zoneId = zoneId,
                            todayLabel = todayLabel,
                            yesterdayLabel = yesterdayLabel
                        ),
                        totalEffectiveVolumeMl = groupRecords.sumOf {
                            it.countedVolumeMl(hydrationFactorsEnabled)
                        },
                        records = groupRecords.sortedByDescending { it.timestamp }
                    )
                }
                .sortedByDescending { it.dateIso }
        }
    }

    init {
        val filterFlow = combine(
            searchQueryFlow,
            selectedDrinkTypeFlow,
            selectedDateIsoFlow
        ) { query, drinkType, dateIso ->
            FilterCriteria(query, drinkType, dateIso)
        }

        viewModelScope.launch {
            combine(
                repository.observeAllRecords(),
                repository.preferencesFlow,
                currentDateFlow,
                filterFlow
            ) { records, prefs, _, filters ->
                val todayLabel = getApplication<Application>().getString(R.string.today)
                val yesterdayLabel = getApplication<Application>().getString(R.string.yesterday)
                val factorsEnabled = prefs.hydrationFactorsEnabled
                val grouped = filterAndGroupRecords(
                    records = records,
                    searchQuery = filters.searchQuery,
                    selectedDrinkType = filters.selectedDrinkType,
                    selectedDateIso = filters.selectedDateIso,
                    displayUnit = prefs.displayUnit,
                    hydrationFactorsEnabled = factorsEnabled,
                    todayLabel = todayLabel,
                    yesterdayLabel = yesterdayLabel
                )

                _uiState.update {
                    it.copy(
                        dayGroups = grouped,
                        displayUnit = prefs.displayUnit,
                        hydrationFactorsEnabled = factorsEnabled,
                        searchQuery = filters.searchQuery,
                        selectedDrinkType = filters.selectedDrinkType,
                        selectedDateIso = filters.selectedDateIso,
                        hasAnyRecords = records.isNotEmpty()
                    )
                }
            }.collect {}
        }
    }

    fun refreshCurrentDate() {
        currentDateFlow.value = LocalDate.now()
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun setDrinkTypeFilter(drinkType: String?) {
        selectedDrinkTypeFlow.value = if (selectedDrinkTypeFlow.value == drinkType) null else drinkType
    }

    fun setDateFilter(dateIso: String?) {
        selectedDateIsoFlow.value = dateIso
    }

    fun clearFilters() {
        searchQueryFlow.value = ""
        selectedDrinkTypeFlow.value = null
        selectedDateIsoFlow.value = null
    }

    fun openManualLogSheet() {
        _uiState.update { it.copy(isManualLogSheetOpen = true) }
    }

    fun closeManualLogSheet() {
        _uiState.update { it.copy(isManualLogSheetOpen = false) }
    }

    fun logCustom(
        volumeMl: Double,
        hydrationFactor: Double,
        drinkType: String,
        timestamp: Long,
        label: String? = null
    ) {
        viewModelScope.launch {
            val type = DrinkType.fromId(drinkType)
            val trimmedLabel = label?.trim()?.ifBlank { null }
            val record = HydrationRecordEntity(
                timestamp = timestamp,
                volumeMl = volumeMl,
                hydrationFactor = hydrationFactor,
                drinkType = drinkType,
                iconKey = PresetIcon.defaultForDrinkType(type).id,
                label = trimmedLabel
            )
            repository.insertRecord(record)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update { it.copy(isManualLogSheetOpen = false) }
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

    fun requestDeleteConfirmation(record: HydrationRecordEntity) {
        _uiState.update { it.copy(pendingDeleteRecord = record) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(pendingDeleteRecord = null) }
    }

    fun confirmDelete() {
        val record = _uiState.value.pendingDeleteRecord ?: return
        _uiState.update { it.copy(pendingDeleteRecord = null) }
        deleteRecord(record)
    }

    fun deleteRecord(record: HydrationRecordEntity) {
        viewModelScope.launch {
            repository.deleteRecordById(record.id)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update {
                it.copy(
                    editingRecord = null,
                    recentlyDeletedRecord = record,
                    snackbarMessage = getApplication<Application>().getString(R.string.record_deleted)
                )
            }
        }
    }

    fun undoDelete() {
        val record = _uiState.value.recentlyDeletedRecord ?: return
        viewModelScope.launch {
            repository.insertRecord(HealthConnectManager.prepareRestoredEntityForResync(record))
            WidgetUpdateHelper.updateAllWidgets(getApplication())
            _uiState.update { it.copy(recentlyDeletedRecord = null, snackbarMessage = null) }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(recentlyDeletedRecord = null, snackbarMessage = null) }
    }
}
