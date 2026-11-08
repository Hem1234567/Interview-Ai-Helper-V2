package com.interviewcopilot.app.llm

/** How a model reasons. Reasoning models think first, so their first visible word comes later. */
enum class ModelKind { Chat, Reasoning }

/**
 * One downloadable on-device model. Files are quantized GGUF (Q4_K_M unless noted) hosted on
 * Hugging Face and downloaded at run time; nothing is bundled in the APK.
 *
 * [sha256] pins the file when known. When it is null the downloader verifies against the SHA-256
 * that Hugging Face publishes for the file (see ModelDownloader), so every download is checked.
 */
data class LocalModel(
    val id: String,
    val family: String,
    val name: String,
    val params: String,
    val repo: String,
    val file: String,
    val sizeBytes: Long,
    /** RAM the model needs while answering (weights + KV cache at a 2048 context + runtime). */
    val ramNeededMb: Int,
    /** 1..10, relative answer quality. */
    val quality: Int,
    val kind: ModelKind,
    val license: String,
    val note: String,
    val sha256: String? = null,
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$file"
    val sizeMb: Int get() = (sizeBytes / (1024 * 1024)).toInt()
    val sizeGb: Double get() = sizeBytes / 1_000_000_000.0
}

/** Whisper speech-to-text model (ggml format), used for on-device transcription. */
data class SpeechModel(
    val id: String,
    val name: String,
    val file: String,
    val sizeBytes: Long,
    val ramNeededMb: Int,
    /** 1..10 */
    val accuracy: Int,
    val note: String,
    val sha256: String? = null,
) {
    val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
    val sizeMb: Int get() = (sizeBytes / (1024 * 1024)).toInt()
}

