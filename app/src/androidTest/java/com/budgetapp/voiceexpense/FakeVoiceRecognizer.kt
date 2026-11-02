package com.budgetapp.voiceexpense

import com.budgetapp.voiceexpense.voice.VoiceFailure
import com.budgetapp.voiceexpense.voice.VoiceListener
import com.budgetapp.voiceexpense.voice.VoiceRecognizer

/** Stands in for the phone's speech service, which tests can't speak to. */
class FakeVoiceRecognizer(override val isAvailable: Boolean = true) : VoiceRecognizer {
    private var listener: VoiceListener? = null

    var starts = 0
        private set

    val isListening: Boolean get() = listener != null

    override fun start(listener: VoiceListener) {
        this.listener = listener
        starts++
    }

    override fun finish() = Unit

    override fun cancel() {
        listener = null
    }

    fun hearPartial(text: String) {
        listener?.onPartial(text)
    }

    fun hear(text: String) {
        val current = listener
        listener = null
        current?.onResult(text)
    }

    fun fail(failure: VoiceFailure) {
        val current = listener
        listener = null
        current?.onFailure(failure)
    }
}
