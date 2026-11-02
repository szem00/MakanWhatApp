package com.budgetapp.voiceexpense.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.voiceexpense.data.BudgetDao
import com.budgetapp.voiceexpense.data.BudgetPeriod
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseDao
import com.budgetapp.voiceexpense.data.SettingsDao
import com.budgetapp.voiceexpense.domain.DateRange
import com.budgetapp.voiceexpense.domain.PeriodSummary
import com.budgetapp.voiceexpense.domain.dayOf
import com.budgetapp.voiceexpense.domain.daysLeftIncludingToday
import com.budgetapp.voiceexpense.domain.monthOf
import com.budgetapp.voiceexpense.domain.safeToSpendPerDayCents
import com.budgetapp.voiceexpense.domain.weekOf
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val today: LocalDate,
    val week: DateRange,
    val weekSummary: PeriodSummary,
    val daysLeftInWeek: Int,
    val monthSummary: PeriodSummary,
    val monthRange: DateRange,
    val todayExpenses: List<Expense>,
) {
    /** Null without a weekly budget. */
    val safeToSpendCents: Long?
        get() = weekSummary.remainingCents?.let { safeToSpendPerDayCents(it, daysLeftInWeek) }
}

class HomeViewModel(
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    settingsDao: SettingsDao,
    private val clock: Clock,
) : ViewModel() {
    val zone: ZoneId get() = clock.zone

    private val today = MutableStateFlow(LocalDate.now(clock))

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState?> = combine(today, settingsDao.observe()) { date, settings ->
        date to (settings?.weekStartDay ?: DayOfWeek.MONDAY)
    }
        .flatMapLatest { (date, weekStart) -> stateFor(date, weekStart) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Called on resume, so the screen rolls over if the app was left open past midnight. */
    fun refreshDate() {
        today.value = LocalDate.now(clock)
    }

    private fun stateFor(date: LocalDate, weekStart: DayOfWeek): Flow<HomeState> {
        val week = weekOf(date, weekStart)
        val month = monthOf(date)
        val day = dayOf(date)
        val weekBudget = budgetDao.observeOverall(BudgetPeriod.WEEK, date)
        val monthBudget = budgetDao.observeOverall(BudgetPeriod.MONTH, date)
        return combine(
            totalIn(week),
            totalIn(month),
            weekBudget,
            monthBudget,
            expenseDao.observeBetween(day.startInstant(zone), day.endInstant(zone)),
        ) { weekSpent, monthSpent, weekly, monthly, expenses ->
            HomeState(
                today = date,
                week = week,
                weekSummary = PeriodSummary(weekSpent, weekly?.amountCents),
                daysLeftInWeek = daysLeftIncludingToday(date, week),
                monthSummary = PeriodSummary(monthSpent, monthly?.amountCents),
                monthRange = month,
                todayExpenses = expenses,
            )
        }
    }

    private fun totalIn(range: DateRange): Flow<Long> =
        expenseDao.observeTotalCents(range.startInstant(zone), range.endInstant(zone))
}
