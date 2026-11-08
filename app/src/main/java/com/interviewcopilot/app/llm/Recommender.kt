package com.interviewcopilot.app.llm

import kotlin.math.max
import kotlin.math.min

/** What we know about the phone. Built from Android APIs in DeviceProbe; plain data here so it can be tested. */
data class DeviceSpecs(
    val totalRamMb: Long,
    val availRamMb: Long,
    val freeStorageMb: Long,
    val cpuCores: Int,
    val abi: String,
    val socName: String,
    val osName: String,
    val sdkInt: Int,
    val hasDotProd: Boolean,
    val hasI8mm: Boolean,
    val vulkanLevel: Int,
    val hasNpuHint: Boolean,
) {
    val totalRamGb: Double get() = totalRamMb / 1024.0

    /** Memory bandwidth class drives token speed more than anything else on phones. */
    val effectiveBandwidthGbS: Double
        get() {
            val base = when {
                totalRamMb >= 14_000 -> 22.0
                totalRamMb >= 10_000 -> 18.0
                totalRamMb >= 7_000 -> 13.0
                totalRamMb >= 5_000 -> 9.0
                else -> 5.5
            }
            val coreBoost = if (cpuCores >= 8) 1.0 else 0.8
            val isaBoost = when {
                hasI8mm -> 1.25
                hasDotProd -> 1.0
                else -> 0.6
            }
            return base * coreBoost * isaBoost
        }
}

enum class Fit {
    /** Comfortable. */
    Good,

    /** Will run but other apps may be closed by Android. */
    Tight,

    /** Needs more RAM than this phone offers. */
    TooLarge,

    /** Not enough free storage to download it. */
    NoStorage,
}

data class ModelAssessment(
    val model: LocalModel,
    val fit: Fit,
    /** Estimated generation speed, tokens per second. */
    val tokensPerSecond: Double,
    /** Estimated time until the first word of an answer. */
    val firstWordSeconds: Double,
    val recommended: Boolean,
) {
    val runnable: Boolean get() = fit == Fit.Good || fit == Fit.Tight
}

object Recommender {
    /** Android keeps roughly a third of RAM for the system and other apps. */
    private const val USABLE_FRACTION = 0.70
    private const val COMFORT_FRACTION = 0.52
    private const val MIN_TPS_FOR_RECOMMENDATION = 6.0
    private const val TYPICAL_NEW_PROMPT_TOKENS = 120.0

    fun fitFor(model: LocalModel, d: DeviceSpecs): Fit {
        val usable = d.totalRamMb * USABLE_FRACTION
        val comfy = d.totalRamMb * COMFORT_FRACTION
        return when {
            model.ramNeededMb > usable -> Fit.TooLarge
            model.ramNeededMb > comfy -> Fit.Tight
            else -> Fit.Good
        }
    }

    fun tokensPerSecond(model: LocalModel, d: DeviceSpecs): Double {
        // Generation is memory-bound: every token reads the active weights once.
        val activeGb = if (model.id == "gpt-oss-20b") 2.6 else model.sizeGb
        return min(80.0, max(0.5, d.effectiveBandwidthGbS / activeGb * 0.8))
    }

    fun firstWordSeconds(model: LocalModel, d: DeviceSpecs): Double {
        val tps = tokensPerSecond(model, d)
        // Prompt evaluation is ~8x faster than generation. The system prompt is cached, so only the
        // new question has to be evaluated. Reasoning models think for a while first.
        val prefill = TYPICAL_NEW_PROMPT_TOKENS / (tps * 8.0)
        val thinking = if (model.kind == ModelKind.Reasoning) 60.0 / tps else 0.0
        return prefill + 0.25 + thinking
    }

    /**
     * Assess every model for this phone. The "recommended" badge goes to the two best models that fit
     * comfortably and answer fast enough (>= 6 tokens/s); if none qualify, to the largest that runs.
     */
    fun assess(d: DeviceSpecs, models: List<LocalModel> = ModelCatalog.models): List<ModelAssessment> {
        val base = models.map { m ->
            val fit = fitFor(m, d)
            val tps = tokensPerSecond(m, d)
            ModelAssessment(m, fit, tps, firstWordSeconds(m, d), recommended = false)
        }
        val candidates = base
            .filter { it.fit == Fit.Good && it.tokensPerSecond >= MIN_TPS_FOR_RECOMMENDATION && it.model.kind == ModelKind.Chat }
            .sortedByDescending { it.model.quality }
            .take(2)
            .map { it.model.id }
            .toSet()
        val chosen = if (candidates.isNotEmpty()) {
            candidates
        } else {
            base.filter { it.runnable }.maxByOrNull { it.model.quality }?.let { setOf(it.model.id) } ?: emptySet()
        }
        return base.map { it.copy(recommended = it.model.id in chosen) }
    }

    /** Storage check applied on top of the RAM fit when deciding whether a download can start. */
    fun withStorage(a: ModelAssessment, freeStorageMb: Long): ModelAssessment =
        if (freeStorageMb < a.model.sizeMb * 1.1 && a.fit != Fit.TooLarge) a.copy(fit = Fit.NoStorage) else a

    /**
     * Low-end protection: if the selected model is too heavy for this phone, pick the best already
     * downloaded model that fits. Returns null when the current selection is fine or nothing else exists.
     */
    fun fallbackFor(selectedId: String?, downloadedIds: Set<String>, d: DeviceSpecs): LocalModel? {
        val selected = selectedId?.let { ModelCatalog.find(it) }
        if (selected != null && fitFor(selected, d) != Fit.TooLarge) return null
        return downloadedIds
            .mapNotNull { ModelCatalog.find(it) }
            .filter { fitFor(it, d) != Fit.TooLarge }
            .maxByOrNull { it.quality }
    }

    /** Whisper model suggestion for this phone. */
    fun speechModelFor(d: DeviceSpecs): SpeechModel = when {
        d.totalRamMb >= 8_000 && d.effectiveBandwidthGbS >= 14 -> ModelCatalog.findSpeech("whisper-small")!!
        d.totalRamMb >= 3_000 -> ModelCatalog.findSpeech(ModelCatalog.DEFAULT_SPEECH_ID)!!
        else -> ModelCatalog.findSpeech("whisper-tiny")!!
    }
}
