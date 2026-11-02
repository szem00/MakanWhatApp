package com.budgetapp.voiceexpense.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** People often pause before saying the price. Tapping Done ends listening sooner. */
private const val SILENCE_BEFORE_ENDING_MILLIS = 2_000L

enum class VoiceFailure { NOT_HEARD, NO_INTERNET, NO_MIC_PERMISSION, UNAVAILABLE, OTHER }

interface VoiceListener {
    fun onPartial(text: String)

    /** How loud the speech is right now, from 0 to 1. */
    fun onLevel(level: Float)

    fun onResult(text: String)

    fun onFailure(failure: VoiceFailure)
}

/** Separate from Android's class so tests can stand in for speech they can't produce. */
interface VoiceRecognizer {
    val isAvailable: Boolean

    fun start(listener: VoiceListener)

    /** Stops listening and transcribes what was heard so far. */
    fun finish()

    /** Stops listening and discards what was heard. */
    fun cancel()
}

class AndroidVoiceRecognizer(private val context: Context) : VoiceRecognizer {
    private var recognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(listener: VoiceListener) {
        cancel()
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = created
        created.setRecognitionListener(ForwardingListener(created, listener))
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                .putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    SILENCE_BEFORE_ENDING_MILLIS,
                )
                .putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    SILENCE_BEFORE_ENDING_MILLIS,
                ),
        )
    }

    override fun finish() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.run {
            cancel()
            destroy()
        }
        recognizer = null
    }

    /** The session is already over here, so there's nothing left to cancel. */
    private fun release(finished: SpeechRecognizer) {
        if (recognizer === finished) {
            finished.destroy()
            recognizer = null
        }
    }

    private inner class ForwardingListener(
        private val owner: SpeechRecognizer,
        private val listener: VoiceListener,
    ) : RecognitionListener {
        // Some recognizers send one more event after finishing, such as an error after results.
        private val isCurrent: Boolean get() = recognizer === owner

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (isCurrent && !text.isNullOrEmpty()) listener.onPartial(text)
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (isCurrent) listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onResults(results: Bundle?) {
            if (!isCurrent) return
            val text = firstResult(results)
            release(owner)
            if (text.isNullOrEmpty()) {
                listener.onFailure(VoiceFailure.NOT_HEARD)
            } else {
                listener.onResult(text)
            }
        }

        override fun onError(error: Int) {
            if (!isCurrent) return
            release(owner)
            listener.onFailure(failureFor(error))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    private fun failureFor(error: Int): VoiceFailure = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> VoiceFailure.NOT_HEARD

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        -> VoiceFailure.NO_INTERNET

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> VoiceFailure.NO_MIC_PERMISSION

        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> VoiceFailure.UNAVAILABLE

        else -> VoiceFailure.OTHER
    }
}
