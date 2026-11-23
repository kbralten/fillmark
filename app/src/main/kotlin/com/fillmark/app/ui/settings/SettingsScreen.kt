package com.fillmark.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fillmark.app.BuildConfig
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.ThemeMode
import com.fillmark.app.sync.HealthConnectManager
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hcManager = remember { HealthConnectManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshHealthConnectStatus()
        onPauseOrDispose {}
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.refreshHealthConnectStatus()
        if (grantedPermissions.containsAll(hcManager.permissions)) {
            viewModel.setHealthConnectSync(enabled = true, bidirectional = uiState.healthConnectBidirectional)
        }
    }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackupJson(uri)
    }
    val restoreJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportLogsCsv(uri)
    }

    val hasTarget = uiState.defaultTargetMl > 0
    var targetInput by remember {
        mutableStateOf(
            if (uiState.defaultTargetMl > 0) {
                VolumeConverter.formatInputNumber(uiState.defaultTargetMl, uiState.displayUnit)
            } else {
                if (uiState.displayUnit == DisplayUnit.METRIC_ML) "2500" else "85"
            }
        )
    }
    var lastObservedUnit by remember { mutableStateOf(uiState.displayUnit) }
    var lastObservedHasTarget by remember { mutableStateOf(hasTarget) }

    LaunchedEffect(uiState.displayUnit, hasTarget, uiState.defaultTargetMl) {
        if (lastObservedUnit != uiState.displayUnit || lastObservedHasTarget != hasTarget) {
            if (hasTarget) {
                targetInput = VolumeConverter.formatInputNumber(uiState.defaultTargetMl, uiState.displayUnit)
            }
            lastObservedUnit = uiState.displayUnit
            lastObservedHasTarget = hasTarget
        }
    }

    val parsedTarget = targetInput.toDoubleOrNull() ?: 0.0
    val targetInternalMl = VolumeConverter.toInternalMl(parsedTarget, uiState.displayUnit)
    val targetErrorText = when {
        !hasTarget -> null
        targetInput.isBlank() || targetInternalMl <= 0 -> "Enter a target greater than 0"
        targetInternalMl > VolumeConverter.MAX_DAILY_TARGET_ML ->
            "Max daily target is ${VolumeConverter.formatVolume(VolumeConverter.MAX_DAILY_TARGET_ML, uiState.displayUnit)}"
        else -> null
    }

    var showWakeTimePicker by remember { mutableStateOf(false) }
    var showSleepTimePicker by remember { mutableStateOf(false) }

    val privacyUrl = stringResource(R.string.privacy_policy_url)
    val githubIssuesUrl = stringResource(R.string.github_issues_url)
    val githubRepoUrl = stringResource(R.string.github_repo_url)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Card 1: Appearance
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dynamic Color (Material You)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Match app colors to your wallpaper",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.dynamicColorEnabled,
                                onCheckedChange = { viewModel.setDynamicColorEnabled(it) }
                            )
                        }
                    }
                }
            }

            // Card 2: Units
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Display Units",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
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

            // Card 3: Daily Target Goal & Hydration Factors
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
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
                                    val defaultValInUnit = if (uiState.displayUnit == DisplayUnit.METRIC_ML) 2500.0 else 85.0
                                    targetInput = if (uiState.displayUnit == DisplayUnit.METRIC_ML) "2500" else "85"
                                    lastObservedHasTarget = true
                                    viewModel.setDefaultTarget(defaultValInUnit)
                                } else {
                                    lastObservedHasTarget = false
                                    viewModel.setDefaultTargetMl(0.0)
                                }
                            }
                        )
                    }

                    if (hasTarget) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { raw ->
                                val sanitized = VolumeConverter.sanitizeDecimalInput(raw)
                                targetInput = sanitized
                                val num = sanitized.toDoubleOrNull()
                                if (num != null) {
                                    val ml = VolumeConverter.toInternalMl(num, uiState.displayUnit)
                                    if (ml > 0 && ml <= VolumeConverter.MAX_DAILY_TARGET_ML) {
                                        viewModel.setDefaultTargetMl(ml)
                                    }
                                }
                            },
                            label = { Text("Daily Target") },
                            suffix = { Text(uiState.displayUnit.symbol) },
                            singleLine = true,
                            isError = targetErrorText != null,
                            supportingText = targetErrorText?.let { msg -> { Text(msg) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Drink Hydration Factors",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.hydrationFactorsEnabled) {
                                    "Adjust effective water by drink type (e.g., 0.85x for coffee)"
                                } else {
                                    "Disabled: all drinks count 1:1 by raw volume"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.hydrationFactorsEnabled,
                            onCheckedChange = { viewModel.setHydrationFactorsEnabled(it) }
                        )
                    }
                }
            }

            // Card 4: Waking Hours Schedule & Pacing
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
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

            // Card 5: Health Connect
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Health Connect Integration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when {
                            uiState.isHealthConnectAvailable && uiState.hasHealthConnectPermissions ->
                                "Status: Connected and ready to sync."
                            uiState.isHealthConnectAvailable ->
                                "Status: Health Connect available. Permissions required."
                            uiState.healthConnectSdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                "Status: Health Connect needs to be installed or updated."
                            else ->
                                "Status: Health Connect is not available on this device."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.hasHealthConnectPermissions) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (uiState.healthConnectSdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val marketIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.google.android.apps.healthdata")
                                ).apply {
                                    setPackage("com.android.vending")
                                }
                                runCatching { context.startActivity(marketIntent) }
                                    .onFailure {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                                                )
                                            )
                                        }
                                    }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Install / Update Health Connect")
                        }
                    } else if (uiState.isHealthConnectAvailable && !uiState.hasHealthConnectPermissions) {
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
                            Text(
                                "Enable Outbound Sync",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
                                Text(
                                    "Bidirectional Inbound Sync",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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

                        if (uiState.healthConnectSyncEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            val lastSyncText = if (uiState.lastHealthConnectSyncMillis > 0L) {
                                "Last synced: ${TimeUtils.formatDateTime(uiState.lastHealthConnectSyncMillis)}"
                            } else {
                                "Last synced: Never"
                            }
                            Text(
                                text = lastSyncText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.syncHealthConnectNow() },
                                enabled = !uiState.isSyncingHealthConnect,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isSyncingHealthConnect) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Syncing...")
                                } else {
                                    Text("Sync Now")
                                }
                            }
                        }
                    }
                }
            }

            // Card 6: Data & Backup
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Data & Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Export your drink history, presets, and targets locally to transfer devices or analyze in a spreadsheet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportJsonLauncher.launch("fillmark-backup-${LocalDate.now()}.json") },
                            enabled = !uiState.isBackupBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = { restoreJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = !uiState.isBackupBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Restore JSON")
                        }
                    }
                    OutlinedButton(
                        onClick = { exportCsvLauncher.launch("fillmark-logs-${LocalDate.now()}.csv") },
                        enabled = !uiState.isBackupBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Drink Logs (CSV)")
                    }
                    TextButton(
                        onClick = { showClearDataConfirm = true },
                        enabled = !uiState.isBackupBusy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset & Clear All Local Data")
                    }
                }
            }

            // Card 7: Home Screen Widgets
            val appWidgetManager = remember { android.appwidget.AppWidgetManager.getInstance(context) }
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Home Screen Widgets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
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
                            OutlinedButton(
                                onClick = {
                                    val provider = android.content.ComponentName(
                                        context,
                                        com.fillmark.app.widget.FullDashboardWidgetReceiver::class.java
                                    )
                                    appWidgetManager.requestPinAppWidget(provider, null, null)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Full Widget", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    val provider = android.content.ComponentName(
                                        context,
                                        com.fillmark.app.widget.ProgressMeterWidgetReceiver::class.java
                                    )
                                    appWidgetManager.requestPinAppWidget(provider, null, null)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Progress (3x1)", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = {
                                    val provider = android.content.ComponentName(
                                        context,
                                        com.fillmark.app.widget.QuickLogBarWidgetReceiver::class.java
                                    )
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

            // Card 8: About Fillmark
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About Fillmark",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fillmark is for general wellness tracking only and is not a medical device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Privacy Policy", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubIssuesUrl)))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Report Bug", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(
                        onClick = { showLicensesDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open-Source License (GPL-3.0)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore Backup?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Restoring a backup replaces your current local drink logs, presets, and daily targets with the contents of the selected file.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreBackupJson(uri)
                        pendingRestoreUri = null
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestoreUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear All Local Data?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This permanently deletes all local drink logs, custom presets, and daily target history on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDataConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-Source License", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fillmark is free, open-source software licensed under the GNU General Public License v3.0 (GPL-3.0).")
                    Text(
                        "Built with Android Jetpack (Compose, Room, DataStore, Glance, WorkManager, Health Connect) and Kotlinx Serialization (Apache 2.0).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showLicensesDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubRepoUrl)))
                        }
                    }
                ) {
                    Text("Source Code")
                }
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
