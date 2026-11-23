package com.fillmark.app.ui.settings

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.ShortcutHelper
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.backup.BackupManager
import com.fillmark.app.data.local.FillmarkDatabase
import com.fillmark.app.data.local.ThemeMode
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
import kotlin.math.abs

data class SettingsUiState(
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val defaultTargetMl: Double = 2500.0,
    val wakingHoursStart: String = "07:00",
    val wakingHoursEnd: String = "23:00",
    val wakingPacingEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val hydrationFactorsEnabled: Boolean = true,
    val healthConnectSyncEnabled: Boolean = false,
    val healthConnectBidirectional: Boolean = false,
    val healthConnectSdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthConnectWritePermission: Boolean = false,
    val hasHealthConnectReadPermission: Boolean = false,
    val hasHealthConnectPermissions: Boolean = false,
    val lastHealthConnectSyncMillis: Long = 0L,
    val isSyncingHealthConnect: Boolean = false,
    val isBackupBusy: Boolean = false,
    val dataRevision: Int = 0,
    val statusMessage: String? = null
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
                val permState = if (isAvailable) hcManager.getPermissionState() else null
                _uiState.update {
                    it.copy(
                        displayUnit = prefs.displayUnit,
                        defaultTargetMl = prefs.defaultDailyTargetMl,
                        wakingHoursStart = prefs.wakingHoursStart,
                        wakingHoursEnd = prefs.wakingHoursEnd,
                        wakingPacingEnabled = prefs.wakingPacingEnabled,
                        themeMode = prefs.themeMode,
                        dynamicColorEnabled = prefs.dynamicColorEnabled,
                        hydrationFactorsEnabled = prefs.hydrationFactorsEnabled,
                        healthConnectSyncEnabled = prefs.healthConnectSyncEnabled,
                        healthConnectBidirectional = prefs.healthConnectBidirectional,
                        healthConnectSdkStatus = sdkStatus,
                        isHealthConnectAvailable = isAvailable,
                        hasHealthConnectWritePermission = permState?.hasWrite == true,
                        hasHealthConnectReadPermission = permState?.hasRead == true,
                        hasHealthConnectPermissions = permState?.hasAll == true,
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
            ShortcutHelper.updateShortcuts(getApplication())
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.updateThemeMode(mode)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateDynamicColorEnabled(enabled)
            WidgetUpdateHelper.updateAllWidgets(getApplication())
        }
    }

    fun setHydrationFactorsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHydrationFactorsEnabled(enabled)
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
        if (abs(targetMl - _uiState.value.defaultTargetMl) < 0.001) return
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
                if (hcManager.isAvailable()) {
                    val database = FillmarkDatabase.getInstance(getApplication())
                    val permState = hcManager.getPermissionState()
                    var anySynced = false
                    var allSucceeded = true

                    if (permState.hasWrite) {
                        anySynced = true
                        val outResult = hcManager.syncOutbound(database)
                        if (!outResult.succeeded) {
                            allSucceeded = false
                        }
                    }

                    if (_uiState.value.healthConnectBidirectional && permState.hasRead) {
                        anySynced = true
                        val inResult = hcManager.syncInbound(database)
                        if (!inResult.succeeded) {
                            allSucceeded = false
                        }
                        if (inResult.importedCount > 0) {
                            WidgetUpdateHelper.updateAllWidgets(getApplication())
                        }
                    }

                    if (anySynced && allSucceeded) {
                        preferencesRepository.setLastHealthConnectSyncMillis(System.currentTimeMillis())
                    }
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
            val permState = if (isAvailable) hcManager.getPermissionState() else null
            _uiState.update {
                it.copy(
                    healthConnectSdkStatus = sdkStatus,
                    isHealthConnectAvailable = isAvailable,
                    hasHealthConnectWritePermission = permState?.hasWrite == true,
                    hasHealthConnectReadPermission = permState?.hasRead == true,
                    hasHealthConnectPermissions = permState?.hasAll == true
                )
            }
        }
    }

    fun exportBackupJson(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            val app = getApplication<Application>()
            val message = runCatching {
                val res = BackupManager.exportBackupToUri(app, uri)
                app.getString(R.string.settings_exported_backup, res.recordsCount, res.presetsCount)
            }.getOrElse {
                app.getString(R.string.settings_export_failed, it.localizedMessage ?: app.getString(R.string.error_unknown))
            }
            _uiState.update { it.copy(isBackupBusy = false, statusMessage = message) }
        }
    }

    fun restoreBackupJson(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            val app = getApplication<Application>()
            var restored = false
            val message = runCatching {
                val res = BackupManager.restoreBackupFromUri(app, uri)
                restored = true
                app.getString(R.string.settings_restored_backup, res.recordsCount, res.presetsCount)
            }.getOrElse {
                app.getString(R.string.settings_restore_failed, it.localizedMessage ?: app.getString(R.string.error_invalid_backup))
            }
            _uiState.update {
                it.copy(
                    isBackupBusy = false,
                    dataRevision = if (restored) it.dataRevision + 1 else it.dataRevision,
                    statusMessage = message
                )
            }
        }
    }

    fun exportLogsCsv(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            val app = getApplication<Application>()
            val message = runCatching {
                val count = BackupManager.exportCsvToUri(app, uri)
                app.getString(R.string.settings_exported_csv, count)
            }.getOrElse {
                app.getString(R.string.settings_csv_failed, it.localizedMessage ?: app.getString(R.string.error_unknown))
            }
            _uiState.update { it.copy(isBackupBusy = false, statusMessage = message) }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupBusy = true) }
            val app = getApplication<Application>()
            var cleared = false
            val message = runCatching {
                hydrationRepository.clearAllData()
                WidgetUpdateHelper.updateAllWidgets(app)
                ShortcutHelper.updateShortcuts(app)
                cleared = true
                app.getString(R.string.settings_cleared_data)
            }.getOrElse {
                app.getString(R.string.settings_clear_failed, it.localizedMessage ?: app.getString(R.string.error_unknown))
            }
            _uiState.update {
                it.copy(
                    isBackupBusy = false,
                    dataRevision = if (cleared) it.dataRevision + 1 else it.dataRevision,
                    statusMessage = message
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
