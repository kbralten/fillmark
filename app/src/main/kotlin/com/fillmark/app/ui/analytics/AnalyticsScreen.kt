package com.fillmark.app.ui.analytics

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fillmark.app.core.VolumeConverter
import com.fillmark.app.ui.analytics.components.BarChart
import com.fillmark.app.ui.theme.FillmarkCardBorder
import com.fillmark.app.ui.theme.FillmarkNavy800
import com.fillmark.app.ui.theme.FillmarkNavyTitle
import com.fillmark.app.ui.theme.FillmarkSlateMuted
import kotlin.math.roundToInt

import androidx.compose.material3.SecondaryTabRow

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

        // Intake Breakdown Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = FillmarkNavy800
            ),
            modifier = Modifier.fillMaxWidth()
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
                    Text(
                        text = "-- Target Line",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8CA3BA)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                BarChart(bars = uiState.bars)
            }
        }

        // Feature Metric Grid (2x2) with tiny card-integrated iconography
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Daily Average",
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
            val hasTarget = uiState.bars.any { it.targetMl > 0 }
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
