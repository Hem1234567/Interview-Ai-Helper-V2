package com.interviewcopilot.app.llm

import com.interviewcopilot.app.core.Checksum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** Everything the UI needs to draw a download row. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val done: Long, val total: Long, val bytesPerSec: Long) : DownloadState
    data class Paused(val done: Long, val total: Long) : DownloadState
    data class Verifying(val done: Long, val total: Long) : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String, val done: Long) : DownloadState
}

/** One file to fetch. [sha256] pins it; null means "verify against the host-published SHA-256". */
data class DownloadSpec(
    val id: String,
    val url: String,
    val dest: File,
    val expectedBytes: Long,
    val sha256: String?,
)

/**
 * Resumable, verified downloads. Partial data lives in `<dest>.part`; pause keeps it, resume sends an
 * HTTP Range request, and the file only appears under its final name after the size and SHA-256 match.
 */
class ModelDownloader(private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val probeClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()
    private val jobs = HashMap<String, Job>()

    private fun set(id: String, s: DownloadState) = _states.update { it + (id to s) }

    fun stateOf(id: String): DownloadState = _states.value[id] ?: DownloadState.Idle

    /** Show an already-complete file as Done and a leftover `.part` as Paused after an app restart. */
    fun restore(spec: DownloadSpec) {
        if (_states.value.containsKey(spec.id)) return
        val part = partFile(spec.dest)
        when {
            spec.dest.exists() -> set(spec.id, DownloadState.Done)
            part.exists() && part.length() > 0 -> set(spec.id, DownloadState.Paused(part.length(), spec.expectedBytes))
        }
    }

    fun start(spec: DownloadSpec) {
        if (jobs[spec.id]?.isActive == true) return
        jobs[spec.id] = scope.launch { run(spec) }
    }

    fun pause(id: String) {
        val job = jobs[id] ?: return
        val cur = stateOf(id)
        job.cancel(CancellationException("paused"))
        if (cur is DownloadState.Downloading) set(id, DownloadState.Paused(cur.done, cur.total))
    }

    fun cancelAndDelete(spec: DownloadSpec) {
        jobs[spec.id]?.cancel(CancellationException("deleted"))
        jobs.remove(spec.id)
        partFile(spec.dest).delete()
        spec.dest.delete()
        _states.update { it - spec.id }
    }

    private fun partFile(dest: File) = File(dest.parentFile, dest.name + ".part")

    private suspend fun run(spec: DownloadSpec) {
        val part = partFile(spec.dest)
        spec.dest.parentFile?.mkdirs()
        try {
            val free = spec.dest.parentFile?.usableSpace ?: Long.MAX_VALUE
            val have = if (part.exists()) part.length() else 0L
            if (free < (spec.expectedBytes - have) + 64L * 1024 * 1024) {
                set(spec.id, DownloadState.Failed("Not enough free storage.", have))
                return
            }
            val attested = probeAttestedHash(spec.url)
            val expected = spec.sha256 ?: attested
            if (spec.sha256 != null && attested != null && attested != spec.sha256) {
                set(spec.id, DownloadState.Failed("The file on the server changed since this app was built.", have))
                return
            }
            download(spec, part)
            verify(spec, part, expected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContextActive()) throw CancellationException("stopped")
            set(spec.id, DownloadState.Failed(e.message ?: "Download failed.", if (part.exists()) part.length() else 0L))
        }
    }

    /** Ask Hugging Face (without following the redirect) for the SHA-256 it stores for the file. */
    private fun probeAttestedHash(url: String): String? = try {
        val req = Request.Builder().url(url).head().build()
        probeClient.newCall(req).execute().use { r ->
            Checksum.parseEtag(r.header("X-Linked-ETag")) ?: Checksum.parseEtag(r.header("ETag"))
        }
    } catch (e: IOException) {
        null
    }

    private suspend fun download(spec: DownloadSpec, part: File) {
        var offset = if (part.exists()) part.length() else 0L
        val builder = Request.Builder().url(spec.url)
        if (offset > 0) builder.header("Range", "bytes=$offset-")
        val call = client.newCall(builder.build())
        kotlin.coroutines.coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        call.execute().use { resp ->
            if (resp.code == 416) { // we already hold the whole file
                return
            }
            if (!resp.isSuccessful) throw IOException("Server answered ${resp.code}.")
            val body = resp.body ?: throw IOException("Empty response.")
            val resumed = resp.code == 206
            if (!resumed && offset > 0) {
                part.delete() // server ignored Range: start over
                offset = 0
            }
            val total = if (resumed) {
                Checksum.totalFromContentRange(resp.header("Content-Range")) ?: (offset + body.contentLength())
            } else {
                body.contentLength().takeIf { it > 0 } ?: spec.expectedBytes
            }
            var done = offset
            var lastTick = System.nanoTime()
            var lastBytes = done
            var speed = 0L
            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(offset)
                val src = body.source()
                val buf = ByteArray(1 shl 17)
                while (true) {
                    if (!currentCoroutineContextActive()) {
                        call.cancel()
                        throw CancellationException("stopped")
                    }
                    val n = src.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    done += n
                    val now = System.nanoTime()
                    if (now - lastTick > 400_000_000L) {
                        speed = ((done - lastBytes) * 1_000_000_000L) / (now - lastTick)
                        lastTick = now
                        lastBytes = done
                        set(spec.id, DownloadState.Downloading(done, total, speed))
                    }
                }
            }
            set(spec.id, DownloadState.Downloading(done, total, speed))
        }
    }

    private suspend fun currentCoroutineContextActive(): Boolean =
        kotlin.coroutines.coroutineContext[Job]?.isActive ?: true

    private fun verify(spec: DownloadSpec, part: File, expected: String?) {
        val size = part.length()
        if (spec.expectedBytes > 0 && size < spec.expectedBytes * 9 / 10) {
            set(spec.id, DownloadState.Failed("Download ended early ($size bytes).", size))
            return
        }
        set(spec.id, DownloadState.Verifying(0, size))
        val actual = Checksum.sha256(part) { set(spec.id, DownloadState.Verifying(it, size)) }
        if (expected != null && actual != expected) {
            part.delete()
            set(spec.id, DownloadState.Failed("Checksum mismatch: the file is corrupt. Please download again.", 0))
            return
        }
        if (!part.renameTo(spec.dest)) {
            part.copyTo(spec.dest, overwrite = true)
            part.delete()
        }
        set(spec.id, DownloadState.Done)
    }
}
