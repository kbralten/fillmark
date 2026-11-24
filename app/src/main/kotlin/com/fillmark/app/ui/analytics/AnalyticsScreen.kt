package com.fillmark.app.ui.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fillmark.app.R
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.domain.DailyGraphMode
import com.fillmark.app.domain.DailyLineChartData
import com.fillmark.app.domain.DrinkTypeBreakdownItem
import com.fillmark.app.ui.analytics.components.BarChart
import com.fillmark.app.ui.analytics.components.DailyLineChart
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedBarIndex by remember(uiState.selectedInterval, uiState.periodOffset, uiState.bars) {
        mutableStateOf<Int?>(null)
    }
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)

    LifecycleResumeEffect(Unit) {
        viewModel.refreshCurrentDate()
        onPauseOrDispose {}
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.analytics_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        SecondaryTabRow(
            selectedTabIndex = uiState.selectedInterval.ordinal,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            divider = {}
        ) {
            AnalyticsInterval.entries.forEach { interval ->
                val isSelected = uiState.selectedInterval == interval
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.selectInterval(interval) },
                    text = {
                        Text(
                            text = stringResource(interval.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        PeriodNavigationBar(
            periodLabel = uiState.periodLabel,
            canGoPrevious = uiState.canGoPrevious,
            canGoNext = uiState.periodOffset > 0,
            showJumpToCurrent = uiState.periodOffset > 0,
            onPrevious = { viewModel.previousPeriod() },
            onNext = { viewModel.nextPeriod() },
            onJumpToCurrent = { viewModel.resetPeriodOffset() }
        )

        // Intake Breakdown Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(uiState.selectedInterval, uiState.periodOffset, uiState.canGoPrevious) {
                    if (!uiState.canGoPrevious) return@pointerInput
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            val threshold = 40.dp.toPx()
                            if (totalDragX > threshold) {
                                viewModel.previousPeriod()
                            } else if (totalDragX < -threshold) {
                                viewModel.nextPeriod()
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.analytics_intake_breakdown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (uiState.selectedInterval == AnalyticsInterval.DAY) {
                        DailyModeSelector(
                            selectedMode = uiState.dailyGraphMode,
                            onModeSelected = { viewModel.selectDailyGraphMode(it) }
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.analytics_target_line),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (uiState.selectedInterval == AnalyticsInterval.DAY) {
                    Spacer(modifier = Modifier.height(10.dp))
                    DailyGraphLegend(
                        mode = uiState.dailyGraphMode,
                        hasTarget = uiState.dailyLineChartData.hasTarget
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = ChartTransitionState(
                        periodOffset = uiState.periodOffset,
                        interval = uiState.selectedInterval,
                        dailyLineChartData = uiState.dailyLineChartData,
                        bars = uiState.bars
                    ),
                    contentKey = { "${it.interval.name}_${it.periodOffset}" },
                    transitionSpec = {
                        if (targetState.periodOffset > initialState.periodOffset) {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                slideOutHorizontally { it } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(
                                slideOutHorizontally { -it } + fadeOut()
                            )
                        }
                    },
                    label = "PeriodChartTransition"
                ) { state ->
                    if (state.interval == AnalyticsInterval.DAY) {
                        DailyLineChart(data = state.dailyLineChartData)
                    } else if (state.bars.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(205.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.analytics_empty_history),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        BarChart(
                            bars = state.bars,
                            displayUnit = uiState.displayUnit,
                            selectedIndex = selectedBarIndex,
                            onBarClick = { idx, _ ->
                                selectedBarIndex = if (selectedBarIndex == idx) null else idx
                            }
                        )
                    }
                }

                val inspectedBar = selectedBarIndex?.let { uiState.bars.getOrNull(it) }
                if (inspectedBar != null && uiState.selectedInterval != AnalyticsInterval.DAY) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val title = inspectedBar.dateIso?.let {
                                    TimeUtils.formatFriendlyDate(
                                        dateString = it,
                                        todayLabel = todayLabel,
                                        yesterdayLabel = yesterdayLabel
                                    )
                                } ?: inspectedBar.label.ifEmpty { stringResource(R.string.analytics_period_fallback) }
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val volText = VolumeConverter.formatVolume(inspectedBar.valueMl, uiState.displayUnit)
                                val targetText = if (inspectedBar.targetMl > 0) {
                                    " / ${VolumeConverter.formatVolume(inspectedBar.targetMl, uiState.displayUnit)}"
                                } else ""
                                Text(
                                    text = "$volText$targetText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (inspectedBar.dateIso != null) {
                                TextButton(onClick = { viewModel.inspectDate(inspectedBar.dateIso) }) {
                                    Text(
                                        text = stringResource(R.string.analytics_view_day),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feature Metric Grid (2x2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = if (uiState.selectedInterval == AnalyticsInterval.DAY) {
                    if (uiState.periodOffset == 0) {
                        stringResource(R.string.dashboard_todays_intake)
                    } else {
                        stringResource(R.string.analytics_day_intake)
                    }
                } else {
                    stringResource(R.string.analytics_daily_average)
                },
                value = VolumeConverter.formatVolume(uiState.averageDailyVolumeMl, uiState.displayUnit),
                icon = Icons.Outlined.CalendarToday,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = stringResource(R.string.analytics_total_intake),
                value = VolumeConverter.formatVolume(uiState.totalVolumeMl, uiState.displayUnit),
                icon = Icons.Outlined.WaterDrop,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val hasTarget = if (uiState.selectedInterval == AnalyticsInterval.DAY) {
                uiState.dailyLineChartData.hasTarget
            } else {
                uiState.bars.any { it.targetMl > 0 }
            }
            val successRate = if (hasTarget && uiState.totalDays > 0) {
                ((uiState.daysGoalMet.toFloat() / uiState.totalDays.toFloat()) * 100).roundToInt()
            } else 0

            MetricCard(
                title = stringResource(R.string.analytics_goal_hit_rate),
                value = if (hasTarget) "$successRate%" else "0%",
                icon = Icons.Outlined.TrackChanges,
                subtitle = if (hasTarget && uiState.totalDays > 0) {
                    pluralStringResource(
                        R.plurals.analytics_days_met_subtitle,
                        uiState.totalDays,
                        uiState.daysGoalMet,
                        uiState.totalDays
                    )
                } else null,
                modifier = Modifier.weight(1f)
            )

            val streakLabel = pluralStringResource(R.plurals.streak_days, uiState.currentStreak, uiState.currentStreak)
            val bestLabel = pluralStringResource(R.plurals.streak_days, uiState.bestStreak, uiState.bestStreak)
            MetricCard(
                title = stringResource(R.string.analytics_goal_streak),
                value = streakLabel,
                icon = Icons.Outlined.LocalFireDepartment,
                subtitle = stringResource(R.string.analytics_best_streak, bestLabel),
                modifier = Modifier.weight(1f)
            )
        }

        if (uiState.drinkBreakdown.isNotEmpty()) {
            DrinkBreakdownCard(
                items = uiState.drinkBreakdown,
                displayUnit = uiState.displayUnit
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DrinkBreakdownCard(
    items: List<DrinkTypeBreakdownItem>,
    displayUnit: DisplayUnit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_drink_breakdown),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(item.drinkType.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${VolumeConverter.formatVolume(item.volumeMl, displayUnit)} (${item.percentage.roundToInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((item.percentage / 100f).coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailyModeSelector(
    selectedMode: DailyGraphMode,
    onModeSelected: (DailyGraphMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DailyGraphMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Box(
                modifier = Modifier
                    .widthIn(min = 48.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(mode.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailyGraphLegend(
    mode: DailyGraphMode,
    hasTarget: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp))
            )
            Text(
                text = if (mode == DailyGraphMode.CUMULATIVE) {
                    stringResource(R.string.analytics_legend_intake)
                } else {
                    stringResource(R.string.analytics_legend_rate)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (hasTarget) {
            if (mode == DailyGraphMode.CUMULATIVE) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.analytics_legend_target),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "··",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = stringResource(R.string.analytics_legend_target_pace),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.analytics_legend_target_rate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigationBar(
    periodLabel: String,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    showJumpToCurrent: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpToCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = canGoPrevious,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = stringResource(R.string.analytics_prev_period_cd),
                tint = if (canGoPrevious) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (showJumpToCurrent) {
                IconButton(
                    onClick = onJumpToCurrent,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Today,
                        contentDescription = stringResource(R.string.analytics_jump_current_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                enabled = canGoNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = stringResource(R.string.analytics_next_period_cd),
                    tint = if (canGoNext) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private data class ChartTransitionState(
    val periodOffset: Int,
    val interval: AnalyticsInterval,
    val dailyLineChartData: DailyLineChartData,
    val bars: List<ChartBarData>
)
