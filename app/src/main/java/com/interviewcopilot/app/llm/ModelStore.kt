package com.interviewcopilot.app.llm

import android.content.Context
import java.io.File

/** Where model files live on disk. Final files exist only after the checksum matched. */
class ModelStore(ctx: Context) {
    private val base = ctx.applicationContext.filesDir

    private val llmDir get() = File(base, "models").apply { mkdirs() }
    private val sttDir get() = File(base, "whisper").apply { mkdirs() }

    fun file(m: LocalModel) = File(llmDir, m.file)
    fun file(m: SpeechModel) = File(sttDir, m.file)

    fun isDownloaded(m: LocalModel) = file(m).let { it.exists() && it.length() > 0 }
    fun isDownloaded(m: SpeechModel) = file(m).let { it.exists() && it.length() > 0 }

    fun spec(m: LocalModel) = DownloadSpec("llm:${m.id}", m.url, file(m), m.sizeBytes, m.sha256)
    fun spec(m: SpeechModel) = DownloadSpec("stt:${m.id}", m.url, file(m), m.sizeBytes, m.sha256)

    fun wipe() {
        llmDir.deleteRecursively()
        sttDir.deleteRecursively()
    }
}
