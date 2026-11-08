package com.interviewcopilot.app.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What the detector saw in one audio frame. */
enum class VadEvent { None, SpeechStarted, SpeechEnded }

/**
 * Energy based voice activity detector with an adaptive noise floor.
 *
 * Speech is declared when the frame energy rises [startRatio] times above the running noise floor
 * (and above an absolute minimum) for [startFrames] frames. It ends after [endSilenceMs] of quiet.
 * Everything before the start and after the end is trimmed from what Whisper receives.
 */
class Vad(
    private val sampleRate: Int = 16_000,
    private val frameMs: Int = 30,
    private val startRatio: Float = 3.2f,
    private val minRms: Float = 0.006f,
    private val startFrames: Int = 3,
    private val endSilenceMs: Int = 1_100,
) {
    var noiseFloor: Float = 0.003f
        private set
    var inSpeech: Boolean = false
        private set
    var level: Float = 0f
        private set

    private var loudRun = 0
    private var quietMs = 0

    val frameSamples: Int get() = sampleRate * frameMs / 1000

    fun reset() {
        noiseFloor = 0.003f
        inSpeech = false
        loudRun = 0
        quietMs = 0
        level = 0f
    }

    /** Feed one frame of float PCM (-1..1). */
    fun process(frame: FloatArray, length: Int = frame.size): VadEvent {
        val rms = rms(frame, length)
        level = min(1f, rms * 14f)
        val threshold = max(minRms, noiseFloor * startRatio)
        val loud = rms > threshold

        if (!inSpeech) {
            // Track the background level only while nobody is talking.
            noiseFloor = if (rms < noiseFloor) {
                noiseFloor * 0.9f + rms * 0.1f
            } else {
                noiseFloor * 0.995f + rms * 0.005f
            }
            loudRun = if (loud) loudRun + 1 else 0
            if (loudRun >= startFrames) {
                inSpeech = true
                quietMs = 0
                return VadEvent.SpeechStarted
            }
            return VadEvent.None
        }

        if (loud || rms > noiseFloor * 1.8f) {
            quietMs = 0
        } else {
            quietMs += frameMs
            if (quietMs >= endSilenceMs) {
                inSpeech = false
                loudRun = 0
                quietMs = 0
                return VadEvent.SpeechEnded
            }
        }
        return VadEvent.None
    }

    companion object {
        fun rms(frame: FloatArray, length: Int = frame.size): Float {
            if (length <= 0) return 0f
            var sum = 0.0
            for (i in 0 until length) sum += frame[i] * frame[i]
            return sqrt(sum / length).toFloat()
        }
    }
}

/** Small DSP helpers for the Whisper input. */
object AudioDsp {
    /** Convert 16-bit PCM to float in -1..1. */
    fun toFloat(src: ShortArray, length: Int, dst: FloatArray) {
        for (i in 0 until length) dst[i] = src[i] / 32768f
    }

    /** Remove DC offset and scale quiet speech up (auto gain), never above [maxGain]. */
    fun normalize(samples: FloatArray, targetPeak: Float = 0.9f, maxGain: Float = 8f): FloatArray {
        if (samples.isEmpty()) return samples
        var mean = 0.0
        for (s in samples) mean += s
        mean /= samples.size
        var peak = 0f
        for (i in samples.indices) {
            val v = samples[i] - mean.toFloat()
            samples[i] = v
            peak = max(peak, abs(v))
        }
        if (peak < 1e-4f) return samples
        val gain = min(maxGain, targetPeak / peak)
        if (gain > 1.05f) for (i in samples.indices) samples[i] *= gain
        return samples
    }
}
