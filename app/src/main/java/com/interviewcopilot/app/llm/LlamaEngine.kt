package com.interviewcopilot.app.llm

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

sealed interface EngineState {
    data object Unloaded : EngineState
    data class Loading(val modelId: String) : EngineState
    data class Ready(val modelId: String) : EngineState
    data class Error(val message: String) : EngineState
}

/** Timing of the last answer, for the "first words in N ms" label. */
data class GenStats(
    val promptTokens: Int,
    val reusedTokens: Int,
    val firstWordMs: Long,
    val generatedTokens: Int,
    val generateMs: Long,
) {
    val tokensPerSecond: Double get() = if (generateMs <= 0) 0.0 else generatedTokens * 1000.0 / generateMs
}

/**
 * Keeps one model loaded (warm start) and streams answers from it. All native calls run on a single
 * dedicated thread, so inference never touches the UI thread and calls can never overlap.
 */
class LlamaEngine(private val ctx: Context) {
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "llama-engine").apply { priority = Thread.NORM_PRIORITY + 1 } }
        .asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    private val _state = MutableStateFlow<EngineState>(EngineState.Unloaded)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile
    var lastStats: GenStats? = null
        private set

    private var backendReady = false
    private var loadedPath: String? = null

    val loadedModelId: String? get() = (state.value as? EngineState.Ready)?.modelId

    private fun threads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores / 2).coerceIn(2, 6) // big cores only; hammering efficiency cores slows decoding
    }

    private fun ensureBackend() {
        if (backendReady) return
        NativeLlm.nativeInit(ctx.applicationInfo.nativeLibraryDir)
        backendReady = true
    }

    /** Load [model] in the background. Safe to call repeatedly; does nothing if it is already loaded. */
    fun warmUp(model: LocalModel, file: File, nCtx: Int = CONTEXT_TOKENS) {
        scope.launch { load(model, file, nCtx) }
    }

    suspend fun load(model: LocalModel, file: File, nCtx: Int = CONTEXT_TOKENS): Boolean = withContext(dispatcher) {
        if (loadedPath == file.absolutePath && NativeLlm.nativeIsLoaded()) return@withContext true
        if (!file.exists()) {
            _state.value = EngineState.Error("Model file is missing. Download it again.")
            return@withContext false
        }
        _state.value = EngineState.Loading(model.id)
        try {
            ensureBackend()
            val err = NativeLlm.nativeLoad(file.absolutePath, nCtx, threads())
            if (err.isNotEmpty()) {
                loadedPath = null
                _state.value = EngineState.Error(err)
                false
            } else {
                loadedPath = file.absolutePath
                _state.value = EngineState.Ready(model.id)
                true
            }
        } catch (e: LinkageError) {
            _state.value = EngineState.Error("Native engine is not available on this device (${e.message}).")
            false
        }
    }

    fun unload() {
        NativeLlm.nativeCancel()
        scope.launch {
            if (backendReady) NativeLlm.nativeUnload()
            loadedPath = null
            _state.value = EngineState.Unloaded
        }
    }

    fun cancel() {
        if (backendReady) NativeLlm.nativeCancel()
    }

    /** Stream the answer for [system] + [user]. Cancelling the collector stops generation immediately. */
    fun stream(system: String, user: String, maxTokens: Int, temperature: Float = 0.4f, thinking: Boolean = false): Flow<String> =
        callbackFlow {
            val json = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user))
                .toString()
                .toByteArray(Charsets.UTF_8)
            val job = launch(dispatcher) {
                val sink = TokenSink { bytes -> trySend(String(bytes, Charsets.UTF_8)).isSuccess && isActive }
                val rc = NativeLlm.nativeGenerate(json, maxTokens, temperature, thinking, sink)
                readStats()
                if (rc < 0) close(IOException(describe(rc))) else close()
            }
            awaitClose {
                NativeLlm.nativeCancel()
                job.cancel()
            }
        }

    private fun readStats() {
        val p = NativeLlm.nativeLastStats().split(",").mapNotNull { it.toLongOrNull() }
        if (p.size >= 6) lastStats = GenStats(p[0].toInt(), p[1].toInt(), p[3], p[4].toInt(), p[5])
    }

    private fun describe(rc: Int): String = when (rc) {
        -1 -> "No model is loaded."
        -3 -> "The model's chat template could not be applied."
        -5 -> "The question is too long for the model's context window."
        else -> "The model stopped unexpectedly (code $rc)."
    }

    companion object {
        /** Limited context window keeps memory low and the first word fast. */
        const val CONTEXT_TOKENS = 2048
    }
}
