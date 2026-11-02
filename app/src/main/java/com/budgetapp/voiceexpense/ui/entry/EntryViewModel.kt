package com.budgetapp.voiceexpense.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseDao
import com.budgetapp.voiceexpense.data.ExpenseSource
import com.budgetapp.voiceexpense.domain.centsToInput
import com.budgetapp.voiceexpense.domain.inferMealCategory
import com.budgetapp.voiceexpense.domain.parseAmountToCents
import com.budgetapp.voiceexpense.domain.parseVoiceExpense
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.launch

const val NEW_EXPENSE_ID = -1L

data class EntryForm(
    val amountText: String = "",
    val amountInvalid: Boolean = false,
    val category: Category,
    /** Once someone picks a category, changing the time no longer overrides it. */
    val categoryChosen: Boolean = false,
    val note: String = "",
    val date: LocalDate,
    val time: LocalTime,
    /** Voice heard the phrase but no amount in it. */
    val amountNotHeard: Boolean = false,
    val ready: Boolean = true,
    val finished: Boolean = false,
)

class EntryViewModel(
    private val expenseDao: ExpenseDao,
    private val clock: Clock,
    expenseId: Long,
    /** What voice heard, which makes this form the confirm card. */
    val transcript: String? = null,
    private val onDataChanged: () -> Unit = {},
) : ViewModel() {
    val isNew = expenseId == NEW_EXPENSE_ID
    val today: LocalDate get() = LocalDate.now(clock)

    private var existing: Expense? = null
    private var writing = false

    var form by mutableStateOf(newForm(ready = isNew))
        private set

    init {
        if (!isNew) {
            viewModelScope.launch {
                val expense = expenseDao.get(expenseId)
                existing = expense
                form = if (expense == null) form.copy(finished = true) else formFor(expense)
            }
        }
    }

    fun onAmountChange(text: String) {
        form = form.copy(amountText = text, amountInvalid = false, amountNotHeard = false)
    }

    fun onCategoryChange(category: Category) {
        form = form.copy(category = category, categoryChosen = true)
    }

    fun onNoteChange(note: String) {
        form = form.copy(note = note)
    }

    fun onDateChange(date: LocalDate) {
        form = form.copy(date = date)
    }

    fun onTimeChange(time: LocalTime) {
        val category = if (form.categoryChosen) form.category else inferMealCategory(time)
        form = form.copy(time = time, category = category)
    }

    fun save() {
        if (writing) return
        val cents = parseAmountToCents(form.amountText)
        if (cents == null) {
            form = form.copy(amountInvalid = true)
            return
        }
        writing = true
        val timestamp = LocalDateTime.of(form.date, form.time).atZone(clock.zone).toInstant()
        viewModelScope.launch {
            val current = existing
            if (current == null) {
                expenseDao.insert(
                    Expense(
                        timestamp = timestamp,
                        amountCents = cents,
                        category = form.category,
                        note = form.note.trim(),
                        rawTranscript = transcript,
                        source = if (transcript == null) ExpenseSource.MANUAL else ExpenseSource.VOICE,
                    ),
                )
            } else {
                expenseDao.update(
                    current.copy(
                        timestamp = timestamp,
                        amountCents = cents,
                        category = form.category,
                        note = form.note.trim(),
                    ),
                )
            }
            onDataChanged()
            form = form.copy(finished = true)
        }
    }

    fun delete() {
        val current = existing
        if (writing || current == null) return
        writing = true
        viewModelScope.launch {
            expenseDao.delete(current)
            onDataChanged()
            form = form.copy(finished = true)
        }
    }

    private fun newForm(ready: Boolean): EntryForm {
        val now = LocalDateTime.now(clock)
        val parsed = transcript?.let { parseVoiceExpense(it, now.toLocalTime()) }
        return EntryForm(
            amountText = parsed?.amountCents?.let(::centsToInput).orEmpty(),
            amountNotHeard = parsed != null && parsed.amountCents == null,
            category = parsed?.category ?: inferMealCategory(now.toLocalTime()),
            categoryChosen = parsed?.categorySaid ?: false,
            note = parsed?.note.orEmpty(),
            date = now.toLocalDate(),
            time = now.toLocalTime(),
            ready = ready,
        )
    }

    private fun formFor(expense: Expense): EntryForm {
        val moment = expense.timestamp.atZone(clock.zone)
        return EntryForm(
            amountText = centsToInput(expense.amountCents),
            category = expense.category,
            categoryChosen = true,
            note = expense.note,
            date = moment.toLocalDate(),
            time = moment.toLocalTime(),
        )
    }
}
