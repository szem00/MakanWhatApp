package com.budgetapp.voiceexpense.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.budgetapp.voiceexpense.MainActivity
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.VoiceExpenseApp
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.data.BudgetPeriod
import com.budgetapp.voiceexpense.domain.PeriodSummary
import com.budgetapp.voiceexpense.domain.daysLeftIncludingToday
import com.budgetapp.voiceexpense.domain.formatMoney
import com.budgetapp.voiceexpense.domain.safeToSpendPerDayCents
import com.budgetapp.voiceexpense.domain.weekOf
import com.budgetapp.voiceexpense.quick.QuickCaptureActivity
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WeekStatus(
    val summary: PeriodSummary,
    val safeToSpendPerDayCents: Long?,
    val daysLeft: Int,
)

suspend fun loadWeekStatus(database: AppDatabase, clock: Clock): WeekStatus {
    val today = LocalDate.now(clock)
    val weekStart = database.settingsDao().observe().first()?.weekStartDay ?: DayOfWeek.MONDAY
    val week = weekOf(today, weekStart)
    val spent = database.expenseDao()
        .observeTotalCents(week.startInstant(clock.zone), week.endInstant(clock.zone))
        .first()
    val budget = database.budgetDao().observeOverall(BudgetPeriod.WEEK, today).first()?.amountCents
    val summary = PeriodSummary(spent, budget)
    val daysLeft = daysLeftIncludingToday(today, week)
    val safe = summary.remainingCents?.let { safeToSpendPerDayCents(it, daysLeft) }
    return WeekStatus(summary, safe, daysLeft)
}

/** Reloads every widget on the home screen. Safe to call whether or not there are any. */
fun refreshBudgetWidgets(context: Context) {
    val app = context.applicationContext as VoiceExpenseApp
    widgetScope.launch { renderWidgets(app) }
}

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private suspend fun renderWidgets(app: VoiceExpenseApp) {
    val manager = AppWidgetManager.getInstance(app)
    val ids = manager.getAppWidgetIds(ComponentName(app, BudgetWidgetProvider::class.java))
    if (ids.isEmpty()) return
    val status = loadWeekStatus(app.container.database, app.container.clock)
    manager.updateAppWidget(ids, widgetViews(app, status))
}

private fun widgetViews(context: Context, status: WeekStatus): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_budget).apply {
        val summary = status.summary
        val remaining = summary.remainingCents
        val amountView = if (summary.isOverBudget) R.id.widget_amount_over else R.id.widget_amount
        setViewVisibility(R.id.widget_amount, if (summary.isOverBudget) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_amount_over, if (summary.isOverBudget) View.VISIBLE else View.GONE)
        setTextViewText(
            amountView,
            when {
                remaining == null ->
                    context.getString(R.string.widget_spent_this_week, formatMoney(summary.spentCents))
                summary.isOverBudget ->
                    context.getString(R.string.widget_over_this_week, formatMoney(abs(remaining)))
                else -> context.getString(R.string.widget_left_this_week, formatMoney(remaining))
            },
        )
        setTextViewText(
            R.id.widget_detail,
            when {
                remaining == null -> context.getString(R.string.widget_no_budget)
                summary.isOverBudget -> context.getString(
                    R.string.spent_of_budget_full,
                    formatMoney(summary.spentCents),
                    formatMoney(summary.budgetCents ?: 0),
                )
                else -> context.getString(
                    if (status.daysLeft == 1) R.string.safe_to_spend_today else R.string.widget_safe_per_day,
                    formatMoney(status.safeToSpendPerDayCents ?: 0),
                )
            },
        )
        setOnClickPendingIntent(
            R.id.widget_mic,
            activityIntent(context, requestCode = 1, QuickCaptureActivity.intent(context)),
        )
        setOnClickPendingIntent(
            R.id.widget_summary,
            activityIntent(context, requestCode = 2, Intent(context, MainActivity::class.java)),
        )
    }

private fun activityIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

class BudgetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as VoiceExpenseApp
        val pending = goAsync()
        widgetScope.launch {
            try {
                renderWidgets(app)
            } finally {
                pending.finish()
            }
        }
    }
}
