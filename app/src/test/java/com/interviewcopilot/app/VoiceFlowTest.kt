package com.interviewcopilot.app

import com.interviewcopilot.app.core.Stage
import com.interviewcopilot.app.core.VoiceEvent
import com.interviewcopilot.app.core.VoiceFlow
import com.interviewcopilot.app.core.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFlowTest {
    private fun run(vararg events: VoiceEvent, from: VoiceState = VoiceState()): VoiceState =
        events.fold(from) { s, e -> VoiceFlow.reduce(s, e) }

    @Test
    fun speakingNeverStartsGeneration() {
        val s = run(VoiceEvent.MicTapped, VoiceEvent.Partial("hello"), VoiceEvent.SpeechFinal("Hello there."))
        assertEquals(Stage.Transcribed, s.stage)
        assertEquals("Hello there.", s.text)
        assertEquals("", s.answer)
        assertTrue(s.canGenerate)
    }

    @Test
    fun fullHappyPathEndsIdleWithAnswerKept() {
        val s = run(
            VoiceEvent.MicTapped,
            VoiceEvent.SpeechFinal("What is a thread?"),
            VoiceEvent.GenerateTapped,
            VoiceEvent.Token("A thread"),
            VoiceEvent.Token("A thread is a path of execution."),
            VoiceEvent.GenerationFinished,
            VoiceEvent.ResetDone,
        )
        assertEquals(Stage.Idle, s.stage)
        assertEquals("", s.text)
        assertEquals("What is a thread?", s.question)
        assertEquals("A thread is a path of execution.", s.answer)
    }

    @Test
    fun generateDisabledWhenEmptyOrBusy() {
        assertFalse(VoiceState().canGenerate)
        assertFalse(VoiceState(stage = Stage.Transcribed, text = "   ").canGenerate)
        assertFalse(VoiceState(stage = Stage.Generating, text = "hi").canGenerate)
        assertFalse(VoiceState(stage = Stage.Listening, text = "hi").canGenerate)
        assertTrue(VoiceState(stage = Stage.Transcribed, text = "hi").canGenerate)
    }

    @Test
    fun generateTapIgnoredWhenDisabled() {
        val s = run(VoiceEvent.GenerateTapped)
        assertEquals(Stage.Idle, s.stage)
    }

    @Test
    fun editingTextKeepsItReadyAndEmptyingDisables() {
        var s = run(VoiceEvent.MicTapped, VoiceEvent.SpeechFinal("helo wrld"))
        s = VoiceFlow.reduce(s, VoiceEvent.TextEdited("Hello world"))
        assertEquals("Hello world", s.text)
        assertTrue(s.canGenerate)
        s = VoiceFlow.reduce(s, VoiceEvent.TextEdited(""))
        assertFalse(s.canGenerate)
        assertEquals(Stage.Idle, s.stage)
    }

    @Test
    fun stopKeepsTextForRetry() {
        val s = run(VoiceEvent.MicTapped, VoiceEvent.SpeechFinal("Explain REST"), VoiceEvent.GenerateTapped, VoiceEvent.StopTapped)
        assertEquals(Stage.Transcribed, s.stage)
        assertEquals("Explain REST", s.text)
        assertTrue(s.canGenerate)
    }

    @Test
    fun emptySpeechReturnsToIdleWithMessage() {
        val s = run(VoiceEvent.MicTapped, VoiceEvent.SpeechFinal("   "))
        assertEquals(Stage.Idle, s.stage)
        assertTrue(s.error != null)
    }

    @Test
    fun editsAreIgnoredWhileGenerating() {
        val s = run(VoiceEvent.MicTapped, VoiceEvent.SpeechFinal("Question"), VoiceEvent.GenerateTapped, VoiceEvent.TextEdited("changed"))
        assertEquals("Question", s.text)
    }

    @Test
    fun failureReturnsToTranscribedWithError() {
        val s = run(VoiceEvent.MicTapped, VoiceEvent.SpeechFinal("Q"), VoiceEvent.GenerateTapped, VoiceEvent.GenerationFailed("boom"))
        assertEquals(Stage.Transcribed, s.stage)
        assertEquals("boom", s.error)
    }

    @Test
    fun secondQuestionIsIndependent() {
        val first = run(
            VoiceEvent.MicTapped,
            VoiceEvent.SpeechFinal("One"),
            VoiceEvent.GenerateTapped,
            VoiceEvent.Token("A1"),
            VoiceEvent.GenerationFinished,
            VoiceEvent.ResetDone,
        )
        val second = run(VoiceEvent.MicTapped, VoiceEvent.SpeechFinal("Two"), VoiceEvent.GenerateTapped, from = first)
        assertEquals("Two", second.question)
        assertEquals("", second.answer)
    }
}
