package com.budgetapp.voiceexpense.ui.entry

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.ui.AppBarSaveButton
import com.budgetapp.voiceexpense.ui.AppViewModels
import com.budgetapp.voiceexpense.ui.PinnedSaveButton
import com.budgetapp.voiceexpense.ui.formatDay
import com.budgetapp.voiceexpense.ui.isShortWindow
import com.budgetapp.voiceexpense.ui.labelRes
import com.budgetapp.voiceexpense.ui.rememberTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onSayAgain: () -> Unit,
    viewModel: EntryViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val form = viewModel.form
    val shortWindow = isShortWindow()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(form.finished) {
        if (form.finished) onFinished()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                viewModel.transcript != null -> R.string.confirm_expense
                                viewModel.isNew -> R.string.add_expense
                                else -> R.string.edit_expense
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (viewModel.transcript != null) {
                        IconButton(onClick = onSayAgain) {
                            Icon(
                                painterResource(R.drawable.ic_mic),
                                contentDescription = stringResource(R.string.say_again),
                            )
                        }
                    }
                    if (shortWindow) AppBarSaveButton(form.ready, viewModel::save)
                    if (!viewModel.isNew) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!shortWindow) PinnedSaveButton(form.ready, viewModel::save)
        },
    ) { padding ->
        if (form.ready) {
            EntryFields(
                viewModel = viewModel,
                form = form,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_expense_title)) },
            text = { Text(stringResource(R.string.delete_expense_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun EntryFields(viewModel: EntryViewModel, form: EntryForm, modifier: Modifier) {
    val amountFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    // Voice usually fills the amount, and the keyboard would then only hide the rest of the card.
    LaunchedEffect(Unit) {
        if (viewModel.isNew && form.amountText.isEmpty()) amountFocus.requestFocus()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        viewModel.transcript?.let { transcript ->
            Text(
                text = stringResource(R.string.heard, transcript),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = form.amountText,
            onValueChange = viewModel::onAmountChange,
            label = { Text(stringResource(R.string.amount)) },
            prefix = { Text("$") },
            isError = form.amountInvalid,
            supportingText = when {
                form.amountInvalid -> {
                    { Text(stringResource(R.string.amount_error)) }
                }
                form.amountNotHeard -> {
                    { Text(stringResource(R.string.amount_not_heard)) }
                }
                else -> null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { noteFocus.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(amountFocus),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    FilterChip(
                        selected = form.category == category,
                        onClick = { viewModel.onCategoryChange(category) },
                        label = { Text(stringResource(category.labelRes())) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = form.note,
            onValueChange = viewModel::onNoteChange,
            label = { Text(stringResource(R.string.note)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(noteFocus),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.when_label), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingDate = true }) {
                    Text(formatDay(form.date))
                }
                OutlinedButton(onClick = { pickingTime = true }) {
                    Text(form.time.format(rememberTimeFormatter()))
                }
            }
        }
    }

    if (pickingDate) {
        DateDialog(
            date = form.date,
            today = viewModel.today,
            onPick = viewModel::onDateChange,
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        TimeDialog(
            time = form.time,
            onPick = viewModel::onTimeChange,
            onDismiss = { pickingTime = false },
        )
    }
}

/** Material's date picker works in UTC midnights, whatever the phone's time zone is. */
private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    date: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.toPickerMillis(),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= today.toPickerMillis()
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(it.toPickerDate()) }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_time)) },
        text = { TimeInput(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    onPick(LocalTime.of(state.hour, state.minute))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
