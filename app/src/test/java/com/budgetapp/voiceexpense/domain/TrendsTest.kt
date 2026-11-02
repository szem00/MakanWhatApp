package com.budgetapp.voiceexpense.domain

import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs on Wednesday 4 Nov 2026 in Singapore. */
class TrendsTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val today = LocalDate.of(2026, 11, 4)

    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun spent(
        cents: Long,
        on: LocalDate,
        category: Category = Category.OTHER,
        hour: Int = 12,
        minute: Int = 0,
    ) = Expense(
        timestamp = LocalDateTime.of(on, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant(),
        amountCents = cents,
        category = category,
    )

    private fun build(
        expenses: List<Expense>,
        period: TrendPeriod = TrendPeriod.THIS_MONTH,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        budgetCents: Long? = null,
    ) = buildTrends(expenses, today, weekStart, zone, budgetCents, period)

    @Test
    fun dailyTotalsCoverTheWholeWeekEvenWithNoSpending() {
        val trends = build(emptyList())
        assertEquals((2..8).map { date(11, it) }, trends.days.map { it.date })
        assertTrue(trends.days.all { it.cents == 0L })
    }

    @Test
    fun dailyTotalsAddUpEachDay() {
        val trends = build(
            listOf(spent(100, date(11, 2)), spent(250, date(11, 2)), spent(700, date(11, 4))),
        )
        assertEquals(listOf(350L, 0L, 700L, 0L, 0L, 0L, 0L), trends.days.map { it.cents })
    }

    @Test
    fun daysFollowTheLocalDateNotUtc() {
        // 00:30 on the 3rd in Singapore is still the 2nd in UTC.
        val justAfterMidnight = spent(100, date(11, 3), hour = 0, minute = 30)
        val justBeforeMidnight = spent(200, date(11, 2), hour = 23, minute = 59)
        val trends = build(listOf(justAfterMidnight, justBeforeMidnight))
        assertEquals(200L, trends.days[0].cents)
        assertEquals(100L, trends.days[1].cents)
    }

    @Test
    fun weekStartSettingShiftsTheDays() {
        val trends = build(emptyList(), weekStart = DayOfWeek.SUNDAY)
        assertEquals(date(11, 1), trends.days.first().date)
        assertEquals(date(11, 7), trends.days.last().date)
    }

    @Test
    fun weeklyTotalsAreTwelveWeeksOldestFirstEndingThisWeek() {
        val trends = build(emptyList())
        assertEquals(12, trends.weeks.size)
        assertEquals(date(11, 2), trends.weeks.last().start)
        assertEquals(date(8, 17), trends.weeks.first().start)
    }

    @Test
    fun weeklyTotalsBucketAtTheWeekBoundary() {
        val trends = build(
            listOf(
                spent(100, date(11, 1)), // Sunday, last week.
                spent(200, date(11, 2)), // Monday, this week.
                spent(400, date(8, 17)), // First day of the oldest week.
                spent(800, date(8, 16)), // One day too old to show.
            ),
        )
        assertEquals(200L, trends.weeks[11].cents)
        assertEquals(100L, trends.weeks[10].cents)
        assertEquals(400L, trends.weeks[0].cents)
        assertEquals(700L, trends.weeks.sumOf { it.cents })
    }

    @Test
    fun categoriesAreSortedLargestFirstWithShares() {
        val trends = build(
            listOf(
                spent(100, date(11, 2), Category.TRANSPORT),
                spent(600, date(11, 3), Category.LUNCH),
                spent(300, date(11, 4), Category.GROCERIES),
            ),
        )
        assertEquals(
            listOf(Category.LUNCH, Category.GROCERIES, Category.TRANSPORT),
            trends.categories.map { it.category },
        )
        assertEquals(0.6f, trends.categories[0].share, 0.001f)
        assertEquals(1f, trends.categories.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun categoriesLeaveOutAnythingUnspent() {
        val trends = build(listOf(spent(100, date(11, 2), Category.LUNCH)))
        assertEquals(listOf(Category.LUNCH), trends.categories.map { it.category })
    }

    @Test
    fun periodDecidesWhatCategoriesAndMealsCount() {
        val expenses = listOf(
            spent(100, date(11, 2), Category.LUNCH), // This week and this month.
            spent(200, date(11, 1), Category.LUNCH), // This month, last week.
            spent(400, date(10, 20), Category.LUNCH), // Only the 12 weeks.
            spent(800, date(8, 1), Category.LUNCH), // Too old for all of them.
        )
        fun total(period: TrendPeriod) = build(expenses, period).categories.sumOf { it.cents }
        assertEquals(100L, total(TrendPeriod.THIS_WEEK))
        assertEquals(300L, total(TrendPeriod.THIS_MONTH))
        assertEquals(700L, total(TrendPeriod.LAST_12_WEEKS))
        assertEquals(1, build(expenses, TrendPeriod.THIS_WEEK).meals[1].count)
        assertEquals(3, build(expenses, TrendPeriod.LAST_12_WEEKS).meals[1].count)
    }

    @Test
    fun mealAveragesCoverAllFourInOrder() {
        val trends = build(
            listOf(
                spent(500, date(11, 2), Category.LUNCH),
                spent(800, date(11, 3), Category.LUNCH),
                spent(999, date(11, 3), Category.SHOPPING),
            ),
        )
        assertEquals(
            listOf(Category.BREAKFAST, Category.LUNCH, Category.DINNER, Category.SUPPER),
            trends.meals.map { it.category },
        )
        assertNull(trends.meals[0].averageCents)
        assertEquals(0, trends.meals[0].count)
        assertEquals(650L, trends.meals[1].averageCents)
        assertEquals(2, trends.meals[1].count)
    }

    @Test
    fun mealAverageRoundsHalfUp() {
        fun lunches(vararg cents: Long) =
            build(cents.map { spent(it, date(11, 2), Category.LUNCH) }).meals[1].averageCents
        assertEquals(2L, lunches(1, 2)) // 1.5 rounds up.
        assertEquals(1L, lunches(1, 1, 2)) // 1.33 rounds down.
        assertEquals(2L, lunches(1, 2, 2)) // 1.67 rounds up.
    }

    @Test
    fun windowCoversTheLongerOfTheMonthAndTheWeeks() {
        val window = trendWindow(today, DayOfWeek.MONDAY)
        assertEquals(date(8, 17), window.start)
        assertEquals(date(12, 1), window.endExclusive)
    }

    @Test
    fun windowStretchesWhenTheWeekSpillsIntoNextMonth() {
        val window = trendWindow(LocalDate.of(2026, 12, 31), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2027, 1, 4), window.endExclusive)
    }

    @Test
    fun axisMaxIsTheNextCleanNumber() {
        assertEquals(1_000L, niceAxisMaxCents(0))
        assertEquals(100L, niceAxisMaxCents(5))
        assertEquals(100L, niceAxisMaxCents(100))
        assertEquals(200L, niceAxisMaxCents(101))
        assertEquals(500L, niceAxisMaxCents(350))
        assertEquals(1_000L, niceAxisMaxCents(800))
        assertEquals(20_000L, niceAxisMaxCents(15_000))
        assertEquals(100_000L, niceAxisMaxCents(100_000))
        assertEquals(200_000L, niceAxisMaxCents(100_001))
    }

    @Test
    fun sharesNeverClaimZeroOrAllWhenThatIsNotTrue() {
        assertEquals("59%", formatShare(0.59f))
        assertEquals("1%", formatShare(0.005f))
        assertEquals("<1%", formatShare(0.004f))
        assertEquals(">99%", formatShare(0.996f))
        assertEquals("100%", formatShare(1f))
        assertEquals("0%", formatShare(0f))
    }

    @Test
    fun axisLabelsStayShort() {
        assertEquals("$0", formatAxisMoney(0))
        assertEquals("$150", formatAxisMoney(15_000))
        assertEquals("$2.50", formatAxisMoney(250))
        assertEquals("$0.50", formatAxisMoney(50))
        assertEquals("$1K", formatAxisMoney(100_000))
        assertEquals("$2.5K", formatAxisMoney(250_000))
    }
}
