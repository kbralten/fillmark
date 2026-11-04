package com.fillmark.app.ui.history.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Hydration Entry",
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
                                drinkType = selectedDrinkType.id
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
}

