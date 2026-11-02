package com.budgetapp.voiceexpense.ui.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.budgetapp.voiceexpense.domain.mentionsPrice
import com.budgetapp.voiceexpense.voice.VoiceFailure
import com.budgetapp.voiceexpense.voice.VoiceListener
import com.budgetapp.voiceexpense.voice.VoiceRecognizer

sealed interface VoiceState {
    data object Idle : VoiceState

    data class Listening(
        val partial: String = "",
        val level: Float = 0f,
        /** Some words are in but the price isn't yet. */
        val keepGoing: Boolean = false,
    ) : VoiceState

    data object NeedsMicPermission : VoiceState

    data class Failed(val failure: VoiceFailure) : VoiceState

    data class Heard(val transcript: String) : VoiceState
}

class VoiceViewModel(private val recognizer: VoiceRecognizer) : ViewModel(), VoiceListener {
    var state by mutableStateOf<VoiceState>(VoiceState.Idle)
        private set

    /** The system only shows its prompt a limited number of times, so ask once per visit. */
    var askedForMic = false

    /**
     * Speech services can stop at the first pause, even after a single word. So until a price is
     * heard, each stop just starts another listen and the words are joined up here.
     */
    private var heardSoFar = ""
    private var finishing = false

    fun start() {
        if (!recognizer.isAvailable) {
            state = VoiceState.Failed(VoiceFailure.UNAVAILABLE)
            return
        }
        finishing = false
        listen()
    }

    private fun listen() {
        state = VoiceState.Listening(partial = heardSoFar, keepGoing = heardSoFar.isNotEmpty())
        recognizer.start(this)
    }

    fun finish() {
        finishing = true
        recognizer.finish()
    }

    /** Forgets what was heard, for when it wasn't enough to use. */
    fun startOver() {
        heardSoFar = ""
        start()
    }

    /** Android cuts off the mic for apps that aren't visible, so stop rather than fail silently. */
    fun onLeftScreen() {
        if (state is VoiceState.Listening) {
            recognizer.cancel()
            state = VoiceState.Idle
        }
    }

    fun onMicDenied() {
        state = VoiceState.NeedsMicPermission
    }

    private fun withHeardSoFar(text: String): String =
        if (heardSoFar.isEmpty()) text else "$heardSoFar $text"

    override fun onPartial(text: String) {
        (state as? VoiceState.Listening)?.let { state = it.copy(partial = withHeardSoFar(text)) }
    }

    override fun onLevel(level: Float) {
        (state as? VoiceState.Listening)?.let { state = it.copy(level = level) }
    }

    override fun onResult(text: String) {
        heardSoFar = withHeardSoFar(text)
        if (finishing || mentionsPrice(heardSoFar)) {
            state = VoiceState.Heard(heardSoFar)
        } else {
            listen()
        }
    }

    override fun onFailure(failure: VoiceFailure) {
        state = when {
            // Usually silence after the last word. The confirm card can take it from here.
            heardSoFar.isNotEmpty() -> VoiceState.Heard(heardSoFar)
            failure == VoiceFailure.NO_MIC_PERMISSION -> VoiceState.NeedsMicPermission
            else -> VoiceState.Failed(failure)
        }
    }

    override fun onCleared() {
        recognizer.cancel()
    }
}
