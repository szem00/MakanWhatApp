package com.budgetapp.voiceexpense.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodsTest {
    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)

    @Test
    fun mondayWeekContainsEachDayOfItself() {
        val expected = DateRange(date(11, 2), date(11, 9))
        (2..8).forEach { day ->
            assertEquals(expected, weekOf(date(11, day), DayOfWeek.MONDAY))
        }
    }

    @Test
    fun sundayWeekStartsOnTheSundayBefore() {
        val expected = DateRange(date(11, 1), date(11, 8))
        assertEquals(expected, weekOf(date(11, 1), DayOfWeek.SUNDAY))
        assertEquals(expected, weekOf(date(11, 4), DayOfWeek.SUNDAY))
        assertEquals(expected, weekOf(date(11, 7), DayOfWeek.SUNDAY))
    }

    @Test
    fun weekCanSpanTwoYears() {
        val week = weekOf(date(1, 1, 2027), DayOfWeek.MONDAY)
        assertEquals(date(12, 28), week.start)
        assertEquals(date(1, 4, 2027), week.endExclusive)
    }

    @Test
    fun monthCoversCalendarMonth() {
        assertEquals(DateRange(date(11, 1), date(12, 1)), monthOf(date(11, 15)))
        assertEquals(
            DateRange(date(12, 1), date(1, 1, 2027)),
            monthOf(date(12, 31)),
        )
        assertEquals(date(2, 29, 2028), monthOf(date(2, 10, 2028)).lastDay)
    }

    @Test
    fun daysLeftCountsTodayThroughTheLastDay() {
        val week = weekOf(date(11, 4), DayOfWeek.MONDAY)
        assertEquals(7, daysLeftIncludingToday(date(11, 2), week))
        assertEquals(5, daysLeftIncludingToday(date(11, 4), week))
        assertEquals(1, daysLeftIncludingToday(date(11, 8), week))
    }

    @Test
    fun rangeInstantsUseLocalMidnight() {
        val zone = ZoneId.of("Asia/Singapore")
        val day = dayOf(date(11, 4))
        assertEquals("2026-11-03T16:00:00Z", day.startInstant(zone).toString())
        assertEquals("2026-11-04T16:00:00Z", day.endInstant(zone).toString())
    }
}
