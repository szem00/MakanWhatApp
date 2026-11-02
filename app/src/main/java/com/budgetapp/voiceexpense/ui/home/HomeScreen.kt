package com.budgetapp.voiceexpense.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.domain.PeriodSummary
import com.budgetapp.voiceexpense.domain.formatMoney
import com.budgetapp.voiceexpense.ui.AppViewModels
import com.budgetapp.voiceexpense.ui.ExpenseRow
import com.budgetapp.voiceexpense.ui.formatMonth
import com.budgetapp.voiceexpense.ui.formatRange
import java.time.ZoneId
import kotlin.math.abs

private val FabClearance = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSpeak: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMealFinder: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenTrends) {
                        Icon(
                            painterResource(R.drawable.ic_bar_chart),
                            contentDescription = stringResource(R.string.trends),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.budgets_and_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingActionButton(
                    onClick = onAddExpense,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.add_expense),
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = onSpeak,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Material hides this button's text from screen readers, so the icon has to say it.
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_mic),
                            contentDescription = stringResource(R.string.log_by_voice),
                        )
                    },
                    text = { Text(stringResource(R.string.speak)) },
                )
            }
        },
    ) { padding ->
        state?.let {
            HomeContent(
                state = it,
                zone = viewModel.zone,
                padding = padding,
                onOpenExpense = onOpenExpense,
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
                onOpenMealFinder = onOpenMealFinder,
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    zone: ZoneId,
    padding: PaddingValues,
    onOpenExpense: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMealFinder: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + FabClearance,
        ),
    ) {
        item {
            PeriodCard(
                title = stringResource(R.string.this_week),
                dateLabel = formatRange(state.week),
                summary = state.weekSummary,
                footnote = safeToSpendText(state),
                onSetBudget = onOpenSettings,
            )
        }
        item {
            PeriodCard(
                title = formatMonth(state.today),
                dateLabel = formatRange(state.monthRange),
                summary = state.monthSummary,
                footnote = null,
                onSetBudget = onOpenSettings,
            )
        }
        // The fork's main feature gets a button rather than a fourth app bar icon, which would
        // squeeze the app's long name onto two lines.
        item {
            FilledTonalButton(
                onClick = onOpenMealFinder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_restaurant),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.find_a_meal_nearby))
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.today), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenHistory) {
                    Text(stringResource(R.string.all_expenses))
                }
            }
        }
        if (state.todayExpenses.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.nothing_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(state.todayExpenses, key = { it.id }) { expense ->
                ExpenseRow(expense, zone, onClick = { onOpenExpense(expense.id) })
            }
        }
    }
}

@Composable
private fun safeToSpendText(state: HomeState): String? {
    val perDay = state.safeToSpendCents ?: return null
    if (state.weekSummary.isOverBudget) return null
    return if (state.daysLeftInWeek == 1) {
        stringResource(R.string.safe_to_spend_today, formatMoney(perDay))
    } else {
        stringResource(R.string.safe_to_spend_per_day, formatMoney(perDay), state.daysLeftInWeek)
    }
}

@Composable
private fun PeriodCard(
    title: String,
    dateLabel: String,
    summary: PeriodSummary,
    footnote: String?,
    onSetBudget: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatMoney(summary.spentCents),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = summary.budgetCents?.let {
                        stringResource(R.string.spent_of_budget, formatMoney(it))
                    } ?: stringResource(R.string.spent),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            val remaining = summary.remainingCents
            val fraction = summary.fractionUsed
            if (remaining != null && fraction != null) {
                val barColor = if (summary.isOverBudget) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = barColor,
                )
                if (summary.isOverBudget) {
                    Text(
                        text = stringResource(R.string.amount_over, formatMoney(abs(remaining))),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.amount_left, formatMoney(remaining)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                footnote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            } else {
                TextButton(onClick = onSetBudget, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.set_budget))
                }
            }
        }
    }
}
