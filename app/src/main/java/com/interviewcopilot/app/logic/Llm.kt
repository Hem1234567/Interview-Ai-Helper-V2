package com.interviewcopilot.app.logic

import com.interviewcopilot.app.llm.LlamaEngine
import com.interviewcopilot.app.llm.LocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface LlmProvider {
    val name: String
    val firstTokenTimeoutMs: Long
    fun stream(system: String, user: String, maxTokens: Int): Flow<String>
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

private fun JSONObject.str(key: String): String =
    if (isNull(key)) "" else optString(key, "")

class OpenRouterProvider(private val key: String, private val model: String) : LlmProvider {
    override val name = "OpenRouter"
    override val firstTokenTimeoutMs = 6000L

    override fun stream(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("max_tokens", maxTokens)
            .put("temperature", 0.4)
            .put("messages", messages)
        val req = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("HTTP-Referer", "https://interviewcopilot.app")
            .addHeader("X-Title", "Interview Copilot")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = Http.client.newCall(req)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string()?.take(200) ?: ""
                    throw IOException("OpenRouter error ${resp.code}: $msg")
                }
                val src = resp.body!!.source()
                while (!src.exhausted()) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val piece = try {
                        val choice = JSONObject(data).getJSONArray("choices").getJSONObject(0)
                        choice.getJSONObject("delta").str("content")
                    } catch (e: Exception) {
                        ""
                    }
                    if (piece.isNotEmpty()) emit(piece)
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}

class GeminiProvider(private val key: String, private val model: String) : LlmProvider {
    override val name = "Gemini"
    override val firstTokenTimeoutMs = 6000L

    override fun stream(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))),
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", user))),
                ),
            )
            .put(
                "generationConfig",
                JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.4),
            )
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$key"
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = Http.client.newCall(req)
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = resp.body?.string()?.take(200) ?: ""
                    throw IOException("Gemini error ${resp.code}: $msg")
                }
                val src = resp.body!!.source()
                while (!src.exhausted()) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    val piece = try {
                        val parts = JSONObject(data).getJSONArray("candidates").getJSONObject(0)
                            .getJSONObject("content").getJSONArray("parts")
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).str("text"))
                        sb.toString()
                    } catch (e: Exception) {
                        ""
                    }
                    if (piece.isNotEmpty()) emit(piece)
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}

/** On-device answer engine (llama.cpp). Loads the model on first use if the warm-up has not finished. */
class LocalProvider(
    private val engine: LlamaEngine,
    private val model: LocalModel,
    private val file: java.io.File,
) : LlmProvider {
    override val name = "Local"
    override val firstTokenTimeoutMs = 120_000L

    override fun stream(system: String, user: String, maxTokens: Int): Flow<String> = flow {
        if (!engine.load(model, file)) throw IOException("The on-device model could not be loaded.")
        emitAll(engine.stream(system, user, maxTokens, thinking = false))
    }
}
