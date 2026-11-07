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
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.fillmark.app.ui.theme.FillmarkCardBorder
import com.fillmark.app.ui.theme.FillmarkCyanDark

@Composable
fun EditLogDialog(
    record: HydrationRecordEntity,
    displayUnit: DisplayUnit,
    onDismiss: () -> Unit,
    onSave: (HydrationRecordEntity) -> Unit
) {
    val initialDisplayVal = VolumeConverter.toDisplayValue(record.volumeMl, displayUnit)
    var volumeInput by remember { mutableStateOf(VolumeConverter.formatNumberOnly(record.volumeMl, displayUnit)) }
    var selectedDrinkType by remember { mutableStateOf(DrinkType.fromId(record.drinkType)) }
    var selectedTimestamp by remember { mutableStateOf(record.timestamp) }
    var showDateTimePicker by remember { mutableStateOf(false) }

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
                OutlinedTextField(
                    value = volumeInput,
                    onValueChange = { volumeInput = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Volume (${displayUnit.symbol})") },
                    singleLine = true,
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
                    border = BorderStroke(1.dp, FillmarkCardBorder),
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
                                tint = FillmarkCyanDark,
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
                    val inputVal = volumeInput.toDoubleOrNull() ?: 0.0
                    val ml = VolumeConverter.toInternalMl(inputVal, displayUnit)
                    if (ml > 0) {
                        onSave(
                            record.copy(
                                volumeMl = ml,
                                hydrationFactor = selectedDrinkType.defaultHydrationFactor,
                                drinkType = selectedDrinkType.id,
                                timestamp = selectedTimestamp
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
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


