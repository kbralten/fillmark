package com.fillmark.app.ui.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.PresetEntity
import java.util.UUID

@Composable
fun PresetEditorDialog(
    preset: PresetEntity?, // null if adding new preset
    displayUnit: DisplayUnit,
    onDismiss: () -> Unit,
    onSave: (PresetEntity) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var labelInput by remember { mutableStateOf(preset?.label ?: "Cup") }
    var volumeInput by remember {
        mutableStateOf(
            if (preset != null) {
                VolumeConverter.formatNumberOnly(preset.volumeMl, displayUnit)
            } else {
                if (displayUnit == DisplayUnit.METRIC_ML) "250" else "8.5"
            }
        )
    }
    var selectedIcon by remember {
        mutableStateOf(PresetIcon.fromId(preset?.iconKey))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (preset == null) "Add Quick Preset" else "Edit Preset",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Preset Name
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text("Preset Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Volume input in active unit
                OutlinedTextField(
                    value = volumeInput,
                    onValueChange = { volumeInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Standard Volume (${displayUnit.symbol})") },
                    suffix = { Text(displayUnit.symbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Picker
                Text(
                    text = "Select Icon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetIcon.entries.forEach { icon ->
                        val isSelected = icon == selectedIcon
                        val border = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = bg),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedIcon = icon }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = icon.drawableRes),
                                    contentDescription = icon.displayName,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val inputVal = volumeInput.toDoubleOrNull() ?: 0.0
                    val ml = VolumeConverter.toInternalMl(inputVal, displayUnit)
                    if (labelInput.isNotBlank() && ml > 0) {
                        val updated = preset?.copy(
                            label = labelInput.trim(),
                            volumeMl = ml,
                            iconKey = selectedIcon.id
                        ) ?: PresetEntity(
                            id = UUID.randomUUID().toString(),
                            label = labelInput.trim(),
                            volumeMl = ml,
                            iconKey = selectedIcon.id,
                            hydrationFactor = 1.0,
                            displayOrder = 99
                        )
                        onSave(updated)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (preset != null && onDelete != null) {
                    IconButton(onClick = { onDelete(preset.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete preset",
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
}