object ModelCatalog {
    /** Exactly ten models, ordered from smallest to largest. */
    val models: List<LocalModel> = listOf(
        LocalModel(
            id = "qwen3.5-0.8b",
            family = "Qwen (Alibaba)",
            name = "Qwen3.5 0.8B",
            params = "0.8B",
            repo = "unsloth/Qwen3.5-0.8B-GGUF",
            file = "Qwen3.5-0.8B-Q4_K_M.gguf",
            sizeBytes = 532_517_120L,
            ramNeededMb = 900,
            quality = 4,
            kind = ModelKind.Chat,
            license = "Apache-2.0",
            note = "Tiny and instant. Best for low-end phones.",
        ),
        LocalModel(
            id = "llama3.2-1b",
            family = "Meta",
            name = "Llama 3.2 1B Instruct",
            params = "1B",
            repo = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            file = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytes = 807_694_464L,
            ramNeededMb = 1_300,
            quality = 4,
            kind = ModelKind.Chat,
            license = "Llama 3.2 Community License",
            note = "Fast and fluent English.",
        ),
        LocalModel(
            id = "deepseek-r1-1.5b",
            family = "DeepSeek",
            name = "DeepSeek R1 Distill Qwen 1.5B",
            params = "1.5B",
            repo = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
            file = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            sizeBytes = 1_117_320_736L,
            ramNeededMb = 1_600,
            quality = 5,
            kind = ModelKind.Reasoning,
            license = "MIT",
            note = "Thinks before answering, so the first word comes later.",
        ),
        LocalModel(
            id = "qwen3.5-2b",
            family = "Qwen (Alibaba)",
            name = "Qwen3.5 2B",
            params = "2B",
            repo = "unsloth/Qwen3.5-2B-GGUF",
            file = "Qwen3.5-2B-Q4_K_M.gguf",
            sizeBytes = 1_280_000_000L,
            ramNeededMb = 1_800,
            quality = 6,
            kind = ModelKind.Chat,
            license = "Apache-2.0",
            note = "Great balance of speed and quality for most phones.",
        ),
        LocalModel(
            id = "llama3.2-3b",
            family = "Meta",
            name = "Llama 3.2 3B Instruct",
            params = "3B",
            repo = "bartowski/Llama-3.2-3B-Instruct-GGUF",
            file = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_019_377_696L,
            ramNeededMb = 2_700,
            quality = 6,
            kind = ModelKind.Chat,
            license = "Llama 3.2 Community License",
            note = "Natural spoken-style answers.",
        ),
        LocalModel(
            id = "phi-4-mini",
            family = "Microsoft",
            name = "Phi-4 mini Instruct",
            params = "3.8B",
            repo = "bartowski/microsoft_Phi-4-mini-instruct-GGUF",
            file = "microsoft_Phi-4-mini-instruct-Q4_K_M.gguf",
            sizeBytes = 2_491_874_688L,
            ramNeededMb = 3_300,
            quality = 7,
            kind = ModelKind.Chat,
            license = "MIT",
            note = "Strong at logic, maths and code.",
        ),
        LocalModel(
            id = "qwen3.5-4b",
            family = "Qwen (Alibaba)",
            name = "Qwen3.5 4B",
            params = "4B",
            repo = "unsloth/Qwen3.5-4B-GGUF",
            file = "Qwen3.5-4B-Q4_K_M.gguf",
            sizeBytes = 2_740_000_000L,
            ramNeededMb = 3_500,
            quality = 8,
            kind = ModelKind.Chat,
            license = "Apache-2.0",
            note = "Best all-round answers that still fit a mid-range phone.",
        ),
        LocalModel(
            id = "gemma-4-e2b",
            family = "Google",
            name = "Gemma 4 E2B Instruct",
            params = "2B effective",
            repo = "unsloth/gemma-4-E2B-it-GGUF",
            file = "gemma-4-E2B-it-Q4_K_M.gguf",
            sizeBytes = 3_110_000_000L,
            ramNeededMb = 4_000,
            quality = 7,
            kind = ModelKind.Chat,
            license = "Apache-2.0",
            note = "Google's edge model; clear, well-structured answers.",
        ),
        LocalModel(
            id = "gemma-4-e4b",
            family = "Google",
            name = "Gemma 4 E4B Instruct",
            params = "4B effective",
            repo = "unsloth/gemma-4-E4B-it-GGUF",
            file = "gemma-4-E4B-it-Q4_K_M.gguf",
            sizeBytes = 4_980_000_000L,
            ramNeededMb = 6_200,
            quality = 9,
            kind = ModelKind.Chat,
            license = "Apache-2.0",
            note = "Highest quality that fits a 8 GB+ phone.",
        ),
        LocalModel(
            id = "gpt-oss-20b",
            family = "OpenAI",
            name = "gpt-oss 20B",
            params = "21B (3.6B active)",
            repo = "ggml-org/gpt-oss-20b-GGUF",
            file = "gpt-oss-20b-mxfp4.gguf",
            sizeBytes = 12_100_000_000L,
            ramNeededMb = 15_000,
            quality = 10,
            kind = ModelKind.Reasoning,
            license = "Apache-2.0",
            note = "Open-weight model from OpenAI. Ships as MXFP4, not Q4_K_M. Needs 16 GB+ RAM.",
        ),
    )

    /** Whisper models. Base is the default; small/medium trade speed for accuracy. */
    val speech: List<SpeechModel> = listOf(
        SpeechModel("whisper-tiny", "Whisper Tiny", "ggml-tiny-q5_1.bin", 32_000_000L, 200, 3, "Fastest, rough accuracy."),
        SpeechModel("whisper-base", "Whisper Base", "ggml-base-q5_1.bin", 60_000_000L, 300, 5, "Fast and good. Default."),
        SpeechModel("whisper-small", "Whisper Small", "ggml-small-q5_1.bin", 190_000_000L, 700, 7, "Very accurate on most phones."),
        SpeechModel("whisper-medium", "Whisper Medium", "ggml-medium-q5_0.bin", 539_000_000L, 1_700, 9, "Most accurate. Needs 6 GB+ RAM."),
    )

    const val DEFAULT_SPEECH_ID = "whisper-base"

    fun find(id: String): LocalModel? = models.firstOrNull { it.id == id }
    fun findSpeech(id: String): SpeechModel? = speech.firstOrNull { it.id == id }
}
