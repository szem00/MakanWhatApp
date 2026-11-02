package com.budgetapp.voiceexpense.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseDao
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ExpenseDay(val date: LocalDate, val totalCents: Long, val expenses: List<Expense>)

class HistoryViewModel(expenseDao: ExpenseDao, private val clock: Clock) : ViewModel() {
    val zone: ZoneId get() = clock.zone
    val today: LocalDate get() = LocalDate.now(clock)

    val days: StateFlow<List<ExpenseDay>?> = expenseDao.observeAll()
        .map { expenses ->
            expenses
                .groupBy { it.timestamp.atZone(zone).toLocalDate() }
                .map { (date, onDay) -> ExpenseDay(date, onDay.sumOf { it.amountCents }, onDay) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
