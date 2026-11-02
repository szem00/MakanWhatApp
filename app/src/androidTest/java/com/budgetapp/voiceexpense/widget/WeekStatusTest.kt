package com.budgetapp.voiceexpense.widget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.BudgetPeriod
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.Settings
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a Wednesday, with 5 days left in a Monday-to-Sunday week. */
@RunWith(AndroidJUnit4::class)
class WeekStatusTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val now = LocalDateTime.of(2026, 11, 4, 12, 30)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    private lateinit var database: AppDatabase

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

    private suspend fun spend(cents: Long, at: LocalDateTime = now) {
        database.expenseDao().insert(
            Expense(
                timestamp = at.atZone(zone).toInstant(),
                amountCents = cents,
                category = Category.OTHER,
            ),
        )
    }

    private suspend fun setWeeklyBudget(cents: Long) {
        database.budgetDao().setOverall(BudgetPeriod.WEEK, cents, LocalDate.of(2026, 11, 2))
    }

    @Test
    fun withoutABudgetThereIsNothingLeftToWorkOut() = runBlocking {
        spend(600)
        val status = loadWeekStatus(database, clock)
        assertEquals(600L, status.summary.spentCents)
        assertNull(status.summary.remainingCents)
        assertNull(status.safeToSpendPerDayCents)
    }

    @Test
    fun withABudgetItWorksOutWhatsLeft() = runBlocking {
        setWeeklyBudget(10_000)
        spend(600)
        val status = loadWeekStatus(database, clock)
        assertEquals(9_400L, status.summary.remainingCents)
        assertEquals(1_880L, status.safeToSpendPerDayCents)
        assertEquals(5, status.daysLeft)
    }

    @Test
    fun overBudgetHasNothingSafeToSpend() = runBlocking {
        setWeeklyBudget(1_000)
        spend(1_500)
        val status = loadWeekStatus(database, clock)
        assertTrue(status.summary.isOverBudget)
        assertEquals(0L, status.safeToSpendPerDayCents)
    }

    @Test
    fun onlyThisWeeksSpendingCounts() = runBlocking {
        spend(100, now.minusDays(2)) // Monday, this week.
        spend(200, now.minusDays(3)) // Sunday, last week.
        assertEquals(100L, loadWeekStatus(database, clock).summary.spentCents)
    }

    @Test
    fun followsTheWeekStartSetting() = runBlocking {
        database.settingsDao().upsert(Settings(weekStartDay = DayOfWeek.SUNDAY))
        spend(200, now.minusDays(3)) // Sunday, which now starts this week.
        assertEquals(200L, loadWeekStatus(database, clock).summary.spentCents)
    }
}
