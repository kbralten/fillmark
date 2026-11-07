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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.domain.DailyGraphMode
import com.fillmark.app.ui.analytics.components.BarChart
import com.fillmark.app.ui.analytics.components.DailyLineChart
import com.fillmark.app.ui.theme.FillmarkCardBorder
import com.fillmark.app.ui.theme.FillmarkCyan
import com.fillmark.app.ui.theme.FillmarkCyanDark
import com.fillmark.app.ui.theme.FillmarkNavy700
import com.fillmark.app.ui.theme.FillmarkNavy800
import com.fillmark.app.ui.theme.FillmarkNavy900
import com.fillmark.app.ui.theme.FillmarkNavyTitle
import com.fillmark.app.ui.theme.FillmarkSlateMuted
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Integrated Modern Brand Header
        Text(
            text = "Drink Analytics",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = FillmarkNavyTitle
        )

        // Interval Tabs
        SecondaryTabRow(
            selectedTabIndex = uiState.selectedInterval.ordinal,
            containerColor = Color.Transparent,
            contentColor = FillmarkNavyTitle,
            divider = {}
        ) {
            AnalyticsInterval.entries.forEach { interval ->
                val isSelected = uiState.selectedInterval == interval
                Tab(
                    selected = isSelected,
                    onClick = { viewModel.selectInterval(interval) },
                    text = {
                        Text(
                            text = interval.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) FillmarkNavyTitle else FillmarkSlateMuted
                        )
                    }
                )
            }
        }

        // Period Navigation Bar (Previous, Next, and Jump to Current)
        PeriodNavigationBar(
            periodLabel = uiState.periodLabel,
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
                containerColor = FillmarkNavy800
            ),
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(uiState.selectedInterval, uiState.periodOffset) {
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
                        text = "Intake Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    if (uiState.selectedInterval == AnalyticsInterval.DAY) {
                        DailyModeSelector(
                            selectedMode = uiState.dailyGraphMode,
                            onModeSelected = { viewModel.selectDailyGraphMode(it) }
                        )
                    } else {
                        Text(
                            text = "-- Target Line",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8CA3BA)
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
                    targetState = uiState.periodOffset,
                    transitionSpec = {
                        if (targetState > initialState) {
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
                ) { _ ->
                    if (uiState.selectedInterval == AnalyticsInterval.DAY) {
                        DailyLineChart(data = uiState.dailyLineChartData)
                    } else {
                        BarChart(bars = uiState.bars)
                    }
                }
            }
        }

        // Feature Metric Grid (2x2) with tiny card-integrated iconography
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = if (uiState.selectedInterval == AnalyticsInterval.DAY) {
                    if (uiState.periodOffset == 0) "Today's Intake" else "Day's Intake"
                } else "Daily Average",
                value = VolumeConverter.formatVolume(uiState.averageDailyVolumeMl, uiState.displayUnit),
                icon = Icons.Outlined.CalendarToday,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Total Intake",
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
                title = "Goal Hit Rate",
                value = if (hasTarget) "$successRate%" else "0%",
                icon = Icons.Outlined.TrackChanges,
                subtitle = if (hasTarget) "${uiState.daysGoalMet} of ${uiState.totalDays} days" else null,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Active Unit",
                value = uiState.displayUnit.symbol,
                icon = Icons.Outlined.BarChart,
                subtitle = uiState.displayUnit.label,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, FillmarkCardBorder),
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
                    color = FillmarkSlateMuted
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FillmarkNavyTitle.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = FillmarkNavyTitle
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = FillmarkSlateMuted
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
            .background(FillmarkNavy700, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DailyGraphMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) FillmarkCyan else Color.Transparent)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) FillmarkNavy900 else FillmarkSlateMuted
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
        // Line indicator for intake/rate
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 3.dp)
                    .background(FillmarkCyan, RoundedCornerShape(1.5.dp))
            )
            Text(
                text = if (mode == DailyGraphMode.CUMULATIVE) "Intake" else "Rate",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCAD7E2)
            )
        }

        if (hasTarget) {
            if (mode == DailyGraphMode.CUMULATIVE) {
                // Target line
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8CA3BA)
                    )
                    Text(
                        text = "Target",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8CA3BA)
                    )
                }

                // Target Pace
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "··",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "Target Pace",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8)
                    )
                }
            } else {
                // Rate Target Line
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8CA3BA)
                    )
                    Text(
                        text = "Target Rate",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8CA3BA)
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigationBar(
    periodLabel: String,
    canGoNext: Boolean,
    showJumpToCurrent: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpToCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous period button
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = "Previous period",
                tint = FillmarkNavyTitle,
                modifier = Modifier.size(22.dp)
            )
        }

        // Center period label
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = FillmarkNavyTitle
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Little icon to jump back to the current time period
            if (showJumpToCurrent) {
                IconButton(
                    onClick = onJumpToCurrent,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Today,
                        contentDescription = "Jump to current period",
                        tint = FillmarkCyanDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Next period button
            IconButton(
                onClick = onNext,
                enabled = canGoNext,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Next period",
                    tint = if (canGoNext) FillmarkNavyTitle else FillmarkSlateMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
