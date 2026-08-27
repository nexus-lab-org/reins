package co.maxasif.reins.data.mosh

import co.maxasif.reins.data.DataChannelSession
import co.maxasif.reins.data.ssh.ReinsSshTransport
import co.maxasif.reins.mosh.MoshSession

/**
 * The Mosh Data Channel (ticket 025): implements the same [DataChannelSession] shape as
 * [co.maxasif.reins.data.ssh.SshShellDataChannelSession] so `:app`'s connect orchestration wires
 * either transport identically, but backed by a real UDP [MoshSession] instead of the SSH exec
 * pipe.
 */
class MoshDataChannelSession private constructor(private val session: MoshSession) : DataChannelSession {

    /** True if this session is running :mosh's simulated loopback stand-in, not a real Mosh connection - see [MoshSession.isSimulated]. */
    val isSimulated: Boolean get() = session.isSimulated

    override fun sendInput(data: ByteArray) = session.sendInput(data)

    override fun sendResize(cols: Int, rows: Int) = session.sendResize(cols, rows)

    override fun startReading(onTerminalBytes: (ByteArray) -> Unit, onShutdown: (String?) -> Unit, onError: (Throwable) -> Unit) =
        session.startReading(onTerminalBytes, onShutdown, onError)

    override fun close() = session.close()

    companion object {
        /**
         * Negotiates a Mosh session over [transport]'s existing SSH connection (see
         * [MoshNegotiator]) and starts the native UDP transport against the same host [transport]
         * is already connected to.
         */
        fun connect(
            transport: ReinsSshTransport,
            hostAddress: String,
            cols: Int,
            rows: Int,
        ): MoshDataChannelSession {
            val params = MoshNegotiator.negotiate(transport)
            val session = MoshSession.start(
                host = hostAddress,
                port = params.udpPort,
                key = params.sessionKey,
                cols = cols,
                rows = rows,
            )
            return MoshDataChannelSession(session)
        }
    }
}
