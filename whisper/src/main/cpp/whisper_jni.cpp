#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <cstring>

#include "whisper.h"

// Real whisper.cpp integration (ticket 024), adapted from whisper.cpp's official
// examples/whisper.android JNI layer (lib/src/main/jni/whisper/jni.c). Trimmed to
// what WhisperBridge.kt actually needs: load a bundled ggml model from the APK's
// assets/, transcribe one utterance, read back the resulting text segments, free
// the context. Renamed to match co.maxasif.reins.whisper.WhisperNative's JNI names
// (upstream's benchmarking hooks and file/InputStream loaders were dropped — Reins
// only ever loads the one bundled asset model). See VENDORED.md.

#define TAG "reins-whisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_co_maxasif_reins_whisper_WhisperNative_nativeVersion(JNIEnv *env, jobject /* this */) {
    return env->NewStringUTF(WHISPER_VERSION);
}

static size_t asset_read(void *ctx, void *output, size_t read_size) {
    return AAsset_read((AAsset *) ctx, output, read_size);
}

static bool asset_is_eof(void *ctx) {
    return AAsset_getRemainingLength64((AAsset *) ctx) <= 0;
}

static void asset_close(void *ctx) {
    AAsset_close((AAsset *) ctx);
}

extern "C" JNIEXPORT jlong JNICALL
Java_co_maxasif_reins_whisper_WhisperNative_initContextFromAsset(
        JNIEnv *env, jobject /* this */, jobject assetManager, jstring assetPathStr) {
    const char *assetPath = env->GetStringUTFChars(assetPathStr, nullptr);
    LOGI("Loading model from asset '%s'", assetPath);

    AAssetManager *nativeAssetManager = AAssetManager_fromJava(env, assetManager);
    AAsset *asset = AAssetManager_open(nativeAssetManager, assetPath, AASSET_MODE_STREAMING);
    env->ReleaseStringUTFChars(assetPathStr, assetPath);

    if (!asset) {
        LOGW("Failed to open model asset");
        return 0;
    }

    whisper_model_loader loader = {
            .context = asset,
            .read = &asset_read,
            .eof = &asset_is_eof,
            .close = &asset_close,
    };

    struct whisper_context *context =
            whisper_init_with_params(&loader, whisper_context_default_params());
    return (jlong) context;
}

extern "C" JNIEXPORT void JNICALL
Java_co_maxasif_reins_whisper_WhisperNative_freeContext(
        JNIEnv * /* env */, jobject /* this */, jlong contextPtr) {
    whisper_free((struct whisper_context *) contextPtr);
}

extern "C" JNIEXPORT void JNICALL
Java_co_maxasif_reins_whisper_WhisperNative_fullTranscribe(
        JNIEnv *env, jobject /* this */, jlong contextPtr, jint numThreads, jfloatArray audioData) {
    auto *context = (struct whisper_context *) contextPtr;
    jfloat *audio = env->GetFloatArrayElements(audioData, nullptr);
    const jsize audioLength = env->GetArrayLength(audioData);

    // Greedy decoding + these flags match the reference Android example, which is
    // itself tuned for short, single-utterance audio rather than long-form dictation
    // (see the "greedy is the right default for commands" reasoning in the ticket 005
    // asset) — Ad Hoc Commands are exactly that: short spoken commands, not dictation.
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = numThreads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    whisper_reset_timings(context);
    if (whisper_full(context, params, audio, audioLength) != 0) {
        LOGW("whisper_full failed");
    }

    env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);
}

extern "C" JNIEXPORT jint JNICALL
Java_co_maxasif_reins_whisper_WhisperNative_getTextSegmentCount(
        JNIEnv * /* env */, jobject /* this */, jlong contextPtr) {
    return whisper_full_n_segments((struct whisper_context *) contextPtr);
}

extern "C" JNIEXPORT jstring JNICALL
Java_co_maxasif_reins_whisper_WhisperNative_getTextSegment(
        JNIEnv *env, jobject /* this */, jlong contextPtr, jint index) {
    const char *text = whisper_full_get_segment_text((struct whisper_context *) contextPtr, index);
    return env->NewStringUTF(text);
}
