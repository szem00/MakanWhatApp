package com.budgetapp.voiceexpense.ui.quick

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetapp.voiceexpense.R
import com.budgetapp.voiceexpense.data.Expense
import com.budgetapp.voiceexpense.domain.formatMoney
import com.budgetapp.voiceexpense.ui.AppViewModels
import com.budgetapp.voiceexpense.ui.labelRes
import com.budgetapp.voiceexpense.ui.voice.Listening
import com.budgetapp.voiceexpense.ui.voice.VoiceState
import com.budgetapp.voiceexpense.ui.voice.VoiceViewModel
import com.budgetapp.voiceexpense.ui.voice.messageRes
import com.budgetapp.voiceexpense.ui.voice.rememberVoiceSession
import com.budgetapp.voiceexpense.voice.VoiceFailure

@Composable
fun QuickCaptureScreen(
    onClose: () -> Unit,
    onEdit: (Long) -> Unit,
    onType: (String?) -> Unit,
    voice: VoiceViewModel = viewModel(factory = AppViewModels.Factory),
    quick: QuickCaptureViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val context = LocalContext.current
    val askForMic = rememberVoiceSession(voice)
    val state = voice.state

    LaunchedEffect(state) {
        if (state is VoiceState.Heard) quick.onHeard(state.transcript)
    }
    LaunchedEffect(quick.closing) {
        if (quick.closing) onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                // Stops a tap on the card from counting as a tap outside it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val result = quick.result) {
                    is QuickResult.Saved -> Saved(
                        expense = result.expense,
                        onUndo = {
                            Toast.makeText(context, R.string.quick_removed, Toast.LENGTH_SHORT).show()
                            quick.undo()
                        },
                        onEdit = { onEdit(result.expense.id) },
                    )
                    is QuickResult.NoPrice -> NoPrice(
                        transcript = result.transcript,
                        onTryAgain = {
                            quick.tryAgain()
                            voice.startOver()
                        },
                        onType = { onType(result.transcript) },
                        onClose = onClose,
                    )
                    null -> when (state) {
                        is VoiceState.Listening -> Listening(state, onDone = voice::finish, onCancel = onClose)
                        is VoiceState.Failed -> Failed(
                            failure = state.failure,
                            onTryAgain = voice::startOver,
                            onType = { onType(null) },
                            onClose = onClose,
                        )
                        VoiceState.NeedsMicPermission -> NeedsMic(onAllow = askForMic, onClose = onClose)
                        VoiceState.Idle, is VoiceState.Heard -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun Saved(expense: Expense, onUndo: () -> Unit, onEdit: () -> Unit) {
    Text(stringResource(R.string.quick_saved), style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringResource(
            R.string.quick_saved_detail,
            stringResource(expense.category.labelRes()),
            formatMoney(expense.amountCents),
        ),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    if (expense.note.isNotEmpty()) {
        Text(
            text = expense.note,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onUndo) { Text(stringResource(R.string.quick_undo)) }
        OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.quick_edit)) }
    }
}

@Composable
private fun NoPrice(transcript: String, onTryAgain: () -> Unit, onType: () -> Unit, onClose: () -> Unit) {
    Text(
        text = stringResource(R.string.quick_no_price),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.heard, transcript),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    RetryButtons(onTryAgain, onType, onClose)
}

@Composable
private fun Failed(failure: VoiceFailure, onTryAgain: () -> Unit, onType: () -> Unit, onClose: () -> Unit) {
    Text(
        text = stringResource(failure.messageRes()),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    RetryButtons(onTryAgain.takeIf { failure != VoiceFailure.UNAVAILABLE }, onType, onClose)
}

@Composable
private fun RetryButtons(onTryAgain: (() -> Unit)?, onType: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onTryAgain != null) {
            Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.try_again))
            }
        }
        OutlinedButton(onClick = onType, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.type_instead))
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun NeedsMic(onAllow: () -> Unit, onClose: () -> Unit) {
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
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }
}
