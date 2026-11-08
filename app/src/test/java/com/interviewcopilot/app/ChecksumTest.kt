package com.interviewcopilot.app

import com.interviewcopilot.app.core.Checksum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksumTest {
    @Test
    fun sha256OfKnownString() {
        val h = Checksum.sha256("abc".byteInputStream())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h)
    }

    @Test
    fun detectsCorruption() {
        val good = Checksum.sha256("model-bytes".byteInputStream())
        val bad = Checksum.sha256("model-bytez".byteInputStream())
        assertFalse(good == bad)
    }

    @Test
    fun parsesHuggingFaceEtag() {
        val sha = "bd258782e35f7f458f8aced1adc053e6e92e89bc735ba3be89d38a06121dc517"
        assertEquals(sha, Checksum.parseEtag("\"$sha\""))
        assertEquals(sha, Checksum.parseEtag("W/\"$sha\""))
        assertNull(Checksum.parseEtag("\"not-a-hash\""))
        assertNull(Checksum.parseEtag(null))
        assertTrue(Checksum.isValidSha256(sha))
    }

    @Test
    fun parsesContentRange() {
        assertEquals(1000L, Checksum.totalFromContentRange("bytes 100-999/1000"))
        assertNull(Checksum.totalFromContentRange(null))
    }
}
