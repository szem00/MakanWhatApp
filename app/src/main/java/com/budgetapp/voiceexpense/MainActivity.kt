package com.budgetapp.voiceexpense

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.budgetapp.voiceexpense.ui.AppNavHost
import com.budgetapp.voiceexpense.ui.entryRoute
import com.budgetapp.voiceexpense.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openRoute = when {
            intent.hasExtra(EXTRA_EXPENSE_ID) ->
                entryRoute(expenseId = intent.getLongExtra(EXTRA_EXPENSE_ID, 0))
            intent.getBooleanExtra(EXTRA_NEW_ENTRY, false) ->
                entryRoute(transcript = intent.getStringExtra(EXTRA_TRANSCRIPT))
            else -> null
        }
        setContent {
            AppTheme(this) {
                AppNavHost(openRoute = openRoute)
            }
        }
    }

    companion object {
        private const val EXTRA_EXPENSE_ID = "expense_id"
        private const val EXTRA_NEW_ENTRY = "new_entry"
        private const val EXTRA_TRANSCRIPT = "transcript"

        private fun freshIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        fun editIntent(context: Context, expenseId: Long): Intent =
            freshIntent(context).putExtra(EXTRA_EXPENSE_ID, expenseId)

        /** Opens the form, as the confirm card if there's a [transcript] to fill it from. */
        fun entryIntent(context: Context, transcript: String?): Intent =
            freshIntent(context)
                .putExtra(EXTRA_NEW_ENTRY, true)
                .putExtra(EXTRA_TRANSCRIPT, transcript)
    }
}
