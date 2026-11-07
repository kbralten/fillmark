package com.fillmark.app.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.domain.DailyGraphCalculator
import com.fillmark.app.domain.DailyGraphMode
import com.fillmark.app.domain.DailyLineChartData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AnalyticsInterval(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    THREE_MONTHS("3M"),
    YEAR("Year")
}

data class ChartBarData(
    val label: String,
    val valueMl: Double,
    val targetMl: Double,
    val isCurrent: Boolean = false
)

data class AnalyticsUiState(
    val selectedInterval: AnalyticsInterval = AnalyticsInterval.WEEK,
    val dailyGraphMode: DailyGraphMode = DailyGraphMode.CUMULATIVE,
    val periodOffset: Int = 0,
    val periodLabel: String = "",
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val bars: List<ChartBarData> = emptyList(),
    val dailyLineChartData: DailyLineChartData = DailyLineChartData(),
    val totalVolumeMl: Double = 0.0,
    val averageDailyVolumeMl: Double = 0.0,
    val daysGoalMet: Int = 0,
    val totalDays: Int = 0
)

class AnalyticsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: HydrationRepository = HydrationRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private var allRecords: List<HydrationRecordEntity> = emptyList()
    private var defaultTargetMl: Double = 2500.0
    private var allTargetsMap: Map<String, Double> = emptyMap()
    private var userPreferences: UserPreferences = UserPreferences()

    init {
        viewModelScope.launch {
            combine(
                repository.observeAllRecords(),
                repository.preferencesFlow,
                repository.observeAllTargets()
            ) { records, prefs, targetsList ->
                allRecords = records
                userPreferences = prefs
                defaultTargetMl = prefs.defaultDailyTargetMl
                allTargetsMap = targetsList.associate { it.dateString to it.targetMl }
                _uiState.update { it.copy(displayUnit = prefs.displayUnit) }
                calculateAnalytics(_uiState.value.selectedInterval, _uiState.value.periodOffset)
            }.collect {}
        }
    }

    fun selectInterval(interval: AnalyticsInterval) {
        _uiState.update { it.copy(selectedInterval = interval, periodOffset = 0) }
        calculateAnalytics(interval, 0)
    }

    fun selectDailyGraphMode(mode: DailyGraphMode) {
        _uiState.update { it.copy(dailyGraphMode = mode) }
        calculateAnalytics(_uiState.value.selectedInterval, _uiState.value.periodOffset)
    }

    fun previousPeriod() {
        val newOffset = _uiState.value.periodOffset + 1
        _uiState.update { it.copy(periodOffset = newOffset) }
        calculateAnalytics(_uiState.value.selectedInterval, newOffset)
    }

    fun nextPeriod() {
        val currentOffset = _uiState.value.periodOffset
        if (currentOffset > 0) {
            val newOffset = currentOffset - 1
            _uiState.update { it.copy(periodOffset = newOffset) }
            calculateAnalytics(_uiState.value.selectedInterval, newOffset)
        }
    }

    fun resetPeriodOffset() {
        if (_uiState.value.periodOffset != 0) {
            _uiState.update { it.copy(periodOffset = 0) }
            calculateAnalytics(_uiState.value.selectedInterval, 0)
        }
    }

    private fun formatPeriodLabel(
        interval: AnalyticsInterval,
        offset: Int,
        today: LocalDate
    ): String {
        val monthDayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        return when (interval) {
            AnalyticsInterval.DAY -> {
                val date = today.minusDays(offset.toLong())
                when (offset) {
                    0 -> "Today (${date.format(monthDayFormat)})"
                    1 -> "Yesterday (${date.format(monthDayFormat)})"
                    else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
                }
            }
            AnalyticsInterval.WEEK -> {
                val end = today.minusDays((offset * 7).toLong())
                val start = end.minusDays(6)
                when (offset) {
                    0 -> "This Week (${start.format(monthDayFormat)} – ${end.format(monthDayFormat)})"
                    1 -> "Last Week (${start.format(monthDayFormat)} – ${end.format(monthDayFormat)})"
                    else -> "${start.format(monthDayFormat)} – ${end.format(monthDayFormat)}"
                }
            }
            AnalyticsInterval.MONTH -> {
                val end = today.minusDays((offset * 30).toLong())
                val start = end.minusDays(29)
                when (offset) {
                    0 -> "Past 30 Days (${start.format(monthDayFormat)} – ${end.format(monthDayFormat)})"
                    else -> "${start.format(monthDayFormat)} – ${end.format(monthDayFormat)}"
                }
            }
            AnalyticsInterval.THREE_MONTHS -> {
                val end = today.minusDays((offset * 12 * 7).toLong())
                val start = end.minusDays((12 * 7 - 1).toLong())
                val yearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
                when (offset) {
                    0 -> "Past 12 Weeks (${start.format(monthDayFormat)} – ${end.format(monthDayFormat)})"
                    else -> "${start.format(yearFormat)} – ${end.format(yearFormat)}"
                }
            }
            AnalyticsInterval.YEAR -> {
                val end = today.minusDays((offset * 52 * 7).toLong())
                val start = end.minusDays((52 * 7 - 1).toLong())
                val yearMonthFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
                when (offset) {
                    0 -> "Past 52 Weeks (${start.format(yearMonthFormat)} – ${end.format(yearMonthFormat)})"
                    else -> "${start.format(yearMonthFormat)} – ${end.format(yearMonthFormat)}"
                }
            }
        }
    }

    private fun calculateAnalytics(interval: AnalyticsInterval, offset: Int = _uiState.value.periodOffset) {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val periodLabel = formatPeriodLabel(interval, offset, today)

        when (interval) {
            AnalyticsInterval.DAY -> {
                val targetDate = today.minusDays(offset.toLong())
                val targetDateIso = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val targetMlForDay = allTargetsMap[targetDateIso] ?: defaultTargetMl

                val dayChartData = DailyGraphCalculator.calculate(
                    records = allRecords,
                    date = targetDate,
                    zoneId = zoneId,
                    targetMl = targetMlForDay,
                    wakingHoursStart = userPreferences.wakingHoursStart,
                    wakingHoursEnd = userPreferences.wakingHoursEnd,
                    wakingPacingEnabled = userPreferences.wakingPacingEnabled,
                    displayUnit = userPreferences.displayUnit,
                    mode = _uiState.value.dailyGraphMode
                )

                _uiState.update {
                    it.copy(
                        periodOffset = offset,
                        periodLabel = periodLabel,
                        bars = emptyList(),
                        dailyLineChartData = dayChartData,
                        totalVolumeMl = dayChartData.totalIntakeMl,
                        averageDailyVolumeMl = dayChartData.totalIntakeMl,
                        daysGoalMet = if (dayChartData.hasTarget && dayChartData.totalIntakeMl >= dayChartData.targetVolumeMl) 1 else 0,
                        totalDays = 1
                    )
                }
            }

            AnalyticsInterval.WEEK -> {
                // 7 days ending at today.minusDays(offset * 7)
                val endOfWeek = today.minusDays((offset * 7).toLong())
                val bars = (6 downTo 0).map { daysAgo ->
                    val date = endOfWeek.minusDays(daysAgo.toLong())
                    val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val start = TimeUtils.startOfDayMillis(date, zoneId)
                    val end = TimeUtils.endOfDayMillis(date, zoneId)
                    val dayRecords = allRecords.filter { it.timestamp in start..end }
                    val effective = dayRecords.sumOf { it.effectiveVolumeMl }
                    val dayTarget = allTargetsMap[dateIso] ?: defaultTargetMl

                    ChartBarData(
                        label = date.format(DateTimeFormatter.ofPattern("EEE", Locale.US)),
                        valueMl = effective,
                        targetMl = dayTarget,
                        isCurrent = (offset == 0 && daysAgo == 0)
                    )
                }

                val total = bars.sumOf { it.valueMl }
                val met = bars.count { it.targetMl > 0 && it.valueMl >= it.targetMl }
                _uiState.update {
                    it.copy(
                        periodOffset = offset,
                        periodLabel = periodLabel,
                        bars = bars,
                        totalVolumeMl = total,
                        averageDailyVolumeMl = total / 7.0,
                        daysGoalMet = met,
                        totalDays = 7
                    )
                }
            }

            AnalyticsInterval.MONTH -> {
                // 30 days ending at today.minusDays(offset * 30)
                val endOfMonth = today.minusDays((offset * 30).toLong())
                val bars = (29 downTo 0).map { daysAgo ->
                    val date = endOfMonth.minusDays(daysAgo.toLong())
                    val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val start = TimeUtils.startOfDayMillis(date, zoneId)
                    val end = TimeUtils.endOfDayMillis(date, zoneId)
                    val dayRecords = allRecords.filter { it.timestamp in start..end }
                    val effective = dayRecords.sumOf { it.effectiveVolumeMl }
                    val label = if (daysAgo % 5 == 0) date.format(DateTimeFormatter.ofPattern("d", Locale.US)) else ""
                    val dayTarget = allTargetsMap[dateIso] ?: defaultTargetMl

                    ChartBarData(
                        label = label,
                        valueMl = effective,
                        targetMl = dayTarget,
                        isCurrent = (offset == 0 && daysAgo == 0)
                    )
                }

                val total = bars.sumOf { it.valueMl }
                val met = bars.count { it.targetMl > 0 && it.valueMl >= it.targetMl }
                _uiState.update {
                    it.copy(
                        periodOffset = offset,
                        periodLabel = periodLabel,
                        bars = bars,
                        totalVolumeMl = total,
                        averageDailyVolumeMl = total / 30.0,
                        daysGoalMet = met,
                        totalDays = 30
                    )
                }
            }

            AnalyticsInterval.THREE_MONTHS, AnalyticsInterval.YEAR -> {
                val numWeeks = if (interval == AnalyticsInterval.THREE_MONTHS) 12 else 52
                val endOfPeriod = today.minusDays((offset * numWeeks * 7).toLong())
                val bars = (numWeeks - 1 downTo 0).map { weeksAgo ->
                    val end = endOfPeriod.minusDays((weeksAgo * 7).toLong())
                    val start = end.minusDays(6)
                    val startMs = TimeUtils.startOfDayMillis(start, zoneId)
                    val endMs = TimeUtils.endOfDayMillis(end, zoneId)
                    val weekRecords = allRecords.filter { it.timestamp in startMs..endMs }
                    val effective = weekRecords.sumOf { it.effectiveVolumeMl }

                    ChartBarData(
                        label = if (weeksAgo % 4 == 0) start.format(DateTimeFormatter.ofPattern("MMM", Locale.US)) else "",
                        valueMl = effective / 7.0,
                        targetMl = defaultTargetMl,
                        isCurrent = (offset == 0 && weeksAgo == 0)
                    )
                }

                val total = bars.sumOf { it.valueMl * 7.0 }
                val met = bars.count { it.targetMl > 0 && it.valueMl >= it.targetMl }
                _uiState.update {
                    it.copy(
                        periodOffset = offset,
                        periodLabel = periodLabel,
                        bars = bars,
                        totalVolumeMl = total,
                        averageDailyVolumeMl = bars.map { b -> b.valueMl }.average().takeIf { !it.isNaN() } ?: 0.0,
                        daysGoalMet = met * 7,
                        totalDays = numWeeks * 7
                    )
                }
            }
        }
    }
}

