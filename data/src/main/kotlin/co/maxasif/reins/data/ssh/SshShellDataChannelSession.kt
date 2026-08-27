package co.maxasif.reins.data.ssh

import co.maxasif.reins.data.DataChannelSession
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

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
class SshShellDataChannelSession private constructor(
    private val channel: ReinsSshTransport.ShellChannel,
) : DataChannelSession {

    private sealed class WriterCommand {
        data class Input(val data: ByteArray) : WriterCommand()
        data class Resize(val cols: Int, val rows: Int) : WriterCommand()
    }

    private val closed = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private val writerQueue = LinkedBlockingQueue<WriterCommand>()
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
        }
    }, "ssh-shell-data-channel-writer").apply {
        isDaemon = true
        start()
    }

    override fun sendInput(data: ByteArray) {
        writerQueue.put(WriterCommand.Input(data))
    }

    override fun sendResize(cols: Int, rows: Int) {
        writerQueue.put(WriterCommand.Resize(cols, rows))
    }

    override fun startReading(onTerminalBytes: (ByteArray) -> Unit, onShutdown: (String?) -> Unit, onError: (Throwable) -> Unit) {
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
                if (!closed.get()) onError(t)
            }
        }, "ssh-shell-data-channel-reader")
        thread.isDaemon = true
        readerThread = thread
        thread.start()
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
