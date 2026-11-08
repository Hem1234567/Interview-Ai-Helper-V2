package com.interviewcopilot.app.voice

import android.os.Handler
import android.os.Looper
import com.interviewcopilot.app.core.AudioDsp
import com.interviewcopilot.app.core.Vad
import com.interviewcopilot.app.core.VadEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * On-device Whisper session: records 16 kHz mono PCM, trims silence with a VAD, shows live partial
 * text while the user talks, and runs a slower, more accurate pass on the finished utterance.
 * Capture and decoding run on background threads; callbacks are posted to the main thread.
 */
class WhisperRecognizer(
    private val language: String,
    private val vocabularyHint: String,
    private val listener: SttListener,
) : SttSession {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    private var stopRequested = false

    @Volatile
    private var cancelled = false

    private val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
    private val prompt: ByteArray = buildPrompt().toByteArray(Charsets.UTF_8)

    private fun buildPrompt(): String {
        val terms = vocabularyHint.lines().mapNotNull { l -> l.substringAfter('=', "").trim().ifEmpty { null } }
            .distinct().take(30).joinToString(", ")
        val base = "Hello. This is a clear, correctly punctuated and capitalized sentence."
        return if (terms.isEmpty()) base else "$base Terms: $terms."
    }

    override fun start() {
        stopRequested = false
        cancelled = false
        job = scope.launch(Dispatchers.IO) { capture() }
    }

    override fun stop() {
        stopRequested = true
    }

    override fun cancel() {
        cancelled = true
        stopRequested = true
        NativeWhisper.nativeCancel()
        job?.cancel()
        scope.cancel()
    }

    private fun post(block: () -> Unit) {
        if (!cancelled) main.post { if (!cancelled) block() }
    }

    private suspend fun capture() {
        val mic = AudioCapture()
        if (!mic.open()) {
            mic.close()
            post { listener.onError("Could not open the microphone.") }
            return
        }
        val vad = Vad()
        val frame = ShortArray(vad.frameSamples)
        val frameF = FloatArray(vad.frameSamples)
        val preRoll = ArrayDeque<FloatArray>()
        val maxPreRoll = 10 // 300 ms kept from before speech started, so first syllables are not clipped
        val utterance = ArrayList<FloatArray>()
        var speechSeen = false
        var waitedMs = 0
        var sinceLastPartialMs = 0
        var partialBusy = false

        try {
            while (scope.isActive && !cancelled) {
                val n = mic.read(frame, frame.size)
                if (n <= 0) {
                    post { listener.onError("Microphone stopped responding.") }
                    return
                }
                AudioDsp.toFloat(frame, n, frameF)
                val ev = vad.process(frameF, n)
                post { listener.onLevel(vad.level) }
                val copy = frameF.copyOf(n)

                if (!speechSeen) {
                    preRoll.addLast(copy)
                    if (preRoll.size > maxPreRoll) preRoll.removeFirst()
                    waitedMs += 30
                    if (ev == VadEvent.SpeechStarted) {
                        speechSeen = true
                        utterance.addAll(preRoll)
                        preRoll.clear()
                    } else if (waitedMs > NO_SPEECH_TIMEOUT_MS && !stopRequested) {
                        post { listener.onError("No speech detected. Tap the mic and try again.") }
                        return
                    }
                } else {
                    utterance.add(copy)
                    sinceLastPartialMs += 30
                    if (sinceLastPartialMs >= PARTIAL_EVERY_MS && !partialBusy && utterance.size > 20) {
                        sinceLastPartialMs = 0
                        partialBusy = true
                        val snapshot = flatten(utterance, MAX_PARTIAL_SECONDS)
                        scope.launch(Dispatchers.Default) {
                            try {
                                val t = transcribe(snapshot, accurate = false)
                                if (t.isNotBlank()) post { listener.onPartial(t) }
                            } finally {
                                partialBusy = false
                            }
                        }
                    }
                    if (ev == VadEvent.SpeechEnded) break
                    if (utterance.size * 30 >= MAX_UTTERANCE_MS) break
                }
                if (stopRequested) break
            }
        } finally {
            mic.close()
        }
        if (cancelled) return
        post { listener.onLevel(0f) }
        // Wait for an in-flight partial to finish, then run the accurate pass on the whole utterance.
        val audio = flatten(utterance, MAX_UTTERANCE_MS / 1000)
        if (audio.size < 4_800) {
            post { listener.onFinal("") }
            return
        }
        val text = transcribe(audio, accurate = true)
        post { listener.onFinal(text) }
    }

    private fun transcribe(audio: FloatArray, accurate: Boolean): String {
        val pcm = AudioDsp.normalize(audio)
        val bytes = NativeWhisper.nativeTranscribe(pcm, language, prompt, threads, accurate)
        return String(bytes, Charsets.UTF_8).trim()
    }

    private fun flatten(frames: List<FloatArray>, maxSeconds: Int): FloatArray {
        val total = frames.sumOf { it.size }
        val limit = maxSeconds * AudioCapture.SAMPLE_RATE
        val start = maxOf(0, total - limit)
        val out = FloatArray(total - start)
        var skipped = 0
        var pos = 0
        for (f in frames) {
            for (v in f) {
                if (skipped < start) {
                    skipped++
                } else {
                    out[pos++] = v
                }
            }
        }
        return out
    }

    private companion object {
        const val NO_SPEECH_TIMEOUT_MS = 9_000
        const val PARTIAL_EVERY_MS = 1_200
        const val MAX_PARTIAL_SECONDS = 20
        const val MAX_UTTERANCE_MS = 29_000
    }
}
