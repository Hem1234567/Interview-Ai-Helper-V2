package com.interviewcopilot.app.voice

/** Callbacks from a speech session. All are invoked on the main thread. */
interface SttListener {
    fun onLevel(level: Float)
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(message: String)
}

/** One push-to-talk capture. [stop] ends capture and still delivers a final result. */
interface SttSession {
    fun start()
    fun stop()
    fun cancel()
}

/** "auto" lets Whisper detect the language. */
data class SttLanguage(val code: String, val label: String)

object SttLanguages {
    val all = listOf(
        SttLanguage("auto", "Auto-detect"),
        SttLanguage("en", "English"),
        SttLanguage("hi", "Hindi"),
        SttLanguage("ta", "Tamil"),
        SttLanguage("te", "Telugu"),
        SttLanguage("es", "Spanish"),
        SttLanguage("fr", "French"),
        SttLanguage("de", "German"),
        SttLanguage("pt", "Portuguese"),
        SttLanguage("it", "Italian"),
        SttLanguage("ja", "Japanese"),
        SttLanguage("ko", "Korean"),
        SttLanguage("zh", "Chinese"),
        SttLanguage("ar", "Arabic"),
    )

    fun label(code: String) = all.firstOrNull { it.code == code }?.label ?: code
}
