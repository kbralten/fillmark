package com.fillmark.app.data.local

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

enum class ThemeMode(val label: String, @param:StringRes val labelRes: Int) {
    SYSTEM("System", R.string.theme_mode_system),
    LIGHT("Light", R.string.theme_mode_light),
    DARK("Dark", R.string.theme_mode_dark);

    companion object {
        fun fromName(name: String?): ThemeMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
        }
    }
}

data class UserPreferences(
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val defaultDailyTargetMl: Double = 2500.0,
    val wakingHoursStart: String = "07:00",
    val wakingHoursEnd: String = "23:00",
    val wakingPacingEnabled: Boolean = true,
    val healthConnectSyncEnabled: Boolean = false,
    val healthConnectBidirectional: Boolean = false,
    val lastHealthConnectSyncMillis: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val hydrationFactorsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val lastWidgetLogRecordId: String? = null,
    val lastWidgetLogLabel: String? = null,
    val lastWidgetLogTimeMillis: Long = 0L
)

class UserPreferencesRepository(private val context: Context) {
    private val dataStore = context.dataStore

    companion object {
        private val KEY_DISPLAY_UNIT = stringPreferencesKey("display_unit")
        private val KEY_DEFAULT_TARGET_ML = doublePreferencesKey("default_daily_target_ml")
        private val KEY_WAKING_HOURS_START = stringPreferencesKey("waking_hours_start")
        private val KEY_WAKING_HOURS_END = stringPreferencesKey("waking_hours_end")
        private val KEY_WAKING_PACING_ENABLED = booleanPreferencesKey("waking_pacing_enabled")
        private val KEY_HEALTH_CONNECT_SYNC_ENABLED = booleanPreferencesKey("health_connect_sync_enabled")
        private val KEY_HEALTH_CONNECT_BIDIRECTIONAL = booleanPreferencesKey("health_connect_bidirectional")
        private val KEY_HEALTH_CONNECT_CHANGES_TOKEN = stringPreferencesKey("health_connect_changes_token")
        private val KEY_LAST_HEALTH_CONNECT_SYNC_MILLIS = longPreferencesKey("last_health_connect_sync_millis")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        private val KEY_HYDRATION_FACTORS_ENABLED = booleanPreferencesKey("hydration_factors_enabled")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_LAST_WIDGET_LOG_RECORD_ID = stringPreferencesKey("last_widget_log_record_id")
        private val KEY_LAST_WIDGET_LOG_LABEL = stringPreferencesKey("last_widget_log_label")
        private val KEY_LAST_WIDGET_LOG_TIME_MILLIS = longPreferencesKey("last_widget_log_time_millis")

        fun localeDefaultDisplayUnit(locale: Locale = Locale.getDefault()): DisplayUnit {
            return if (locale.country in setOf("US", "LR", "MM")) {
                DisplayUnit.IMPERIAL_FL_OZ
            } else {
                DisplayUnit.METRIC_ML
            }
        }

        internal fun applyRestoredPreferencesTo(
            preferences: MutablePreferences,
            displayUnit: DisplayUnit,
            defaultDailyTargetMl: Double,
            wakingHoursStart: String,
            wakingHoursEnd: String,
            wakingPacingEnabled: Boolean,
            themeMode: ThemeMode,
            dynamicColorEnabled: Boolean,
            hydrationFactorsEnabled: Boolean
        ) {
            preferences[KEY_DISPLAY_UNIT] = displayUnit.name
            preferences[KEY_DEFAULT_TARGET_ML] = defaultDailyTargetMl
            preferences[KEY_WAKING_HOURS_START] = wakingHoursStart
            preferences[KEY_WAKING_HOURS_END] = wakingHoursEnd
            preferences[KEY_WAKING_PACING_ENABLED] = wakingPacingEnabled
            preferences[KEY_THEME_MODE] = themeMode.name
            preferences[KEY_DYNAMIC_COLOR_ENABLED] = dynamicColorEnabled
            preferences[KEY_HYDRATION_FACTORS_ENABLED] = hydrationFactorsEnabled
            preferences[KEY_ONBOARDING_COMPLETED] = true
            // Pause Health Connect sync and reset sync tokens/undo state so restored records are not duplicated
            preferences[KEY_HEALTH_CONNECT_SYNC_ENABLED] = false
            preferences[KEY_HEALTH_CONNECT_BIDIRECTIONAL] = false
            preferences.remove(KEY_HEALTH_CONNECT_CHANGES_TOKEN)
            preferences[KEY_LAST_HEALTH_CONNECT_SYNC_MILLIS] = 0L
            preferences.remove(KEY_LAST_WIDGET_LOG_RECORD_ID)
            preferences.remove(KEY_LAST_WIDGET_LOG_LABEL)
            preferences.remove(KEY_LAST_WIDGET_LOG_TIME_MILLIS)
        }

        internal fun hasHealthConnectChangesToken(preferences: Preferences): Boolean =
            preferences[KEY_HEALTH_CONNECT_CHANGES_TOKEN] != null

        internal fun isHealthConnectSyncEnabled(preferences: Preferences): Boolean =
            preferences[KEY_HEALTH_CONNECT_SYNC_ENABLED] ?: false

        internal fun isHealthConnectBidirectional(preferences: Preferences): Boolean =
            preferences[KEY_HEALTH_CONNECT_BIDIRECTIONAL] ?: false

        internal fun lastHealthConnectSyncMillis(preferences: Preferences): Long =
            preferences[KEY_LAST_HEALTH_CONNECT_SYNC_MILLIS] ?: 0L

        internal fun seedHealthConnectStateForTest(
            preferences: MutablePreferences,
            syncEnabled: Boolean,
            bidirectional: Boolean,
            changesToken: String?,
            lastSyncMillis: Long
        ) {
            preferences[KEY_HEALTH_CONNECT_SYNC_ENABLED] = syncEnabled
            preferences[KEY_HEALTH_CONNECT_BIDIRECTIONAL] = bidirectional
            if (changesToken != null) {
                preferences[KEY_HEALTH_CONNECT_CHANGES_TOKEN] = changesToken
            }
            preferences[KEY_LAST_HEALTH_CONNECT_SYNC_MILLIS] = lastSyncMillis
        }
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val unitStr = preferences[KEY_DISPLAY_UNIT]
            val displayUnit = if (unitStr != null) {
                try {
                    DisplayUnit.valueOf(unitStr)
                } catch (_: Exception) {
                    localeDefaultDisplayUnit()
                }
            } else {
                localeDefaultDisplayUnit()
            }

            UserPreferences(
                displayUnit = displayUnit,
                defaultDailyTargetMl = preferences[KEY_DEFAULT_TARGET_ML] ?: 2500.0,
                wakingHoursStart = preferences[KEY_WAKING_HOURS_START] ?: "07:00",
                wakingHoursEnd = preferences[KEY_WAKING_HOURS_END] ?: "23:00",
                wakingPacingEnabled = preferences[KEY_WAKING_PACING_ENABLED] ?: true,
                healthConnectSyncEnabled = preferences[KEY_HEALTH_CONNECT_SYNC_ENABLED] ?: false,
                healthConnectBidirectional = preferences[KEY_HEALTH_CONNECT_BIDIRECTIONAL] ?: false,
                lastHealthConnectSyncMillis = preferences[KEY_LAST_HEALTH_CONNECT_SYNC_MILLIS] ?: 0L,
                themeMode = ThemeMode.fromName(preferences[KEY_THEME_MODE]),
                dynamicColorEnabled = preferences[KEY_DYNAMIC_COLOR_ENABLED] ?: false,
                hydrationFactorsEnabled = preferences[KEY_HYDRATION_FACTORS_ENABLED] ?: true,
                onboardingCompleted = preferences[KEY_ONBOARDING_COMPLETED] ?: false,
                lastWidgetLogRecordId = preferences[KEY_LAST_WIDGET_LOG_RECORD_ID],
                lastWidgetLogLabel = preferences[KEY_LAST_WIDGET_LOG_LABEL],
                lastWidgetLogTimeMillis = preferences[KEY_LAST_WIDGET_LOG_TIME_MILLIS] ?: 0L
            )
        }

