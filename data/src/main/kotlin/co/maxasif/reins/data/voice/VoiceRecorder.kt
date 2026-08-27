package co.maxasif.reins.data.voice

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import co.maxasif.reins.whisper.WhisperBridge
import kotlin.math.min

private const val SAMPLE_RATE_HZ = 16_000 // whisper.cpp's whisper_full expects 16kHz mono float32 PCM
private const val READ_CHUNK_FRAMES = 1_600 // 100ms per read, small enough to stop promptly

/**
 * Captures one microphone recording and runs it through [WhisperBridge] (ticket 024). Plain class,
 * not `co.maxasif.reins.presentation.terminal.VoiceTranscriber` itself - `:data` can't see
 * `:presentation` (dependency rule: `presentation -> domain`, not the reverse), so `:app` adapts
 * this into that interface (`AppVoiceTranscriber`).
 *
 * One [VoiceRecorder] instance owns one lazily-created [WhisperBridge] (loading the bundled model
 * is not free) reused across recordings, and enforces the same "[start] must be followed by
 * exactly one [stopAndTranscribe] or [cancel]" contract the interface documents.
 */
class VoiceRecorder(private val assetManager: AssetManager) {
    private val whisper: WhisperBridge by lazy { WhisperBridge.fromAsset(assetManager) }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val capturedChunks = mutableListOf<ShortArray>()
    @Volatile private var chunkLengths = mutableListOf<Int>()
    @Volatile private var recording = false

    @SuppressLint("MissingPermission") // caller (VoiceCommandInput) gates this on a granted runtime permission
    fun start() {
        capturedChunks.clear()
        chunkLengths.clear()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBufferSize, READ_CHUNK_FRAMES * 2) * 4,
        )
        audioRecord = record
        recording = true
        record.startRecording()

        recordingThread = Thread {
            val buffer = ShortArray(READ_CHUNK_FRAMES)
            while (recording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(capturedChunks) {
                        capturedChunks.add(buffer.copyOf(read))
                        chunkLengths.add(read)
                    }
                }
            }
        }.also { it.start() }
    }

    suspend fun stopAndTranscribe(): String {
        val pcm = stopCapture() ?: return ""
        if (pcm.isEmpty()) return ""
        return whisper.transcribe(pcm)
    }

    fun cancel() {
        stopCapture()
    }

    /** Stops capture and converts the buffered PCM16 samples to whisper.cpp's normalized float32 format. */
    private fun stopCapture(): FloatArray? {
        recording = false
        recordingThread?.join()
        recordingThread = null

        val record = audioRecord ?: return null
        audioRecord = null
        record.stop()
        record.release()

        val totalFrames = synchronized(capturedChunks) { chunkLengths.sum() }
        val pcm = FloatArray(totalFrames)
        var offset = 0
        synchronized(capturedChunks) {
            for (i in capturedChunks.indices) {
                val chunk = capturedChunks[i]
                val length = min(chunkLengths[i], chunk.size)
                for (j in 0 until length) {
                    pcm[offset + j] = chunk[j] / 32768f
                }
                offset += length
            }
            capturedChunks.clear()
            chunkLengths.clear()
        }
        return pcm
    }
}
