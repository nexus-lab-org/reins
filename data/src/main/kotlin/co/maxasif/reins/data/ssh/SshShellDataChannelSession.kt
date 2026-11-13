package co.maxasif.reins.data.ssh

import co.maxasif.reins.data.DataChannelSession
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The shell operations [SshShellDataChannelSession] actually uses. [ReinsSshTransport.ShellChannel]
 * is the production implementation; tests supply a stand-in whose streams fail the way a dropped
 * network does.
 */
internal interface ShellChannelIo : AutoCloseable {
    val inputStream: InputStream
    val outputStream: OutputStream
    fun resize(cols: Int, rows: Int)
}

/**
 * The SSH Data Channel: a plain interactive login shell over [ReinsSshTransport], carrying raw
 * bytes both ways exactly as CONTEXT.md's Data Channel definition requires ("raw keystrokes...
 * exactly as if typed at a local keyboard"). No herdr-specific framing of any kind - whatever the
 * remote shell echoes back (a bash prompt, `herdr attach`'s tmux-like UI, anything else the user
 * runs) is fed straight to the terminal.
 *
 * [sendInput]/[sendResize] never do the actual network write on the calling thread - they enqueue
 * onto a dedicated writer thread instead. [com.termux.terminal.TerminalSession]'s write callback
 * (which both of these back) can fire from the main thread: not just for a physically-typed
 * keystroke, but also when [com.termux.terminal.TerminalEmulator] answers a device-control query
 * the remote shell sent (e.g. a DA1 capability probe a real login shell's prompt/motd setup runs)
 * - that reply is written synchronously from inside the main-thread `Handler` that processes
 * incoming PTY bytes. A blocking SSH channel write from there is a real
 * `android.os.NetworkOnMainThreadException`, not a theoretical one - confirmed by reproducing it.
 */
class SshShellDataChannelSession internal constructor(
    private val channel: ShellChannelIo,
) : DataChannelSession {

    private sealed class WriterCommand {
        data class Input(val data: ByteArray) : WriterCommand()
        data class Resize(val cols: Int, val rows: Int) : WriterCommand()
    }

    private val closed = AtomicBoolean(false)
    private val failed = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private val writerQueue = LinkedBlockingQueue<WriterCommand>()

    // The writer thread starts below, during construction, which can be before [startReading]
    // installs a listener. A failure in that window is held here and delivered once the listener
    // exists, so it can't be lost and can't escape the thread.
    private val errorLock = Any()
    private var errorListener: ((Throwable) -> Unit)? = null
    private var pendingError: Throwable? = null

    private val writerThread = Thread({
        try {
            while (true) {
                when (val command = writerQueue.take()) {
                    is WriterCommand.Input -> {
                        channel.outputStream.write(command.data)
                        channel.outputStream.flush()
                    }
                    is WriterCommand.Resize -> channel.resize(command.cols, command.rows)
                }
            }
        } catch (_: InterruptedException) {
            // Normal shutdown path - close() interrupts this thread.
        } catch (t: Throwable) {
            // A dropped network surfaces here as an IOException (or sshj TransportException) from
            // the socket write or a PTY resize. An uncaught exception on this thread kills the
            // whole process on Android, and the in-memory settings state dies with it. Report the
            // failure the same way the reader thread does instead.
            reportTransportError(t)
        }
    }, "ssh-shell-data-channel-writer").apply {
        isDaemon = true
        start()
    }

    override fun sendInput(data: ByteArray) {
        enqueue(WriterCommand.Input(data))
    }

    override fun sendResize(cols: Int, rows: Int) {
        enqueue(WriterCommand.Resize(cols, rows))
    }

    private fun enqueue(command: WriterCommand) {
        if (closed.get() || failed.get()) return
        writerQueue.put(command)
    }

    override fun startReading(onTerminalBytes: (ByteArray) -> Unit, onShutdown: (String?) -> Unit, onError: (Throwable) -> Unit) {
        installErrorListener(onError)
        val thread = Thread({
            try {
                val buffer = ByteArray(8192)
                while (!closed.get()) {
                    val read = channel.inputStream.read(buffer)
                    if (read < 0) {
                        onShutdown(null)
                        break
                    }
                    if (read > 0) onTerminalBytes(buffer.copyOf(read))
                }
            } catch (t: Throwable) {
                reportTransportError(t)
            }
        }, "ssh-shell-data-channel-reader")
        thread.isDaemon = true
        readerThread = thread
        thread.start()
    }

    private fun installErrorListener(listener: (Throwable) -> Unit) {
        val pending = synchronized(errorLock) {
            errorListener = listener
            pendingError.also { pendingError = null }
        }
        if (pending != null && !closed.get()) listener(pending)
    }

    private fun reportTransportError(t: Throwable) {
        failed.set(true)
        if (closed.get()) return
        val listener = synchronized(errorLock) {
            val current = errorListener
            if (current == null) pendingError = t
            current
        }
        listener?.invoke(t)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            readerThread?.interrupt()
            writerThread.interrupt()
            channel.close()
        }
    }

    companion object {
        fun connect(transport: ReinsSshTransport, cols: Int, rows: Int): SshShellDataChannelSession =
            SshShellDataChannelSession(transport.openShellChannel(cols, rows))
    }
}
