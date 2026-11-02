package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

enum class TrendPeriod { THIS_WEEK, THIS_MONTH, LAST_12_WEEKS }

const val WEEKS_IN_TREND = 12

private val MEALS = listOf(Category.BREAKFAST, Category.LUNCH, Category.DINNER, Category.SUPPER)

data class DayTotal(val date: LocalDate, val cents: Long)

data class WeekTotal(val start: LocalDate, val cents: Long)

data class CategoryTotal(val category: Category, val cents: Long, val share: Float)

/** [averageCents] is per logged entry, and null when none were logged. */
data class MealAverage(val category: Category, val averageCents: Long?, val count: Int)

data class Trends(
    val today: LocalDate,
    val days: List<DayTotal>,
    val weeks: List<WeekTotal>,
    val budgetCents: Long?,
    val period: TrendPeriod,
    val categories: List<CategoryTotal>,
    val meals: List<MealAverage>,
)

private fun lastWeeks(today: LocalDate, weekStart: DayOfWeek): DateRange {
    val current = weekOf(today, weekStart)
    return DateRange(current.start.minusWeeks(WEEKS_IN_TREND - 1L), current.endExclusive)
}

fun rangeFor(period: TrendPeriod, today: LocalDate, weekStart: DayOfWeek): DateRange = when (period) {
    TrendPeriod.THIS_WEEK -> weekOf(today, weekStart)
    TrendPeriod.THIS_MONTH -> monthOf(today)
    TrendPeriod.LAST_12_WEEKS -> lastWeeks(today, weekStart)
}

/** The span of expenses [buildTrends] needs, whichever period is selected. */
fun trendWindow(today: LocalDate, weekStart: DayOfWeek): DateRange {
    val weeks = lastWeeks(today, weekStart)
    val month = monthOf(today)
    return DateRange(minOf(weeks.start, month.start), maxOf(weeks.endExclusive, month.endExclusive))
}

fun buildTrends(
    expenses: List<Expense>,
    today: LocalDate,
    weekStart: DayOfWeek,
    zone: ZoneId,
    budgetCents: Long?,
    period: TrendPeriod,
): Trends {
    val dated = expenses.map { it.timestamp.atZone(zone).toLocalDate() to it }
    fun inRange(range: DateRange) =
        dated.filter { (date, _) -> date >= range.start && date < range.endExclusive }.map { it.second }

    val week = weekOf(today, weekStart)
    val days = (0L until 7L).map { offset ->
        val date = week.start.plusDays(offset)
        DayTotal(date, inRange(dayOf(date)).sumOf { it.amountCents })
    }
    val weeks = (WEEKS_IN_TREND - 1L downTo 0L).map { weeksBack ->
        val start = week.start.minusWeeks(weeksBack)
        WeekTotal(start, inRange(DateRange(start, start.plusWeeks(1))).sumOf { it.amountCents })
    }

    val inPeriod = inRange(rangeFor(period, today, weekStart))
    val periodTotal = inPeriod.sumOf { it.amountCents }
    val categories = inPeriod.groupBy { it.category }
        .map { (category, spent) ->
            val cents = spent.sumOf { it.amountCents }
            CategoryTotal(category, cents, cents.toFloat() / periodTotal)
        }
        .sortedByDescending { it.cents }
    val meals = MEALS.map { meal ->
        val logged = inPeriod.filter { it.category == meal }
        MealAverage(meal, averageCents(logged), logged.size)
    }
    return Trends(today, days, weeks, budgetCents, period, categories, meals)
}

/** Rounds half up to the nearest cent. */
private fun averageCents(expenses: List<Expense>): Long? {
    if (expenses.isEmpty()) return null
    return (expenses.sumOf { it.amountCents } + expenses.size / 2) / expenses.size
}

/** A share as a percentage, without ever claiming a real expense is 0% or that one is all of it. */
fun formatShare(share: Float): String {
    val percent = (share * 100).roundToInt()
    return when {
        percent == 0 && share > 0f -> "<1%"
        percent == 100 && share < 1f -> ">99%"
        else -> "$percent%"
    }
}

/** The top of a chart's axis: the next clean number at or above [valueCents]. */
fun niceAxisMaxCents(valueCents: Long): Long {
    if (valueCents <= 0) return 1_000
    var magnitude = 100L
    while (magnitude * 10 < valueCents) magnitude *= 10
    return listOf(1L, 2L, 5L, 10L).map { it * magnitude }.first { it >= valueCents }
}

/** Short money for axis labels: $150, $2.50, $1.5K. */
fun formatAxisMoney(cents: Long): String = when {
    cents >= 100_000 -> {
        val thousands = cents / 100_000.0
        if (cents % 100_000 == 0L) {
            String.format(Locale.US, "$%dK", cents / 100_000)
        } else {
            String.format(Locale.US, "$%.1fK", thousands)
        }
    }
    cents % 100 == 0L -> String.format(Locale.US, "$%d", cents / 100)
    else -> formatMoney(cents)
}
