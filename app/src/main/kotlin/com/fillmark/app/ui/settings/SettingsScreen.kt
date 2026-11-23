package com.fillmark.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.fillmark.app.ui.common.DailyGoalEstimatorDialog
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
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

    var pendingInboundEnableOnReadGrant by remember { mutableStateOf(false) }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        viewModel.refreshHealthConnectStatus()
        val hasWrite = hcManager.writePermission in grantedPermissions
        val hasRead = hcManager.readPermission in grantedPermissions
        if (hasWrite && !uiState.healthConnectSyncEnabled) {
            viewModel.setHealthConnectSync(
                enabled = true,
                bidirectional = uiState.healthConnectBidirectional && hasRead
            )
        } else if (hasRead && pendingInboundEnableOnReadGrant) {
            pendingInboundEnableOnReadGrant = false
            viewModel.setHealthConnectSync(
                enabled = uiState.healthConnectSyncEnabled || hasWrite,
                bidirectional = true
            )
        } else {
            pendingInboundEnableOnReadGrant = false
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

    val focusManager = LocalFocusManager.current
    val hasTarget = uiState.defaultTargetMl > 0
    val targetInputState = remember {
        DailyTargetInputState(
            initialTargetMl = uiState.defaultTargetMl,
            initialUnit = uiState.displayUnit,
            initialHasTarget = hasTarget,
            initialRevision = uiState.dataRevision
        )
    }

    LaunchedEffect(uiState.displayUnit, hasTarget, uiState.defaultTargetMl, uiState.dataRevision) {
        targetInputState.syncFromExternal(
            defaultTargetMl = uiState.defaultTargetMl,
            displayUnit = uiState.displayUnit,
            hasTarget = hasTarget,
            dataRevision = uiState.dataRevision
        )
    }

    val latestUnit by rememberUpdatedState(uiState.displayUnit)
    val latestSavedTargetMl by rememberUpdatedState(uiState.defaultTargetMl)
    val latestHasTarget by rememberUpdatedState(hasTarget)

    fun commitPendingTarget() {
        if (!latestHasTarget || !targetInputState.isDirty) return
        targetInputState.commitIfValid(
            displayUnit = latestUnit,
            savedTargetMl = latestSavedTargetMl,
            onCommit = { viewModel.setDefaultTargetMl(it) }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            commitPendingTarget()
        }
    }

    val targetInput = targetInputState.text
    val parsedTarget = targetInput.toDoubleOrNull() ?: 0.0
    val targetInternalMl = VolumeConverter.toInternalMl(parsedTarget, uiState.displayUnit)
    val targetErrorText = when {
        !hasTarget -> null
        targetInput.isBlank() || targetInternalMl <= 0 -> stringResource(R.string.error_target_gt_zero)
        targetInternalMl > VolumeConverter.MAX_DAILY_TARGET_ML -> stringResource(
            R.string.error_max_daily_target,
            VolumeConverter.formatVolume(VolumeConverter.MAX_DAILY_TARGET_ML, uiState.displayUnit)
        )
        else -> null
    }
    var targetFieldWasFocused by remember { mutableStateOf(false) }

    var showWakeTimePicker by remember { mutableStateOf(false) }
    var showSleepTimePicker by remember { mutableStateOf(false) }
    var showEstimatorDialog by remember { mutableStateOf(false) }

    val privacyUrl = stringResource(R.string.privacy_policy_url)
    val githubIssuesUrl = stringResource(R.string.github_issues_url)
    val githubRepoUrl = stringResource(R.string.github_repo_url)
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)
    val atFormat = stringResource(R.string.date_at_time)

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
                text = stringResource(R.string.settings_title),
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
                        text = stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                                    text = stringResource(R.string.settings_dynamic_color),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.settings_dynamic_color_desc),
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
                        text = stringResource(R.string.settings_display_units),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = uiState.displayUnit == DisplayUnit.METRIC_ML,
                            onClick = { viewModel.setDisplayUnit(DisplayUnit.METRIC_ML) },
                            label = { Text(stringResource(R.string.unit_metric_full)) }
                        )
                        FilterChip(
                            selected = uiState.displayUnit == DisplayUnit.IMPERIAL_FL_OZ,
                            onClick = { viewModel.setDisplayUnit(DisplayUnit.IMPERIAL_FL_OZ) },
                            label = { Text(stringResource(R.string.unit_imperial_full)) }
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
                                text = stringResource(R.string.settings_daily_goal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasTarget) {
                                    stringResource(R.string.settings_daily_goal_desc_on)
                                } else {
                                    stringResource(R.string.settings_daily_goal_desc_off)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hasTarget,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    val defaultValInUnit = if (uiState.displayUnit == DisplayUnit.METRIC_ML) 2500.0 else 85.0
                                    targetInputState.setDisplayText(
                                        if (uiState.displayUnit == DisplayUnit.METRIC_ML) "2500" else "85"
                                    )
                                    targetInputState.markExternalTargetChange(true)
                                    viewModel.setDefaultTarget(defaultValInUnit)
                                } else {
                                    targetInputState.markExternalTargetChange(false)
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
                                targetInputState.onTextChanged(raw, uiState.displayUnit) {}
                            },
                            label = { Text(stringResource(R.string.settings_daily_target_label)) },
                            suffix = { Text(uiState.displayUnit.symbol) },
                            trailingIcon = if (targetInputState.isDirty && targetErrorText == null) {
                                {
                                    TextButton(
                                        onClick = {
                                            commitPendingTarget()
                                            focusManager.clearFocus()
                                        }
                                    ) {
                                        Text(stringResource(R.string.action_apply))
                                    }
                                }
                            } else {
                                null
                            },
                            singleLine = true,
                            isError = targetErrorText != null,
                            supportingText = targetErrorText?.let { msg -> { Text(msg) } },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    commitPendingTarget()
                                    focusManager.clearFocus()
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (targetFieldWasFocused && !focusState.isFocused) {
                                        commitPendingTarget()
                                    }
                                    targetFieldWasFocused = focusState.isFocused
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { showEstimatorDialog = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.estimator_button))
                        }
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
                                text = stringResource(R.string.settings_hydration_factors),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.hydrationFactorsEnabled) {
                                    stringResource(R.string.settings_hydration_factors_on)
                                } else {
                                    stringResource(R.string.settings_hydration_factors_off)
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
                                text = stringResource(R.string.settings_waking_pacing),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_waking_pacing_desc),
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
                                label = stringResource(R.string.settings_wake_time),
                                timeStr = uiState.wakingHoursStart,
                                onClick = { showWakeTimePicker = true },
                                modifier = Modifier.weight(1f)
                            )
                            TimePickerField(
                                label = stringResource(R.string.settings_sleep_time),
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
                        text = stringResource(R.string.settings_hc_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when {
                            uiState.isHealthConnectAvailable && uiState.hasHealthConnectPermissions ->
                                stringResource(R.string.settings_hc_connected)
                            uiState.isHealthConnectAvailable && uiState.hasHealthConnectWritePermission ->
                                stringResource(R.string.settings_hc_connected_outbound_only)
                            uiState.isHealthConnectAvailable && uiState.hasHealthConnectReadPermission ->
                                stringResource(R.string.settings_hc_connected_read_only)
                            uiState.isHealthConnectAvailable ->
                                stringResource(R.string.settings_hc_needs_perms)
                            uiState.healthConnectSdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                stringResource(R.string.settings_hc_update_required)
                            else ->
                                stringResource(R.string.settings_hc_unavailable)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.hasHealthConnectWritePermission) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (uiState.healthConnectSdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { HealthConnectManager.openPlayStore(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_hc_install_update))
                        }
                    } else if (uiState.isHealthConnectAvailable && !uiState.hasHealthConnectWritePermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                requestPermissionsLauncher.launch(hcManager.permissions)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (uiState.hasHealthConnectReadPermission) {
                                    stringResource(R.string.settings_hc_grant_write_perm)
                                } else {
                                    stringResource(R.string.settings_hc_grant_perms)
                                }
                            )
                        }
                    }

                    if (uiState.isHealthConnectAvailable && uiState.hasHealthConnectWritePermission) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_hc_outbound_sync),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = uiState.healthConnectSyncEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setHealthConnectSync(
                                        enabled,
                                        uiState.healthConnectBidirectional && uiState.hasHealthConnectReadPermission
                                    )
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
                                    text = stringResource(R.string.settings_hc_inbound_sync),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.hasHealthConnectReadPermission) {
                                        stringResource(R.string.settings_hc_inbound_sync_desc)
                                    } else {
                                        stringResource(R.string.settings_hc_inbound_needs_read)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.healthConnectBidirectional && uiState.hasHealthConnectReadPermission,
                                enabled = uiState.healthConnectSyncEnabled,
                                onCheckedChange = { bi ->
                                    if (bi && !uiState.hasHealthConnectReadPermission) {
                                        pendingInboundEnableOnReadGrant = true
                                        requestPermissionsLauncher.launch(setOf(hcManager.readPermission))
                                    } else {
                                        viewModel.setHealthConnectSync(uiState.healthConnectSyncEnabled, bi)
                                    }
                                }
                            )
                        }

                        if (!uiState.hasHealthConnectReadPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    pendingInboundEnableOnReadGrant = true
                                    requestPermissionsLauncher.launch(setOf(hcManager.readPermission))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.settings_hc_grant_read_perm))
                            }
                        }

                        if (uiState.healthConnectSyncEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            val lastSyncText = if (uiState.lastHealthConnectSyncMillis > 0L) {
                                val formatted = TimeUtils.formatDateTime(
                                    epochMillis = uiState.lastHealthConnectSyncMillis,
                                    is24Hour = is24Hour,
                                    todayLabel = todayLabel,
                                    yesterdayLabel = yesterdayLabel,
                                    atFormat = atFormat
                                )
                                stringResource(R.string.settings_hc_last_synced, formatted)
                            } else {
                                stringResource(R.string.settings_hc_last_synced_never)
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
                                    Text(stringResource(R.string.settings_hc_syncing))
                                } else {
                                    Text(stringResource(R.string.settings_hc_sync_now))
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
                        text = stringResource(R.string.settings_data_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_data_backup_desc),
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
                            Text(stringResource(R.string.settings_export_json))
                        }
                        OutlinedButton(
                            onClick = { restoreJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = !uiState.isBackupBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_restore_json))
                        }
                    }
                    OutlinedButton(
                        onClick = { exportCsvLauncher.launch("fillmark-logs-${LocalDate.now()}.csv") },
                        enabled = !uiState.isBackupBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_export_csv))
                    }
                    TextButton(
                        onClick = { showClearDataConfirm = true },
                        enabled = !uiState.isBackupBusy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_clear_all))
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
                            text = stringResource(R.string.settings_widgets_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.settings_widgets_desc),
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
                                Text(stringResource(R.string.settings_widget_full), style = MaterialTheme.typography.labelMedium)
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
                                Text(stringResource(R.string.settings_widget_progress), style = MaterialTheme.typography.labelMedium)
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
                                Text(stringResource(R.string.settings_widget_quick_bar), style = MaterialTheme.typography.labelMedium)
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
                        text = stringResource(R.string.settings_about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_version_format,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.wellness_disclaimer),
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
                            Text(stringResource(R.string.settings_privacy_policy), style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubIssuesUrl)))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_report_bug), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(
                        onClick = { showLicensesDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_open_source_license))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showWakeTimePicker) {
        TimePickerModalDialog(
            title = stringResource(R.string.settings_set_wake_time),
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
            title = stringResource(R.string.settings_set_sleep_time),
            initialTime = uiState.wakingHoursEnd,
            onDismiss = { showSleepTimePicker = false },
            onConfirm = { newTime ->
                viewModel.setWakingSchedule(uiState.wakingHoursStart, newTime, uiState.wakingPacingEnabled)
                showSleepTimePicker = false
            }
        )
    }

    if (showEstimatorDialog) {
        DailyGoalEstimatorDialog(
            displayUnit = uiState.displayUnit,
            onDismiss = { showEstimatorDialog = false },
            onApplyEstimateMl = { estimatedMl ->
                targetInputState.setDisplayText(
                    VolumeConverter.formatInputNumber(estimatedMl, uiState.displayUnit)
                )
                viewModel.setDefaultTargetMl(estimatedMl)
                showEstimatorDialog = false
            }
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.settings_restore_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_restore_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreBackupJson(uri)
                        pendingRestoreUri = null
                    }
                ) {
                    Text(stringResource(R.string.action_restore))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestoreUri = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_delete_everything))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDataConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text(stringResource(R.string.settings_license_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_license_dialog_gpl))
                    Text(
                        text = stringResource(R.string.settings_license_dialog_libs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showLicensesDialog = false }) {
                    Text(stringResource(R.string.action_close))
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
                    Text(stringResource(R.string.settings_source_code))
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
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)

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
                    text = TimeUtils.formatTimeString(timeStr, is24Hour = is24Hour),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.settings_pick_time_cd, label),
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
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val initialLocalTime = TimeUtils.parseTimeString(initialTime)
    val timePickerState = rememberTimePickerState(
        initialHour = initialLocalTime.hour,
        initialMinute = initialLocalTime.minute,
        is24Hour = is24Hour
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
                    val formatted = String.format(Locale.US, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    onConfirm(formatted)
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
