package com.budgetapp.voiceexpense.ui

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.data.Category
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.domain.DateRange
import com.budgetapp.voiceexpense.domain.formatMoney
import com.budgetapp.voiceexpense.ui.theme.TABULAR_FIGURES
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM")
private const val SHORT_WINDOW_DP = 480

fun formatDay(date: LocalDate): String = date.format(dayFormatter)

fun formatMonth(date: LocalDate): String = date.format(monthFormatter)

fun formatRange(range: DateRange): String =
    "${formatDay(range.start)} – ${formatDay(range.lastDay)}"

/** In landscape the keyboard leaves no room for a pinned button, so Save moves to the app bar. */
@Composable
fun isShortWindow(): Boolean = LocalConfiguration.current.screenHeightDp < SHORT_WINDOW_DP

@Composable
fun PinnedSaveButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.save))
    }
}

@Composable
fun AppBarSaveButton(enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(stringResource(R.string.save))
    }
}

/** Follows the phone's 12 or 24 hour setting. */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24Hour) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a") }
}

@StringRes
fun Category.labelRes(): Int = when (this) {
    Category.BREAKFAST -> R.string.category_breakfast
    Category.LUNCH -> R.string.category_lunch
    Category.DINNER -> R.string.category_dinner
    Category.SUPPER -> R.string.category_supper
    Category.TRANSPORT -> R.string.category_transport
    Category.GROCERIES -> R.string.category_groceries
    Category.SHOPPING -> R.string.category_shopping
    Category.OTHER -> R.string.category_other
}

@Composable
fun ExpenseRow(expense: Expense, zone: ZoneId, onClick: () -> Unit) {
    val time = expense.timestamp.atZone(zone).format(rememberTimeFormatter())
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(stringResource(expense.category.labelRes()), fontWeight = FontWeight.Bold)
        },
        supportingContent = {
            Text(
                text = if (expense.note.isBlank()) time else "${expense.note} · $time",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                text = formatMoney(expense.amountCents),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        },
    )
}
