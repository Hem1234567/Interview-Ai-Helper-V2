// whisper.cpp JNI bridge: on-device speech recognition for 16 kHz mono float PCM.
#include <atomic>
#include <cstring>
#include <algorithm>
#include <mutex>
#include <string>

#include "native_common.h"
#include "whisper.h"

namespace {

whisper_context *g_ctx = nullptr;
std::mutex g_mutex;
std::atomic<bool> g_cancel{false};
std::string g_lang = "";

bool abort_callback(void * /*user*/) { return g_cancel.load(); }

void log_callback(ggml_log_level level, const char *text, void * /*user*/) {
    if (level >= GGML_LOG_LEVEL_ERROR) COPILOT_LOGE("%s", text);
}

// Trim whitespace and collapse the sentinel tokens Whisper sometimes emits for silence.
std::string clean(std::string s) {
    auto drop = [&s](const char *needle) {
        size_t pos;
        const size_t len = std::strlen(needle);
        while ((pos = s.find(needle)) != std::string::npos) s.erase(pos, len);
    };
    drop("[BLANK_AUDIO]");
    drop("[ Silence ]");
    drop("(silence)");
    const char *ws = " \t\r\n";
    const size_t b = s.find_first_not_of(ws);
    if (b == std::string::npos) return {};
    const size_t e = s.find_last_not_of(ws);
    return s.substr(b, e - b + 1);
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_interviewcopilot_app_voice_NativeWhisper_nativeLoad(
    JNIEnv *env, jobject /*thiz*/, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
    whisper_log_set(log_callback, nullptr);
    const char *p = env->GetStringUTFChars(path, nullptr);
    if (p == nullptr) return JNI_FALSE;
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    g_ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_interviewcopilot_app_voice_NativeWhisper_nativeFree(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    g_cancel.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx != nullptr) whisper_free(g_ctx);
    g_ctx = nullptr;
}

JNIEXPORT void JNICALL Java_com_interviewcopilot_app_voice_NativeWhisper_nativeCancel(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    g_cancel.store(true);
}

JNIEXPORT jboolean JNICALL Java_com_interviewcopilot_app_voice_NativeWhisper_nativeIsLoaded(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_com_interviewcopilot_app_voice_NativeWhisper_nativeLastLanguage(
    JNIEnv *env, jobject /*thiz*/) {
    return env->NewStringUTF(g_lang.c_str());
}

// language: "auto" or an ISO 639-1 code. prompt: UTF-8 bias text (vocabulary + punctuation hint).
// accurate=true uses beam search (final pass); false uses greedy decoding (live partials).
JNIEXPORT jbyteArray JNICALL Java_com_interviewcopilot_app_voice_NativeWhisper_nativeTranscribe(
    JNIEnv *env, jobject /*thiz*/, jfloatArray pcm, jstring language, jbyteArray prompt,
    jint threads, jboolean accurate) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx == nullptr) return string_to_bytes(env, "");
    g_cancel.store(false);

    const jsize n = env->GetArrayLength(pcm);
    if (n < 1600) return string_to_bytes(env, "");  // < 0.1 s
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    if (samples == nullptr) return string_to_bytes(env, "");

    const char *lang_c = env->GetStringUTFChars(language, nullptr);
    const std::string lang = lang_c != nullptr ? lang_c : "auto";
    if (lang_c != nullptr) env->ReleaseStringUTFChars(language, lang_c);
    const std::string initial = bytes_to_string(env, prompt);

    whisper_full_params wp = whisper_full_default_params(
        accurate == JNI_TRUE ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    wp.n_threads = std::max<jint>(threads, 1);
    wp.language = lang.c_str();
    wp.detect_language = false;  // "auto" is handled by whisper_full itself
    wp.translate = false;
    wp.no_context = true;
    wp.single_segment = true;
    wp.no_timestamps = true;
    wp.print_progress = false;
    wp.print_realtime = false;
    wp.print_special = false;
    wp.print_timestamps = false;
    wp.suppress_blank = true;
    wp.suppress_nst = true;
    wp.temperature = 0.0f;
    wp.temperature_inc = accurate == JNI_TRUE ? 0.2f : 0.0f;
    wp.no_speech_thold = 0.6f;
    wp.initial_prompt = initial.empty() ? nullptr : initial.c_str();
    wp.abort_callback = abort_callback;
    wp.abort_callback_user_data = nullptr;
    if (accurate == JNI_TRUE) wp.beam_search.beam_size = 5;

    std::string text;
    if (whisper_full(g_ctx, wp, samples, n) == 0) {
        const int segs = whisper_full_n_segments(g_ctx);
        for (int i = 0; i < segs; ++i) {
            const char *t = whisper_full_get_segment_text(g_ctx, i);
            if (t != nullptr) text += t;
        }
        const int id = whisper_full_lang_id(g_ctx);
        const char *code = id >= 0 ? whisper_lang_str(id) : nullptr;
        g_lang = code != nullptr ? code : "";
    }
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    return string_to_bytes(env, clean(text));
}

}  // extern "C"
