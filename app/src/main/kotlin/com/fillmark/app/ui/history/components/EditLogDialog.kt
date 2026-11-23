package com.fillmark.app.ui.history.components

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.ui.common.DateTimePickerDialog
import com.fillmark.app.ui.common.UnitToggle

@Composable
fun EditLogDialog(
    record: HydrationRecordEntity,
    displayUnit: DisplayUnit,
    onDismiss: () -> Unit,
    onSave: (HydrationRecordEntity) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var selectedUnit by remember { mutableStateOf(displayUnit) }
    var volumeInput by remember {
        mutableStateOf(VolumeConverter.formatInputNumber(record.volumeMl, displayUnit))
    }
    var selectedDrinkType by remember { mutableStateOf(DrinkType.fromId(record.drinkType)) }
    var selectedTimestamp by remember { mutableStateOf(record.timestamp) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    val inputVal = volumeInput.toDoubleOrNull() ?: 0.0
    val ml = VolumeConverter.toInternalMl(inputVal, selectedUnit)
    val isVolumeValid = ml > 0 && ml <= VolumeConverter.MAX_SINGLE_DRINK_ML
    val volumeErrorText = when {
        volumeInput.isBlank() || ml <= 0 -> "Enter a volume greater than 0"
        ml > VolumeConverter.MAX_SINGLE_DRINK_ML ->
            "Max single drink is ${VolumeConverter.formatVolume(VolumeConverter.MAX_SINGLE_DRINK_ML, selectedUnit)}"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Drink Entry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Volume",
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
                    label = { Text("Volume (${selectedUnit.symbol})") },
                    suffix = { Text(selectedUnit.symbol) },
                    singleLine = true,
                    isError = volumeErrorText != null,
                    supportingText = volumeErrorText?.let { msg -> { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Drink Category",
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
                            onClick = { selectedDrinkType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Date & Time",
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
                                text = TimeUtils.formatDateTime(selectedTimestamp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Change",
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
                    if (isVolumeValid) {
                        onSave(
                            record.copy(
                                volumeMl = ml,
                                hydrationFactor = selectedDrinkType.defaultHydrationFactor,
                                drinkType = selectedDrinkType.id,
                                timestamp = selectedTimestamp
                            )
                        )
                    }
                },
                enabled = isVolumeValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete entry",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )

    if (showDateTimePicker) {
        DateTimePickerDialog(
            initialTimestampMillis = selectedTimestamp,
            title = "Change Date & Time",
            disclaimerText = null,
            confirmButtonText = "Save Date & Time",
            onDismiss = { showDateTimePicker = false },
            onConfirm = { updatedMillis ->
                selectedTimestamp = updatedMillis
                showDateTimePicker = false
            }
        )
    }
}


