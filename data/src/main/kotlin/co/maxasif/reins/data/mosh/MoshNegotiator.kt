package co.maxasif.reins.data.mosh

import co.maxasif.reins.data.ssh.ReinsSshTransport

/** The UDP port and base64 session key `mosh-server new` announces on stdout before daemonizing. */
data class MoshConnectParams(val udpPort: Int, val sessionKey: String)

/** `mosh-server new`'s stdout announcement was missing or didn't match the expected shape. */
class MoshNegotiationFailedException(message: String) : Exception(message)

/**
 * Bootstraps a Mosh session the same way the real `mosh` client does: one SSH round-trip (over the
 * already-connected [ReinsSshTransport], reused rather than opening a second connection - ticket
 * 025) execs `mosh-server new`, which prints `MOSH CONNECT <port> <key>` to stdout and then
 * daemonizes, closing stdout. Everything after that point is plain UDP, driven by [MoshSession
 * co.maxasif.reins.mosh.MoshSession] - no more SSH involvement for the Data Channel itself
 * (mirroring upstream mosh's own division of labor between its `mosh` wrapper script and
 * `mosh-client`/`mosh-server`).
 *
 * No child command is given to `mosh-server new` - with none, it runs the remote user's own login
 * shell, the same plain-shell behavior the SSH Data Channel uses. Reins carries no herdr-specific
 * protocol; anything the user wants to run remotely, they type themselves once attached.
 */
object MoshNegotiator {
    private val CONNECT_LINE = Regex("""MOSH CONNECT (\d+) (\S+)""")

    fun negotiate(transport: ReinsSshTransport): MoshConnectParams {
        val output = transport.execCapture("mosh-server new")
        val match = CONNECT_LINE.find(output)
            ?: throw MoshNegotiationFailedException(
                "mosh-server didn't announce a MOSH CONNECT line (is mosh-server installed on the remote host?): $output",
            )
        return MoshConnectParams(udpPort = match.groupValues[1].toInt(), sessionKey = match.groupValues[2])
    }
}
