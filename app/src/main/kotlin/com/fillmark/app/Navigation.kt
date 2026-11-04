package com.fillmark.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fillmark.app.ui.analytics.AnalyticsScreen
import com.fillmark.app.ui.analytics.AnalyticsViewModel
import com.fillmark.app.ui.dashboard.DashboardScreen
import com.fillmark.app.ui.dashboard.DashboardViewModel
import com.fillmark.app.ui.history.HistoryScreen
import com.fillmark.app.ui.history.HistoryViewModel
import com.fillmark.app.ui.settings.SettingsScreen
import com.fillmark.app.ui.settings.SettingsViewModel

enum class NavigationTab(val label: String) {
    DASHBOARD("Today"),
    HISTORY("History"),
    ANALYTICS("Analytics"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    BackHandler(enabled = currentTab != NavigationTab.DASHBOARD) {
        currentTab = NavigationTab.DASHBOARD
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Fillmark",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val icon = when (tab) {
                        NavigationTab.DASHBOARD -> Icons.Default.WaterDrop
                        NavigationTab.HISTORY -> Icons.Default.History
                        NavigationTab.ANALYTICS -> Icons.Default.BarChart
                        NavigationTab.SETTINGS -> Icons.Default.Settings
                    }

                    NavigationBarItem(
                        icon = { Icon(imageVector = icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = isSelected,
                        onClick = { currentTab = tab }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationTab.DASHBOARD -> {
                    val viewModel: DashboardViewModel = viewModel()
                    DashboardScreen(viewModel = viewModel)
                }
                NavigationTab.HISTORY -> {
                    val viewModel: HistoryViewModel = viewModel()
                    HistoryScreen(viewModel = viewModel)
                }
                NavigationTab.ANALYTICS -> {
                    val viewModel: AnalyticsViewModel = viewModel()
                    AnalyticsScreen(viewModel = viewModel)
                }
                NavigationTab.SETTINGS -> {
                    val viewModel: SettingsViewModel = viewModel()
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
