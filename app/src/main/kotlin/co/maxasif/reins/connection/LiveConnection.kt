package co.maxasif.reins.connection

import co.maxasif.reins.data.DataChannelSession
import co.maxasif.reins.data.ssh.ReinsSshTransport
import co.maxasif.reins.presentation.connect.ConnectUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * One live Host connection's full resource set (ticket 026): the native SSH/Mosh transport, the
 * Data Channel shell built on top of it, and the JNI-backed [com.termux.terminal.TerminalSession] -
 * all independent of every other [LiveConnection] [ConnectionService] is holding. Closing one
 * never touches another's resources or
 * [connectionScope] (ticket 013's per-Host independence requirement).
 */
class LiveConnection(
    val hostId: String,
    val displayName: String,
    val connected: ConnectUiState.Connected,
    private val transport: ReinsSshTransport,
    private val dataChannel: DataChannelSession,
    private val connectionScope: CoroutineScope,
) : AutoCloseable {
    /** Explicit per-Host disconnect (ticket 026) - tears this Host's channels down, nothing else's. */
    override fun close() {
        connected.session.finishIfRunning()
        runCatching { dataChannel.close() }
        runCatching { transport.close() }
        connectionScope.cancel()
    }
}
