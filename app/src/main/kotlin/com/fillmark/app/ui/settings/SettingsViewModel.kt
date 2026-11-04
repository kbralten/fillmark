package com.fillmark.app.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.sync.HealthConnectSyncWorker
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val defaultTargetMl: Double = 2500.0,
    val wakingHoursStart: String = "07:00",
    val wakingHoursEnd: String = "23:00",
    val wakingPacingEnabled: Boolean = true,
    val healthConnectSyncEnabled: Boolean = false,
    val healthConnectBidirectional: Boolean = false,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthConnectPermissions: Boolean = false
)

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val hcManager = HealthConnectManager(application)

    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                val hasPerms = hcManager.hasAllPermissions()
                _uiState.update {
                    it.copy(
                        displayUnit = prefs.displayUnit,
                        defaultTargetMl = prefs.defaultDailyTargetMl,
                        wakingHoursStart = prefs.wakingHoursStart,
                        wakingHoursEnd = prefs.wakingHoursEnd,
                        wakingPacingEnabled = prefs.wakingPacingEnabled,
                        healthConnectSyncEnabled = prefs.healthConnectSyncEnabled,
                        healthConnectBidirectional = prefs.healthConnectBidirectional,
                        isHealthConnectAvailable = hcManager.isAvailable(),
                        hasHealthConnectPermissions = hasPerms
                    )
                }
            }
        }
    }

    fun setDisplayUnit(unit: DisplayUnit) {
        viewModelScope.launch {
            preferencesRepository.updateDisplayUnit(unit)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
        }
    }

    fun setDefaultTarget(targetInCurrentUnit: Double) {
        viewModelScope.launch {
            val internalMl = if (targetInCurrentUnit > 0) {
                VolumeConverter.toInternalMl(targetInCurrentUnit, _uiState.value.displayUnit)
            } else {
                0.0
            }
            preferencesRepository.updateDefaultDailyTargetMl(internalMl)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
        }
    }

    fun setWakingSchedule(start: String, end: String, pacingEnabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateWakingSchedule(start, end, pacingEnabled)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
        }
    }

    fun setHealthConnectSync(enabled: Boolean, bidirectional: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHealthConnectSettings(enabled, bidirectional)
            if (enabled) {
                try {
                    val syncRequest = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
                    WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                        "HealthConnectOutboundSync",
                        ExistingWorkPolicy.REPLACE,
                        syncRequest
                    )
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Failed to enqueue initial Health Connect sync", e)
                }
            }
        }
    }

    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val hasPerms = hcManager.hasAllPermissions()
            _uiState.update {
                it.copy(
                    isHealthConnectAvailable = hcManager.isAvailable(),
                    hasHealthConnectPermissions = hasPerms
                )
            }
        }
    }
}

