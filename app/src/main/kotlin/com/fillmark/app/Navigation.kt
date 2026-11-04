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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fillmark.app.ui.analytics.AnalyticsScreen
import com.fillmark.app.ui.analytics.AnalyticsViewModel
import com.fillmark.app.ui.dashboard.DashboardScreen
import com.fillmark.app.ui.dashboard.DashboardViewModel
import com.fillmark.app.ui.history.HistoryScreen
import com.fillmark.app.ui.history.HistoryViewModel
import com.fillmark.app.ui.settings.SettingsScreen
import com.fillmark.app.ui.settings.SettingsViewModel

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkNavy900
import com.fillmark.app.ui.theme.FillmarkSlateMuted

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.fillmark.app.ui.theme.FillmarkCardBorder
import com.fillmark.app.ui.theme.FillmarkCyanContainer
import com.fillmark.app.ui.theme.FillmarkCyanDark

enum class NavigationTab(val label: String) {
    DASHBOARD("Today"),
    HISTORY("History"),
    ANALYTICS("Analytics"),
    SETTINGS("Settings")
}

@Composable
fun MainNavigation() {
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    BackHandler(enabled = currentTab != NavigationTab.DASHBOARD) {
        currentTab = NavigationTab.DASHBOARD
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .border(
                        BorderStroke(1.dp, FillmarkCardBorder),
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
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
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FillmarkCyanDark,
                            selectedTextColor = FillmarkCyanDark,
                            indicatorColor = FillmarkCyanContainer,
                            unselectedIconColor = FillmarkSlateMuted,
                            unselectedTextColor = FillmarkSlateMuted
                        )
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
