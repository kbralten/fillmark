package com.fillmark.app.ui.settings

import android.app.Application
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
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
    val healthConnectSdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthConnectPermissions: Boolean = false,
    val lastHealthConnectSyncMillis: Long = 0L,
    val isSyncingHealthConnect: Boolean = false
)

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val preferencesRepository: UserPreferencesRepository = UserPreferencesRepository(application),
    private val hydrationRepository: HydrationRepository = HydrationRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val hcManager = HealthConnectManager(application)

    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                val sdkStatus = hcManager.getSdkStatus()
                val isAvailable = sdkStatus == HealthConnectClient.SDK_AVAILABLE
                val hasPerms = if (isAvailable) hcManager.hasAllPermissions() else false
                _uiState.update {
                    it.copy(
                        displayUnit = prefs.displayUnit,
                        defaultTargetMl = prefs.defaultDailyTargetMl,
                        wakingHoursStart = prefs.wakingHoursStart,
                        wakingHoursEnd = prefs.wakingHoursEnd,
                        wakingPacingEnabled = prefs.wakingPacingEnabled,
                        healthConnectSyncEnabled = prefs.healthConnectSyncEnabled,
                        healthConnectBidirectional = prefs.healthConnectBidirectional,
                        healthConnectSdkStatus = sdkStatus,
                        isHealthConnectAvailable = isAvailable,
                        hasHealthConnectPermissions = hasPerms,
                        lastHealthConnectSyncMillis = prefs.lastHealthConnectSyncMillis
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
        val internalMl = if (targetInCurrentUnit > 0) {
            VolumeConverter.toInternalMl(targetInCurrentUnit, _uiState.value.displayUnit)
        } else {
            0.0
        }
        setDefaultTargetMl(internalMl)
    }

    fun setDefaultTargetMl(targetMl: Double) {
        viewModelScope.launch {
            hydrationRepository.updateDailyTargetPreservingHistory(targetMl)
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
                HealthConnectManager.schedulePeriodicSync(getApplication())
                HealthConnectManager.enqueueImmediateSync(getApplication(), replace = true)
            } else {
                HealthConnectManager.cancelPeriodicSync(getApplication())
            }
        }
    }

    fun syncHealthConnectNow() {
        if (_uiState.value.isSyncingHealthConnect) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncingHealthConnect = true) }
            try {
                if (hcManager.isAvailable() && hcManager.hasAllPermissions()) {
                    val database = FillmarkDatabase.getInstance(getApplication())
                    hcManager.syncOutbound(database)
                    if (_uiState.value.healthConnectBidirectional) {
                        val imported = hcManager.syncInbound(database)
                        if (imported > 0) {
                            WidgetUpdateHelper.updateAllWidgets(getApplication())
                        }
                    }
                    preferencesRepository.setLastHealthConnectSyncMillis(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Manual Health Connect sync failed", e)
            } finally {
                _uiState.update { it.copy(isSyncingHealthConnect = false) }
            }
        }
    }

    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val sdkStatus = hcManager.getSdkStatus()
            val isAvailable = sdkStatus == HealthConnectClient.SDK_AVAILABLE
            val hasPerms = if (isAvailable) hcManager.hasAllPermissions() else false
            _uiState.update {
                it.copy(
                    healthConnectSdkStatus = sdkStatus,
                    isHealthConnectAvailable = isAvailable,
                    hasHealthConnectPermissions = hasPerms
                )
            }
        }
    }
}
