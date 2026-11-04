package com.fillmark.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fillmark.app.core.DisplayUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val defaultDailyTargetMl: Double = 2500.0,
    val wakingHoursStart: String = "07:00",
    val wakingHoursEnd: String = "23:00",
    val wakingPacingEnabled: Boolean = true,
    val healthConnectSyncEnabled: Boolean = false,
    val healthConnectBidirectional: Boolean = false
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
            val unitStr = preferences[KEY_DISPLAY_UNIT] ?: DisplayUnit.METRIC_ML.name
            val displayUnit = try {
                DisplayUnit.valueOf(unitStr)
            } catch (e: Exception) {
                DisplayUnit.METRIC_ML
            }

            UserPreferences(
                displayUnit = displayUnit,
                defaultDailyTargetMl = preferences[KEY_DEFAULT_TARGET_ML] ?: 2500.0,
                wakingHoursStart = preferences[KEY_WAKING_HOURS_START] ?: "07:00",
                wakingHoursEnd = preferences[KEY_WAKING_HOURS_END] ?: "23:00",
                wakingPacingEnabled = preferences[KEY_WAKING_PACING_ENABLED] ?: true,
                healthConnectSyncEnabled = preferences[KEY_HEALTH_CONNECT_SYNC_ENABLED] ?: false,
                healthConnectBidirectional = preferences[KEY_HEALTH_CONNECT_BIDIRECTIONAL] ?: false
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
}

