package com.qingricheng.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object TimeUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val prettyDate = DateTimeFormatter.ofPattern("M月d日")
    private val prettyDateTime = DateTimeFormatter.ofPattern("M月d日 HH:mm")

    fun zone(): ZoneId = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone())
    fun todayKey(): String = today().format(dateFormatter)
    fun nowText(): String = ZonedDateTime.now(zone()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (zzzz)"))

    fun dateKey(date: LocalDate): String = date.format(dateFormatter)
    fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value, dateFormatter) }.getOrNull()
    fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value, timeFormatter) }.getOrNull()
    fun prettyDate(value: String): String = parseDate(value)?.format(prettyDate) ?: value
    fun prettyDateTime(date: String, time: String): String =
        if (time.isBlank()) "${prettyDate(date)} · 全天" else "${prettyDate(date)} $time"

    fun monthStart(month: YearMonth): LocalDate = month.atDay(1)
    fun monthEnd(month: YearMonth): LocalDate = month.atEndOfMonth()
    fun gridStart(month: YearMonth): LocalDate {
        val first = month.atDay(1)
        val offset = (first.dayOfWeek.value + 6) % 7 // Monday = 0
        return first.minusDays(offset.toLong())
    }
    fun gridDays(month: YearMonth): List<LocalDate> {
        val start = gridStart(month)
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }

    fun betweenInclusive(start: LocalDate, end: LocalDate): List<LocalDate> {
        if (end.isBefore(start)) return emptyList()
        val days = ChronoUnit.DAYS.between(start, end).toInt()
        return (0..days).map { start.plusDays(it.toLong()) }
    }

    fun epochMillis(date: String, time: String, allDay: Boolean, zone: ZoneId = zone()): Long? {
        val d = parseDate(date) ?: return null
        val t = if (allDay) LocalTime.MIDNIGHT else parseTime(time) ?: return null
        return LocalDateTime.of(d, t).atZone(zone).toInstant().toEpochMilli()
    }

    fun instantToDate(instant: Instant, zone: ZoneId = zone()): String =
        instant.atZone(zone).toLocalDate().format(dateFormatter)

    fun instantToTime(instant: Instant, zone: ZoneId = zone()): String =
        instant.atZone(zone).toLocalTime().format(timeFormatter)

    fun dayRange(month: YearMonth): Pair<String, String> {
        val start = gridStart(month).minusDays(7)
        val end = gridStart(month).plusDays(48)
        return dateKey(start) to dateKey(end)
    }

    fun localDateTimeToIcs(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))

    fun dateToIcs(value: LocalDate): String = value.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
}
