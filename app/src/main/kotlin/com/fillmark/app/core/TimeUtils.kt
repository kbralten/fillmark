package com.fillmark.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    private fun timeFormatter(is24Hour: Boolean, locale: Locale): DateTimeFormatter {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, locale)
    }

    private fun shortDateFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d", locale)

    private fun monthDayFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", locale)

    private fun fullDateFormatter(locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", locale)

    fun formatCurrentDateHeader(
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        todayLabel: String = "Today"
    ): String {
        val date = LocalDate.now(zoneId)
        val monthDay = date.format(monthDayFormatter(locale))
        return "$todayLabel, $monthDay"
    }

    fun currentDateIso(zoneId: ZoneId = ZoneId.systemDefault()): String {
        return LocalDate.now(zoneId).format(ISO_DATE_FORMATTER)
    }

    fun toIsoDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return date.format(ISO_DATE_FORMATTER)
    }

    fun toLocalDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
    }

    fun toLocalTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalTime {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalTime()
    }

    fun toEpochMillis(date: LocalDate, time: LocalTime, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun utcMillisToLocalDate(utcMillis: Long): LocalDate {
        return Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    }

    fun localDateToUtcMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }

    fun startOfDayMillis(date: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun endOfDayMillis(date: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
    }

    fun formatTime(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        is24Hour: Boolean = false,
        locale: Locale = Locale.getDefault()
    ): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalTime()
        return time.format(timeFormatter(is24Hour, locale))
    }

    fun formatTime(
        time: LocalTime,
        is24Hour: Boolean = false,
        locale: Locale = Locale.getDefault()
    ): String {
        return time.format(timeFormatter(is24Hour, locale))
    }

    fun formatFullDate(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        return date.format(fullDateFormatter(locale))
    }

    fun formatDateTime(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        is24Hour: Boolean = false,
        locale: Locale = Locale.getDefault(),
        todayLabel: String = "Today",
        yesterdayLabel: String = "Yesterday",
        atFormat: String = "%1\$s at %2\$s"
    ): String {
        val date = toLocalDate(epochMillis, zoneId)
        val timeStr = formatTime(epochMillis, zoneId, is24Hour, locale)
        val today = LocalDate.now(zoneId)
        val dateStr = when (date) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
            else -> date.format(fullDateFormatter(locale))
        }
        return String.format(locale, atFormat, dateStr, timeStr)
    }

    fun formatFriendlyDate(
        dateString: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
        todayLabel: String = "Today",
        yesterdayLabel: String = "Yesterday"
    ): String {
        val date = LocalDate.parse(dateString, ISO_DATE_FORMATTER)
        val today = LocalDate.now(zoneId)
        return when (date) {
            today -> todayLabel
            today.minusDays(1) -> yesterdayLabel
            else -> date.format(shortDateFormatter(locale))
        }
    }

    fun formatShortDate(dateString: String, locale: Locale = Locale.getDefault()): String {
        val date = LocalDate.parse(dateString, ISO_DATE_FORMATTER)
        return date.format(monthDayFormatter(locale))
    }

    fun parseTimeString(timeStr: String): LocalTime {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    fun formatTimeString(
        timeStr: String,
        is24Hour: Boolean = false,
        locale: Locale = Locale.getDefault()
    ): String {
        return parseTimeString(timeStr).format(timeFormatter(is24Hour, locale))
    }
}
