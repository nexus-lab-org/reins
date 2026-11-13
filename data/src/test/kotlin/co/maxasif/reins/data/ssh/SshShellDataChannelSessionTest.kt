package co.maxasif.reins.data.ssh

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SshShellDataChannelSessionTest {
    @Test
    fun `a dropped network reports the write failure instead of killing the writer thread`() {
        val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> uncaught.add(throwable) }

        val reported = AtomicReference<Throwable>()
        val errorArrived = CountDownLatch(1)
        val session = SshShellDataChannelSession(
            FakeShellChannel(
                inputStream = ByteArrayInputStream(ByteArray(0)),
                outputStream = object : OutputStream() {
                    override fun write(b: Int) = throw IOException("Network is unreachable")
                },
            ),
        )
        try {
            session.startReading(
                onTerminalBytes = {},
                onShutdown = {},
                onError = { throwable ->
                    reported.set(throwable)
                    errorArrived.countDown()
                },
            )
            session.sendInput(byteArrayOf('\n'.code.toByte()))

            assertTrue(
                "write failure was never reported to onError",
                errorArrived.await(3, TimeUnit.SECONDS),
            )
            assertTrue(reported.get() is IOException)
            assertTrue(
                "writer thread died with ${uncaught.firstOrNull()}",
                uncaught.isEmpty(),
            )
        } finally {
            session.close()
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }
}

private class FakeShellChannel(
    override val inputStream: InputStream,
    override val outputStream: OutputStream,
) : ShellChannelIo {
    override fun resize(cols: Int, rows: Int) = Unit
    override fun close() = Unit
}
