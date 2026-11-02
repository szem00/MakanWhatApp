package com.budgetapp.voiceexpense.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetapp.voiceexpense.VoiceExpenseApp
import com.budgetapp.voiceexpense.data.ThemeMode
import com.budgetapp.voiceexpense.data.isDark

@Composable
private fun VoiceExpenseTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }
    // Samsung's palette makes these two differ, which shows list rows as bands on the page.
    MaterialTheme(colorScheme = colorScheme.copy(background = colorScheme.surface), content = content)
}

/**
 * The theme for one of the app's screens, following the Light, Dark or System choice in settings.
 * The status and navigation bar icons follow it too, which they wouldn't on their own before Android 12.
 */
@Composable
fun AppTheme(activity: ComponentActivity, content: @Composable () -> Unit) {
    val settings = (activity.application as VoiceExpenseApp).container.themeSettings
    val mode by settings.mode.collectAsStateWithLifecycle(initialValue = null)
    val darkTheme = mode.isDark(systemDark = isSystemInDarkTheme())
    DisposableEffect(darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
        )
        onDispose {}
    }
    VoiceExpenseTheme(darkTheme, content)
}

// The scrims enableEdgeToEdge() uses by default, for 3-button navigation.
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/**
 * From Android 12, hands the choice to the system, which applies it to the whole app: the splash
 * screen, the status bar and every dark-mode resource. Older versions have no such setting.
 */
fun applyNightMode(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
        when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        },
    )
}
