package com.budgetapp.voiceexpense.ui.trends

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.voiceexpense.data.BudgetDao
import com.budgetapp.voiceexpense.data.BudgetPeriod
import com.budgetapp.voiceexpense.data.ExpenseDao
import com.budgetapp.voiceexpense.data.SettingsDao
import com.budgetapp.voiceexpense.domain.TrendPeriod
import com.budgetapp.voiceexpense.domain.Trends
import com.budgetapp.voiceexpense.domain.buildTrends
import com.budgetapp.voiceexpense.domain.expensesToCsv
import com.budgetapp.voiceexpense.domain.trendWindow
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrendsViewModel(
    private val expenseDao: ExpenseDao,
    private val budgetDao: BudgetDao,
    settingsDao: SettingsDao,
    private val clock: Clock,
) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now(clock))
    private val period = MutableStateFlow(TrendPeriod.THIS_MONTH)

    @OptIn(ExperimentalCoroutinesApi::class)
    val trends: StateFlow<Trends?> = combine(today, settingsDao.observe()) { date, settings ->
        date to (settings?.weekStartDay ?: DayOfWeek.MONDAY)
    }
        .flatMapLatest { (date, weekStart) ->
            val window = trendWindow(date, weekStart)
            combine(
                expenseDao.observeBetween(
                    window.startInstant(clock.zone),
                    window.endInstant(clock.zone),
                ),
                budgetDao.observeOverall(BudgetPeriod.WEEK, date),
                period,
            ) { expenses, budget, selected ->
                buildTrends(expenses, date, weekStart, clock.zone, budget?.amountCents, selected)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPeriodChange(selected: TrendPeriod) {
        period.value = selected
    }

    /** Called on resume, so the screen rolls over if the app was left open past midnight. */
    fun refreshDate() {
        today.value = LocalDate.now(clock)
    }

    fun exportFileName(): String = "voice-expenses-${LocalDate.now(clock)}.csv"

    /** Writes every expense to the file the person picked. Reports how many, or null if it failed. */
    fun exportCsv(resolver: ContentResolver, target: Uri, onDone: (Int?) -> Unit) {
        viewModelScope.launch {
            val expenses = expenseDao.observeAll().first()
            val count = try {
                withContext(Dispatchers.IO) {
                    val csv = expensesToCsv(expenses, clock.zone)
                    val stream = resolver.openOutputStream(target) ?: throw IOException("No stream")
                    stream.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                expenses.size
            } catch (_: IOException) {
                null
            }
            onDone(count)
        }
    }
}
