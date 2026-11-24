package com.fillmark.app.ui.dashboard.components

import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.PresetEntity
import com.fillmark.app.ui.common.UnitToggle
import java.util.UUID
import kotlin.math.abs

val PRESET_PALETTE = listOf(
    "#2196F3", // Blue
    "#03A9F4", // Light Blue
    "#00BCD4", // Cyan
    "#009688", // Teal
    "#4CAF50", // Green
    "#8BC34A", // Lime
    "#FF9800", // Orange
    "#795548", // Brown / Coffee
    "#9C27B0", // Purple
    "#E91E63", // Pink
    "#F44336", // Red
    "#607D8B"  // Slate
)

fun parseHexColor(hex: String?, fallback: Color = Color(0xFF2196F3)): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(hex.toColorInt())
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun PresetEditorDialog(
    preset: PresetEntity?, // null if adding new preset
    displayUnit: DisplayUnit,
    nextDisplayOrder: Int = 0,
    onDismiss: () -> Unit,
    onSave: (PresetEntity) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val defaultPresetName = stringResource(R.string.preset_default_name)
    var selectedUnit by remember { mutableStateOf(displayUnit) }
    var labelInput by remember { mutableStateOf(preset?.label ?: defaultPresetName) }
    var volumeInput by remember {
        mutableStateOf(
            if (preset != null) {
                VolumeConverter.formatInputNumber(preset.volumeMl, displayUnit)
            } else {
                if (displayUnit == DisplayUnit.METRIC_ML) "250" else "8.5"
            }
        )
    }
    var selectedDrinkType by remember {
        mutableStateOf(DrinkType.fromId(preset?.drinkType ?: "WATER"))
    }
    var hydrationFactorInput by remember {
        mutableStateOf(
            VolumeConverter.formatHydrationFactor(
                preset?.hydrationFactor ?: DrinkType.fromId(preset?.drinkType ?: "WATER").defaultHydrationFactor
            )
        )
    }
    var isFactorCustomized by remember {
        mutableStateOf(
            preset != null &&
                abs(preset.hydrationFactor - DrinkType.fromId(preset.drinkType).defaultHydrationFactor) > 0.001
        )
    }
    var selectedColorHex by remember {
        mutableStateOf(preset?.colorHex ?: "#2196F3")
    }
    var selectedIcon by remember {
        mutableStateOf(PresetIcon.fromId(preset?.iconKey))
    }

    val inputVal = VolumeConverter.parseDecimalInput(volumeInput) ?: 0.0
    val ml = VolumeConverter.toInternalMl(inputVal, selectedUnit)
    val parsedFactor = VolumeConverter.parseDecimalInput(hydrationFactorInput) ?: 0.0
    val isFactorValid = VolumeConverter.isValidHydrationFactor(parsedFactor)
    val isLabelValid = labelInput.isNotBlank()
    val isVolumeValid = ml > 0 && ml <= VolumeConverter.MAX_SINGLE_DRINK_ML
    val volumeErrorText = when {
        volumeInput.isBlank() || ml <= 0 -> stringResource(R.string.error_volume_gt_zero)
        ml > VolumeConverter.MAX_SINGLE_DRINK_ML -> stringResource(
            R.string.error_max_single_drink,
            VolumeConverter.formatVolume(VolumeConverter.MAX_SINGLE_DRINK_ML, selectedUnit)
        )
        else -> null
    }
    val canSave = isLabelValid && isVolumeValid && isFactorValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (preset == null) {
                    stringResource(R.string.preset_add_title)
                } else {
                    stringResource(R.string.preset_edit_title)
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Preset Name
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(stringResource(R.string.preset_name_label)) },
                    singleLine = true,
                    isError = !isLabelValid,
                    supportingText = if (!isLabelValid) {
                        { Text(stringResource(R.string.preset_name_error)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Standard Volume Section Header with Unit Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.preset_standard_volume),
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

                // Volume input in active unit
                OutlinedTextField(
                    value = volumeInput,
                    onValueChange = { volumeInput = VolumeConverter.sanitizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.preset_standard_volume_unit, selectedUnit.symbol)) },
                    suffix = { Text(selectedUnit.symbol) },
                    singleLine = true,
                    isError = volumeErrorText != null,
                    supportingText = volumeErrorText?.let { msg -> { Text(msg) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Drink Category Selector
                Text(
                    text = stringResource(R.string.drink_category_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
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
                                when (type) {
                                    DrinkType.COFFEE -> {
                                        selectedIcon = PresetIcon.MUG
                                        if (selectedColorHex == "#2196F3") selectedColorHex = "#795548"
                                    }
                                    DrinkType.TEA -> {
                                        selectedIcon = PresetIcon.TUMBLER
                                        if (selectedColorHex == "#2196F3") selectedColorHex = "#4CAF50"
                                    }
                                    DrinkType.JUICE -> {
                                        if (selectedColorHex == "#2196F3") selectedColorHex = "#FF9800"
                                    }
                                    DrinkType.ELECTROLYTE -> {
                                        if (selectedColorHex == "#2196F3") selectedColorHex = "#9C27B0"
                                    }
                                    DrinkType.SODA -> {
                                        selectedIcon = PresetIcon.CAN
                                        if (selectedColorHex == "#2196F3") selectedColorHex = "#607D8B"
                                    }
                                    else -> {}
                                }
                            },
                            label = { Text(stringResource(type.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Hydration Factor
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

                Spacer(modifier = Modifier.height(16.dp))

                // Color Picker
                Text(
                    text = stringResource(R.string.preset_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PRESET_PALETTE.forEach { hex ->
                        val color = parseHexColor(hex)
                        val isSelected = hex.equals(selectedColorHex, ignoreCase = true)
                        val swatchLabel = if (isSelected) {
                            stringResource(R.string.preset_color_selected_cd)
                        } else {
                            stringResource(R.string.preset_color_swatch_cd, hex)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { selectedColorHex = hex }
                                .semantics { contentDescription = swatchLabel },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Picker
                Text(
                    text = stringResource(R.string.preset_icon_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val activeColor = parseHexColor(selectedColorHex)
                    PresetIcon.entries.forEach { icon ->
                        val isSelected = icon == selectedIcon
                        val bg = if (isSelected) activeColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = bg),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedIcon = icon }
                                .then(
                                    if (isSelected) Modifier.border(2.dp, activeColor, RoundedCornerShape(12.dp)) else Modifier
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = icon.drawableRes),
                                    contentDescription = stringResource(icon.labelRes),
                                    tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    if (canSave) {
                        val updated = preset?.copy(
                            label = labelInput.trim(),
                            volumeMl = ml,
                            iconKey = selectedIcon.id,
                            drinkType = selectedDrinkType.id,
                            hydrationFactor = parsedFactor,
                            colorHex = selectedColorHex
                        ) ?: PresetEntity(
                            id = UUID.randomUUID().toString(),
                            label = labelInput.trim(),
                            volumeMl = ml,
                            iconKey = selectedIcon.id,
                            hydrationFactor = parsedFactor,
                            displayOrder = nextDisplayOrder,
                            drinkType = selectedDrinkType.id,
                            colorHex = selectedColorHex
                        )
                        onSave(updated)
                    }
                },
                enabled = canSave
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (preset != null && onDelete != null) {
                    IconButton(onClick = { onDelete(preset.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.preset_delete_cd),
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
}
