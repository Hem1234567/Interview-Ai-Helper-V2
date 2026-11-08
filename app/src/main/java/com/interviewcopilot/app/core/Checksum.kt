package com.interviewcopilot.app.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 helpers used to verify downloaded model files. */
object Checksum {
    private val HEX = Regex("^[0-9a-f]{64}$")

    fun isValidSha256(s: String?): Boolean = s != null && HEX.matches(s)

    fun sha256(stream: InputStream, onProgress: ((Long) -> Unit)? = null): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 16)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
            total += n
            onProgress?.invoke(total)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File, onProgress: ((Long) -> Unit)? = null): String =
        file.inputStream().buffered().use { sha256(it, onProgress) }

    /** Hugging Face sends the LFS SHA-256 as an ETag, sometimes quoted or weak ("W/"). */
    fun parseEtag(raw: String?): String? {
        val t = raw?.trim()?.removePrefix("W/")?.trim('"')?.lowercase()
        return if (isValidSha256(t)) t else null
    }

    /** Total size from a `Content-Range: bytes 100-999/1000` header, or null. */
    fun totalFromContentRange(header: String?): Long? =
        header?.substringAfterLast('/')?.trim()?.toLongOrNull()
}
