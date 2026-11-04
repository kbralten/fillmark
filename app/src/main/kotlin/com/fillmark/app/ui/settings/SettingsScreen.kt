package com.fillmark.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.sync.HealthConnectManager

import com.fillmark.app.ui.theme.FillmarkCardBorder
import com.fillmark.app.ui.theme.FillmarkNavyTitle

@OptIn(ExperimentalMaterial3Api::class)
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
        mutableStateOf(
            if (uiState.defaultTargetMl > 0) {
                VolumeConverter.formatNumberOnly(uiState.defaultTargetMl, uiState.displayUnit)
            } else {
                if (uiState.displayUnit == DisplayUnit.METRIC_ML) "2500" else "85"
            }
        )
    }

    var showWakeTimePicker by remember { mutableStateOf(false) }
    var showSleepTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = FillmarkNavyTitle
        )

        // Card 1: Units
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, FillmarkCardBorder),
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
        val hasTarget = uiState.defaultTargetMl > 0
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
                            text = "Daily Target Goal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasTarget) "Track intake towards a daily goal" else "No goal set (displays intake in big numbers)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hasTarget,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                val defaultVal = if (uiState.displayUnit == DisplayUnit.METRIC_ML) 2500.0 else 85.0
                                viewModel.setDefaultTarget(defaultVal)
                                targetInput = VolumeConverter.formatNumberOnly(defaultVal, uiState.displayUnit)
                            } else {
                                viewModel.setDefaultTarget(0.0)
                            }
                        }
                    )
                }

                if (hasTarget) {
                    Spacer(modifier = Modifier.height(14.dp))
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
                        modifier = Modifier.fillMaxWidth()
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
                            text = "Calculate expected progress based on waking schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.wakingPacingEnabled,
                        onCheckedChange = { isEnabled ->
                            viewModel.setWakingSchedule(uiState.wakingHoursStart, uiState.wakingHoursEnd, isEnabled)
                        }
                    )
                }

                if (uiState.wakingPacingEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TimePickerField(
                            label = "Wake Time",
                            timeStr = uiState.wakingHoursStart,
                            onClick = { showWakeTimePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        TimePickerField(
                            label = "Sleep Time",
                            timeStr = uiState.wakingHoursEnd,
                            onClick = { showSleepTimePicker = true },
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

    if (showWakeTimePicker) {
        TimePickerModalDialog(
            title = "Set Wake Time",
            initialTime = uiState.wakingHoursStart,
            onDismiss = { showWakeTimePicker = false },
            onConfirm = { newTime ->
                viewModel.setWakingSchedule(newTime, uiState.wakingHoursEnd, uiState.wakingPacingEnabled)
                showWakeTimePicker = false
            }
        )
    }

    if (showSleepTimePicker) {
        TimePickerModalDialog(
            title = "Set Sleep Time",
            initialTime = uiState.wakingHoursEnd,
            onDismiss = { showSleepTimePicker = false },
            onConfirm = { newTime ->
                viewModel.setWakingSchedule(uiState.wakingHoursStart, newTime, uiState.wakingPacingEnabled)
                showSleepTimePicker = false
            }
        )
    }
}

@Composable
fun TimePickerField(
    label: String,
    timeStr: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TimeUtils.formatTimeString(timeStr),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Pick $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModalDialog(
    title: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialLocalTime = TimeUtils.parseTimeString(initialTime)
    val timePickerState = rememberTimePickerState(
        initialHour = initialLocalTime.hour,
        initialMinute = initialLocalTime.minute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formatted = String.format(java.util.Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    onConfirm(formatted)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

