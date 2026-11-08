package com.interviewcopilot.app

import com.interviewcopilot.app.core.AudioDsp
import com.interviewcopilot.app.core.Vad
import com.interviewcopilot.app.core.VadEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class VadTest {
    private val rnd = Random(7)

    private fun noise(vad: Vad, amp: Float) = FloatArray(vad.frameSamples) { (rnd.nextFloat() - 0.5f) * 2 * amp }
    private fun tone(vad: Vad, amp: Float, offset: Int) =
        FloatArray(vad.frameSamples) { i -> amp * sin(2.0 * PI * 220.0 * (offset + i) / 16000.0).toFloat() + (rnd.nextFloat() - 0.5f) * 0.002f }

    @Test
    fun backgroundNoiseIsNeverSpeech() {
        val vad = Vad()
        repeat(400) { assertEquals(VadEvent.None, vad.process(noise(vad, 0.004f))) }
        assertFalse(vad.inSpeech)
    }

    @Test
    fun speechStartsAndEndsAfterSilence() {
        val vad = Vad()
        repeat(60) { vad.process(noise(vad, 0.003f)) }
        var started = false
        var ended = false
        var pos = 0
        repeat(50) {
            if (vad.process(tone(vad, 0.25f, pos)) == VadEvent.SpeechStarted) started = true
            pos += vad.frameSamples
        }
        assertTrue(started)
        assertTrue(vad.inSpeech)
        repeat(60) { if (vad.process(noise(vad, 0.003f)) == VadEvent.SpeechEnded) ended = true }
        assertTrue(ended)
        assertFalse(vad.inSpeech)
    }

    @Test
    fun shortPauseDoesNotEndSpeech() {
        val vad = Vad()
        repeat(60) { vad.process(noise(vad, 0.003f)) }
        var pos = 0
        repeat(30) { vad.process(tone(vad, 0.25f, pos)); pos += vad.frameSamples }
        repeat(15) { vad.process(noise(vad, 0.003f)) } // 450 ms
        assertTrue(vad.inSpeech)
    }

    @Test
    fun normalizeBoostsQuietAudioButCapsGain() {
        val quiet = FloatArray(1600) { 0.01f * sin(it / 5.0).toFloat() }
        val out = AudioDsp.normalize(quiet.copyOf())
        val peak = out.maxOf { kotlin.math.abs(it) }
        assertTrue(peak > 0.05f)
        assertTrue(peak <= 0.9f)
        val silent = AudioDsp.normalize(FloatArray(100))
        assertEquals(0f, silent.maxOf { kotlin.math.abs(it) }, 0f)
    }
}
