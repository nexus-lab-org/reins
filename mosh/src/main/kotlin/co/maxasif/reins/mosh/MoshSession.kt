package co.maxasif.reins.mosh

import java.util.concurrent.atomic.AtomicBoolean

/** A native-side error surfaced from [MoshBridge.Callbacks.onError], or session-start failure. */
class MoshTransportException(message: String) : Exception(message)

/**
 * A live Mosh session (ticket 025): one `Network::Transport<UserStream, Complete>` per instance,
 * driving mosh's own UDP roaming/retransmit logic on a native background thread. Shaped to match
 * `co.maxasif.reins.data.ssh.SshShellDataChannelSession`'s Kotlin surface exactly
 * (`sendInput`/`sendResize`/`startReading`/`close`) so [co.maxasif.reins.data.DataChannelSession]
 * covers either transport identically.
 *
 * Roaming (the ticket's headline acceptance criterion) needs no explicit action from this class:
 * mosh's `Network::Connection` re-learns the peer's source address on any validly-authenticated
 * packet, entirely inside the native libs. This class's only roaming-relevant responsibility is
 * the same one any long-lived Android network session has - don't call [close] just because
 * `ConnectivityManager` reported a network change; let the background loop keep running so it can
 * pick up packets from the new address once they start arriving.
 */
class MoshSession private constructor(
    private val bridge: MoshBridge,
    private val handle: Long,
) : AutoCloseable {

    private val closed = AtomicBoolean(false)

    /** True if this session is the [nativeIsSimulated] loopback stand-in, not a real Mosh connection. */
    val isSimulated: Boolean get() = bridge.nativeIsSimulated()

    fun sendInput(data: ByteArray) {
        if (!closed.get()) bridge.nativeSendInput(handle, data)
    }

    fun sendResize(cols: Int, rows: Int) {
        if (!closed.get()) bridge.nativeResize(handle, cols, rows)
    }

    /** Starts the background loop; callbacks are invoked off the calling thread, matching [co.maxasif.reins.data.DataChannelSession.startReading]. */
    fun startReading(onTerminalBytes: (ByteArray) -> Unit, onShutdown: (String?) -> Unit, onError: (Throwable) -> Unit) {
        bridge.nativeStartLoop(
            handle,
            object : MoshBridge.Callbacks {
                override fun onOutput(bytes: ByteArray) = onTerminalBytes(bytes)
                override fun onShutdown() = onShutdown(null)
                override fun onError(message: String) = onError(MoshTransportException(message))
            },
        )
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            bridge.nativeShutdown(handle)
            bridge.nativeDestroy(handle)
        }
    }

    companion object {
        /**
         * Starts a Mosh session against a server already negotiated via `mosh-server new` (the SSH
         * bootstrap step lives in `:data`'s `MoshNegotiator`, not here - this class only drives the
         * UDP session once a port/key are known, mirroring mosh-client's own division of labor
         * from `mosh`'s wrapper shell script).
         */
        fun start(host: String, port: Int, key: String, cols: Int, rows: Int): MoshSession {
            val bridge = MoshBridge()
            val handle = bridge.nativeCreate(key, host, port.toString(), cols, rows)
            if (handle == 0L) {
                throw MoshTransportException("Failed to construct the native Mosh transport for $host:$port")
            }
            return MoshSession(bridge, handle)
        }
    }
}
