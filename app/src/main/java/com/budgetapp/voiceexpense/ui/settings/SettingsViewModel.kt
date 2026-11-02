package com.budgetapp.voiceexpense.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.voiceexpense.data.BudgetDao
import com.budgetapp.voiceexpense.data.BudgetPeriod
import com.budgetapp.voiceexpense.data.Settings
import com.budgetapp.voiceexpense.data.SettingsDao
import com.budgetapp.voiceexpense.domain.centsToInput
import com.budgetapp.voiceexpense.domain.parseAmountToCents
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsForm(
    val weeklyText: String = "",
    val weeklyInvalid: Boolean = false,
    val monthlyText: String = "",
    val monthlyInvalid: Boolean = false,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val ready: Boolean = false,
    val finished: Boolean = false,
)

class SettingsViewModel(
    private val budgetDao: BudgetDao,
    private val settingsDao: SettingsDao,
    private val clock: Clock,
    private val onDataChanged: () -> Unit = {},
) : ViewModel() {
    var form by mutableStateOf(SettingsForm())
        private set

    private var savedWeeklyCents: Long? = null
    private var savedMonthlyCents: Long? = null
    private var writing = false

    init {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            savedWeeklyCents = budgetDao.observeOverall(BudgetPeriod.WEEK, today).first()?.amountCents
            savedMonthlyCents = budgetDao.observeOverall(BudgetPeriod.MONTH, today).first()?.amountCents
            form = SettingsForm(
                weeklyText = savedWeeklyCents?.let(::centsToInput).orEmpty(),
                monthlyText = savedMonthlyCents?.let(::centsToInput).orEmpty(),
                weekStart = settingsDao.observe().first()?.weekStartDay ?: DayOfWeek.MONDAY,
                ready = true,
            )
        }
    }

    fun onWeeklyChange(text: String) {
        form = form.copy(weeklyText = text, weeklyInvalid = false)
    }

    fun onMonthlyChange(text: String) {
        form = form.copy(monthlyText = text, monthlyInvalid = false)
    }

    fun onWeekStartChange(day: DayOfWeek) {
        form = form.copy(weekStart = day)
    }

    fun save() {
        if (writing) return
        val weeklyInvalid = isInvalid(form.weeklyText)
        val monthlyInvalid = isInvalid(form.monthlyText)
        if (weeklyInvalid || monthlyInvalid) {
            form = form.copy(weeklyInvalid = weeklyInvalid, monthlyInvalid = monthlyInvalid)
            return
        }
        writing = true
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            applyBudget(BudgetPeriod.WEEK, form.weeklyText, savedWeeklyCents, today)
            applyBudget(BudgetPeriod.MONTH, form.monthlyText, savedMonthlyCents, today)
            settingsDao.upsert(Settings(weekStartDay = form.weekStart))
            onDataChanged()
            form = form.copy(finished = true)
        }
    }

    private fun isInvalid(text: String): Boolean =
        text.isNotBlank() && parseAmountToCents(text) == null

    private suspend fun applyBudget(
        period: BudgetPeriod,
        text: String,
        savedCents: Long?,
        today: LocalDate,
    ) {
        val cents = parseAmountToCents(text)
        when {
            cents == savedCents -> Unit
            cents == null -> budgetDao.deleteOverall(period)
            else -> budgetDao.setOverall(period, cents, today)
        }
    }
}
