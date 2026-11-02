package com.budgetapp.voiceexpense.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.domain.formatMoney
import com.budgetapp.voiceexpense.ui.AppViewModels
import com.budgetapp.voiceexpense.ui.ExpenseRow
import com.budgetapp.voiceexpense.ui.formatDay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.all_expenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val loaded = days ?: return@Scaffold
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            if (loaded.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_expenses),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            loaded.forEach { day ->
                item(key = day.date.toEpochDay()) {
                    DayHeader(day.date, day.totalCents, viewModel.today)
                }
                items(day.expenses, key = { "expense-${it.id}" }) { expense ->
                    ExpenseRow(expense, viewModel.zone, onClick = { onOpenExpense(expense.id) })
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, totalCents: Long, today: LocalDate) {
    val label = when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> formatDay(date)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            text = formatMoney(totalCents),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
