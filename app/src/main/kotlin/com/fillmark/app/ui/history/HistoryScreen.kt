package com.fillmark.app.ui.history

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.DrinkType
import com.fillmark.app.core.PresetIcon
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.ui.dashboard.components.ManualLogBottomSheet
import com.fillmark.app.ui.history.components.EditLogDialog
import com.fillmark.app.ui.theme.FillmarkCyan
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val undoLabel = stringResource(R.string.action_undo)
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)
    var showDateFilterDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshCurrentDate()
        onPauseOrDispose {}
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.clearSnackbar()
            }
        }
    }

    val hasActiveFilter = uiState.searchQuery.isNotBlank() ||
        uiState.selectedDrinkType != null ||
        uiState.selectedDateIso != null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openManualLogSheet() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.history_add_drink_cd)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { showDateFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = stringResource(R.string.history_filter_date_cd),
                            tint = if (uiState.selectedDateIso != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (uiState.hasAnyRecords) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.history_search_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = if (uiState.searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.history_clear_search_cd)
                                        )
                                    }
                                }
                            } else null,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            uiState.selectedDateIso?.let { dateIso ->
                                FilterChip(
                                    selected = true,
                                    onClick = { viewModel.setDateFilter(null) },
                                    label = {
                                        Text(
                                            TimeUtils.formatFriendlyDate(
                                                dateString = dateIso,
                                                todayLabel = todayLabel,
                                                yesterdayLabel = yesterdayLabel
                                            )
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }

                            FilterChip(
                                selected = uiState.selectedDrinkType == null,
                                onClick = { viewModel.setDrinkTypeFilter(null) },
                                label = { Text(stringResource(R.string.history_filter_all)) }
                            )

                            DrinkType.entries.forEach { type ->
                                FilterChip(
                                    selected = uiState.selectedDrinkType == type.id,
                                    onClick = { viewModel.setDrinkTypeFilter(type.id) },
                                    label = { Text(type.displayName) }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.dayGroups.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (uiState.hasAnyRecords && hasActiveFilter) {
                                stringResource(R.string.history_no_matches)
                            } else {
                                stringResource(R.string.history_empty)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.hasAnyRecords && hasActiveFilter) {
                            OutlinedButton(onClick = { viewModel.clearFilters() }) {
                                Text(stringResource(R.string.history_clear_filters))
                            }
                        }
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
                        SwipeableHistoryRecordItem(
                            record = record,
                            displayUnit = uiState.displayUnit,
                            hydrationFactorsEnabled = uiState.hydrationFactorsEnabled,
                            onClick = { viewModel.startEditing(record) },
                            onDeleteClick = { viewModel.deleteRecord(record) },
                            onSwipeDeleteRequest = { viewModel.requestDeleteConfirmation(record) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }

        if (uiState.isManualLogSheetOpen) {
            val initialTimestamp = uiState.selectedDateIso?.let { dateIso ->
                runCatching {
                    val date = LocalDate.parse(dateIso, DateTimeFormatter.ISO_LOCAL_DATE)
                    if (date.isBefore(LocalDate.now())) {
                        TimeUtils.toEpochMillis(date, LocalTime.NOON)
                    } else {
                        null
                    }
                }.getOrNull()
            }
            ManualLogBottomSheet(
                displayUnit = uiState.displayUnit,
                hydrationFactorsEnabled = uiState.hydrationFactorsEnabled,
                initialTimestampMillis = initialTimestamp,
                onDismiss = { viewModel.closeManualLogSheet() },
                onLogRecord = { volume, factor, drinkType, timestamp, label ->
                    viewModel.logCustom(volume, factor, drinkType, timestamp, label)
                },
                sheetState = sheetState
            )
        }

        uiState.editingRecord?.let { recordToEdit ->
            EditLogDialog(
                record = recordToEdit,
                displayUnit = uiState.displayUnit,
                hydrationFactorsEnabled = uiState.hydrationFactorsEnabled,
                onDismiss = { viewModel.dismissEditing() },
                onSave = { updatedRecord -> viewModel.saveEditedRecord(updatedRecord) },
                onDelete = { viewModel.deleteRecord(recordToEdit) }
            )
        }

        uiState.pendingDeleteRecord?.let { pendingRecord ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteConfirmation() },
                title = {
                    Text(
                        text = stringResource(R.string.history_delete_confirm_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            R.string.history_delete_confirm_body,
                            pendingRecord.displayTitle(),
                            VolumeConverter.formatVolume(
                                pendingRecord.countedVolumeMl(uiState.hydrationFactorsEnabled),
                                uiState.displayUnit
                            )
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDelete() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showDateFilterDialog) {
            val todayUtcMillis = TimeUtils.localDateToUtcMillis(LocalDate.now())
            val initialDateMillis = uiState.selectedDateIso?.let { dateIso ->
                runCatching {
                    TimeUtils.localDateToUtcMillis(LocalDate.parse(dateIso, DateTimeFormatter.ISO_LOCAL_DATE))
                }.getOrNull()
            } ?: todayUtcMillis

            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = initialDateMillis,
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        return utcTimeMillis <= todayUtcMillis
                    }

                    override fun isSelectableYear(year: Int): Boolean {
                        return year <= LocalDate.now().year
                    }
                }
            )

            DatePickerDialog(
                onDismissRequest = { showDateFilterDialog = false },
                confirmButton = {
                    Button(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { utcMillis ->
                                val pickedDate = TimeUtils.utcMillisToLocalDate(utcMillis)
                                viewModel.setDateFilter(pickedDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            }
                            showDateFilterDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.action_ok))
                    }
                },
                dismissButton = {
                    Row {
                        if (uiState.selectedDateIso != null) {
                            TextButton(
                                onClick = {
                                    viewModel.setDateFilter(null)
                                    showDateFilterDialog = false
                                }
                            ) {
                                Text(stringResource(R.string.action_reset))
                            }
                        }
                        OutlinedButton(onClick = { showDateFilterDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryRecordItem(
    record: HydrationRecordEntity,
    displayUnit: DisplayUnit,
    hydrationFactorsEnabled: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSwipeDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onSwipeDeleteRequest()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.history_delete_record_cd),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        HistoryRecordItem(
            record = record,
            displayUnit = displayUnit,
            hydrationFactorsEnabled = hydrationFactorsEnabled,
            onClick = onClick,
            onDelete = onDeleteClick
        )
    }
}

@Composable
private fun DayHeader(
    friendlyDate: String,
    totalEffectiveVolume: Double,
    displayUnit: DisplayUnit
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
            text = stringResource(
                R.string.history_day_total,
                VolumeConverter.formatVolume(totalEffectiveVolume, displayUnit)
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HistoryRecordItem(
    record: HydrationRecordEntity,
    displayUnit: DisplayUnit,
    hydrationFactorsEnabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val title = record.displayTitle()
    val categoryName = DrinkType.fromId(record.drinkType).displayName
    val timeText = TimeUtils.formatTime(record.timestamp, is24Hour = is24Hour)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = FillmarkCyan.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = PresetIcon.getDrawableRes(record.iconKey)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (!record.label.isNullOrBlank() && !record.label.equals(categoryName, ignoreCase = true)) {
                        stringResource(R.string.history_type_and_time, categoryName, timeText)
                    } else {
                        timeText
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = VolumeConverter.formatVolume(record.countedVolumeMl(hydrationFactorsEnabled), displayUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hydrationFactorsEnabled && record.hydrationFactor != 1.0) {
                    Text(
                        text = stringResource(
                            R.string.history_factor_label,
                            VolumeConverter.formatHydrationFactor(record.hydrationFactor)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.history_delete_record_cd),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
