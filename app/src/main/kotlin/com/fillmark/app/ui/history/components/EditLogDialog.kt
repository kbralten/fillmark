package com.fillmark.app.ui.history.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.ui.common.DateTimePickerDialog
import com.fillmark.app.ui.common.UnitToggle
import kotlin.math.abs

@Composable
fun EditLogDialog(
    record: HydrationRecordEntity,
    displayUnit: DisplayUnit,
    hydrationFactorsEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (HydrationRecordEntity) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)
    val atFormat = stringResource(R.string.date_at_time)

    var selectedUnit by remember { mutableStateOf(displayUnit) }
    var volumeInput by remember {
        mutableStateOf(VolumeConverter.formatInputNumber(record.volumeMl, displayUnit))
    }
    var labelInput by remember { mutableStateOf(record.label.orEmpty()) }
    var selectedDrinkType by remember { mutableStateOf(DrinkType.fromId(record.drinkType)) }
    var hydrationFactorInput by remember {
        mutableStateOf(VolumeConverter.formatHydrationFactor(record.hydrationFactor))
    }
    var selectedTimestamp by remember { mutableLongStateOf(record.timestamp) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    val inputVal = VolumeConverter.parseDecimalInput(volumeInput) ?: 0.0
    val ml = VolumeConverter.toInternalMl(inputVal, selectedUnit)
    val parsedFactor = VolumeConverter.parseDecimalInput(hydrationFactorInput) ?: 0.0
    val isFactorValid = !hydrationFactorsEnabled || VolumeConverter.isValidHydrationFactor(parsedFactor)
    val activeFactor = if (hydrationFactorsEnabled) {
        if (isFactorValid) parsedFactor else record.hydrationFactor
    } else {
        record.hydrationFactor
    }
    val isVolumeValid = ml > 0 && ml <= VolumeConverter.MAX_SINGLE_DRINK_ML
    val volumeErrorText = when {
        volumeInput.isBlank() || ml <= 0 -> stringResource(R.string.error_volume_gt_zero)
        ml > VolumeConverter.MAX_SINGLE_DRINK_ML -> stringResource(
            R.string.error_max_single_drink,
            VolumeConverter.formatVolume(VolumeConverter.MAX_SINGLE_DRINK_ML, selectedUnit)
        )
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.edit_log_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.volume_label),
                        style = MaterialTheme.typography.labelMedium,
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

                OutlinedTextField(
                    value = volumeInput,
                    onValueChange = { volumeInput = VolumeConverter.sanitizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.volume_with_unit, selectedUnit.symbol)) },
                    suffix = { Text(selectedUnit.symbol) },
                    singleLine = true,
                    isError = volumeErrorText != null,
                    supportingText = volumeErrorText?.let { msg -> { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

                Text(
                    text = stringResource(R.string.drink_category_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DrinkType.entries.forEach { type ->
                        FilterChip(
                            selected = type == selectedDrinkType,
                            onClick = {
                                if (type != selectedDrinkType) {
                                    selectedDrinkType = type
                                    hydrationFactorInput = VolumeConverter.formatHydrationFactor(
                                        type.defaultHydrationFactor
                                    )
                                }
                            },
                            label = { Text(stringResource(type.labelRes)) }
                        )
                    }
                }

                if (hydrationFactorsEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = hydrationFactorInput,
                        onValueChange = { hydrationFactorInput = VolumeConverter.sanitizeDecimalInput(it) },
                        label = { Text(stringResource(R.string.hydration_factor_label)) },
                        suffix = { Text("x") },
                        trailingIcon = if (abs(parsedFactor - selectedDrinkType.defaultHydrationFactor) > 0.001) {
                            {
                                TextButton(
                                    onClick = {
                                        hydrationFactorInput = VolumeConverter.formatHydrationFactor(
                                            selectedDrinkType.defaultHydrationFactor
                                        )
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.edit_log_datetime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDateTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = TimeUtils.formatDateTime(
                                    epochMillis = selectedTimestamp,
                                    is24Hour = is24Hour,
                                    todayLabel = todayLabel,
                                    yesterdayLabel = yesterdayLabel,
                                    atFormat = atFormat
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = stringResource(R.string.action_change),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isVolumeValid && isFactorValid) {
                        focusManager.clearFocus()
                        onSave(
                            record.copy(
                                volumeMl = ml,
                                hydrationFactor = activeFactor,
                                drinkType = selectedDrinkType.id,
                                timestamp = selectedTimestamp,
                                label = labelInput.trim().ifBlank { null }
                            )
                        )
                    }
                },
                enabled = isVolumeValid && isFactorValid
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.edit_log_delete_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )

    if (showDateTimePicker) {
        DateTimePickerDialog(
            initialTimestampMillis = selectedTimestamp,
            title = stringResource(R.string.edit_log_change_datetime_title),
            disclaimerText = null,
            confirmButtonText = stringResource(R.string.edit_log_save_datetime),
            onDismiss = { showDateTimePicker = false },
            onConfirm = { updatedMillis ->
                selectedTimestamp = updatedMillis
                showDateTimePicker = false
            }
        )
    }
}
