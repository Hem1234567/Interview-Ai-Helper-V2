package com.interviewcopilot.app.voice

import com.interviewcopilot.app.llm.NativeLlm

/** JNI surface of whisper.cpp (see app/src/main/cpp/native_whisper.cpp). */
object NativeWhisper {
    init {
        // Same library as the LLM; loading it twice is harmless.
        System.loadLibrary("copilot_native")
    }

    /** Make sure ggml CPU backends are registered once for the whole process. */
    fun ensureBackends(libDir: String) {
        NativeLlm.nativeInit(libDir)
    }

    external fun nativeLoad(path: String): Boolean
    external fun nativeFree()
    external fun nativeCancel()
    external fun nativeIsLoaded(): Boolean
    external fun nativeLastLanguage(): String
    external fun nativeTranscribe(
        pcm: FloatArray,
        language: String,
        prompt: ByteArray,
        threads: Int,
        accurate: Boolean,
    ): ByteArray
}
