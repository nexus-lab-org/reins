package co.maxasif.reins.voice

import android.util.Log
import co.maxasif.reins.data.voice.VoiceRecorder
import co.maxasif.reins.presentation.terminal.VoiceTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "VoiceTranscriber"

/**
 * [VoiceTranscriber] backed by a live [VoiceRecorder] (ticket 024). `:app` is the only module that
 * sees both `:presentation`'s Voice interface and `:data`'s real recorder/whisper.cpp client, so
 * this is where they're wired together.
 *
 * [cancel] fires on [scope] fire-and-forget; [stopAndTranscribe] is awaited directly by the caller
 * (it's the value [co.maxasif.reins.presentation.terminal.VoiceCommandInput] is suspended on while
 * showing its "Transcribing..." state), so it isn't wrapped in [scope].
 */
class AppVoiceTranscriber(
    private val recorder: VoiceRecorder,
    private val scope: CoroutineScope,
) : VoiceTranscriber {
    override fun start() = recorder.start()

    override suspend fun stopAndTranscribe(): String = try {
        recorder.stopAndTranscribe()
    } catch (t: Throwable) {
        Log.e(TAG, "Voice transcription failed", t)
        ""
    }

    override fun cancel() {
        scope.launch {
            try {
                recorder.cancel()
            } catch (t: Throwable) {
                Log.e(TAG, "Voice cancel failed", t)
            }
        }
    }
}