    suspend fun updateDisplayUnit(unit: DisplayUnit) {
        dataStore.edit { preferences ->
            preferences[KEY_DISPLAY_UNIT] = unit.name
        }
    }

    suspend fun updateDefaultDailyTargetMl(targetMl: Double) {
        dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_TARGET_ML] = targetMl
        }
    }

    suspend fun updateWakingSchedule(start: String, end: String, pacingEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_WAKING_HOURS_START] = start
            preferences[KEY_WAKING_HOURS_END] = end
            preferences[KEY_WAKING_PACING_ENABLED] = pacingEnabled
        }
    }

    suspend fun updateHealthConnectSettings(enabled: Boolean, bidirectional: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HEALTH_CONNECT_SYNC_ENABLED] = enabled
            preferences[KEY_HEALTH_CONNECT_BIDIRECTIONAL] = bidirectional
        }
    }

    suspend fun getHealthConnectChangesToken(): String? {
        val preferences = dataStore.data.first()
        return preferences[KEY_HEALTH_CONNECT_CHANGES_TOKEN]
    }

    suspend fun setHealthConnectChangesToken(token: String?) {
        dataStore.edit { preferences ->
            if (token == null) {
                preferences.remove(KEY_HEALTH_CONNECT_CHANGES_TOKEN)
            } else {
                preferences[KEY_HEALTH_CONNECT_CHANGES_TOKEN] = token
            }
        }
    }

    suspend fun setLastHealthConnectSyncMillis(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_HEALTH_CONNECT_SYNC_MILLIS] = timestamp
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    suspend fun updateHydrationFactorsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HYDRATION_FACTORS_ENABLED] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setLastWidgetLog(recordId: String?, label: String?, timeMillis: Long) {
        dataStore.edit { preferences ->
            if (recordId == null) {
                preferences.remove(KEY_LAST_WIDGET_LOG_RECORD_ID)
                preferences.remove(KEY_LAST_WIDGET_LOG_LABEL)
                preferences.remove(KEY_LAST_WIDGET_LOG_TIME_MILLIS)
            } else {
                preferences[KEY_LAST_WIDGET_LOG_RECORD_ID] = recordId
                if (label != null) preferences[KEY_LAST_WIDGET_LOG_LABEL] = label
                preferences[KEY_LAST_WIDGET_LOG_TIME_MILLIS] = timeMillis
            }
        }
    }

    suspend fun resetPreferencesKeepOnboarding() {
        dataStore.edit { preferences ->
            preferences.clear()
            preferences[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun applyRestoredPreferences(
        displayUnit: DisplayUnit,
        defaultDailyTargetMl: Double,
        wakingHoursStart: String,
        wakingHoursEnd: String,
        wakingPacingEnabled: Boolean,
        themeMode: ThemeMode,
        dynamicColorEnabled: Boolean,
        hydrationFactorsEnabled: Boolean
    ) {
        dataStore.edit { preferences ->
            applyRestoredPreferencesTo(
                preferences = preferences,
                displayUnit = displayUnit,
                defaultDailyTargetMl = defaultDailyTargetMl,
                wakingHoursStart = wakingHoursStart,
                wakingHoursEnd = wakingHoursEnd,
                wakingPacingEnabled = wakingPacingEnabled,
                themeMode = themeMode,
                dynamicColorEnabled = dynamicColorEnabled,
                hydrationFactorsEnabled = hydrationFactorsEnabled
            )
        }
    }
}
