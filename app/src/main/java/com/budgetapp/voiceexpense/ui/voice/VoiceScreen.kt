package com.budgetapp.voiceexpense.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.ui.AppViewModels
import com.budgetapp.voiceexpense.voice.VoiceFailure

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/** Listens while the screen is visible, asking for the mic first if needed. Returns a way to ask again. */
@Composable
internal fun rememberVoiceSession(viewModel: VoiceViewModel): () -> Unit {
    val context = LocalContext.current
    val micRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.start() else viewModel.onMicDenied()
    }

    LifecycleStartEffect(Unit) {
        val current = viewModel.state
        when {
            current != VoiceState.Idle && current != VoiceState.NeedsMicPermission -> Unit
            // Also covers coming back from the app's settings with the mic now allowed.
            context.hasMicPermission() -> viewModel.start()
            !viewModel.askedForMic -> {
                viewModel.askedForMic = true
                micRequest.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        onStopOrDispose { viewModel.onLeftScreen() }
    }
    return { micRequest.launch(Manifest.permission.RECORD_AUDIO) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onHeard: (String) -> Unit,
    onBack: () -> Unit,
    onTypeInstead: () -> Unit,
    viewModel: VoiceViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val context = LocalContext.current
    val state = viewModel.state
    val askForMic = rememberVoiceSession(viewModel)

    LaunchedEffect(state) {
        if (state is VoiceState.Heard) onHeard(state.transcript)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_by_voice)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            when (state) {
                is VoiceState.Listening -> Listening(state, onDone = viewModel::finish, onCancel = onBack)
                is VoiceState.Failed -> Failed(
                    failure = state.failure,
                    onRetry = viewModel::start,
                    onTypeInstead = onTypeInstead,
                )
                VoiceState.NeedsMicPermission -> NeedsMic(
                    onAllow = askForMic,
                    onOpenSettings = { context.openAppSettings() },
                    onTypeInstead = onTypeInstead,
                )
                VoiceState.Idle, is VoiceState.Heard -> Unit
            }
        }
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
}

@Composable
internal fun Listening(state: VoiceState.Listening, onDone: () -> Unit, onCancel: () -> Unit) {
    val scale = animateFloatAsState(1f + state.level * 0.35f, label = "mic level")
    Box(
        modifier = Modifier
            .size(120.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_mic),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp),
        )
    }
    Text(stringResource(R.string.voice_listening), style = MaterialTheme.typography.titleLarge)
    Text(
        text = state.partial.ifEmpty { stringResource(R.string.voice_hint) },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = if (state.partial.isEmpty()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    if (state.keepGoing) {
        Text(
            text = stringResource(R.string.voice_keep_going),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onDone) { Text(stringResource(R.string.voice_done)) }
    }
}

@StringRes
internal fun VoiceFailure.messageRes(): Int = when (this) {
    VoiceFailure.NOT_HEARD -> R.string.voice_not_heard
    VoiceFailure.NO_INTERNET -> R.string.voice_no_internet
    VoiceFailure.UNAVAILABLE -> R.string.voice_unavailable
    VoiceFailure.NO_MIC_PERMISSION -> R.string.voice_needs_mic
    VoiceFailure.OTHER -> R.string.voice_other_error
}

@Composable
private fun Failed(failure: VoiceFailure, onRetry: () -> Unit, onTypeInstead: () -> Unit) {
    Text(
        text = stringResource(failure.messageRes()),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (failure != VoiceFailure.UNAVAILABLE) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.try_again))
            }
        }
        OutlinedButton(onClick = onTypeInstead, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.type_instead))
        }
    }
}

@Composable
private fun NeedsMic(onAllow: () -> Unit, onOpenSettings: () -> Unit, onTypeInstead: () -> Unit) {
    Text(
        text = stringResource(R.string.voice_needs_mic),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.allow_microphone))
        }
        // Once someone has declined twice, Android stops showing its prompt.
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_settings))
        }
        OutlinedButton(onClick = onTypeInstead, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.type_instead))
        }
    }
}
