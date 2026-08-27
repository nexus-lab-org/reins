package co.maxasif.reins.whisper

import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

private const val LOG_TAG = "WhisperBridge"

/** Path within this module's `assets/` to the bundled ggml model (ticket 005: tiny.en, quantized). */
const val BUNDLED_MODEL_ASSET_PATH = "models/ggml-tiny.en-q5_1.bin"

/**
 * JNI bridge to whisper.cpp (ticket 005/024): a real on-device transcription context around one
 * loaded ggml model. Not thread-safe by design (whisper.cpp itself forbids concurrent access to
 * one context) — every call is funneled through a single-thread dispatcher, same constraint
 * whisper.cpp's own examples/whisper.android enforces in `LibWhisper.kt`.
 */
class WhisperBridge private constructor(private var contextPtr: Long) {
    private val scope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    /**
     * Transcribes one utterance already decoded to mono 16kHz float32 PCM in [-1, 1] (the format
     * whisper.cpp's `whisper_full` requires) and returns the concatenated text of all segments,
     * trimmed. Ad Hoc Commands are short single utterances, so segments are just joined in order
     * with no timestamp formatting (contrast the reference example's dictation-oriented output).
     */
    suspend fun transcribe(pcm16kMono: FloatArray): String = withContext(scope.coroutineContext) {
        check(contextPtr != 0L) { "WhisperBridge already released" }
        val threads = WhisperCpuConfig.preferredThreadCount
        WhisperNative.fullTranscribe(contextPtr, threads, pcm16kMono)
        val segmentCount = WhisperNative.getTextSegmentCount(contextPtr)
        buildString {
            for (i in 0 until segmentCount) {
                append(WhisperNative.getTextSegment(contextPtr, i))
            }
        }.trim()
    }

    /** Frees the native whisper_context. Safe to call more than once. */
    suspend fun release() = withContext(scope.coroutineContext) {
        if (contextPtr != 0L) {
            WhisperNative.freeContext(contextPtr)
            contextPtr = 0L
        }
    }

    companion object {
        /** Loads the model straight from the APK's assets — no first-run extraction/copy needed. */
        fun fromAsset(
            assetManager: AssetManager,
            assetPath: String = BUNDLED_MODEL_ASSET_PATH,
        ): WhisperBridge {
            val ptr = WhisperNative.initContextFromAsset(assetManager, assetPath)
            check(ptr != 0L) { "Couldn't load whisper model from asset '$assetPath'" }
            return WhisperBridge(ptr)
        }
    }
}

/**
 * Raw JNI surface, split out from [WhisperBridge] so the ABI-variant `System.loadLibrary` dance
 * lives in one place. Mirrors whisper.cpp's own CPU-feature runtime dispatch: the CMake build
 * (ticket 024) produces up to two extra ABI-tuned `.so`s per device (`reins_whisper_v8fp16_va` on
 * arm64 with fp16 support, `reins_whisper_vfpv4` on armeabi-v7a with NEON vfpv4) alongside the
 * always-built `reins-whisper` fallback; picking the best one needs a `/proc/cpuinfo` check no
 * static manifest entry can express.
 */
internal object WhisperNative {
    init {
        var loadV8fp16 = false
        var loadVfpv4 = false
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        when (primaryAbi) {
            "arm64-v8a" -> cpuInfo()?.let { info ->
                if (info.contains("fphp")) loadV8fp16 = true
            }
            "armeabi-v7a" -> cpuInfo()?.let { info ->
                if (info.contains("vfpv4")) loadVfpv4 = true
            }
        }

        val libName = when {
            loadV8fp16 -> "reins_whisper_v8fp16_va"
            loadVfpv4 -> "reins_whisper_vfpv4"
            else -> "reins-whisper"
        }
        Log.d(LOG_TAG, "Loading lib$libName.so for ABI $primaryAbi")
        System.loadLibrary(libName)
    }

    private fun cpuInfo(): String? = try {
        File("/proc/cpuinfo").inputStream().bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Couldn't read /proc/cpuinfo", e)
        null
    }

    external fun nativeVersion(): String
    external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray)
    external fun getTextSegmentCount(contextPtr: Long): Int
    external fun getTextSegment(contextPtr: Long, index: Int): String
}

/** Thread-count heuristic ported from whisper.cpp's `examples/whisper.android` `WhisperCpuConfig.kt`. */
private object WhisperCpuConfig {
    val preferredThreadCount: Int
        get() = (Runtime.getRuntime().availableProcessors() - 2).coerceAtLeast(2)
}
