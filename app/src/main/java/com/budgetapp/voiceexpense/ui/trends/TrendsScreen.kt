package com.budgetapp.voiceexpense.ui.trends

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.domain.CategoryTotal
import com.budgetapp.voiceexpense.domain.MealAverage
import com.budgetapp.voiceexpense.domain.TrendPeriod
import com.budgetapp.voiceexpense.domain.Trends
import com.budgetapp.voiceexpense.domain.formatAxisMoney
import com.budgetapp.voiceexpense.domain.formatMoney
import com.budgetapp.voiceexpense.domain.formatShare
import com.budgetapp.voiceexpense.ui.AppViewModels
import com.budgetapp.voiceexpense.ui.formatDay
import com.budgetapp.voiceexpense.ui.labelRes
import java.time.format.DateTimeFormatter

private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE")
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM")

/** With twelve columns only every fourth label fits, counted back so the current week has one. */
private const val WEEK_LABEL_EVERY = 4

/** Keeps columns of money lined up. */
private val TabularFigures = TextStyle(fontFeatureSettings = "tnum")

private val PERIOD_OPTIONS = listOf(
    TrendPeriod.THIS_WEEK to R.string.this_week,
    TrendPeriod.THIS_MONTH to R.string.trends_this_month,
    TrendPeriod.LAST_12_WEEKS to R.string.trends_12_weeks,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onBack: () -> Unit,
    viewModel: TrendsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val trends by viewModel.trends.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trends)) },
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
        val loaded = trends ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DailyCard(loaded)
            WeeklyCard(loaded)
            PeriodSelector(loaded.period, viewModel::onPeriodChange)
            CategoryCard(loaded.categories)
            MealCard(loaded.meals)
            ExportButton(viewModel)
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    showingNumbers: Boolean,
    onToggleNumbers: () -> Unit,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onToggleNumbers) {
                    Text(
                        stringResource(
                            if (showingNumbers) R.string.trends_show_chart else R.string.trends_show_numbers,
                        ),
                    )
                }
            }
            Column(modifier = Modifier.padding(end = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun DailyCard(trends: Trends) {
    var showingNumbers by rememberSaveable { mutableStateOf(false) }
    val todayIndex = trends.days.indexOfFirst { it.date == trends.today }.coerceAtLeast(0)
    var selected by rememberSaveable(trends.today) { mutableIntStateOf(todayIndex) }
    val readouts = trends.days.map { formatDay(it.date) to formatMoney(it.cents) }

    ChartCard(
        title = stringResource(R.string.this_week),
        showingNumbers = showingNumbers,
        onToggleNumbers = { showingNumbers = !showingNumbers },
    ) {
        if (showingNumbers) {
            NumbersTable(readouts)
        } else {
            Readout(readouts[selected])
            ColumnChart(
                columns = trends.days.map {
                    ChartColumn(
                        label = it.date.format(weekdayFormatter),
                        cents = it.cents,
                        highlighted = it.date == trends.today,
                    )
                },
                selected = selected,
                onSelect = { selected = it },
                description = chartDescription(readouts),
                modifier = Modifier.testTag("daily_chart"),
            )
        }
    }
}

@Composable
private fun WeeklyCard(trends: Trends) {
    var showingNumbers by rememberSaveable { mutableStateOf(false) }
    val lastIndex = trends.weeks.lastIndex
    var selected by rememberSaveable(trends.today) { mutableIntStateOf(lastIndex) }
    val readouts = trends.weeks.map {
        stringResource(R.string.trends_week_of, it.start.format(shortDateFormatter)) to formatMoney(it.cents)
    }
    val budgetLabel = trends.budgetCents?.let {
        stringResource(R.string.trends_budget_label, formatMoney(it))
    }

    ChartCard(
        title = stringResource(R.string.trends_last_12_weeks),
        showingNumbers = showingNumbers,
        onToggleNumbers = { showingNumbers = !showingNumbers },
    ) {
        if (showingNumbers) {
            NumbersTable(readouts)
            budgetLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Readout(readouts[selected], Modifier.weight(1f))
                trends.budgetCents?.let { BudgetKey(it) }
            }
            ColumnChart(
                columns = trends.weeks.mapIndexed { index, week ->
                    ChartColumn(
                        label = week.start.format(shortDateFormatter)
                            .takeIf { (lastIndex - index) % WEEK_LABEL_EVERY == 0 },
                        cents = week.cents,
                        highlighted = index == lastIndex,
                    )
                },
                selected = selected,
                onSelect = { selected = it },
                description = listOfNotNull(budgetLabel, chartDescription(readouts)).joinToString(". "),
                modifier = Modifier.testTag("weekly_chart"),
                referenceCents = trends.budgetCents,
            )
        }
    }
}

private fun chartDescription(readouts: List<Pair<String, String>>): String =
    readouts.joinToString(", ") { (label, value) -> "$label $value" }

@Composable
private fun Readout(readout: Pair<String, String>, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.trends_readout, readout.first, readout.second),
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/** Sits beside the chart so the label never covers a column, with a swatch matching the line. */
@Composable
private fun BudgetKey(budgetCents: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onSurface),
        )
        Text(
            text = stringResource(R.string.trends_budget_label, formatAxisMoney(budgetCents)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun NumbersTable(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium.merge(TabularFigures))
            }
        }
    }
}

/** One selector above everything it scopes, instead of one inside each card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(selected: TrendPeriod, onChange: (TrendPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        PERIOD_OPTIONS.forEachIndexed { index, (period, label) ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onChange(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = PERIOD_OPTIONS.size),
                icon = {},
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun CategoryCard(categories: List<CategoryTotal>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.trends_by_category), style = MaterialTheme.typography.titleMedium)
            if (categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.trends_nothing_in_period),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val largest = categories.maxOfOrNull { it.cents } ?: 0L
            categories.forEach { CategoryRow(it, largest) }
        }
    }
}

@Composable
private fun CategoryRow(total: CategoryTotal, largestCents: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(total.category.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.width(78.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((total.cents.toFloat() / largestCents).coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                    ),
            )
        }
        Text(
            text = formatMoney(total.cents),
            style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        Text(
            text = formatShare(total.share),
            style = MaterialTheme.typography.labelSmall.merge(TabularFigures),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(34.dp),
        )
    }
}

@Composable
private fun MealCard(meals: List<MealAverage>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.trends_average_per_meal), style = MaterialTheme.typography.titleMedium)
            meals.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { MealTile(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MealTile(meal: MealAverage, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(meal.category.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = meal.averageCents?.let(::formatMoney) ?: stringResource(R.string.trends_no_average),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = pluralStringResource(R.plurals.trends_logged, meal.count, meal.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExportButton(viewModel: TrendsViewModel) {
    val context = LocalContext.current
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { target ->
        if (target != null) {
            viewModel.exportCsv(context.contentResolver, target) { count ->
                val message = if (count == null) {
                    context.getString(R.string.export_failed)
                } else {
                    context.resources.getQuantityString(R.plurals.export_done, count, count)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }
    OutlinedButton(
        onClick = { pickFile.launch(viewModel.exportFileName()) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Text(stringResource(R.string.export_csv))
    }
}
