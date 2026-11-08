#pragma once
// Shared helpers for the JNI layer: logging and Java <-> UTF-8 byte-array conversion.
// Text crosses the JNI boundary as UTF-8 byte arrays (never as modified-UTF-8 jstring), so emoji
// and every other character round-trip correctly.
#include <jni.h>

#include <cstdarg>
#include <cstdio>
#include <string>

#ifdef __ANDROID__
#include <android/log.h>
#define COPILOT_LOG(prio, ...) __android_log_print(prio, "CopilotNative", __VA_ARGS__)
#define COPILOT_LOGI(...) COPILOT_LOG(ANDROID_LOG_INFO, __VA_ARGS__)
#define COPILOT_LOGE(...) COPILOT_LOG(ANDROID_LOG_ERROR, __VA_ARGS__)
#else
#define COPILOT_LOGI(...) (std::fprintf(stderr, "[I] " __VA_ARGS__), std::fputc('\n', stderr))
#define COPILOT_LOGE(...) (std::fprintf(stderr, "[E] " __VA_ARGS__), std::fputc('\n', stderr))
#endif

inline std::string bytes_to_string(JNIEnv *env, jbyteArray arr) {
    if (arr == nullptr) return {};
    const jsize n = env->GetArrayLength(arr);
    std::string out(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte *>(&out[0]));
    return out;
}

inline jbyteArray string_to_bytes(JNIEnv *env, const std::string &s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    if (arr != nullptr && !s.empty()) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()),
                                reinterpret_cast<const jbyte *>(s.data()));
    }
    return arr;
}

// Length of the longest prefix of `s` that ends on a complete UTF-8 code point.
inline size_t utf8_complete_prefix(const std::string &s) {
    size_t i = s.size();
    size_t back = 0;
    while (i > 0 && back < 4) {
        const unsigned char c = static_cast<unsigned char>(s[i - 1]);
        if ((c & 0xC0) == 0x80) {          // continuation byte, keep walking back
            --i;
            ++back;
            continue;
        }
        size_t need = 1;
        if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        return (back + 1 >= need) ? s.size() : i - 1;
    }
    return s.size();
}
