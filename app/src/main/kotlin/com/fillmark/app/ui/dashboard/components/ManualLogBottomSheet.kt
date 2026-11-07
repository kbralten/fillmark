package com.fillmark.app.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.fillmark.app.ui.common.DateTimePickerDialog
import java.time.LocalTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLogBottomSheet(
    displayUnit: DisplayUnit,
    onDismiss: () -> Unit,
    onLogRecord: (volumeMl: Double, factor: Double, drinkType: String, timestamp: Long) -> Unit,
    sheetState: SheetState
) {
    var volumeInput by remember { mutableStateOf("250") }
    var selectedDrinkType by remember { mutableStateOf(DrinkType.WATER) }
    var timestampMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var isOverridden by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    val volumeVal = volumeInput.toDoubleOrNull() ?: 0.0
    val internalMl = VolumeConverter.toInternalMl(volumeVal, displayUnit)
    val effectiveMl = internalMl * selectedDrinkType.defaultHydrationFactor

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Log Drink",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Volume Input
            OutlinedTextField(
                value = volumeInput,
                onValueChange = { volumeInput = it.filter { char -> char.isDigit() || char == '.' } },
                label = { Text("Volume (${displayUnit.symbol})") },
                suffix = { Text(displayUnit.symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Drink Type Selector Chips
            Text(
                text = "Drink Category",
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
                        onClick = { selectedDrinkType = type },
                        label = { Text(type.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        text = "Water Factor: ${selectedDrinkType.defaultHydrationFactor}x",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Effective: ${VolumeConverter.formatVolume(effectiveMl, displayUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                                text = if (isOverridden) "Overridden Time" else "Time of Consumption",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isOverridden) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isOverridden) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (isOverridden) {
                                TimeUtils.formatDateTime(timestampMillis)
                            } else {
                                "Now (${TimeUtils.formatTime(timestampMillis)})"
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
                                    text = "Reset to Now",
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
                                text = if (isOverridden) "Change" else "Override time/date...",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            if (showDateTimePicker) {
                DateTimePickerDialog(
                    initialTimestampMillis = timestampMillis,
                    title = "Override Date & Time",
                    disclaimerText = "Fillmark works best when drinks are tracked in real-time. Only override if you forgot to log a past drink.",
                    confirmButtonText = "Set Past Time",
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
                    if (internalMl > 0) {
                        onLogRecord(
                            internalMl,
                            selectedDrinkType.defaultHydrationFactor,
                            selectedDrinkType.id,
                            timestampMillis
                        )
                    }
                },
                enabled = internalMl > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Log Intake",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

