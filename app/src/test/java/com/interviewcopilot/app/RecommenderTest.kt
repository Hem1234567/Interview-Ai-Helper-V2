package com.interviewcopilot.app

import com.interviewcopilot.app.llm.DeviceSpecs
import com.interviewcopilot.app.llm.Fit
import com.interviewcopilot.app.llm.ModelCatalog
import com.interviewcopilot.app.llm.Recommender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private fun phone(ramMb: Long, cores: Int = 8, dot: Boolean = true, i8mm: Boolean = false) = DeviceSpecs(
        totalRamMb = ramMb,
        availRamMb = ramMb / 2,
        freeStorageMb = 50_000,
        cpuCores = cores,
        abi = "arm64-v8a",
        socName = "test",
        osName = "Android 15",
        sdkInt = 35,
        hasDotProd = dot,
        hasI8mm = i8mm,
        vulkanLevel = 1,
        hasNpuHint = true,
    )

    @Test
    fun catalogHasExactlyTenUniqueModels() {
        assertEquals(10, ModelCatalog.models.size)
        assertEquals(10, ModelCatalog.models.map { it.id }.toSet().size)
        assertEquals(10, ModelCatalog.models.map { it.file }.toSet().size)
    }

    @Test
    fun everyModelIsQuantizedGgufOnHuggingFace() {
        ModelCatalog.models.forEach { m ->
            assertTrue(m.file, m.file.endsWith(".gguf"))
            assertTrue(m.url, m.url.startsWith("https://huggingface.co/") && m.url.contains("/resolve/main/"))
            assertTrue(m.id, m.sizeBytes > 100_000_000L && m.ramNeededMb > m.sizeMb)
            assertTrue(m.id, m.sha256 == null || Regex("^[0-9a-f]{64}$").matches(m.sha256!!))
            // Q4_K_M everywhere except the one model that is only published as MXFP4.
            assertTrue(m.id, m.file.contains("Q4_K_M") || m.id == "gpt-oss-20b")
        }
    }

    @Test
    fun speechCatalogUsesWhisperGgml() {
        assertTrue(ModelCatalog.speech.size >= 3)
        ModelCatalog.speech.forEach { assertTrue(it.url, it.url.endsWith(".bin") && it.url.contains("whisper.cpp")) }
        assertNotNull(ModelCatalog.findSpeech(ModelCatalog.DEFAULT_SPEECH_ID))
    }

    @Test
    fun lowEndPhoneGetsSmallModelsAndBigOnesAreTooLarge() {
        val a = Recommender.assess(phone(3_000, cores = 8, dot = false))
        val byId = a.associateBy { it.model.id }
        assertEquals(Fit.TooLarge, byId.getValue("gpt-oss-20b").fit)
        assertEquals(Fit.TooLarge, byId.getValue("gemma-4-e4b").fit)
        val recommended = a.filter { it.recommended }
        assertTrue(recommended.isNotEmpty())
        recommended.forEach { assertTrue(it.model.id, it.model.sizeBytes < 2_100_000_000L) }
    }

    @Test
    fun flagshipPhoneRecommendsBiggerModelsAndNeverGptOssBelow16Gb() {
        val a = Recommender.assess(phone(12_288, i8mm = true))
        val rec = a.filter { it.recommended }.map { it.model }
        assertEquals(2, rec.size)
        assertTrue(rec.maxOf { it.quality } >= 8)
        assertEquals(Fit.TooLarge, a.first { it.model.id == "gpt-oss-20b" }.fit)
    }

    @Test
    fun gptOssRunnableOnlyWithVeryLargeRam() {
        val a = Recommender.assess(phone(24_000, i8mm = true)).first { it.model.id == "gpt-oss-20b" }
        assertTrue(a.runnable)
    }

    @Test
    fun speedFallsAsModelsGrow() {
        val d = phone(8_192)
        val small = Recommender.tokensPerSecond(ModelCatalog.find("qwen3.5-0.8b")!!, d)
        val big = Recommender.tokensPerSecond(ModelCatalog.find("gemma-4-e4b")!!, d)
        assertTrue(small > big * 3)
    }

    @Test
    fun fallbackSwitchesAwayFromModelThatIsTooLarge() {
        val d = phone(3_000, dot = false)
        val pick = Recommender.fallbackFor("gemma-4-e4b", setOf("gemma-4-e4b", "qwen3.5-0.8b", "llama3.2-1b"), d)
        assertNotNull(pick)
        assertTrue(pick!!.id == "qwen3.5-0.8b" || pick.id == "llama3.2-1b")
        assertNull(Recommender.fallbackFor("qwen3.5-0.8b", setOf("qwen3.5-0.8b"), d))
    }

    @Test
    fun noStorageIsReported() {
        val d = phone(8_192).copy(freeStorageMb = 500)
        val a = Recommender.assess(d).map { Recommender.withStorage(it, d.freeStorageMb) }
        assertEquals(Fit.NoStorage, a.first { it.model.id == "qwen3.5-2b" }.fit)
    }

    @Test
    fun whisperSuggestionScalesWithPhone() {
        assertEquals("whisper-tiny", Recommender.speechModelFor(phone(2_000)).id)
        assertEquals("whisper-base", Recommender.speechModelFor(phone(4_096)).id)
        assertEquals("whisper-small", Recommender.speechModelFor(phone(12_288, i8mm = true)).id)
    }
}
