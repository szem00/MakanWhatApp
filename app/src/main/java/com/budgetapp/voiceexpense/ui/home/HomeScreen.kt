package com.budgetapp.voiceexpense.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.budgetapp.voiceexpense.ui.theme.HeroAmount
import com.budgetapp.voiceexpense.ui.theme.LocalBrandColors
import com.budgetapp.voiceexpense.ui.theme.TABULAR_FIGURES
import com.budgetapp.voiceexpense.ui.theme.Wordmark
import java.time.ZoneId
import kotlin.math.abs

private val FabClearance = 88.dp
private val FabShape = RoundedCornerShape(18.dp)

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
                title = { Wordmark() },
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
                    shape = FabShape,
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
                    shape = FabShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Material hides this button's text from screen readers, so the icon has to say it.
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_mic),
                            contentDescription = stringResource(R.string.log_by_voice),
                        )
                    },
                    text = { Text(stringResource(R.string.speak), fontWeight = FontWeight.ExtraBold) },
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
            WeekCard(
                dateLabel = formatRange(state.week),
                summary = state.weekSummary,
                safeToSpend = safeToSpend(state),
                onSetBudget = onOpenSettings,
            )
        }
        item {
            MonthCard(
                title = formatMonth(state.today),
                summary = state.monthSummary,
                onSetBudget = onOpenSettings,
            )
        }
        // The fork's main feature gets a button rather than a fourth app bar icon, which would
        // crowd the app's name.
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
                    .padding(start = 20.dp, end = 4.dp, top = 8.dp),
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        } else {
            items(state.todayExpenses, key = { it.id }) { expense ->
                ExpenseRow(expense, zone, onClick = { onOpenExpense(expense.id) })
            }
        }
    }
}

/** Short enough to share a line with the amount spent, while screen readers say the whole sentence. */
private class SafeToSpend(val text: String, val spoken: String)

@Composable
private fun safeToSpend(state: HomeState): SafeToSpend? {
    val perDay = state.safeToSpendCents ?: return null
    if (state.weekSummary.isOverBudget) return null
    val amount = formatMoney(perDay)
    return if (state.daysLeftInWeek == 1) {
        SafeToSpend(
            text = stringResource(R.string.safe_today_short, amount),
            spoken = stringResource(R.string.safe_to_spend_today, amount),
        )
    } else {
        SafeToSpend(
            text = stringResource(R.string.safe_per_day_short, amount),
            spoken = stringResource(R.string.safe_to_spend_per_day, amount, state.daysLeftInWeek),
        )
    }
}

/** The week leads, with what's left in large type on the coffee-milk card. */
@Composable
private fun WeekCard(
    dateLabel: String,
    summary: PeriodSummary,
    safeToSpend: SafeToSpend?,
    onSetBudget: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.this_week), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }
            Text(
                text = statusText(summary, HeroAmount),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = TextUnit.Unspecified),
                color = statusColor(summary),
                modifier = Modifier.padding(top = 6.dp),
            )
            val budget = summary.budgetCents
            if (budget != null) {
                BudgetBar(summary, height = 10.dp, modifier = Modifier.padding(top = 14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val detailStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    )
                    Text(
                        text = stringResource(
                            R.string.spent_of_total,
                            formatMoney(summary.spentCents),
                            formatMoney(budget),
                        ),
                        style = detailStyle,
                        modifier = Modifier.weight(1f),
                    )
                    safeToSpend?.let {
                        Text(
                            text = it.text,
                            style = detailStyle,
                            textAlign = TextAlign.End,
                            modifier = Modifier.semantics { contentDescription = it.spoken },
                        )
                    }
                }
            } else {
                SetBudgetButton(onSetBudget)
            }
        }
    }
}

/** The month sits quietly under the week: one line of what's left, a thin bar and the totals. */
@Composable
private fun MonthCard(title: String, summary: PeriodSummary, onSetBudget: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
                Text(
                    text = statusText(summary, amountStyle = null),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                    color = statusColor(summary),
                    textAlign = TextAlign.End,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            val budget = summary.budgetCents
            if (budget != null) {
                BudgetBar(summary, height = 6.dp, modifier = Modifier.padding(top = 12.dp))
                Text(
                    text = stringResource(
                        R.string.spent_of_total,
                        formatMoney(summary.spentCents),
                        formatMoney(budget),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                SetBudgetButton(onSetBudget)
            }
        }
    }
}

/** "$94.00 left", "$6.00 over budget", or "$12.00 spent" without a budget, as one piece of text. */
@Composable
private fun statusText(summary: PeriodSummary, amountStyle: SpanStyle?): AnnotatedString {
    val remaining = summary.remainingCents
    return when {
        remaining == null -> amountIn(R.string.amount_spent, formatMoney(summary.spentCents), amountStyle)
        remaining < 0 -> amountIn(R.string.amount_over, formatMoney(abs(remaining)), amountStyle)
        else -> amountIn(R.string.amount_left, formatMoney(remaining), amountStyle)
    }
}

@Composable
private fun amountIn(@StringRes template: Int, amount: String, amountStyle: SpanStyle?): AnnotatedString {
    val text = stringResource(template, amount)
    val start = text.indexOf(amount)
    return buildAnnotatedString {
        append(text)
        if (amountStyle != null && start >= 0) addStyle(amountStyle, start, start + amount.length)
    }
}

@Composable
private fun statusColor(summary: PeriodSummary) =
    if (summary.isOverBudget) MaterialTheme.colorScheme.error else LocalContentColor.current

/** Green while on track, amber once most of it is gone, red when it's over. */
@Composable
private fun BudgetBar(summary: PeriodSummary, height: Dp, modifier: Modifier = Modifier) {
    val brand = LocalBrandColors.current
    val color = when {
        summary.isOverBudget -> MaterialTheme.colorScheme.error
        summary.isNearlyUsed -> brand.nearlyUsed
        else -> brand.onTrack
    }
    LinearProgressIndicator(
        progress = { summary.fractionUsed ?: 0f },
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        color = color,
        trackColor = LocalContentColor.current.copy(alpha = 0.16f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
private fun SetBudgetButton(onSetBudget: () -> Unit) {
    TextButton(
        onClick = onSetBudget,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(stringResource(R.string.set_budget))
    }
}
