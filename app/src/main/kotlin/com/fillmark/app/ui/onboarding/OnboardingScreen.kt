package com.fillmark.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.ShortcutHelper
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.UserPreferencesRepository
import com.fillmark.app.sync.HealthConnectManager
import com.fillmark.app.ui.settings.TimePickerField
import com.fillmark.app.ui.settings.TimePickerModalDialog
import com.fillmark.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    initialUnit: DisplayUnit,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember { UserPreferencesRepository(context) }
    val hcManager = remember { HealthConnectManager(context) }

    var step by remember { mutableIntStateOf(0) }
    var selectedUnit by remember { mutableStateOf(initialUnit) }
    var hasGoal by remember { mutableStateOf(true) }
    var targetInput by remember {
        mutableStateOf(if (initialUnit == DisplayUnit.METRIC_ML) "2500" else "85")
    }
    var wakingPacingEnabled by remember { mutableStateOf(true) }
    var wakeTime by remember { mutableStateOf("07:00") }
    var sleepTime by remember { mutableStateOf("23:00") }
    var showWakePicker by remember { mutableStateOf(false) }
    var showSleepPicker by remember { mutableStateOf(false) }
    var healthConnectEnabled by remember { mutableStateOf(false) }

    val hcAvailable = remember { hcManager.isAvailable() }
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(hcManager.permissions)) {
            healthConnectEnabled = true
        }
    }

    val parsedTarget = targetInput.toDoubleOrNull() ?: 0.0
    val targetMl = if (hasGoal) VolumeConverter.toInternalMl(parsedTarget, selectedUnit) else 0.0
    val targetValid = !hasGoal || (targetMl > 0.0 && targetMl <= VolumeConverter.MAX_DAILY_TARGET_ML)

    fun finishOnboarding() {
        scope.launch {
            prefsRepo.updateDisplayUnit(selectedUnit)
            prefsRepo.updateDefaultDailyTargetMl(if (targetValid) targetMl else 2500.0)
            prefsRepo.updateWakingSchedule(wakeTime, sleepTime, wakingPacingEnabled)
            if (healthConnectEnabled) {
                prefsRepo.updateHealthConnectSettings(enabled = true, bidirectional = false)
                HealthConnectManager.schedulePeriodicSync(context)
            }
            prefsRepo.setOnboardingCompleted(true)
            WidgetUpdateHelper.updateAllWidgets(context)
            ShortcutHelper.updateShortcuts(context)
            onCompleted()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${step + 1} of 3",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { finishOnboarding() }) {
                    Text("Skip")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (step) {
                    0 -> {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "Welcome to Fillmark",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Local-first hydration tracking with no accounts, no ads, and zero cloud tracking. Choose your preferred unit:",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(
                                selected = selectedUnit == DisplayUnit.METRIC_ML,
                                onClick = {
                                    if (selectedUnit != DisplayUnit.METRIC_ML) {
                                        targetInput = VolumeConverter.convertInputVolume(targetInput, selectedUnit, DisplayUnit.METRIC_ML)
                                        selectedUnit = DisplayUnit.METRIC_ML
                                    }
                                },
                                label = { Text("Metric (ml)") }
                            )
                            FilterChip(
                                selected = selectedUnit == DisplayUnit.IMPERIAL_FL_OZ,
                                onClick = {
                                    if (selectedUnit != DisplayUnit.IMPERIAL_FL_OZ) {
                                        targetInput = VolumeConverter.convertInputVolume(targetInput, selectedUnit, DisplayUnit.IMPERIAL_FL_OZ)
                                        selectedUnit = DisplayUnit.IMPERIAL_FL_OZ
                                    }
                                },
                                label = { Text("Imperial (fl oz)") }
                            )
                        }
                    }

                    1 -> {
                        Text(
                            text = "Set Your Daily Goal",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Choose a daily hydration target or track freely without a goal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        val quickPresets = if (selectedUnit == DisplayUnit.METRIC_ML) {
                            listOf("2000", "2500", "3000")
                        } else {
                            listOf("68", "85", "100")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickPresets.forEach { preset ->
                                FilterChip(
                                    selected = hasGoal && targetInput == preset,
                                    onClick = {
                                        hasGoal = true
                                        targetInput = preset
                                    },
                                    label = { Text("$preset ${selectedUnit.symbol}") }
                                )
                            }
                            FilterChip(
                                selected = !hasGoal,
                                onClick = { hasGoal = false },
                                label = { Text("No Goal") }
                            )
                        }

                        if (hasGoal) {
                            OutlinedTextField(
                                value = targetInput,
                                onValueChange = { targetInput = VolumeConverter.sanitizeDecimalInput(it) },
                                label = { Text("Daily Target (${selectedUnit.symbol})") },
                                suffix = { Text(selectedUnit.symbol) },
                                singleLine = true,
                                isError = !targetValid,
                                supportingText = if (!targetValid) {
                                    { Text("Enter a valid goal up to ${VolumeConverter.formatVolume(VolumeConverter.MAX_DAILY_TARGET_ML, selectedUnit)}") }
                                } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            text = "Fillmark is for general wellness tracking only and is not a medical device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    2 -> {
                        Text(
                            text = "Waking Hours & Sync",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Fillmark uses your waking window to tell you whether you are ahead, on track, or behind pace.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Waking Hours Pacing",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Switch(
                                        checked = wakingPacingEnabled,
                                        onCheckedChange = { wakingPacingEnabled = it }
                                    )
                                }
                                if (wakingPacingEnabled) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        TimePickerField(
                                            label = "Wake Time",
                                            timeStr = wakeTime,
                                            onClick = { showWakePicker = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                        TimePickerField(
                                            label = "Sleep Time",
                                            timeStr = sleepTime,
                                            onClick = { showSleepPicker = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        if (hcAvailable) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Health Connect (Optional)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (healthConnectEnabled) {
                                            "Connected! Your drink logs will sync with Health Connect."
                                        } else {
                                            "Sync hydration records with other fitness apps on your device."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!healthConnectEnabled) {
                                        OutlinedButton(
                                            onClick = { requestPermissionsLauncher.launch(hcManager.permissions) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Connect Health Connect")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = {
                        if (step < 2) step++ else finishOnboarding()
                    },
                    enabled = if (step == 1) targetValid else true,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (step < 2) "Next" else "Get Started")
                }
            }
        }
    }

    if (showWakePicker) {
        TimePickerModalDialog(
            title = "Set Wake Time",
            initialTime = wakeTime,
            onDismiss = { showWakePicker = false },
            onConfirm = {
                wakeTime = it
                showWakePicker = false
            }
        )
    }
    if (showSleepPicker) {
        TimePickerModalDialog(
            title = "Set Sleep Time",
            initialTime = sleepTime,
            onDismiss = { showSleepPicker = false },
            onConfirm = {
                sleepTime = it
                showSleepPicker = false
            }
        )
    }
}
