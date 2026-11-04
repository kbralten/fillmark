package com.fillmark.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun currentDateIso(zoneId: ZoneId = ZoneId.systemDefault()): String {
        return LocalDate.now(zoneId).format(ISO_DATE_FORMATTER)
    }

    fun toIsoDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return date.format(ISO_DATE_FORMATTER)
    }

    fun startOfDayMillis(date: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun endOfDayMillis(date: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    }

    fun formatTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalTime()
        return time.format(TIME_FORMATTER)
    }

    fun formatFriendlyDate(dateString: String, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = LocalDate.parse(dateString, ISO_DATE_FORMATTER)
        val today = LocalDate.now(zoneId)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(SHORT_DATE_FORMATTER)
        }
    }

    fun formatShortDate(dateString: String): String {
        val date = LocalDate.parse(dateString, ISO_DATE_FORMATTER)
        return date.format(MONTH_DAY_FORMATTER)
    }

    fun parseTimeString(timeStr: String): LocalTime {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    fun formatTimeString(timeStr: String): String {
        return parseTimeString(timeStr).format(TIME_FORMATTER)
    }
}

