package co.maxasif.reins.data

/**
 * The shape shared by [co.maxasif.reins.data.ssh.SshShellDataChannelSession] (SSH transport) and
 * [co.maxasif.reins.data.mosh.MoshDataChannelSession] (Mosh transport, ticket 025) - raw keystroke
 * bytes in, terminal bytes out, matching CONTEXT.md's Data Channel definition exactly regardless
 * of which transport carries them. Lets `:app`'s connect orchestration wire either transport into
 * the same `TerminalSession` glue without a transport-specific branch past connect time.
 */
interface DataChannelSession : AutoCloseable {
    fun sendInput(data: ByteArray)
    fun sendResize(cols: Int, rows: Int)
    fun startReading(onTerminalBytes: (ByteArray) -> Unit, onShutdown: (String?) -> Unit, onError: (Throwable) -> Unit)
}
