package com.interviewcopilot.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class QA(
    val id: String,
    val session: String,
    val question: String,
    val answer: String,
    val time: Long,
    val mode: String,
    val favorite: Boolean = false,
)

data class JobProfile(
    val id: String,
    val title: String,
    val description: String,
    val keywords: String = "",
    val active: Boolean = false,
)

object Defaults {
    val map: Map<String, String> = mapOf(
        "provider" to "Local",
        "fallback" to "true",
        "orKey" to "",
        "orModel" to "google/gemini-2.0-flash-001",
        "geminiKey" to "",
        "geminiModel" to "gemini-2.0-flash",
        "localModelId" to "",
        "whisperModelId" to "whisper-base",
        "sttEngine" to "Auto",
        "sttLang" to "auto",
        "memory" to "false",
        "theme" to "System",
        "style" to "Concise",
        "speed" to "Fast",
        "mode" to "Interview",
        "resume" to "",
        "vocab" to "",
        "pscores" to "",
    )
}

class Store(private val ctx: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx,
            "secure_prefs",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("prefs_fallback", Context.MODE_PRIVATE)
    }

    private val historyFile get() = File(ctx.filesDir, "history.json")
    private val jobsFile get() = File(ctx.filesDir, "jobs.json")

    fun all(): Map<String, String> {
        val out = HashMap<String, String>()
        for ((k, v) in prefs.all) {
            if (v is String) out[k] = v
        }
        return out
    }

    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun loadHistory(): List<QA> {
        return try {
            if (!historyFile.exists()) return emptyList()
            val arr = JSONArray(historyFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                QA(
                    id = o.getString("id"),
                    session = o.getString("session"),
                    question = o.getString("question"),
                    answer = o.getString("answer"),
                    time = o.getLong("time"),
                    mode = o.optString("mode", "Interview"),
                    favorite = o.optBoolean("favorite", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(list: List<QA>) {
        try {
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("session", it.session)
                        .put("question", it.question)
                        .put("answer", it.answer)
                        .put("time", it.time)
                        .put("mode", it.mode)
                        .put("favorite", it.favorite),
                )
            }
            historyFile.writeText(arr.toString())
        } catch (ignored: Exception) {
        }
    }

    fun loadJobs(): List<JobProfile> {
        return try {
            if (!jobsFile.exists()) return emptyList()
            val arr = JSONArray(jobsFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                JobProfile(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    description = o.getString("description"),
                    keywords = o.optString("keywords", ""),
                    active = o.optBoolean("active", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveJobs(list: List<JobProfile>) {
        try {
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("description", it.description)
                        .put("keywords", it.keywords)
                        .put("active", it.active),
                )
            }
            jobsFile.writeText(arr.toString())
        } catch (ignored: Exception) {
        }
    }

    fun wipe() {
        prefs.edit().clear().apply()
        historyFile.delete()
        jobsFile.delete()
    }
}
