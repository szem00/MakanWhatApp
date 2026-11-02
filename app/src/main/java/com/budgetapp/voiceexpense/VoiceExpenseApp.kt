package com.budgetapp.voiceexpense

import android.app.Application
import androidx.annotation.VisibleForTesting
import com.budgetapp.voiceexpense.data.AppDatabase
import com.budgetapp.voiceexpense.voice.AndroidVoiceRecognizer
import com.budgetapp.voiceexpense.voice.VoiceRecognizer
import com.budgetapp.voiceexpense.widget.refreshBudgetWidgets
import java.time.Clock

class AppContainer(
    val database: AppDatabase,
    val clock: Clock = Clock.systemDefaultZone(),
    val createVoiceRecognizer: () -> VoiceRecognizer,
    /** Called after any change to what the widget shows. */
    val onDataChanged: () -> Unit = {},
    /** How long quick capture's "saved" card stays up before closing itself. */
    val savedCardMillis: Long = 3_000,
)

class VoiceExpenseApp : Application() {
    private val defaultContainer by lazy {
        AppContainer(
            database = AppDatabase.create(this),
            createVoiceRecognizer = { AndroidVoiceRecognizer(this) },
            onDataChanged = { refreshBudgetWidgets(this) },
        )
    }

    /** Lets tests use an in-memory database, so they never open the real one. */
    @VisibleForTesting
    var containerOverride: AppContainer? = null

    val container: AppContainer get() = containerOverride ?: defaultContainer
}
