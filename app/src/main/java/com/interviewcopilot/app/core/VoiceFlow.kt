package com.interviewcopilot.app.core

/** The only states the voice screen can be in. Order of the happy path: see [VoiceFlow]. */
enum class Stage { Idle, Listening, Transcribed, Generating, Answered }

data class VoiceState(
    val stage: Stage = Stage.Idle,
    /** The editable text box. Filled by speech, editable by the user, cleared after an answer. */
    val text: String = "",
    /** Live (not yet final) transcription shown while the user is speaking. */
    val partial: String = "",
    /** The question that the visible answer belongs to. */
    val question: String = "",
    val answer: String = "",
    val error: String? = null,
) {
    /** "Generate Answer" is only usable with non-blank text and when nothing is running. */
    val canGenerate: Boolean
        get() = text.isNotBlank() && (stage == Stage.Idle || stage == Stage.Transcribed)

    val canStop: Boolean get() = stage == Stage.Generating
    val micBusy: Boolean get() = stage == Stage.Generating
}

sealed interface VoiceEvent {
    data object MicTapped : VoiceEvent
    data class Partial(val text: String) : VoiceEvent
    data class SpeechFinal(val text: String) : VoiceEvent
    data class SpeechFailed(val message: String) : VoiceEvent
    data class TextEdited(val text: String) : VoiceEvent
    data object GenerateTapped : VoiceEvent
    data class Token(val answerSoFar: String) : VoiceEvent
    data object GenerationFinished : VoiceEvent
    data object StopTapped : VoiceEvent
    data class GenerationFailed(val message: String) : VoiceEvent
    data object ResetDone : VoiceEvent
}

/**
 * Pure reducer for the voice flow. Speech and answer generation are separate steps:
 *
 * Idle -> Listening -> Transcribed -> Generating -> Answered -> Idle
 *
 * Nothing in here ever starts generation by itself; only [VoiceEvent.GenerateTapped] does.
 */
object VoiceFlow {
    fun reduce(s: VoiceState, e: VoiceEvent): VoiceState = when (e) {
        VoiceEvent.MicTapped -> {
            when (s.stage) {
                Stage.Listening -> s

                // the controller stops capture, the final text arrives as SpeechFinal
                Stage.Generating -> s

                else -> s.copy(stage = Stage.Listening, text = "", partial = "", error = null)
            }
        }

        is VoiceEvent.Partial -> {
            if (s.stage == Stage.Listening) s.copy(partial = e.text) else s
        }

        is VoiceEvent.SpeechFinal -> {
            if (s.stage != Stage.Listening) {
                s
            } else if (e.text.isBlank()) {
                s.copy(stage = Stage.Idle, partial = "", error = "I did not catch that. Tap the mic and try again.")
            } else {
                s.copy(stage = Stage.Transcribed, text = e.text.trim(), partial = "", error = null)
            }
        }

        is VoiceEvent.SpeechFailed -> {
            if (s.stage == Stage.Listening) s.copy(stage = Stage.Idle, partial = "", error = e.message) else s
        }

        is VoiceEvent.TextEdited -> {
            when (s.stage) {
                Stage.Idle, Stage.Transcribed -> {
                    s.copy(text = e.text, stage = if (e.text.isBlank()) Stage.Idle else Stage.Transcribed, error = null)
                }

                else -> {
                    s
                }
            }
        }

        VoiceEvent.GenerateTapped -> {
            if (s.canGenerate) s.copy(stage = Stage.Generating, question = s.text.trim(), answer = "", error = null) else s
        }

        is VoiceEvent.Token -> {
            if (s.stage == Stage.Generating) s.copy(answer = e.answerSoFar) else s
        }

        VoiceEvent.GenerationFinished -> {
            if (s.stage == Stage.Generating) s.copy(stage = Stage.Answered) else s
        }

        VoiceEvent.StopTapped -> {
            // Cancelled: keep the text so the user can generate again or edit it.
            if (s.stage == Stage.Generating) s.copy(stage = Stage.Transcribed) else s
        }

        is VoiceEvent.GenerationFailed -> {
            if (s.stage == Stage.Generating) s.copy(stage = Stage.Transcribed, error = e.message) else s
        }

        VoiceEvent.ResetDone -> {
            // Mic and text box are ready for the next question; the answer stays on screen.
            if (s.stage == Stage.Answered) s.copy(stage = Stage.Idle, text = "", partial = "") else s
        }
    }
}
