package com.fillmark.app.ui.analytics

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.R
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class AnalyticsInterval(val label: String, @param:StringRes val labelRes: Int) {
    DAY("Day", R.string.analytics_interval_day),
    WEEK("Week", R.string.analytics_interval_week),
    MONTH("Month", R.string.analytics_interval_month),
    THREE_MONTHS("3M", R.string.analytics_interval_3m),
    YEAR("Year", R.string.analytics_interval_year),
    ALL_TIME("All", R.string.analytics_interval_all)
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
    private var calculationJob: Job? = null

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
        val app = getApplication<Application>()
        val locale = Locale.getDefault()
        val monthDayFormat = DateTimeFormatter.ofPattern("MMM d", locale)
        return when (interval) {
            AnalyticsInterval.DAY -> {
                val date = today.minusDays(offset.toLong())
                when (offset) {
                    0 -> app.getString(R.string.analytics_period_today, date.format(monthDayFormat))
                    1 -> app.getString(R.string.analytics_period_yesterday, date.format(monthDayFormat))
                    else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
                }
            }
            AnalyticsInterval.WEEK -> {
                val end = today.minusDays((offset * 7).toLong())
                val start = end.minusDays(6)
                when (offset) {
                    0 -> app.getString(
                        R.string.analytics_period_this_week,
                        start.format(monthDayFormat),
                        end.format(monthDayFormat)
                    )
                    1 -> app.getString(
                        R.string.analytics_period_last_week,
                        start.format(monthDayFormat),
                        end.format(monthDayFormat)
                    )
                    else -> app.getString(
                        R.string.analytics_period_range,
                        start.format(monthDayFormat),
                        end.format(monthDayFormat)
                    )
                }
            }
            AnalyticsInterval.MONTH -> {
                val end = today.minusDays((offset * 30).toLong())
                val start = end.minusDays(29)
                when (offset) {
                    0 -> app.getString(
                        R.string.analytics_period_past_30_days,
                        start.format(monthDayFormat),
                        end.format(monthDayFormat)
                    )
                    else -> app.getString(
                        R.string.analytics_period_range,
                        start.format(monthDayFormat),
                        end.format(monthDayFormat)
                    )
                }
            }
            AnalyticsInterval.THREE_MONTHS -> {
                val end = today.minusDays((offset * 12 * 7).toLong())
                val start = end.minusDays((12 * 7 - 1).toLong())
                val yearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
                when (offset) {
                    0 -> app.getString(
                        R.string.analytics_period_past_12_weeks,
                        start.format(monthDayFormat),
                        end.format(monthDayFormat)
                    )
                    else -> app.getString(
                        R.string.analytics_period_range,
                        start.format(yearFormat),
                        end.format(yearFormat)
                    )
                }
            }
            AnalyticsInterval.YEAR -> {
                val end = today.minusDays((offset * 52 * 7).toLong())
                val start = end.minusDays((52 * 7 - 1).toLong())
                val yearMonthFormat = DateTimeFormatter.ofPattern("MMM yyyy", locale)
                when (offset) {
                    0 -> app.getString(
                        R.string.analytics_period_past_52_weeks,
                        start.format(yearMonthFormat),
                        end.format(yearMonthFormat)
                    )
                    else -> app.getString(
                        R.string.analytics_period_range,
                        start.format(yearMonthFormat),
                        end.format(yearMonthFormat)
                    )
                }
            }
            AnalyticsInterval.ALL_TIME -> app.getString(R.string.analytics_period_all_time)
        }
    }

    private fun calculateAnalytics(interval: AnalyticsInterval, offset: Int = _uiState.value.periodOffset) {
        val recordsSnapshot = allRecords
        val prefsSnapshot = userPreferences
        val defaultTargetSnapshot = defaultTargetMl
        val targetsSnapshot = allTargetsMap
        val dailyModeSnapshot = _uiState.value.dailyGraphMode

        calculationJob?.cancel()
        calculationJob = viewModelScope.launch {
            val computedState = withContext(Dispatchers.Default) {
                val zoneId = ZoneId.systemDefault()
                val today = LocalDate.now(zoneId)
                lastCalculatedDate = today
                val factorsEnabled = prefsSnapshot.hydrationFactorsEnabled
                val periodLabel = formatPeriodLabel(interval, offset, today)
                val streakStats = AnalyticsInsightsCalculator.calculateStreaks(
                    records = recordsSnapshot,
                    defaultTargetMl = defaultTargetSnapshot,
                    allTargetsMap = targetsSnapshot,
                    hydrationFactorsEnabled = factorsEnabled,
                    today = today,
                    zoneId = zoneId
                )

                val recordsByDate = recordsSnapshot.groupBy { TimeUtils.toLocalDate(it.timestamp, zoneId) }

                when (interval) {
                    AnalyticsInterval.DAY -> {
                        val targetDate = today.minusDays(offset.toLong())
                        val targetDateIso = targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val targetMlForDay = targetsSnapshot[targetDateIso] ?: defaultTargetSnapshot
                        val dayRecords = recordsByDate[targetDate].orEmpty()

                        val dayChartData = DailyGraphCalculator.calculate(
                            records = dayRecords,
                            date = targetDate,
                            zoneId = zoneId,
                            targetMl = targetMlForDay,
                            wakingHoursStart = prefsSnapshot.wakingHoursStart,
                            wakingHoursEnd = prefsSnapshot.wakingHoursEnd,
                            wakingPacingEnabled = prefsSnapshot.wakingPacingEnabled,
                            displayUnit = prefsSnapshot.displayUnit,
                            mode = dailyModeSnapshot,
                            hydrationFactorsEnabled = factorsEnabled
                        )
                        val breakdown = AnalyticsInsightsCalculator.calculateDrinkBreakdown(dayRecords, factorsEnabled)

                        _uiState.value.copy(
                            selectedInterval = interval,
                            dailyGraphMode = dailyModeSnapshot,
                            displayUnit = prefsSnapshot.displayUnit,
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

                    AnalyticsInterval.WEEK -> {
                        val endOfWeek = today.minusDays((offset * 7).toLong())
                        val periodRecords = mutableListOf<HydrationRecordEntity>()
                        val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
                        val bars = (6 downTo 0).map { daysAgo ->
                            val date = endOfWeek.minusDays(daysAgo.toLong())
                            val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val dayRecords = recordsByDate[date].orEmpty()
                            periodRecords.addAll(dayRecords)
                            val effective = dayRecords.sumOf { it.countedVolumeMl(factorsEnabled) }
                            val dayTarget = targetsSnapshot[dateIso] ?: defaultTargetSnapshot

                            ChartBarData(
                                label = date.format(dayFormatter),
                                valueMl = effective,
                                targetMl = dayTarget,
                                isCurrent = (offset == 0 && daysAgo == 0),
                                dateIso = dateIso
                            )
                        }

                        val total = bars.sumOf { it.valueMl }
                        val met = bars.count { it.targetMl > 0 && it.valueMl >= it.targetMl }
                        _uiState.value.copy(
                            selectedInterval = interval,
                            dailyGraphMode = dailyModeSnapshot,
                            displayUnit = prefsSnapshot.displayUnit,
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

                    AnalyticsInterval.MONTH -> {
                        val endOfMonth = today.minusDays((offset * 30).toLong())
                        val periodRecords = mutableListOf<HydrationRecordEntity>()
                        val dayFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())
                        val bars = (29 downTo 0).map { daysAgo ->
                            val date = endOfMonth.minusDays(daysAgo.toLong())
                            val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val dayRecords = recordsByDate[date].orEmpty()
                            periodRecords.addAll(dayRecords)
                            val effective = dayRecords.sumOf { it.countedVolumeMl(factorsEnabled) }
                            val label = if (daysAgo % 5 == 0) date.format(dayFormatter) else ""
                            val dayTarget = targetsSnapshot[dateIso] ?: defaultTargetSnapshot

                            ChartBarData(
                                label = label,
                                valueMl = effective,
                                targetMl = dayTarget,
                                isCurrent = (offset == 0 && daysAgo == 0),
                                dateIso = dateIso
                            )
                        }

                        val total = bars.sumOf { it.valueMl }
                        val met = bars.count { it.targetMl > 0 && it.valueMl >= it.targetMl }
                        _uiState.value.copy(
                            selectedInterval = interval,
                            dailyGraphMode = dailyModeSnapshot,
                            displayUnit = prefsSnapshot.displayUnit,
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

                    AnalyticsInterval.THREE_MONTHS, AnalyticsInterval.YEAR -> {
                        val numWeeks = if (interval == AnalyticsInterval.THREE_MONTHS) 12 else 52
                        val summary = computeMultiWeekSummary(
                            records = recordsSnapshot,
                            numWeeks = numWeeks,
                            offset = offset,
                            today = today,
                            zoneId = zoneId,
                            defaultTargetMl = defaultTargetSnapshot,
                            allTargetsMap = targetsSnapshot,
                            hydrationFactorsEnabled = factorsEnabled,
                            pregroupedByDate = recordsByDate
                        )

                        val endOfPeriod = today.minusDays((offset * numWeeks * 7).toLong())
                        val startOfPeriod = endOfPeriod.minusDays((numWeeks * 7 - 1).toLong())
                        val periodRecords = collectRecordsInDateRange(recordsByDate, startOfPeriod, endOfPeriod)

                        _uiState.value.copy(
                            selectedInterval = interval,
                            dailyGraphMode = dailyModeSnapshot,
                            displayUnit = prefsSnapshot.displayUnit,
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

                    AnalyticsInterval.ALL_TIME -> {
                        val validEntries = recordsByDate.entries.filter { !it.key.isAfter(today) }
                        if (validEntries.isEmpty()) {
                            _uiState.value.copy(
                                selectedInterval = interval,
                                dailyGraphMode = dailyModeSnapshot,
                                displayUnit = prefsSnapshot.displayUnit,
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
                        } else {
                            val earliestDate = validEntries.minOf { it.key }
                            val dailyTotals = validEntries.associate { (date, list) ->
                                date to list.sumOf { it.countedVolumeMl(factorsEnabled) }
                            }
                            val validRecords = validEntries.flatMap { it.value }

                            val startYm = YearMonth.from(earliestDate)
                            val endYm = YearMonth.from(today)
                            val months = mutableListOf<YearMonth>()
                            var ymCursor = startYm
                            while (!ymCursor.isAfter(endYm)) {
                                months.add(ymCursor)
                                ymCursor = ymCursor.plusMonths(1)
                            }

                            val labelStride = (months.size / 6).coerceAtLeast(1)
                            val monthLabelFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
                            val bars = months.mapIndexed { idx, ym ->
                                val monthStart = if (ym == startYm) earliestDate else ym.atDay(1)
                                val monthEnd = if (ym == endYm) today else ym.atEndOfMonth()
                                val daysInSlice = (ChronoUnit.DAYS.between(monthStart, monthEnd).toInt() + 1).coerceAtLeast(1)

                                var monthTotal = 0.0
                                var targetSum = 0.0
                                var d = monthStart
                                while (!d.isAfter(monthEnd)) {
                                    monthTotal += dailyTotals[d] ?: 0.0
                                    val dIso = d.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    targetSum += targetsSnapshot[dIso] ?: defaultTargetSnapshot
                                    d = d.plusDays(1)
                                }

                                val showLabel = (idx % labelStride == 0) || ym == endYm
                                ChartBarData(
                                    label = if (showLabel) ym.format(monthLabelFmt) else "",
                                    valueMl = monthTotal / daysInSlice,
                                    targetMl = targetSum / daysInSlice,
                                    isCurrent = (ym == endYm)
                                )
                            }

                            var totalDays = 0
                            var metDays = 0
                            var dayCursor = earliestDate
                            while (!dayCursor.isAfter(today)) {
                                totalDays++
                                val dIso = dayCursor.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val t = targetsSnapshot[dIso] ?: defaultTargetSnapshot
                                val v = dailyTotals[dayCursor] ?: 0.0
                                if (t > 0 && v >= t) metDays++
                                dayCursor = dayCursor.plusDays(1)
                            }

                            val totalVolume = dailyTotals.values.sum()
                            _uiState.value.copy(
                                selectedInterval = interval,
                                dailyGraphMode = dailyModeSnapshot,
                                displayUnit = prefsSnapshot.displayUnit,
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
            _uiState.value = computedState
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
        private fun collectRecordsInDateRange(
            recordsByDate: Map<LocalDate, List<HydrationRecordEntity>>,
            startDate: LocalDate,
            endDate: LocalDate
        ): List<HydrationRecordEntity> {
            val result = mutableListOf<HydrationRecordEntity>()
            var cursor = startDate
            while (!cursor.isAfter(endDate)) {
                recordsByDate[cursor]?.let { result.addAll(it) }
                cursor = cursor.plusDays(1)
            }
            return result
        }

        fun computeMultiWeekSummary(
            records: List<HydrationRecordEntity>,
            numWeeks: Int,
            offset: Int,
            today: LocalDate,
            zoneId: ZoneId,
            defaultTargetMl: Double,
            allTargetsMap: Map<String, Double>,
            hydrationFactorsEnabled: Boolean = true,
            pregroupedByDate: Map<LocalDate, List<HydrationRecordEntity>>? = null
        ): MultiWeekSummary {
            val recordsByDate = pregroupedByDate
                ?: records.groupBy { TimeUtils.toLocalDate(it.timestamp, zoneId) }
            val dailyTotals = recordsByDate.mapValues { (_, list) ->
                list.sumOf { it.countedVolumeMl(hydrationFactorsEnabled) }
            }

            val endOfPeriod = today.minusDays((offset * numWeeks * 7).toLong())
            var daysGoalMet = 0
            var totalVolumeMl = 0.0
            val monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

            val bars = (numWeeks - 1 downTo 0).map { weeksAgo ->
                val weekEnd = endOfPeriod.minusDays((weeksAgo * 7).toLong())
                val weekStart = weekEnd.minusDays(6)
                var weekVolume = 0.0
                var weekTargetSum = 0.0

                for (dayOffset in 0..6) {
                    val day = weekStart.plusDays(dayOffset.toLong())
                    val dayIso = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val dayEffective = dailyTotals[day] ?: 0.0
                    val dayTarget = allTargetsMap[dayIso] ?: defaultTargetMl

                    weekVolume += dayEffective
                    weekTargetSum += dayTarget
                    if (dayTarget > 0 && dayEffective >= dayTarget) {
                        daysGoalMet++
                    }
                }

                totalVolumeMl += weekVolume
                ChartBarData(
                    label = if (weeksAgo % 4 == 0) weekStart.format(monthFmt) else "",
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
