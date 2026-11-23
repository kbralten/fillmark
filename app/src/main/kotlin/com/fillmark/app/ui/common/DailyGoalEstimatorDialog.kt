package com.fillmark.app.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.domain.ActivityLevel
import com.fillmark.app.domain.DailyGoalEstimator
import com.fillmark.app.domain.WeightUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyGoalEstimatorDialog(
    displayUnit: DisplayUnit,
    onDismiss: () -> Unit,
    onApplyEstimateMl: (Double) -> Unit
) {
    var weightUnit by remember {
        mutableStateOf(
            if (displayUnit == DisplayUnit.IMPERIAL_FL_OZ) WeightUnit.LB else WeightUnit.KG
        )
    }
    var weightInput by remember {
        mutableStateOf(if (weightUnit == WeightUnit.LB) "155" else "70")
    }
    var activityLevel by remember { mutableStateOf(ActivityLevel.MODERATE) }
    var warmClimate by remember { mutableStateOf(false) }

    val parsedWeight = weightInput.toDoubleOrNull() ?: 0.0
    val isWeightValid = parsedWeight > 0.0 && parsedWeight <= 500.0
    val estimatedMl = if (isWeightValid) {
        DailyGoalEstimator.estimateDailyTargetMl(
            weight = parsedWeight,
            weightUnit = weightUnit,
            activityLevel = activityLevel,
            warmClimate = warmClimate
        )
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.estimator_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.estimator_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = weightUnit == WeightUnit.KG,
                        onClick = {
                            if (weightUnit != WeightUnit.KG) {
                                if (isWeightValid) {
                                    weightInput = (DailyGoalEstimator.toKg(parsedWeight, WeightUnit.LB))
                                        .toInt().coerceAtLeast(1).toString()
                                }
                                weightUnit = WeightUnit.KG
                            }
                        },
                        label = { Text(stringResource(R.string.estimator_weight_kg)) }
                    )
                    FilterChip(
                        selected = weightUnit == WeightUnit.LB,
                        onClick = {
                            if (weightUnit != WeightUnit.LB) {
                                if (isWeightValid) {
                                    weightInput = (parsedWeight / DailyGoalEstimator.KG_PER_LB)
                                        .toInt().coerceAtLeast(1).toString()
                                }
                                weightUnit = WeightUnit.LB
                            }
                        },
                        label = { Text(stringResource(R.string.estimator_weight_lb)) }
                    )
                }

                val unitSuffix = if (weightUnit == WeightUnit.KG) {
                    stringResource(R.string.estimator_weight_kg)
                } else {
                    stringResource(R.string.estimator_weight_lb)
                }
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = VolumeConverter.sanitizeDecimalInput(it) },
                    label = { Text(stringResource(R.string.estimator_weight_label)) },
                    suffix = { Text(unitSuffix) },
                    singleLine = true,
                    isError = !isWeightValid,
                    supportingText = if (!isWeightValid) {
                        { Text(stringResource(R.string.estimator_weight_error)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.estimator_activity_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = activityLevel == ActivityLevel.LOW,
                        onClick = { activityLevel = ActivityLevel.LOW },
                        label = { Text(stringResource(R.string.estimator_activity_low)) }
                    )
                    FilterChip(
                        selected = activityLevel == ActivityLevel.MODERATE,
                        onClick = { activityLevel = ActivityLevel.MODERATE },
                        label = { Text(stringResource(R.string.estimator_activity_moderate)) }
                    )
                    FilterChip(
                        selected = activityLevel == ActivityLevel.HIGH,
                        onClick = { activityLevel = ActivityLevel.HIGH },
                        label = { Text(stringResource(R.string.estimator_activity_high)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.estimator_warm_climate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = warmClimate,
                        onCheckedChange = { warmClimate = it }
                    )
                }

                if (estimatedMl != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.estimator_result_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = VolumeConverter.formatVolume(estimatedMl, displayUnit),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.estimator_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (estimatedMl != null) {
                        onApplyEstimateMl(estimatedMl)
                    }
                },
                enabled = estimatedMl != null
            ) {
                Text(stringResource(R.string.estimator_use_estimate))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
