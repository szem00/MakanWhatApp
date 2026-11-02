package com.budgetapp.voiceexpense.ui.quick

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.data.ExpenseDao
import com.budgetapp.voiceexpense.data.ExpenseSource
import com.budgetapp.voiceexpense.domain.parseVoiceExpense
import java.time.Clock
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface QuickResult {
    data class Saved(val expense: Expense) : QuickResult

    data class NoPrice(val transcript: String) : QuickResult
}

/** Quick capture saves what it hears straight away, so Undo is the safety net, not a confirm card. */
class QuickCaptureViewModel(
    private val expenseDao: ExpenseDao,
    private val clock: Clock,
    private val onDataChanged: () -> Unit,
    private val savedCardMillis: Long,
) : ViewModel() {
    var result by mutableStateOf<QuickResult?>(null)
        private set

    var closing by mutableStateOf(false)
        private set

    private var saving: Job? = null
    private var closeTimer: Job? = null
    private var undoing = false

    fun onHeard(transcript: String) {
        if (result != null) return
        val parsed = parseVoiceExpense(transcript, LocalTime.now(clock))
        val cents = parsed.amountCents
        if (cents == null) {
            result = QuickResult.NoPrice(transcript)
            return
        }
        val expense = Expense(
            timestamp = clock.instant(),
            amountCents = cents,
            category = parsed.category,
            note = parsed.note,
            rawTranscript = transcript,
            source = ExpenseSource.VOICE,
        )
        // Set before the write finishes so a repeat of this call can't save twice.
        result = QuickResult.Saved(expense)
        saving = viewModelScope.launch {
            val id = expenseDao.insert(expense)
            result = QuickResult.Saved(expense.copy(id = id))
            onDataChanged()
            if (!undoing) {
                closeTimer = viewModelScope.launch {
                    delay(savedCardMillis)
                    closing = true
                }
            }
        }
    }

    fun undo() {
        if (undoing || result !is QuickResult.Saved) return
        undoing = true
        closeTimer?.cancel()
        viewModelScope.launch {
            // A very quick tap can arrive before the save has finished.
            saving?.join()
            (result as? QuickResult.Saved)?.let { expenseDao.delete(it.expense) }
            onDataChanged()
            closing = true
        }
    }

    fun tryAgain() {
        result = null
    }
}
