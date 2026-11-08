// llama.cpp JNI bridge: load a GGUF model once (warm start), then stream chat completions.
//
//  * The chat template embedded in the GGUF is applied with llama.cpp's own Jinja engine, so new
//    model families (Qwen3.5, Gemma 4, gpt-oss ...) are formatted exactly as their authors intend.
//  * The KV cache is kept between requests. Every request re-renders the full prompt, finds the
//    longest token prefix that is already in the cache (system prompt, résumé, earlier turns) and
//    only evaluates the new tokens. That is the "cache the system prompt" optimisation.
#include <atomic>
#include <algorithm>
#include <cstring>
#include <exception>
#include <mutex>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "chat.h"
#include "llama.h"
#include "native_common.h"

namespace {

struct LlmState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    common_chat_templates_ptr templates;
    std::vector<llama_token> cached;  // tokens currently resident in the KV cache (sequence 0)
    int n_ctx = 0;
    int n_batch = 512;
};

LlmState g;
std::mutex g_mutex;
std::atomic<bool> g_cancel{false};
std::string g_stats = "0,0,0,0,0,0";
bool g_backend_ready = false;

void log_callback(ggml_log_level level, const char *text, void * /*user*/) {
    if (level >= GGML_LOG_LEVEL_ERROR) {
        COPILOT_LOGE("%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        COPILOT_LOGI("%s", text);
    }
}

void release_all() {
    g.templates.reset();
    if (g.ctx != nullptr) llama_free(g.ctx);
    if (g.model != nullptr) llama_model_free(g.model);
    g.ctx = nullptr;
    g.model = nullptr;
    g.vocab = nullptr;
    g.cached.clear();
}

std::string piece_for(llama_token tok) {
    char buf[256];
    int n = llama_token_to_piece(g.vocab, tok, buf, sizeof(buf), 0, false);
    if (n < 0) {
        std::string big(static_cast<size_t>(-n), '\0');
        n = llama_token_to_piece(g.vocab, tok, &big[0], static_cast<int32_t>(big.size()), 0, false);
        if (n < 0) return {};
        big.resize(static_cast<size_t>(n));
        return big;
    }
    return std::string(buf, static_cast<size_t>(n));
}

// Decode `n` tokens starting at `tokens`, in n_batch sized slices. Returns 0 ok, 1 cancelled, <0 error.
int decode_tokens(llama_token *tokens, int n) {
    for (int i = 0; i < n; i += g.n_batch) {
        if (g_cancel.load()) return 1;
        const int len = std::min(g.n_batch, n - i);
        llama_batch batch = llama_batch_get_one(tokens + i, len);
        if (llama_decode(g.ctx, batch) != 0) return -2;
    }
    return 0;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_com_interviewcopilot_app_llm_NativeLlm_nativeInit(
    JNIEnv *env, jobject /*thiz*/, jstring libDir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_backend_ready) return;
    llama_log_set(log_callback, nullptr);
    const char *dir = env->GetStringUTFChars(libDir, nullptr);
    if (dir != nullptr) {
        ggml_backend_load_all_from_path(dir);  // picks the best CPU variant for this phone
        env->ReleaseStringUTFChars(libDir, dir);
    }
    llama_backend_init();
    g_backend_ready = true;
}

JNIEXPORT jstring JNICALL Java_com_interviewcopilot_app_llm_NativeLlm_nativeSystemInfo(
    JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF(llama_print_system_info());
}

// Returns "" on success, otherwise a human readable error.
JNIEXPORT jstring JNICALL Java_com_interviewcopilot_app_llm_NativeLlm_nativeLoad(
    JNIEnv *env, jobject /*thiz*/, jstring path, jint nCtx, jint nThreads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_all();
    const char *p = env->GetStringUTFChars(path, nullptr);
    std::string model_path = p != nullptr ? p : "";
    if (p != nullptr) env->ReleaseStringUTFChars(path, p);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;  // CPU path (see README: GPU offload is opt-in at build time)
    mp.load_mode = LLAMA_LOAD_MODE_MMAP;  // weights are paged in lazily, so a 4 GB model does not need 4 GB at once
    g.model = llama_model_load_from_file(model_path.c_str(), mp);
    if (g.model == nullptr) return env->NewStringUTF("Could not load the model file (corrupt, or too large for this device).");

    g.vocab = llama_model_get_vocab(g.model);
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(std::max<jint>(nCtx, 512));
    cp.n_batch = static_cast<uint32_t>(g.n_batch);
    cp.n_ubatch = static_cast<uint32_t>(g.n_batch);
    cp.n_threads = std::max<jint>(nThreads, 1);
    cp.n_threads_batch = std::max<jint>(nThreads, 1);
    g.ctx = llama_init_from_model(g.model, cp);
    if (g.ctx == nullptr) {
        release_all();
        return env->NewStringUTF("Not enough memory to create the model context.");
    }
    g.n_ctx = static_cast<int>(llama_n_ctx(g.ctx));
    try {
        g.templates = common_chat_templates_init(g.model, "");
    } catch (const std::exception &e) {
        release_all();
        return env->NewStringUTF((std::string("Chat template error: ") + e.what()).c_str());
    }
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL Java_com_interviewcopilot_app_llm_NativeLlm_nativeUnload(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    g_cancel.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    release_all();
}

JNIEXPORT void JNICALL Java_com_interviewcopilot_app_llm_NativeLlm_nativeCancel(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    g_cancel.store(true);
}

JNIEXPORT jboolean JNICALL Java_com_interviewcopilot_app_llm_NativeLlm_nativeIsLoaded(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    return g.ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_com_interviewcopilot_app_llm_NativeLlm_nativeLastStats(
    JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF(g_stats.c_str());
}

// messagesJson: OpenAI style [{"role":"system","content":"..."}, ...] as UTF-8.
// Returns 0 = finished, 1 = cancelled, negative = error code.
JNIEXPORT jint JNICALL Java_com_interviewcopilot_app_llm_NativeLlm_nativeGenerate(
    JNIEnv *env, jobject /*thiz*/, jbyteArray messagesJson, jint maxTokens, jfloat temperature,
    jboolean enableThinking, jobject sink) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g.ctx == nullptr) return -1;
    g_cancel.store(false);

    jclass sinkClass = env->GetObjectClass(sink);
    jmethodID onToken = env->GetMethodID(sinkClass, "onToken", "([B)Z");
    if (onToken == nullptr) return -9;

    const int64_t t_start = ggml_time_us();

    // 1. Render the conversation with the model's own chat template.
    std::string prompt;
    try {
        common_chat_templates_inputs inputs;
        inputs.messages = common_chat_msgs_parse_oaicompat(
            nlohmann::ordered_json::parse(bytes_to_string(env, messagesJson)));
        inputs.add_generation_prompt = true;
        inputs.use_jinja = true;
        inputs.enable_thinking = enableThinking == JNI_TRUE;
        prompt = common_chat_templates_apply(g.templates.get(), inputs).prompt;
    } catch (const std::exception &e) {
        COPILOT_LOGE("template error: %s", e.what());
        return -3;
    }

    // 2. Tokenise (the template usually already contains BOS, so drop a doubled one).
    std::vector<llama_token> tokens(prompt.size() + 8);
    int n_tok = llama_tokenize(g.vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                               tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (n_tok < 0) {
        tokens.resize(static_cast<size_t>(-n_tok));
        n_tok = llama_tokenize(g.vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                               tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (n_tok <= 0) return -4;
    tokens.resize(static_cast<size_t>(n_tok));
    const llama_token bos = llama_vocab_bos(g.vocab);
    if (tokens.size() >= 2 && tokens[0] == bos && tokens[1] == bos) tokens.erase(tokens.begin());

    int budget = static_cast<int>(maxTokens);
    if (static_cast<int>(tokens.size()) + 16 > g.n_ctx) return -5;  // prompt does not fit
    budget = std::min(budget, g.n_ctx - static_cast<int>(tokens.size()) - 1);

    // 3. Re-use the KV cache for the longest shared prefix.
    size_t keep = 0;
    while (keep < tokens.size() && keep < g.cached.size() && tokens[keep] == g.cached[keep]) ++keep;
    if (keep == tokens.size()) --keep;  // always evaluate at least one token to get fresh logits
    llama_memory_t mem = llama_get_memory(g.ctx);
    if (keep == 0 || !llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(keep), -1)) {
        // Recurrent / hybrid models cannot drop a suffix; start from scratch.
        llama_memory_clear(mem, false);
        keep = 0;
    }
    g.cached.resize(keep);

    // 4. Evaluate the new part of the prompt.
    const int to_eval = static_cast<int>(tokens.size() - keep);
    int rc = decode_tokens(tokens.data() + keep, to_eval);
    if (rc != 0) {
        g.cached.clear();
        llama_memory_clear(mem, false);
        return rc;
    }
    g.cached.insert(g.cached.end(), tokens.begin() + static_cast<long>(keep), tokens.end());
    const int64_t t_prompt_done = ggml_time_us();

    // 5. Sample and stream.
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
    if (temperature <= 0.01f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    std::string pending;
    int64_t t_first = 0;
    int n_gen = 0;
    int result = 0;
    for (; n_gen < budget; ++n_gen) {
        if (g_cancel.load()) { result = 1; break; }
        llama_token tok = llama_sampler_sample(smpl, g.ctx, -1);
        if (llama_vocab_is_eog(g.vocab, tok)) break;
        if (t_first == 0) t_first = ggml_time_us();

        pending += piece_for(tok);
        const size_t ok = utf8_complete_prefix(pending);
        if (ok > 0) {
            jbyteArray bytes = string_to_bytes(env, pending.substr(0, ok));
            const jboolean cont = env->CallBooleanMethod(sink, onToken, bytes);
            env->DeleteLocalRef(bytes);
            pending.erase(0, ok);
            if (env->ExceptionCheck()) { env->ExceptionClear(); result = -6; break; }
            if (cont == JNI_FALSE) { result = 1; break; }
        }
        if (decode_tokens(&tok, 1) != 0) { result = -7; break; }
        g.cached.push_back(tok);
    }
    llama_sampler_free(smpl);

    const int64_t t_end = ggml_time_us();
    char stats[160];
    std::snprintf(stats, sizeof(stats), "%zu,%d,%lld,%lld,%d,%lld", tokens.size(),
                  static_cast<int>(keep), static_cast<long long>((t_prompt_done - t_start) / 1000),
                  static_cast<long long>(t_first == 0 ? 0 : (t_first - t_start) / 1000), n_gen,
                  static_cast<long long>((t_end - t_prompt_done) / 1000));
    g_stats = stats;
    if (result < 0) {  // cache state is unknown after an error, do not trust it
        g.cached.clear();
        llama_memory_clear(mem, false);
    }
    return result;
}

}  // extern "C"
