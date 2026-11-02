package com.budgetapp.voiceexpense.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {
    val lastDay: LocalDate get() = endExclusive.minusDays(1)

    fun startInstant(zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()

    fun endInstant(zone: ZoneId): Instant = endExclusive.atStartOfDay(zone).toInstant()
}

fun dayOf(date: LocalDate): DateRange = DateRange(date, date.plusDays(1))

fun weekOf(date: LocalDate, weekStart: DayOfWeek): DateRange {
    val start = date.with(TemporalAdjusters.previousOrSame(weekStart))
    return DateRange(start, start.plusDays(7))
}

fun monthOf(date: LocalDate): DateRange {
    val start = date.withDayOfMonth(1)
    return DateRange(start, start.plusMonths(1))
}

/** Today still counts, because more can be spent before the day ends. */
fun daysLeftIncludingToday(today: LocalDate, range: DateRange): Int =
    ChronoUnit.DAYS.between(today, range.endExclusive).toInt()
