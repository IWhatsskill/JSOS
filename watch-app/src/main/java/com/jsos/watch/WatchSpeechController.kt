package com.jsos.watch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

data class WatchSpeechState(
    val isListening: Boolean = false,
    val status: String = "MIC READY",
    val lastText: String = "",
    val error: String? = null
)

class WatchSpeechController(
    context: Context,
    private val onState: (WatchSpeechState) -> Unit,
    private val onFinalText: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private var state = WatchSpeechState()
    private var speechRecognizer: SpeechRecognizer? = null

    fun start() {
        if (state.isListening) {
            stop()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            update(WatchSpeechState(status = "MIC UNAVAILABLE", error = "No speech service"))
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer ->
            recognizer.setRecognitionListener(listener)
        }
        speechRecognizer = recognizer
        update(WatchSpeechState(isListening = true, status = "LISTENING"))
        recognizer.startListening(recognizerIntent())
    }

    fun stop() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        update(state.copy(isListening = false, status = "MIC READY"))
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            update(state.copy(isListening = true, status = "LISTENING", error = null))
        }

        override fun onBeginningOfSpeech() {
            update(state.copy(isListening = true, status = "HEARING", error = null))
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            update(state.copy(isListening = false, status = "THINKING"))
        }

        override fun onError(error: Int) {
            val message = speechError(error)
            update(WatchSpeechState(status = "MIC ERROR", lastText = state.lastText, error = message))
        }

        override fun onResults(results: Bundle?) {
            val text = bestText(results)
            if (text.isBlank()) {
                update(WatchSpeechState(status = "NO SPEECH", lastText = state.lastText, error = "No speech"))
                return
            }
            update(WatchSpeechState(status = "SENT", lastText = text))
            onFinalText(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = bestText(partialResults)
            if (text.isNotBlank()) {
                update(state.copy(isListening = true, status = "HEARING", lastText = text, error = null))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private fun bestText(results: Bundle?): String =
        results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

    private fun update(next: WatchSpeechState) {
        state = next
        onState(next)
    }

    private fun speechError(error: Int): String =
        when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission missing"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Speech error $error"
        }
}
