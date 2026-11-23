package com.fillmark.app

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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

enum class NavigationTab(@param:StringRes val labelRes: Int) {
    DASHBOARD(R.string.nav_today),
    HISTORY(R.string.nav_history),
    ANALYTICS(R.string.nav_analytics),
    SETTINGS(R.string.nav_settings)
}

@Composable
fun MainNavigation(
    pendingShortcutPresetId: String? = null,
    pendingOpenManualLog: Boolean = false,
    onShortcutHandled: () -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(NavigationTab.DASHBOARD) }

    LaunchedEffect(pendingShortcutPresetId, pendingOpenManualLog) {
        if (pendingShortcutPresetId != null || pendingOpenManualLog) {
            currentTab = NavigationTab.DASHBOARD
        }
    }

    BackHandler(enabled = currentTab != NavigationTab.DASHBOARD) {
        currentTab = NavigationTab.DASHBOARD
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        if (useNavigationRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        val label = stringResource(tab.labelRes)
                        NavigationRailItem(
                            icon = { Icon(imageVector = tabIcon(tab), contentDescription = label) },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        NavigationTabContent(
                            currentTab = currentTab,
                            pendingShortcutPresetId = pendingShortcutPresetId,
                            pendingOpenManualLog = pendingOpenManualLog,
                            onShortcutHandled = onShortcutHandled
                        )
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        NavigationTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                icon = { Icon(imageVector = tabIcon(tab), contentDescription = label) },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selected = isSelected,
                                onClick = { currentTab = tab },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    NavigationTabContent(
                        currentTab = currentTab,
                        pendingShortcutPresetId = pendingShortcutPresetId,
                        pendingOpenManualLog = pendingOpenManualLog,
                        onShortcutHandled = onShortcutHandled
                    )
                }
            }
        }
    }
}

private fun tabIcon(tab: NavigationTab) = when (tab) {
    NavigationTab.DASHBOARD -> Icons.Default.WaterDrop
    NavigationTab.HISTORY -> Icons.Default.History
    NavigationTab.ANALYTICS -> Icons.Default.BarChart
    NavigationTab.SETTINGS -> Icons.Default.Settings
}

@Composable
private fun NavigationTabContent(
    currentTab: NavigationTab,
    pendingShortcutPresetId: String?,
    pendingOpenManualLog: Boolean,
    onShortcutHandled: () -> Unit
) {
    when (currentTab) {
        NavigationTab.DASHBOARD -> {
            val viewModel: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = viewModel,
                pendingShortcutPresetId = pendingShortcutPresetId,
                pendingOpenManualLog = pendingOpenManualLog,
                onShortcutHandled = onShortcutHandled
            )
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
