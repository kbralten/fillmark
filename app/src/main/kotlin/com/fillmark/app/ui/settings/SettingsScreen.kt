package com.fillmark.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.sync.HealthConnectManager

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hcManager = remember { HealthConnectManager(context) }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.refreshHealthConnectStatus()
        if (grantedPermissions.containsAll(hcManager.permissions)) {
            viewModel.setHealthConnectSync(enabled = true, bidirectional = uiState.healthConnectBidirectional)
        }
    }

    var targetInput by remember(uiState.defaultTargetMl, uiState.displayUnit) {
        mutableStateOf(VolumeConverter.formatNumberOnly(uiState.defaultTargetMl, uiState.displayUnit))
    }
    var wakeTimeInput by remember(uiState.wakingHoursStart) {
        mutableStateOf(uiState.wakingHoursStart)
    }
    var sleepTimeInput by remember(uiState.wakingHoursEnd) {
        mutableStateOf(uiState.wakingHoursEnd)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Preferences",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Card 1: Units
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Display Units",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = uiState.displayUnit == DisplayUnit.METRIC_ML,
                        onClick = { viewModel.setDisplayUnit(DisplayUnit.METRIC_ML) },
                        label = { Text("Metric (ml)") }
                    )
                    FilterChip(
                        selected = uiState.displayUnit == DisplayUnit.IMPERIAL_FL_OZ,
                        onClick = { viewModel.setDisplayUnit(DisplayUnit.IMPERIAL_FL_OZ) },
                        label = { Text("Imperial (fl oz)") }
                    )
                }
            }
        }

        // Card 2: Daily Target Goal
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Default Daily Goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = {
                            targetInput = it.filter { c -> c.isDigit() || c == '.' }
                            val num = targetInput.toDoubleOrNull()
                            if (num != null && num > 0) {
                                viewModel.setDefaultTarget(num)
                            }
                        },
                        label = { Text("Daily Target") },
                        suffix = { Text(uiState.displayUnit.symbol) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Card 3: Waking Hours Schedule & Pacing
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Waking Hours Pacing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Calculate expected hydration progress based on waking schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.wakingPacingEnabled,
                        onCheckedChange = { isEnabled ->
                            viewModel.setWakingSchedule(wakeTimeInput, sleepTimeInput, isEnabled)
                        }
                    )
                }

                if (uiState.wakingPacingEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = wakeTimeInput,
                            onValueChange = {
                                wakeTimeInput = it
                                viewModel.setWakingSchedule(wakeTimeInput, sleepTimeInput, true)
                            },
                            label = { Text("Wake Time (HH:MM)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = sleepTimeInput,
                            onValueChange = {
                                sleepTimeInput = it
                                viewModel.setWakingSchedule(wakeTimeInput, sleepTimeInput, true)
                            },
                            label = { Text("Sleep Time (HH:MM)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Card 4: Health Connect
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Health Connect Integration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (uiState.isHealthConnectAvailable) {
                        if (uiState.hasHealthConnectPermissions) {
                            "Status: Connected and ready to sync."
                        } else {
                            "Status: Health Connect available. Permissions required."
                        }
                    } else {
                        "Status: Health Connect is not available on this device."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.hasHealthConnectPermissions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiState.isHealthConnectAvailable && !uiState.hasHealthConnectPermissions) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            requestPermissionsLauncher.launch(hcManager.permissions)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Health Connect Permissions")
                    }
                }

                if (uiState.isHealthConnectAvailable && uiState.hasHealthConnectPermissions) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Outbound Sync", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = uiState.healthConnectSyncEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setHealthConnectSync(enabled, uiState.healthConnectBidirectional)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bidirectional Inbound Sync", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Import drinks logged in other fitness apps into Fillmark",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.healthConnectBidirectional,
                            enabled = uiState.healthConnectSyncEnabled,
                            onCheckedChange = { bi ->
                                viewModel.setHealthConnectSync(uiState.healthConnectSyncEnabled, bi)
                            }
                        )
                    }
                }
            }
        }

        // Card 5: Home Screen Widgets
        val appWidgetManager = remember { android.appwidget.AppWidgetManager.getInstance(context) }
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Home Screen Widgets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Quickly add Fillmark widgets to your home screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val provider = android.content.ComponentName(context, com.fillmark.app.widget.FullDashboardWidgetReceiver::class.java)
                                appWidgetManager.requestPinAppWidget(provider, null, null)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Full Widget", style = MaterialTheme.typography.labelMedium)
                        }

                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val provider = android.content.ComponentName(context, com.fillmark.app.widget.ProgressMeterWidgetReceiver::class.java)
                                appWidgetManager.requestPinAppWidget(provider, null, null)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Progress (3x1)", style = MaterialTheme.typography.labelMedium)
                        }

                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val provider = android.content.ComponentName(context, com.fillmark.app.widget.QuickLogBarWidgetReceiver::class.java)
                                appWidgetManager.requestPinAppWidget(provider, null, null)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Quick Bar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

