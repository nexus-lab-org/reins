package co.maxasif.reins.data.ssh

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider

/**
 * sshj SSH transport: connects, authenticates with an [Identity][co.maxasif.reins.domain.model.Identity]'s
 * key, and opens the Data Channel Reins shows as the terminal - a plain interactive login shell
 * over SSH, exactly what a human typing `ssh user@host` would land in. Reins carries no
 * herdr-specific protocol of its own; whatever the remote shell can run (including `herdr attach`)
 * is up to the person typing at it.
 *
 * Host-key verification is caller-supplied ([TofuHostKeyVerifier] in the real connect path,
 * ticket 009/018's TOFU pinning) rather than hardcoded here, so tests can still use
 * [net.schmizz.sshj.transport.verification.PromiscuousVerifier].
 */
class ReinsSshTransport private constructor(private val client: SSHClient) : AutoCloseable {

    /** Remote `$HOME`, resolved once at connect time. */
    lateinit var remoteHome: String
        private set

    /**
     * Opens the Data Channel: a PTY-backed interactive shell (the remote user's own login shell,
     * same as a plain `ssh user@host` with no command). [cols]/[rows] set the initial terminal
     * geometry; [ShellChannel.resize] renegotiates it later as the on-screen [TerminalView][com.termux.view.TerminalView] resizes.
     */
    fun openShellChannel(cols: Int, rows: Int): ShellChannel {
        val session = client.startSession()
        session.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
        val shell = session.startShell()
        return ShellChannel(session, shell)
    }

    class ShellChannel(private val session: Session, private val shell: Session.Shell) : AutoCloseable {
        val inputStream: InputStream get() = shell.inputStream
        val outputStream: OutputStream get() = shell.outputStream

        fun resize(cols: Int, rows: Int) {
            shell.changeWindowDimensions(cols, rows, 0, 0)
        }

        override fun close() {
            runCatching { shell.close() }
            runCatching { session.close() }
        }
    }

    /**
     * Runs [command] over a plain SSH exec, blocks until it closes stdout (or exits), and returns
     * everything it wrote to stdout. Used for one-shot remote commands whose whole output fits in
     * memory - the `$HOME` resolution below, and (ticket 025) `mosh-server new`'s one-line
     * `MOSH CONNECT <port> <key>` announcement before it daemonizes and closes its own stdout.
     */
    fun execCapture(command: String): String {
        client.startSession().use { session ->
            val exec = session.exec(command)
            val output = exec.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            exec.join()
            return output
        }
    }

    /**
     * Appends [authorizedKeysLine] to the remote user's `~/.ssh/authorized_keys` (creating the
     * directory/file with the right permissions if needed), for the one-time key setup that
     * follows a successful password connect - see `ConnectionService`'s post-connect step.
     *
     * The line is base64-encoded before being interpolated into the remote command so it never
     * needs shell-quoting: base64's alphabet (`[A-Za-z0-9+/=]`) contains no shell metacharacters,
     * so it's safe to splice in unquoted regardless of what the public-key comment contains.
     *
     * A leading `printf '\n'` guarantees this key lands on its own line even if the file doesn't
     * already end in a newline (an earlier version of this method appended the decoded key with
     * no separator at all, silently concatenating it onto whatever byte the file happened to end
     * on - corrupting every key appended after the first into one unparseable `authorized_keys`
     * line). A stray leading blank line is harmless; sshd ignores empty lines.
     */
    fun installAuthorizedKey(authorizedKeysLine: String) {
        val encoded = Base64.getEncoder().encodeToString(authorizedKeysLine.trim().toByteArray(StandardCharsets.UTF_8))
        execCapture(
            "sh -c 'mkdir -p ~/.ssh && chmod 700 ~/.ssh && touch ~/.ssh/authorized_keys && " +
                "printf \"\\n\" >> ~/.ssh/authorized_keys && " +
                "echo $encoded | base64 -d >> ~/.ssh/authorized_keys && " +
                "printf \"\\n\" >> ~/.ssh/authorized_keys && " +
                "chmod 600 ~/.ssh/authorized_keys'",
        )
    }

    override fun close() {
        runCatching { client.disconnect() }
    }

    companion object {
        /** Connects, authenticates with [keyProvider], and resolves the remote `$HOME`. */
        fun connect(
            address: String,
            port: Int,
            username: String,
            keyProvider: KeyProvider,
            hostKeyVerifier: HostKeyVerifier,
        ): ReinsSshTransport {
            val client = SSHClient()
            client.addHostKeyVerifier(hostKeyVerifier)
            client.connect(address, port)
            client.authPublickey(username, keyProvider)
            return finishConnecting(client)
        }

        /**
         * Connects, authenticating with a password instead of a key ([HostAuthMethod.Password][co.maxasif.reins.domain.model.HostAuthMethod.Password]).
         * The password is used once here and never stored - the caller is expected to follow a
         * successful connect with [installAuthorizedKey] and switch the Host to key-based auth.
         */
        fun connectWithPassword(
            address: String,
            port: Int,
            username: String,
            password: String,
            hostKeyVerifier: HostKeyVerifier,
        ): ReinsSshTransport {
            val client = SSHClient()
            client.addHostKeyVerifier(hostKeyVerifier)
            client.connect(address, port)
            client.authPassword(username, password)
            return finishConnecting(client)
        }

        private fun finishConnecting(client: SSHClient): ReinsSshTransport {
            val transport = ReinsSshTransport(client)
            transport.remoteHome = transport.execCapture("sh -c 'printf %s \"\$HOME\"'").trim()
            return transport
        }
    }
}
