package com.budgetapp.voiceexpense.quick

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.budgetapp.voiceexpense.MainActivity
import com.budgetapp.voiceexpense.ui.quick.QuickCaptureScreen
import com.budgetapp.voiceexpense.ui.theme.AppTheme

/**
 * The voice shortcut behind the widget: a card over whatever is on screen. The mic only works for
 * a visible app, so this is as close to not opening the app as Android allows.
 */
class QuickCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(this) {
                QuickCaptureScreen(
                    onClose = ::finish,
                    onEdit = { id ->
                        startActivity(MainActivity.editIntent(this, id))
                        finish()
                    },
                    onType = { transcript ->
                        startActivity(MainActivity.entryIntent(this, transcript))
                        finish()
                    },
                )
            }
        }
    }

    /** Listening has already stopped by now, so leaving the card up would only strand it. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, QuickCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
