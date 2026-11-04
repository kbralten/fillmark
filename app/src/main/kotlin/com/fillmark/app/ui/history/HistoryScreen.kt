package com.fillmark.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.ui.dashboard.components.getPresetVector
import com.fillmark.app.ui.history.components.EditLogDialog

import androidx.compose.foundation.BorderStroke
import com.fillmark.app.ui.theme.FillmarkCardBorder
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkCyanDark
import com.fillmark.app.ui.theme.FillmarkNavyTitle
import com.fillmark.app.ui.theme.FillmarkSlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.clearSnackbar()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Intake History",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = FillmarkNavyTitle
                    )
                }
            }

            if (uiState.dayGroups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No drink history recorded yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = FillmarkSlateMuted
                        )
                    }
                }
            } else {
                uiState.dayGroups.forEach { dayGroup ->
                    item(key = "header_${dayGroup.dateIso}") {
                        DayHeader(
                            friendlyDate = dayGroup.friendlyDate,
                            totalEffectiveVolume = dayGroup.totalEffectiveVolumeMl,
                            displayUnit = uiState.displayUnit
                        )
                    }

                    items(dayGroup.records, key = { it.id }) { record ->
                        HistoryRecordItem(
                            record = record,
                            displayUnit = uiState.displayUnit,
                            onClick = { viewModel.startEditing(record) },
                            onDelete = { viewModel.deleteRecord(record) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        uiState.editingRecord?.let { recordToEdit ->
            EditLogDialog(
                record = recordToEdit,
                displayUnit = uiState.displayUnit,
                onDismiss = { viewModel.dismissEditing() },
                onSave = { updatedRecord -> viewModel.saveEditedRecord(updatedRecord) }
            )
        }
    }
}

@Composable
private fun DayHeader(
    friendlyDate: String,
    totalEffectiveVolume: Double,
    displayUnit: com.fillmark.app.core.DisplayUnit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = friendlyDate,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Total: ${VolumeConverter.formatVolume(totalEffectiveVolume, displayUnit)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HistoryRecordItem(
    record: HydrationRecordEntity,
    displayUnit: com.fillmark.app.core.DisplayUnit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, FillmarkCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = FillmarkCyan.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = com.fillmark.app.core.PresetIcon.getDrawableRes(record.iconKey)),
                        contentDescription = null,
                        tint = FillmarkCyanDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = record.drinkType.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FillmarkNavyTitle
                )
                Text(
                    text = TimeUtils.formatTime(record.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = FillmarkSlateMuted
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = VolumeConverter.formatVolume(record.effectiveVolumeMl, displayUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = FillmarkNavyTitle
                )
                if (record.hydrationFactor != 1.0) {
                    Text(
                        text = "Factor: ${record.hydrationFactor}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 4.dp).size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete record",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

