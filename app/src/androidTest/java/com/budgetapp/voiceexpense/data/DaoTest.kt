package com.budgetapp.voiceexpense.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var database: AppDatabase
    private val expenses get() = database.expenseDao()
    private val budgets get() = database.budgetDao()
    private val settings get() = database.settingsDao()

    private val start = Instant.parse("2026-11-01T16:00:00Z")
    private val end = Instant.parse("2026-11-08T16:00:00Z")
    private val monday = LocalDate.of(2026, 11, 2)

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun close() {
        database.close()
    }

    private suspend fun addExpense(timestamp: Instant, cents: Long): Long =
        expenses.insert(Expense(timestamp = timestamp, amountCents = cents, category = Category.OTHER))

    @Test
    fun totalIncludesStartAndExcludesEnd() = runBlocking {
        addExpense(start.minusMillis(1), 1)
        addExpense(start, 10)
        addExpense(end.minusMillis(1), 100)
        addExpense(end, 1_000)
        assertEquals(110L, expenses.observeTotalCents(start, end).first())
    }

    @Test
    fun totalIsZeroWithNoExpenses() = runBlocking {
        assertEquals(0L, expenses.observeTotalCents(start, end).first())
    }

    @Test
    fun expenseRoundTripsEveryField() = runBlocking {
        val saved = Expense(
            timestamp = start,
            amountCents = 550,
            category = Category.LUNCH,
            note = "Chicken rice",
            rawTranscript = "lunch chicken rice 5.50",
            source = ExpenseSource.VOICE,
        )
        val id = expenses.insert(saved)
        assertEquals(saved.copy(id = id), expenses.get(id))
        assertEquals("SGD", expenses.get(id)?.currency)
    }

    @Test
    fun betweenListsNewestFirst() = runBlocking {
        val older = addExpense(start, 1)
        val newer = addExpense(start.plusSeconds(60), 2)
        assertEquals(
            listOf(newer, older),
            expenses.observeBetween(start, end).first().map { it.id },
        )
    }

    @Test
    fun updateAndDelete() = runBlocking {
        val id = addExpense(start, 100)
        val expense = expenses.get(id)!!
        expenses.update(expense.copy(amountCents = 200))
        assertEquals(200L, expenses.get(id)?.amountCents)
        expenses.delete(expense)
        assertNull(expenses.get(id))
    }

    @Test
    fun overallBudgetUsesLatestRowInEffect() = runBlocking {
        budgets.setOverall(BudgetPeriod.WEEK, 10_000, monday)
        budgets.setOverall(BudgetPeriod.WEEK, 20_000, monday.plusDays(7))

        assertNull(budgets.observeOverall(BudgetPeriod.WEEK, monday.minusDays(1)).first())
        assertEquals(
            10_000L,
            budgets.observeOverall(BudgetPeriod.WEEK, monday.plusDays(6)).first()?.amountCents,
        )
        assertEquals(
            20_000L,
            budgets.observeOverall(BudgetPeriod.WEEK, monday.plusDays(7)).first()?.amountCents,
        )
    }

    @Test
    fun secondChangeOnSameDayReplacesTheFirst() = runBlocking {
        budgets.setOverall(BudgetPeriod.MONTH, 10_000, monday)
        budgets.setOverall(BudgetPeriod.MONTH, 30_000, monday)
        assertEquals(
            30_000L,
            budgets.observeOverall(BudgetPeriod.MONTH, monday).first()?.amountCents,
        )
    }

    @Test
    fun overallBudgetIgnoresOtherPeriodsAndCategoryBudgets() = runBlocking {
        budgets.setOverall(BudgetPeriod.MONTH, 40_000, monday)
        budgets.insert(
            Budget(
                period = BudgetPeriod.WEEK,
                amountCents = 5_000,
                category = Category.LUNCH,
                effectiveFrom = monday,
            ),
        )
        assertNull(budgets.observeOverall(BudgetPeriod.WEEK, monday).first())
    }

    @Test
    fun removingOverallBudgetKeepsCategoryBudgets() = runBlocking {
        budgets.setOverall(BudgetPeriod.WEEK, 10_000, monday)
        budgets.insert(
            Budget(
                period = BudgetPeriod.WEEK,
                amountCents = 5_000,
                category = Category.LUNCH,
                effectiveFrom = monday,
            ),
        )
        budgets.deleteOverall(BudgetPeriod.WEEK)
        assertNull(budgets.observeOverall(BudgetPeriod.WEEK, monday).first())
    }

    @Test
    fun settingsDefaultToNoRowThenKeepOneRow() = runBlocking {
        assertNull(settings.observe().first())
        settings.upsert(Settings(weekStartDay = DayOfWeek.SUNDAY))
        settings.upsert(Settings(weekStartDay = DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.MONDAY, settings.observe().first()?.weekStartDay)
    }
}
