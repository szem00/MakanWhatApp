package com.budgetapp.voiceexpense.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.data.ThemeMode
import com.budgetapp.voiceexpense.ui.AppBarSaveButton
import com.budgetapp.voiceexpense.ui.AppViewModels
import com.budgetapp.voiceexpense.ui.PinnedSaveButton
import com.budgetapp.voiceexpense.ui.isShortWindow
import com.budgetapp.voiceexpense.widget.BudgetWidgetProvider
import java.time.DayOfWeek

private val WEEK_START_OPTIONS = listOf(
    DayOfWeek.MONDAY to R.string.monday,
    DayOfWeek.SUNDAY to R.string.sunday,
)

private val THEME_OPTIONS = listOf(
    ThemeMode.SYSTEM to R.string.theme_system,
    ThemeMode.LIGHT to R.string.theme_light,
    ThemeMode.DARK to R.string.theme_dark,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val form = viewModel.form
    val shortWindow = isShortWindow()

    LaunchedEffect(form.finished) {
        if (form.finished) onFinished()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budgets_and_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (shortWindow) AppBarSaveButton(form.ready, viewModel::save)
                },
            )
        },
        bottomBar = {
            if (!shortWindow) PinnedSaveButton(form.ready, viewModel::save)
        },
    ) { padding ->
        if (!form.ready) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BudgetField(
                label = stringResource(R.string.weekly_budget),
                value = form.weeklyText,
                invalid = form.weeklyInvalid,
                imeAction = ImeAction.Next,
                onValueChange = viewModel::onWeeklyChange,
            )
            BudgetField(
                label = stringResource(R.string.monthly_budget),
                value = form.monthlyText,
                invalid = form.monthlyInvalid,
                imeAction = ImeAction.Done,
                onValueChange = viewModel::onMonthlyChange,
            )
            ChoiceRow(
                title = stringResource(R.string.week_starts_on),
                options = WEEK_START_OPTIONS,
                selected = form.weekStart,
                onSelect = viewModel::onWeekStartChange,
            )
            ChoiceRow(
                title = stringResource(R.string.theme),
                options = THEME_OPTIONS,
                selected = form.theme,
                onSelect = viewModel::onThemeChange,
            )
            AddWidgetButton()
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
private fun AddWidgetButton() {
    val context = LocalContext.current
    val widgets = AppWidgetManager.getInstance(context)
    if (!widgets.isRequestPinAppWidgetSupported) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.quick_capture_section),
            style = MaterialTheme.typography.titleSmall,
        )
        OutlinedButton(
            onClick = {
                widgets.requestPinAppWidget(
                    ComponentName(context, BudgetWidgetProvider::class.java),
                    null,
                    null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_widget))
        }
    }
}

@Composable
private fun BudgetField(
    label: String,
    value: String,
    invalid: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = { Text("$") },
        isError = invalid,
        supportingText = {
            Text(stringResource(if (invalid) R.string.amount_error else R.string.budget_help))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
