package com.interviewcopilot.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Fallback: Android's built-in recognizer. Used until a Whisper model has been downloaded. */
class PlatformRecognizer(
    private val ctx: Context,
    private val languageCode: String,
    private val listener: SttListener,
) : SttSession {
    private var rec: SpeechRecognizer? = null
    private var lastPartial = ""
    private var delivered = false

    private fun tag(): String =
        if (languageCode == "auto") Locale.getDefault().toLanguageTag() else languageCode

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = first(partialResults)
            if (!t.isNullOrBlank()) {
                lastPartial = t
                listener.onPartial(t)
            }
        }

        override fun onResults(results: Bundle?) {
            finish(first(results) ?: lastPartial)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    fail("Microphone permission is missing.")
                }

                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (lastPartial.isNotBlank()) finish(lastPartial) else finish("")
                }

                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    fail("The phone's recognizer needs internet. Download a Whisper model in Settings to work offline.")
                }

                else -> {
                    fail("Speech recognition failed (code $error).")
                }
            }
        }
    }

    private fun first(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun finish(text: String) {
        if (delivered) return
        delivered = true
        listener.onLevel(0f)
        listener.onFinal(text.trim())
    }

    private fun fail(message: String) {
        if (delivered) return
        delivered = true
        listener.onError(message)
    }

    override fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            fail("Speech recognition is not available on this phone.")
            return
        }
        delivered = false
        lastPartial = ""
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(callbacks)
        rec = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
        }
        r.startListening(intent)
    }

    override fun stop() {
        rec?.stopListening()
    }

    override fun cancel() {
        delivered = true
        rec?.destroy()
        rec = null
    }
}
