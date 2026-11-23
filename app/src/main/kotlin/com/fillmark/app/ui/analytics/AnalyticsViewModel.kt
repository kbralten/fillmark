package com.fillmark.app.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.UserPreferences
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.repository.HydrationRepository
import com.fillmark.app.domain.AnalyticsInsightsCalculator
import com.fillmark.app.domain.DailyGraphCalculator
import com.fillmark.app.domain.DailyGraphMode
import com.fillmark.app.domain.DailyLineChartData
import com.fillmark.app.domain.DrinkTypeBreakdownItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class AnalyticsInterval(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    THREE_MONTHS("3M"),
    YEAR("Year"),
    ALL_TIME("All")
}

data class ChartBarData(
    val label: String,
    val valueMl: Double,
    val targetMl: Double,
    val isCurrent: Boolean = false,
    val dateIso: String? = null
)

data class AnalyticsUiState(
    val selectedInterval: AnalyticsInterval = AnalyticsInterval.WEEK,
    val dailyGraphMode: DailyGraphMode = DailyGraphMode.CUMULATIVE,
    val periodOffset: Int = 0,
    val periodLabel: String = "",
    val canGoPrevious: Boolean = true,
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val bars: List<ChartBarData> = emptyList(),
    val dailyLineChartData: DailyLineChartData = DailyLineChartData(),
    val totalVolumeMl: Double = 0.0,
    val averageDailyVolumeMl: Double = 0.0,
    val daysGoalMet: Int = 0,
    val totalDays: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val drinkBreakdown: List<DrinkTypeBreakdownItem> = emptyList()
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
    private var lastCalculatedDate: LocalDate = LocalDate.now()

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
        if (_uiState.value.selectedInterval == AnalyticsInterval.ALL_TIME) return
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

    fun refreshCurrentDate() {
        val today = LocalDate.now()
        if (today != lastCalculatedDate) {
            calculateAnalytics(_uiState.value.selectedInterval, _uiState.value.periodOffset)
        }
    }

    fun resetPeriodOffset() {
        if (_uiState.value.periodOffset != 0) {
            _uiState.update { it.copy(periodOffset = 0) }
            calculateAnalytics(_uiState.value.selectedInterval, 0)
        }
    }

    fun inspectDate(dateIso: String) {
        val targetDate = runCatching { LocalDate.parse(dateIso, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() ?: return
        val today = LocalDate.now()
        val offset = ChronoUnit.DAYS.between(targetDate, today).toInt().coerceAtLeast(0)
        _uiState.update { it.copy(selectedInterval = AnalyticsInterval.DAY, periodOffset = offset) }
        calculateAnalytics(AnalyticsInterval.DAY, offset)
    }

    private fun formatPeriodLabel(
        interval: AnalyticsInterval,
        offset: Int,
        today: LocalDate
    ): String {
        val locale = Locale.getDefault()
        val monthDayFormat = DateTimeFormatter.ofPattern("MMM d", locale)
        return when (interval) {
            AnalyticsInterval.DAY -> {
                val date = today.minusDays(offset.toLong())
                when (offset) {
                    0 -> "Today (${date.format(monthDayFormat)})"
                    1 -> "Yesterday (${date.format(monthDayFormat)})"
                    else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
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
                val yearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
                when (offset) {
                    0 -> "Past 12 Weeks (${start.format(monthDayFormat)} – ${end.format(monthDayFormat)})"
                    else -> "${start.format(yearFormat)} – ${end.format(yearFormat)}"
                }
            }
            AnalyticsInterval.YEAR -> {
                val end = today.minusDays((offset * 52 * 7).toLong())
                val start = end.minusDays((52 * 7 - 1).toLong())
                val yearMonthFormat = DateTimeFormatter.ofPattern("MMM yyyy", locale)
                when (offset) {
                    0 -> "Past 52 Weeks (${start.format(yearMonthFormat)} – ${end.format(yearMonthFormat)})"
                    else -> "${start.format(yearMonthFormat)} – ${end.format(yearMonthFormat)}"
                }
            }
            AnalyticsInterval.ALL_TIME -> "All Time"
        }
    }

    private fun calculateAnalytics(interval: AnalyticsInterval, offset: Int = _uiState.value.periodOffset) {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        lastCalculatedDate = today
        val factorsEnabled = userPreferences.hydrationFactorsEnabled
        val periodLabel = formatPeriodLabel(interval, offset, today)
        val streakStats = AnalyticsInsightsCalculator.calculateStreaks(
            records = allRecords,
            defaultTargetMl = defaultTargetMl,
            allTargetsMap = allTargetsMap,
            hydrationFactorsEnabled = factorsEnabled,
            today = today,
            zoneId = zoneId
        )

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
                    mode = _uiState.value.dailyGraphMode,
                    hydrationFactorsEnabled = factorsEnabled
                )
                val dayStart = TimeUtils.startOfDayMillis(targetDate, zoneId)
                val dayEnd = TimeUtils.endOfDayMillis(targetDate, zoneId)
                val periodRecords = allRecords.filter { it.timestamp in dayStart..dayEnd }
                val breakdown = AnalyticsInsightsCalculator.calculateDrinkBreakdown(periodRecords, factorsEnabled)

                _uiState.update {
                    it.copy(
                        periodOffset = offset,
                        periodLabel = periodLabel,
                        canGoPrevious = true,
                        bars = emptyList(),
                        dailyLineChartData = dayChartData,
                        totalVolumeMl = dayChartData.totalIntakeMl,
                        averageDailyVolumeMl = dayChartData.totalIntakeMl,
                        daysGoalMet = if (dayChartData.hasTarget && dayChartData.totalIntakeMl >= dayChartData.targetVolumeMl) 1 else 0,
                        totalDays = 1,
                        currentStreak = streakStats.currentStreak,
                        bestStreak = streakStats.bestStreak,
                        drinkBreakdown = breakdown
                    )
                }
            }

            AnalyticsInterval.WEEK -> {
                val endOfWeek = today.minusDays((offset * 7).toLong())
                val startOfWeek = endOfWeek.minusDays(6)
                val bars = (6 downTo 0).map { daysAgo ->
                    val date = endOfWeek.minusDays(daysAgo.toLong())
                    val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val start = TimeUtils.startOfDayMillis(date, zoneId)
                    val end = TimeUtils.endOfDayMillis(date, zoneId)
                    val dayRecords = allRecords.filter { it.timestamp in start..end }
                    val effective = dayRecords.sumOf { it.countedVolumeMl(factorsEnabled) }
                    val dayTarget = allTargetsMap[dateIso] ?: defaultTargetMl

                    ChartBarData(
                        label = date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
                        valueMl = effective,
                        targetMl = dayTarget,
                        isCurrent = (offset == 0 && daysAgo == 0),
                        dateIso = dateIso
                    )
                }

                val periodRecords = allRecords.filter {
                    it.timestamp in TimeUtils.startOfDayMillis(startOfWeek, zoneId)..TimeUtils.endOfDayMillis(endOfWeek, zoneId)
                }
                val total = bars.sumOf { it.valueMl }
                val met = bars.count { it.targetMl > 0 && it.valueMl >= it.targetMl }
                _uiState.update {
                    it.copy(
                        periodOffset = offset,
                        periodLabel = periodLabel,
                        canGoPrevious = true,
                        bars = bars,
                        totalVolumeMl = total,
                        averageDailyVolumeMl = total / 7.0,
                        daysGoalMet = met,
                        totalDays = 7,
                        currentStreak = streakStats.currentStreak,
                        bestStreak = streakStats.bestStreak,
                        drinkBreakdown = AnalyticsInsightsCalculator.calculateDrinkBreakdown(periodRecords, factorsEnabled)
                    )
                }
            }

            AnalyticsInterval.MONTH -> {
                val endOfMonth = today.minusDays((offset * 30).toLong())
                val startOfMonth = endOfMonth.minusDays(29)
                val bars = (29 downTo 0).map { daysAgo ->
                    val date = endOfMonth.minusDays(daysAgo.toLong())
                    val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val start = TimeUtils.startOfDayMillis(date, zoneId)
                    val end = TimeUtils.endOfDayMillis(date, zoneId)
                    val dayRecords = allRecords.filter { it.timestamp in start..end }
                    val effective = dayRecords.sumOf { it.countedVolumeMl(factorsEnabled) }
                    val label = if (daysAgo % 5 == 0) date.format(DateTimeFormatter.ofPattern("d", Locale.getDefault())) else ""
                    val dayTarget = allTargetsMap[dateIso] ?: defaultTargetMl

                    ChartBarData(
                        label = label,
                        valueMl = effective,
                        targetMl = dayTarget,
                        isCurrent = (offset == 0 && daysAgo == 0),
                        dateIso = dateIso
                    )
                }

                val periodRecords = allRecords.filter {
                    it.timestamp in TimeUtils.startOfDayMillis(startOfMonth, zoneId)..TimeUtils.endOfDayMillis(endOfMonth, zoneId)
                }
                val total = bars.sumOf { it.valueMl }
                val met = bars.count { it.targetMl > 0 && it.valueMl >= it.targetMl }
                _uiState.update {
                    it.copy(
                        periodOffset = offset,
                        periodLabel = periodLabel,
                        canGoPrevious = true,
                        bars = bars,
                        totalVolumeMl = total,
                        averageDailyVolumeMl = total / 30.0,
                        daysGoalMet = met,
                        totalDays = 30,
                        currentStreak = streakStats.currentStreak,
                        bestStreak = streakStats.bestStreak,
                        drinkBreakdown = AnalyticsInsightsCalculator.calculateDrinkBreakdown(periodRecords, factorsEnabled)
                    )
                }
            }

            AnalyticsInterval.THREE_MONTHS, AnalyticsInterval.YEAR -> {
                val numWeeks = if (interval == AnalyticsInterval.THREE_MONTHS) 12 else 52
                val summary = computeMultiWeekSummary(
                    records = allRecords,
                    numWeeks = numWeeks,
                    offset = offset,
                    today = today,
                    zoneId = zoneId,
                    defaultTargetMl = defaultTargetMl,
                    allTargetsMap = allTargetsMap,
                    hydrationFactorsEnabled = factorsEnabled
                )

                val endOfPeriod = today.minusDays((offset * numWeeks * 7).toLong())
                val startOfPeriod = endOfPeriod.minusDays((numWeeks * 7 - 1).toLong())
                val periodRecords = allRecords.filter {
                    it.timestamp in TimeUtils.startOfDayMillis(startOfPeriod, zoneId)..TimeUtils.endOfDayMillis(endOfPeriod, zoneId)
                }

                _uiState.update {
                    it.copy(
                        periodOffset = offset,
                        periodLabel = periodLabel,
                        canGoPrevious = true,
                        bars = summary.bars,
                        totalVolumeMl = summary.totalVolumeMl,
                        averageDailyVolumeMl = summary.averageDailyVolumeMl,
                        daysGoalMet = summary.daysGoalMet,
                        totalDays = summary.totalDays,
                        currentStreak = streakStats.currentStreak,
                        bestStreak = streakStats.bestStreak,
                        drinkBreakdown = AnalyticsInsightsCalculator.calculateDrinkBreakdown(periodRecords, factorsEnabled)
                    )
                }
            }

            AnalyticsInterval.ALL_TIME -> {
                val validRecords = allRecords.filter { !TimeUtils.toLocalDate(it.timestamp, zoneId).isAfter(today) }
                if (validRecords.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            periodOffset = 0,
                            periodLabel = periodLabel,
                            canGoPrevious = false,
                            bars = emptyList(),
                            totalVolumeMl = 0.0,
                            averageDailyVolumeMl = 0.0,
                            daysGoalMet = 0,
                            totalDays = 0,
                            currentStreak = 0,
                            bestStreak = 0,
                            drinkBreakdown = emptyList()
                        )
                    }
                    return
                }

                val earliestDate = validRecords.minOf { TimeUtils.toLocalDate(it.timestamp, zoneId) }
                val startYm = YearMonth.from(earliestDate)
                val endYm = YearMonth.from(today)
                val months = mutableListOf<YearMonth>()
                var ymCursor = startYm
                while (!ymCursor.isAfter(endYm)) {
                    months.add(ymCursor)
                    ymCursor = ymCursor.plusMonths(1)
                }

                val labelStride = (months.size / 6).coerceAtLeast(1)
                val bars = months.mapIndexed { idx, ym ->
                    val monthStart = if (ym == startYm) earliestDate else ym.atDay(1)
                    val monthEnd = if (ym == endYm) today else ym.atEndOfMonth()
                    val daysInSlice = (ChronoUnit.DAYS.between(monthStart, monthEnd).toInt() + 1).coerceAtLeast(1)
                    val startMs = TimeUtils.startOfDayMillis(monthStart, zoneId)
                    val endMs = TimeUtils.endOfDayMillis(monthEnd, zoneId)
                    val monthTotal = validRecords
                        .filter { it.timestamp in startMs..endMs }
                        .sumOf { it.countedVolumeMl(factorsEnabled) }

                    var targetSum = 0.0
                    var d = monthStart
                    while (!d.isAfter(monthEnd)) {
                        val dIso = d.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        targetSum += allTargetsMap[dIso] ?: defaultTargetMl
                        d = d.plusDays(1)
                    }

                    val showLabel = (idx % labelStride == 0) || ym == endYm
                    ChartBarData(
                        label = if (showLabel) ym.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())) else "",
                        valueMl = monthTotal / daysInSlice,
                        targetMl = targetSum / daysInSlice,
                        isCurrent = (ym == endYm)
                    )
                }

                val dailyTotals = mutableMapOf<LocalDate, Double>()
                for (r in validRecords) {
                    val d = TimeUtils.toLocalDate(r.timestamp, zoneId)
                    dailyTotals[d] = (dailyTotals[d] ?: 0.0) + r.countedVolumeMl(factorsEnabled)
                }

                var totalDays = 0
                var metDays = 0
                var dayCursor = earliestDate
                while (!dayCursor.isAfter(today)) {
                    totalDays++
                    val dIso = dayCursor.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val t = allTargetsMap[dIso] ?: defaultTargetMl
                    val v = dailyTotals[dayCursor] ?: 0.0
                    if (t > 0 && v >= t) metDays++
                    dayCursor = dayCursor.plusDays(1)
                }

                val totalVolume = validRecords.sumOf { it.countedVolumeMl(factorsEnabled) }
                _uiState.update {
                    it.copy(
                        periodOffset = 0,
                        periodLabel = periodLabel,
                        canGoPrevious = false,
                        bars = bars,
                        totalVolumeMl = totalVolume,
                        averageDailyVolumeMl = if (totalDays > 0) totalVolume / totalDays else 0.0,
                        daysGoalMet = metDays,
                        totalDays = totalDays,
                        currentStreak = streakStats.currentStreak,
                        bestStreak = streakStats.bestStreak,
                        drinkBreakdown = AnalyticsInsightsCalculator.calculateDrinkBreakdown(validRecords, factorsEnabled)
                    )
                }
            }
        }
    }

    data class MultiWeekSummary(
        val bars: List<ChartBarData>,
        val totalVolumeMl: Double,
        val averageDailyVolumeMl: Double,
        val daysGoalMet: Int,
        val totalDays: Int
    )

    companion object {
        fun computeMultiWeekSummary(
            records: List<HydrationRecordEntity>,
            numWeeks: Int,
            offset: Int,
            today: LocalDate,
            zoneId: ZoneId,
            defaultTargetMl: Double,
            allTargetsMap: Map<String, Double>,
            hydrationFactorsEnabled: Boolean = true
        ): MultiWeekSummary {
            val endOfPeriod = today.minusDays((offset * numWeeks * 7).toLong())
            var daysGoalMet = 0
            var totalVolumeMl = 0.0

            val bars = (numWeeks - 1 downTo 0).map { weeksAgo ->
                val weekEnd = endOfPeriod.minusDays((weeksAgo * 7).toLong())
                val weekStart = weekEnd.minusDays(6)
                var weekVolume = 0.0
                var weekTargetSum = 0.0

                for (dayOffset in 0..6) {
                    val day = weekStart.plusDays(dayOffset.toLong())
                    val dayIso = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val startMs = TimeUtils.startOfDayMillis(day, zoneId)
                    val endMs = TimeUtils.endOfDayMillis(day, zoneId)
                    val dayEffective = records
                        .filter { it.timestamp in startMs..endMs }
                        .sumOf { it.countedVolumeMl(hydrationFactorsEnabled) }
                    val dayTarget = allTargetsMap[dayIso] ?: defaultTargetMl

                    weekVolume += dayEffective
                    weekTargetSum += dayTarget
                    if (dayTarget > 0 && dayEffective >= dayTarget) {
                        daysGoalMet++
                    }
                }

                totalVolumeMl += weekVolume
                ChartBarData(
                    label = if (weeksAgo % 4 == 0) weekStart.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())) else "",
                    valueMl = weekVolume / 7.0,
                    targetMl = weekTargetSum / 7.0,
                    isCurrent = (offset == 0 && weeksAgo == 0)
                )
            }

            val totalDays = numWeeks * 7
            return MultiWeekSummary(
                bars = bars,
                totalVolumeMl = totalVolumeMl,
                averageDailyVolumeMl = if (totalDays > 0) totalVolumeMl / totalDays else 0.0,
                daysGoalMet = daysGoalMet,
                totalDays = totalDays
            )
        }
    }
}
