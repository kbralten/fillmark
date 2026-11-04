package com.fillmark.app.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fillmark.app.core.DisplayUnit
import com.fillmark.app.core.TimeUtils
import com.fillmark.app.data.local.entity.HydrationRecordEntity
import com.fillmark.app.data.repository.HydrationRepository
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
    val displayUnit: DisplayUnit = DisplayUnit.METRIC_ML,
    val bars: List<ChartBarData> = emptyList(),
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

    init {
        viewModelScope.launch {
            combine(
                repository.observeAllRecords(),
                repository.preferencesFlow
            ) { records, prefs ->
                allRecords = records
                defaultTargetMl = prefs.defaultDailyTargetMl
                _uiState.update { it.copy(displayUnit = prefs.displayUnit) }
                calculateAnalytics(_uiState.value.selectedInterval)
            }.collect {}
        }
    }

    fun selectInterval(interval: AnalyticsInterval) {
        _uiState.update { it.copy(selectedInterval = interval) }
        calculateAnalytics(interval)
    }

    private fun calculateAnalytics(interval: AnalyticsInterval) {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        when (interval) {
            AnalyticsInterval.DAY -> {
                // 24 hours breakdown for today
                val startOfDay = TimeUtils.startOfDayMillis(today, zoneId)
                val endOfDay = TimeUtils.endOfDayMillis(today, zoneId)
                val todayRecords = allRecords.filter { it.timestamp in startOfDay..endOfDay }

                val bars = (0..23).map { hour ->
                    val hourRecords = todayRecords.filter {
                        val localHour = Instant.ofEpochMilli(it.timestamp).atZone(zoneId).hour
                        localHour == hour
                    }
                    val effective = hourRecords.sumOf { it.effectiveVolumeMl }
                    val label = when (hour) {
                        0 -> "12a"
                        6 -> "6a"
                        12 -> "12p"
                        18 -> "6p"
                        23 -> "11p"
                        else -> ""
                    }
                    ChartBarData(
                        label = label,
                        valueMl = effective,
                        targetMl = defaultTargetMl / 24.0,
                        isCurrent = hour == Instant.now().atZone(zoneId).hour
                    )
                }

                val total = todayRecords.sumOf { it.effectiveVolumeMl }
                _uiState.update {
                    it.copy(
                        bars = bars,
                        totalVolumeMl = total,
                        averageDailyVolumeMl = total,
                        daysGoalMet = if (total >= defaultTargetMl) 1 else 0,
                        totalDays = 1
                    )
                }
            }

            AnalyticsInterval.WEEK -> {
                // Past 7 days
                val bars = (6 downTo 0).map { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong())
                    val dateIso = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val start = TimeUtils.startOfDayMillis(date, zoneId)
                    val end = TimeUtils.endOfDayMillis(date, zoneId)
                    val dayRecords = allRecords.filter { it.timestamp in start..end }
                    val effective = dayRecords.sumOf { it.effectiveVolumeMl }

                    ChartBarData(
                        label = date.format(DateTimeFormatter.ofPattern("EEE", Locale.US)),
                        valueMl = effective,
                        targetMl = defaultTargetMl,
                        isCurrent = daysAgo == 0
                    )
                }

                val total = bars.sumOf { it.valueMl }
                val met = bars.count { it.valueMl >= it.targetMl }
                _uiState.update {
                    it.copy(
                        bars = bars,
                        totalVolumeMl = total,
                        averageDailyVolumeMl = total / 7.0,
                        daysGoalMet = met,
                        totalDays = 7
                    )
                }
            }

            AnalyticsInterval.MONTH -> {
                // Past 30 days
                val bars = (29 downTo 0).map { daysAgo ->
                    val date = today.minusDays(daysAgo.toLong())
                    val start = TimeUtils.startOfDayMillis(date, zoneId)
                    val end = TimeUtils.endOfDayMillis(date, zoneId)
                    val dayRecords = allRecords.filter { it.timestamp in start..end }
                    val effective = dayRecords.sumOf { it.effectiveVolumeMl }
                    val label = if (daysAgo % 5 == 0) date.format(DateTimeFormatter.ofPattern("d", Locale.US)) else ""

                    ChartBarData(
                        label = label,
                        valueMl = effective,
                        targetMl = defaultTargetMl,
                        isCurrent = daysAgo == 0
                    )
                }

                val total = bars.sumOf { it.valueMl }
                val met = bars.count { it.valueMl >= it.targetMl }
                _uiState.update {
                    it.copy(
                        bars = bars,
                        totalVolumeMl = total,
                        averageDailyVolumeMl = total / 30.0,
                        daysGoalMet = met,
                        totalDays = 30
                    )
                }
            }

            AnalyticsInterval.THREE_MONTHS, AnalyticsInterval.YEAR -> {
                // Weekly aggregates over 12 weeks
                val numWeeks = if (interval == AnalyticsInterval.THREE_MONTHS) 12 else 52
                val bars = (numWeeks - 1 downTo 0).map { weeksAgo ->
                    val end = today.minusDays((weeksAgo * 7).toLong())
                    val start = end.minusDays(6)
                    val startMs = TimeUtils.startOfDayMillis(start, zoneId)
                    val endMs = TimeUtils.endOfDayMillis(end, zoneId)
                    val weekRecords = allRecords.filter { it.timestamp in startMs..endMs }
                    val effective = weekRecords.sumOf { it.effectiveVolumeMl }

                    ChartBarData(
                        label = if (weeksAgo % 4 == 0) start.format(DateTimeFormatter.ofPattern("MMM", Locale.US)) else "",
                        valueMl = effective / 7.0, // daily average for that week
                        targetMl = defaultTargetMl,
                        isCurrent = weeksAgo == 0
                    )
                }

                val total = bars.sumOf { it.valueMl * 7.0 }
                val met = bars.count { it.valueMl >= it.targetMl }
                _uiState.update {
                    it.copy(
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

