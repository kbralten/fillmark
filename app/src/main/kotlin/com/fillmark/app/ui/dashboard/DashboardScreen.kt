package com.fillmark.app.ui.dashboard

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.ui.common.UnitToggle
import com.fillmark.app.ui.dashboard.components.CustomizePresetsDialog
import com.fillmark.app.ui.dashboard.components.FluidProgressMeter
import com.fillmark.app.ui.dashboard.components.ManualLogBottomSheet
import com.fillmark.app.ui.dashboard.components.PresetEditorDialog
import com.fillmark.app.ui.dashboard.components.QuickPresetBar
import com.fillmark.app.ui.history.components.EditLogDialog
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkNavy900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
    pendingShortcutPresetId: String? = null,
    pendingOpenManualLog: Boolean = false,
    onShortcutHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val undoActionLabel = stringResource(R.string.action_undo)

    LaunchedEffect(pendingShortcutPresetId, pendingOpenManualLog) {
        if (pendingShortcutPresetId != null) {
            viewModel.logPresetById(pendingShortcutPresetId)
            onShortcutHandled()
        } else if (pendingOpenManualLog) {
            viewModel.openManualLogSheet()
            onShortcutHandled()
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshCurrentTime()
        onPauseOrDispose {}
    }

    // Handle 4-second undo snackbar
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastLog()
            } else {
                viewModel.clearSnackbar()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openManualLogSheet() },
                containerColor = FillmarkCyan,
                contentColor = FillmarkNavy900,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(62.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.dashboard_manual_log_cd),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 0: Brand & Typography Identity Integrated Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.dateHeader,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section 1: Progress Meter & Pacing Area
            item {
                FluidProgressMeter(
                    effectiveVolumeMl = uiState.effectiveVolumeMl,
                    targetMl = uiState.targetMl,
                    displayUnit = uiState.displayUnit,
                    pacingResult = uiState.pacingResult,
                    isTargetOverridden = uiState.isTargetOverridden,
                    onTargetClick = { viewModel.openTodayTargetDialog() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Section 2: Quick Presets Bar
            item {
                QuickPresetBar(
                    presets = uiState.presets,
                    displayUnit = uiState.displayUnit,
                    onPresetClick = { preset -> viewModel.logPreset(preset) },
                    onEditPreset = { preset -> viewModel.openEditPreset(preset) },
                    onCustomizePresets = { viewModel.openCustomizePresets() },
                    onAddPreset = { viewModel.openAddPreset() }
                )
            }

            // Section 3: Today's Logs Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_todays_intake),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.entries_count,
                            uiState.todayRecords.size,
                            uiState.todayRecords.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 4: Today's Logs Items or Empty State
            if (uiState.todayRecords.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        MinimalistGlassIllustration(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .clearAndSetSemantics {}
                        )
                        Text(
                            text = stringResource(R.string.dashboard_empty_today),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(uiState.todayRecords, key = { it.id }) { record ->
                    TodayLogItem(
                        record = record,
                        displayUnit = uiState.displayUnit,
                        hydrationFactorsEnabled = uiState.hydrationFactorsEnabled,
                        onClick = { viewModel.startEditingRecord(record) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        if (uiState.isManualLogSheetOpen) {
            ManualLogBottomSheet(
                displayUnit = uiState.displayUnit,
                hydrationFactorsEnabled = uiState.hydrationFactorsEnabled,
                onDismiss = { viewModel.closeManualLogSheet() },
                onLogRecord = { volume, factor, drinkType, timestamp ->
                    viewModel.logCustom(volume, factor, drinkType, timestamp)
                },
                sheetState = sheetState
            )
        }

        if (uiState.isCustomizePresetsOpen) {
            CustomizePresetsDialog(
                presets = uiState.presets,
                displayUnit = uiState.displayUnit,
                onDismiss = { viewModel.dismissCustomizePresets() },
                onAddPreset = { viewModel.openAddPreset() },
                onEditPreset = { preset -> viewModel.openEditPreset(preset) },
                onDeletePreset = { id -> viewModel.deletePreset(id) },
                onReorderPresets = { updated -> viewModel.reorderPresets(updated) }
            )
        }

        if (uiState.isPresetEditorOpen) {
            val nextOrder = (uiState.presets.maxOfOrNull { it.displayOrder } ?: -1) + 1
            PresetEditorDialog(
                preset = uiState.editingPreset,
                displayUnit = uiState.displayUnit,
                nextDisplayOrder = nextOrder,
                onDismiss = { viewModel.dismissPresetEditor() },
                onSave = { updated -> viewModel.savePreset(updated) },
                onDelete = { id -> viewModel.deletePreset(id) }
            )
        }

        uiState.editingRecord?.let { recordToEdit ->
            EditLogDialog(
                record = recordToEdit,
                displayUnit = uiState.displayUnit,
                onDismiss = { viewModel.dismissEditingRecord() },
                onSave = { updated -> viewModel.saveEditedRecord(updated) },
                onDelete = { viewModel.deleteRecord(recordToEdit) }
            )
        }

        if (uiState.isTodayTargetDialogOpen) {
            AdjustTodayTargetDialog(
                currentTargetMl = uiState.targetMl,
                defaultTargetMl = uiState.defaultTargetMl,
                isOverridden = uiState.isTargetOverridden,
                displayUnit = uiState.displayUnit,
                onDismiss = { viewModel.dismissTodayTargetDialog() },
                onSave = { ml -> viewModel.setTodayTargetOverride(ml) },
                onResetToDefault = { viewModel.resetTodayTargetToDefault() }
            )
        }
    }
}

@Composable
private fun AdjustTodayTargetDialog(
    currentTargetMl: Double,
    defaultTargetMl: Double,
    isOverridden: Boolean,
    displayUnit: DisplayUnit,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onResetToDefault: () -> Unit
) {
    var selectedUnit by remember { mutableStateOf(displayUnit) }
    var targetInput by remember {
        mutableStateOf(
            if (currentTargetMl > 0) {
                VolumeConverter.formatInputNumber(currentTargetMl, displayUnit)
            } else {
                if (displayUnit == DisplayUnit.METRIC_ML) "2500" else "85"
            }
        )
    }

    val parsed = targetInput.toDoubleOrNull() ?: 0.0
    val ml = VolumeConverter.toInternalMl(parsed, selectedUnit)
    val isValid = ml > 0 && ml <= VolumeConverter.MAX_DAILY_TARGET_ML
    val errorText = when {
        targetInput.isBlank() || ml <= 0 -> stringResource(R.string.error_target_gt_zero)
        ml > VolumeConverter.MAX_DAILY_TARGET_ML -> stringResource(
            R.string.error_max_daily_target,
            VolumeConverter.formatVolume(VolumeConverter.MAX_DAILY_TARGET_ML, selectedUnit)
        )
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dashboard_today_goal_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_today_goal_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.unit_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UnitToggle(
                        selectedUnit = selectedUnit,
                        onUnitSelected = { newUnit ->
                            if (newUnit != selectedUnit) {
                                targetInput = VolumeConverter.convertInputVolume(targetInput, selectedUnit, newUnit)
                                selectedUnit = newUnit
                            }
                        }
                    )
                }

                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = VolumeConverter.sanitizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.dashboard_today_target_input, selectedUnit.symbol)) },
                    suffix = { Text(selectedUnit.symbol) },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { msg -> { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isOverridden) {
                    val defaultText = if (defaultTargetMl > 0) {
                        VolumeConverter.formatVolume(defaultTargetMl, selectedUnit)
                    } else {
                        stringResource(R.string.dashboard_no_goal)
                    }
                    TextButton(
                        onClick = onResetToDefault,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.dashboard_reset_to_default, defaultText))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onSave(ml) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.dashboard_save_for_today))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun MinimalistGlassIllustration(modifier: Modifier = Modifier) {
    val glassOutlineColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
    val rippleLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    Canvas(modifier = modifier.size(54.dp, 72.dp)) {
        val w = size.width
        val h = size.height

        val rimY = 6.dp.toPx()
        val baseY = h - 6.dp.toPx()
        val topWidth = w * 0.78f
        val bottomWidth = w * 0.58f

        val topLeftX = (w - topWidth) / 2
        val topRightX = topLeftX + topWidth
        val bottomLeftX = (w - bottomWidth) / 2
        val bottomRightX = bottomLeftX + bottomWidth

        // Water level inside (at ~50% glass height)
        val waterY = rimY + (baseY - rimY) * 0.50f
        val waterTopWidth = topWidth - (topWidth - bottomWidth) * 0.50f
        val waterLeftX = (w - waterTopWidth) / 2
        val waterRightX = waterLeftX + waterTopWidth

        // Subtle water fill
        val waterFill = Path().apply {
            moveTo(waterLeftX, waterY)
            cubicTo(
                w * 0.4f, waterY - 3.dp.toPx(),
                w * 0.6f, waterY + 3.dp.toPx(),
                waterRightX, waterY
            )
            lineTo(bottomRightX - 1.dp.toPx(), baseY)
            quadraticTo(w / 2, baseY + 3.dp.toPx(), bottomLeftX + 1.dp.toPx(), baseY)
            close()
        }
        drawPath(
            path = waterFill,
            color = FillmarkCyan.copy(alpha = 0.16f)
        )

        // Water surface line
        val ripplePath = Path().apply {
            moveTo(waterLeftX + 1.dp.toPx(), waterY)
            cubicTo(
                w * 0.4f, waterY - 3.dp.toPx(),
                w * 0.6f, waterY + 3.dp.toPx(),
                waterRightX - 1.dp.toPx(), waterY
            )
        }
        drawPath(
            path = ripplePath,
            color = rippleLineColor,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // Glass body outline
        val glassPath = Path().apply {
            moveTo(topLeftX, rimY)
            lineTo(bottomLeftX, baseY)
            quadraticTo(w / 2, baseY + 3.5.dp.toPx(), bottomRightX, baseY)
            lineTo(topRightX, rimY)
        }
        drawPath(
            path = glassPath,
            color = glassOutlineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Rim ellipse
        val rimPath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    topLeftX, rimY - 3.dp.toPx(),
                    topRightX, rimY + 3.dp.toPx()
                )
            )
        }
        drawPath(
            path = rimPath,
            color = glassOutlineColor,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Delicate glass reflection line on left side
        drawLine(
            color = Color.White.copy(alpha = 0.8f),
            start = Offset(topLeftX + 4.dp.toPx(), rimY + 8.dp.toPx()),
            end = Offset(bottomLeftX + 3.dp.toPx(), baseY - 12.dp.toPx()),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun TodayLogItem(
    record: HydrationRecordEntity,
    displayUnit: DisplayUnit,
    hydrationFactorsEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = FillmarkCyan.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = PresetIcon.getDrawableRes(record.iconKey)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = record.drinkType.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = TimeUtils.formatTime(record.timestamp, is24Hour = is24Hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = VolumeConverter.formatVolume(record.countedVolumeMl(hydrationFactorsEnabled), displayUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hydrationFactorsEnabled && record.hydrationFactor != 1.0) {
                    Text(
                        text = stringResource(
                            R.string.dashboard_raw_volume,
                            VolumeConverter.formatVolume(record.volumeMl, displayUnit)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
