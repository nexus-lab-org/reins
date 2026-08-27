package co.maxasif.reins.presentation.connect

import co.maxasif.reins.presentation.terminal.VoiceTranscriber
import com.termux.terminal.TerminalSession

/**
 * Connect screen stepper state (ticket 008: resolve host -> open transport -> attach shell), now
 * driven by a saved Host (ticket 018) instead of ticket 017's hardcoded one.
 */
sealed class ConnectUiState {
    sealed class Stepper : ConnectUiState() {
        object ResolvingHost : Stepper()
        object OpeningTransport : Stepper()
        object AttachingSession : Stepper()
    }

    /**
     * [voiceTranscriber] wires Voice mode (ticket 024) to on-device whisper.cpp - built by `:app`,
     * the only module that sees both this `:presentation` interface and `:data`'s
     * `WhisperBridge`-backed implementation.
     *
     * [keySetupNote], when non-null, tells the user a password-authenticated connect just
     * generated and installed a key so future connects won't need the password again.
     */
    data class Connected(
        val session: TerminalSession,
        val voiceTranscriber: VoiceTranscriber,
        val keySetupNote: String? = null,
    ) : ConnectUiState()

    /** Fail-loud terminal state (ticket 010: herdr protocol mismatches, SSH/auth failures, ...). */
    data class Failed(val message: String) : ConnectUiState()
}
