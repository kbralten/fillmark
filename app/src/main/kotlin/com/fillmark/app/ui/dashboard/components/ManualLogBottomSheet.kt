package com.fillmark.app.ui.dashboard.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.ui.common.DateTimePickerDialog
import com.fillmark.app.ui.common.UnitToggle
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLogBottomSheet(
    displayUnit: DisplayUnit,
    hydrationFactorsEnabled: Boolean = true,
    initialTimestampMillis: Long? = null,
    onDismiss: () -> Unit,
    onLogRecord: (volumeMl: Double, factor: Double, drinkType: String, timestamp: Long, label: String?) -> Unit,
    sheetState: SheetState
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)
    val atFormat = stringResource(R.string.date_at_time)

    var selectedUnit by remember { mutableStateOf(displayUnit) }
    var volumeInput by remember {
        mutableStateOf(if (displayUnit == DisplayUnit.METRIC_ML) "250" else "8.5")
    }
    var labelInput by remember { mutableStateOf("") }
    var selectedDrinkType by remember { mutableStateOf(DrinkType.WATER) }
    var hydrationFactorInput by remember {
        mutableStateOf(VolumeConverter.formatHydrationFactor(DrinkType.WATER.defaultHydrationFactor))
    }
    var isFactorCustomized by remember { mutableStateOf(false) }
    var timestampMillis by remember { mutableStateOf(initialTimestampMillis ?: System.currentTimeMillis()) }
    var isOverridden by remember { mutableStateOf(initialTimestampMillis != null) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    val volumeVal = volumeInput.toDoubleOrNull() ?: 0.0
    val internalMl = VolumeConverter.toInternalMl(volumeVal, selectedUnit)
    val parsedFactor = hydrationFactorInput.toDoubleOrNull() ?: 0.0
    val isFactorValid = !hydrationFactorsEnabled || VolumeConverter.isValidHydrationFactor(parsedFactor)
    val activeFactor = if (hydrationFactorsEnabled) {
        if (isFactorValid) parsedFactor else selectedDrinkType.defaultHydrationFactor
    } else {
        selectedDrinkType.defaultHydrationFactor
    }
    val effectiveMl = internalMl * activeFactor
    val isVolumeValid = internalMl > 0 && internalMl <= VolumeConverter.MAX_SINGLE_DRINK_ML
    val volumeErrorText = when {
        volumeInput.isBlank() || internalMl <= 0 -> stringResource(R.string.error_volume_gt_zero)
        internalMl > VolumeConverter.MAX_SINGLE_DRINK_ML -> stringResource(
            R.string.error_max_single_drink,
            VolumeConverter.formatVolume(VolumeConverter.MAX_SINGLE_DRINK_ML, selectedUnit)
        )
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.manual_log_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Volume Section Header with Unit Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.volume_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                UnitToggle(
                    selectedUnit = selectedUnit,
                    onUnitSelected = { newUnit ->
                        if (newUnit != selectedUnit) {
                            volumeInput = VolumeConverter.convertInputVolume(volumeInput, selectedUnit, newUnit)
                            selectedUnit = newUnit
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Volume Input
            OutlinedTextField(
                value = volumeInput,
                onValueChange = { volumeInput = VolumeConverter.sanitizeDecimalInput(it) },
                label = { Text(stringResource(R.string.volume_with_unit, selectedUnit.symbol)) },
                suffix = { Text(selectedUnit.symbol) },
                singleLine = true,
                isError = volumeErrorText != null,
                supportingText = volumeErrorText?.let { msg -> { Text(msg) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = labelInput,
                onValueChange = { labelInput = it },
                label = { Text(stringResource(R.string.drink_name_optional_label)) },
                placeholder = { Text(stringResource(R.string.drink_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Drink Type Selector Chips
            Text(
                text = stringResource(R.string.drink_category_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrinkType.entries.forEach { type ->
                    val isSelected = type == selectedDrinkType
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedDrinkType = type
                            if (!isFactorCustomized) {
                                hydrationFactorInput = VolumeConverter.formatHydrationFactor(
                                    type.defaultHydrationFactor
                                )
                            }
                        },
                        label = { Text(type.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (hydrationFactorsEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hydrationFactorInput,
                    onValueChange = { raw ->
                        hydrationFactorInput = VolumeConverter.sanitizeDecimalInput(raw)
                        isFactorCustomized = true
                    },
                    label = { Text(stringResource(R.string.hydration_factor_label)) },
                    suffix = { Text("x") },
                    trailingIcon = if (abs(parsedFactor - selectedDrinkType.defaultHydrationFactor) > 0.001) {
                        {
                            TextButton(
                                onClick = {
                                    hydrationFactorInput = VolumeConverter.formatHydrationFactor(
                                        selectedDrinkType.defaultHydrationFactor
                                    )
                                    isFactorCustomized = false
                                }
                            ) {
                                Text(stringResource(R.string.action_reset))
                            }
                        }
                    } else null,
                    singleLine = true,
                    isError = !isFactorValid,
                    supportingText = if (!isFactorValid) {
                        { Text(stringResource(R.string.hydration_factor_error)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Hydration Factor Preview
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.manual_log_water_factor,
                                VolumeConverter.formatHydrationFactor(activeFactor)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val effectiveText = if (selectedUnit != displayUnit) {
                            "${VolumeConverter.formatVolume(effectiveMl, selectedUnit)} (${VolumeConverter.formatVolume(effectiveMl, displayUnit)})"
                        } else {
                            VolumeConverter.formatVolume(effectiveMl, selectedUnit)
                        }
                        Text(
                            text = stringResource(R.string.manual_log_effective, effectiveText),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timestamp display & Override Control
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOverridden) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                },
                border = if (isOverridden) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                } else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOverridden) Icons.Outlined.History else Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = if (isOverridden) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOverridden) {
                                    stringResource(R.string.manual_log_overridden_time)
                                } else {
                                    stringResource(R.string.manual_log_time_of_consumption)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isOverridden) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isOverridden) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (isOverridden) {
                                TimeUtils.formatDateTime(
                                    epochMillis = timestampMillis,
                                    is24Hour = is24Hour,
                                    todayLabel = todayLabel,
                                    yesterdayLabel = yesterdayLabel,
                                    atFormat = atFormat
                                )
                            } else {
                                stringResource(
                                    R.string.manual_log_now,
                                    TimeUtils.formatTime(timestampMillis, is24Hour = is24Hour)
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOverridden) {
                            TextButton(
                                onClick = {
                                    timestampMillis = System.currentTimeMillis()
                                    isOverridden = false
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.manual_log_reset_now),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        TextButton(
                            onClick = { showDateTimePicker = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditCalendar,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isOverridden) {
                                    stringResource(R.string.action_change)
                                } else {
                                    stringResource(R.string.manual_log_override_time)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            if (showDateTimePicker) {
                DateTimePickerDialog(
                    initialTimestampMillis = timestampMillis,
                    title = stringResource(R.string.datetime_override_title),
                    disclaimerText = stringResource(R.string.manual_log_override_disclaimer),
                    confirmButtonText = stringResource(R.string.manual_log_set_past_time),
                    onDismiss = { showDateTimePicker = false },
                    onConfirm = { selectedMillis ->
                        timestampMillis = selectedMillis
                        isOverridden = true
                        showDateTimePicker = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isVolumeValid && isFactorValid) {
                        onLogRecord(
                            internalMl,
                            activeFactor,
                            selectedDrinkType.id,
                            timestampMillis,
                            labelInput.trim().ifBlank { null }
                        )
                    }
                },
                enabled = isVolumeValid && isFactorValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.manual_log_submit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
