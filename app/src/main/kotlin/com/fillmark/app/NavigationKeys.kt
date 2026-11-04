package com.fillmark.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object DashboardKey : NavKey

@Serializable
data object HistoryKey : NavKey

@Serializable
data object AnalyticsKey : NavKey

@Serializable
data object SettingsKey : NavKey
