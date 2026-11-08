package com.interviewcopilot.app.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

/**
 * 16 kHz mono 16-bit PCM microphone capture with the platform's noise suppression, echo cancellation
 * and automatic gain control switched on wherever the device offers them.
 */
class AudioCapture {
    private var record: AudioRecord? = null
    private val effects = ArrayList<android.media.audiofx.AudioEffect>()

    @Volatile
    var effectsEnabled: List<String> = emptyList()
        private set

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested by the UI before this is called.
    fun open(): Boolean {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, // tuned for ASR: no music-style processing
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(min, SAMPLE_RATE * 2),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        val on = ArrayList<String>()
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(rec.audioSessionId)?.also {
                it.setEnabled(true)
                effects.add(it)
                on.add("noise suppression")
            }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(rec.audioSessionId)?.also {
                it.setEnabled(true)
                effects.add(it)
                on.add("echo cancellation")
            }
        }
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(rec.audioSessionId)?.also {
                it.setEnabled(true)
                effects.add(it)
                on.add("auto gain")
            }
        }
        effectsEnabled = on
        record = rec
        rec.startRecording()
        return rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
    }

    /** Blocking read of up to [length] samples. Returns the number read or a negative error. */
    fun read(buffer: ShortArray, length: Int): Int = record?.read(buffer, 0, length) ?: -1

    fun close() {
        effects.forEach { runCatching { it.release() } }
        effects.clear()
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
