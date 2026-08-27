package co.maxasif.reins.mosh

/**
 * Raw JNI surface onto `mosh_jni.cpp`'s `MoshClientSession` (ticket 025) - a real
 * `Network::Transport<UserStream, Terminal::Complete>` session built against
 * rjyo/mosh-android's prebuilt NDK libs (ticket 003), or a documented loopback stand-in
 * ([nativeIsSimulated]) when those libs weren't available at build time.
 *
 * Kotlin callers should use [MoshSession] rather than this class directly - it owns the native
 * handle lifecycle and gives the session's methods normal Kotlin shapes.
 */
class MoshBridge {
    companion object {
        init {
            System.loadLibrary("reins-mosh")
        }
    }

    /** Callback surface invoked from the native background loop thread (ticket 025's `runLoop`). */
    interface Callbacks {
        /** A chunk of remote-diff bytes - already real terminal escape sequences, ready for `TerminalSession.feedIncoming`. */
        fun onOutput(bytes: ByteArray)

        /** The far end acknowledged (or we timed out waiting for an ack of) a clean shutdown. */
        fun onShutdown()

        /** A native-side error occurred; the loop thread has exited. */
        fun onError(message: String)
    }

    external fun nativeVersion(): String

    /** True if this build's `:mosh` native library is running the [REINS_MOSH_SIMULATED loopback stand-in](mosh_jni.cpp), not real mosh. */
    external fun nativeIsSimulated(): Boolean

    /** Constructs a `Network::Transport<UserStream, Complete>` for [host]:[port] with mosh's base64 session [key]. Returns 0 on failure. */
    external fun nativeCreate(key: String, host: String, port: String, cols: Int, rows: Int): Long

    /** Starts the background network loop; [callbacks] is invoked from that thread until [nativeDestroy]. */
    external fun nativeStartLoop(handle: Long, callbacks: Callbacks)

    /** Enqueues raw keystroke bytes onto the session's `UserStream`. */
    external fun nativeSendInput(handle: Long, data: ByteArray)

    /** Enqueues a resize event onto the session's `UserStream`, mirroring mosh-client's own `process_resize`. */
    external fun nativeResize(handle: Long, cols: Int, rows: Int)

    /** Starts mosh's own clean-shutdown handshake (`Transport::start_shutdown`). */
    external fun nativeShutdown(handle: Long)

    /** Stops the loop thread, joins it, and frees the native session. Blocks until the thread exits. */
    external fun nativeDestroy(handle: Long)
}
