package com.interviewcopilot.app.llm

/** Receives streamed UTF-8 text from native code. Return false to stop generation. */
fun interface TokenSink {
    fun onToken(utf8: ByteArray): Boolean
}

/** JNI surface of llama.cpp (see app/src/main/cpp/native_llm.cpp). */
object NativeLlm {
    init {
        System.loadLibrary("copilot_native")
    }

    external fun nativeInit(libDir: String)
    external fun nativeSystemInfo(): String
    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): String
    external fun nativeUnload()
    external fun nativeCancel()
    external fun nativeIsLoaded(): Boolean
    external fun nativeLastStats(): String
    external fun nativeGenerate(
        messagesJson: ByteArray,
        maxTokens: Int,
        temperature: Float,
        enableThinking: Boolean,
        sink: TokenSink,
    ): Int
}
