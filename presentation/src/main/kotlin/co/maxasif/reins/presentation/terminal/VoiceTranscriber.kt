package co.maxasif.reins.presentation.terminal

/**
 * The on-device speech-to-text call Voice mode (ticket 024) needs - one microphone recording,
 * transcribed to text. `:presentation` has no dependency on `:data`/`:whisper` (only `:domain`
 * and `:terminal-view`), so this interface is what decouples [VoiceCommandInput] from
 * `WhisperBridge`/`AudioRecord` - the real implementation is wired up by `:app`, the only module
 * that sees both layers.
 */
interface VoiceTranscriber {
    /** Starts recording from the microphone. Must be followed by exactly one [stopAndTranscribe] or [cancel]. */
    fun start()

    /** Stops recording and runs the captured audio through whisper.cpp, returning the transcript. */
    suspend fun stopAndTranscribe(): String

    /** Stops recording and discards the audio without transcribing (e.g. the user leaves Voice mode mid-listen). */
    fun cancel()
}
