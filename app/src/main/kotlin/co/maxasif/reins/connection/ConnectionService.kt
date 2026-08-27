package co.maxasif.reins.connection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import co.maxasif.reins.MainActivity
import co.maxasif.reins.ReinsApplication
import co.maxasif.reins.data.DataChannelSession
import co.maxasif.reins.data.mosh.MoshDataChannelSession
import co.maxasif.reins.data.repository.IdentityRepositoryImpl
import co.maxasif.reins.data.ssh.HostKeyMismatchException
import co.maxasif.reins.data.ssh.ReinsSshTransport
import co.maxasif.reins.data.ssh.SshKeyProviders
import co.maxasif.reins.data.ssh.SshShellDataChannelSession
import co.maxasif.reins.data.ssh.TofuHostKeyVerifier
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.domain.model.Transport
import co.maxasif.reins.domain.repository.HostRepository
import co.maxasif.reins.presentation.connect.ConnectUiState
import co.maxasif.reins.presentation.terminal.ReinsTerminalSessionClient
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Fixed geometry for the Data Channel handshake (ticket 017). Live resize-to-remote
 * synchronization as [com.termux.view.TerminalView] measures its own size is follow-up ticket work. */
private const val INITIAL_COLS = 80
private const val INITIAL_ROWS = 24

private const val NOTIFICATION_CHANNEL_ID = "reins_live_connections"
private const val NOTIFICATION_ID = 1

/**
 * One connection attempt/session, independent of every other session even for the same [hostId] -
 * a user can run several concurrent sessions against one Host (e.g. one for editing, one for a
 * long-running command), each with its own native SSH/Mosh handle. [label] is a per-Host ordinal
 * ("Session 1", "Session 2", ...) assigned at creation and stable for the process lifetime, even
 * if earlier sessions for that Host have since closed - it's what the session-picker sheet and the
 * notification show, not an identifier anything keys off of (that's [sessionId]).
 */
data class ConnectionSession(
    val sessionId: String,
    val hostId: String,
    val label: String,
    val state: ConnectUiState,
)

/**
 * The foreground `Service` decided in [ticket 013](013-connection-lifecycle-ownership.md), built
 * out in ticket 026 keyed by [Host.id], and re-keyed by session id (ticket 030) to allow several
 * concurrent sessions against the same Host - each [ConnectionSession] owns an independent
 * [LiveConnection] (native Mosh/SSH handle and JNI threading), none of it tied to any
 * Activity/ViewModel/Composable. Leaving the Terminal screen is a UI-stack pop in `:app`'s nav host
 * and never touches this map; only [disconnect] does.
 *
 * Bound by [MainActivity] for the [sessions] flow and the [startNewSession]/[disconnect] calls, and
 * started (via `startForegroundService`) the first time a connection is requested so it outlives
 * the Activity being destroyed or the app being backgrounded - the whole point of ticket 013's
 * Service-over-ViewModel decision. Stops itself once the last live connection is gone.
 */
class ConnectionService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveConnections = mutableMapOf<String, LiveConnection>()

    /** Per-Host count of sessions ever created this process lifetime - feeds [ConnectionSession.label]. */
    private val sessionOrdinals = mutableMapOf<String, Int>()

    private val _sessions = MutableStateFlow<Map<String, ConnectionSession>>(emptyMap())

    /** Every session (any [ConnectUiState]), keyed by session id - `:app`'s nav host observes this directly. */
    val sessions: StateFlow<Map<String, ConnectionSession>> = _sessions.asStateFlow()

    inner class LocalBinder : Binder() {
        val service: ConnectionService get() = this@ConnectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called promptly after startForegroundService() regardless of whether any
        // connection has finished (or even started) yet - the notification is refreshed as soon
        // as the live-host set actually changes (see refreshNotification()).
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        liveConnections.values.forEach { it.close() }
        liveConnections.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** Every live-or-connecting session for [hostId], oldest first - what the session-picker sheet lists. */
    fun sessionsForHost(hostId: String): List<ConnectionSession> =
        _sessions.value.values.filter { it.hostId == hostId }.sortedBy { it.label }

    /**
     * Always starts a brand-new session against [host], independent of any other session already
     * live for that same Host (ticket 030 - multiple concurrent sessions per Host). Returns the new
     * session's id immediately so the caller can navigate to it before the connect finishes; its
     * progress streams through [sessions].
     *
     * [password] is required (and only used) when [Host.authMethod] is [HostAuthMethod.Password] -
     * the caller (`MainActivity`'s connect-request flow) collects it from the user right before
     * calling this, and it's never persisted. A successful password connect upgrades the Host to
     * key-based auth - see [openConnection]'s post-connect step.
     */
    fun startNewSession(host: Host, password: String? = null): String {
        val sessionId = UUID.randomUUID().toString()
        val ordinal = (sessionOrdinals[host.id] ?: 0) + 1
        sessionOrdinals[host.id] = ordinal
        val label = "Session $ordinal"

        // Marks the Service "started" (independent of any Activity's bind) so it survives
        // navigation, backgrounding, and the Activity being destroyed - ticket 013's whole point.
        startForegroundService(Intent(this, ConnectionService::class.java))

        val app = application as ReinsApplication
        updateSession(sessionId, hostId = host.id, label = label, state = ConnectUiState.Stepper.ResolvingHost)
        serviceScope.launch {
            try {
                val resources = openConnection(
                    host = host,
                    password = password,
                    hostRepository = app.hostRepository,
                    identityRepository = app.identityRepository,
                    appContext = applicationContext,
                    onStep = { step -> updateSession(sessionId, host.id, label, step) },
                )
                liveConnections[sessionId] = LiveConnection(
                    hostId = host.id,
                    displayName = host.displayName,
                    connected = resources.connected,
                    transport = resources.transport,
                    dataChannel = resources.dataChannel,
                    connectionScope = resources.connectionScope,
                )
                updateSession(sessionId, host.id, label, resources.connected)
                refreshNotification()
            } catch (t: Throwable) {
                updateSession(sessionId, host.id, label, ConnectUiState.Failed(connectFailureMessage(t, host)))
            }
        }
        return sessionId
    }

    /** Explicit per-session disconnect (ticket 026/030) - tears down only [sessionId]'s channels, never another session's. */
    fun disconnect(sessionId: String) {
        liveConnections.remove(sessionId)?.close()
        _sessions.update { it - sessionId }
        if (liveConnections.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            refreshNotification()
        }
    }

    private fun updateSession(sessionId: String, hostId: String, label: String, state: ConnectUiState) {
        _sessions.update { it + (sessionId to ConnectionSession(sessionId, hostId, label, state)) }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Live connections",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shows which hosts Reins is currently connected to." }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Single expandable/summary notification listing active hosts, per ticket 013's resolution -
     * not one notification per host, and not one per session either: several sessions against the
     * same Host collapse into a single "(n)" count rather than repeating the Host's name.
     */
    private fun buildNotification(): Notification {
        val sessionCountsByHost = liveConnections.values.groupingBy { it.hostId }.eachCount()
        val hostNames = liveConnections.values.associateBy({ it.hostId }, { it.displayName })
        val liveNames = sessionCountsByHost.map { (hostId, count) ->
            val name = hostNames[hostId] ?: hostId
            if (count > 1) "$name ($count)" else name
        }
        val text = if (liveNames.isEmpty()) "No active connections" else "Connected: ${liveNames.joinToString(", ")}"
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Reins")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openAppIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }
}

/**
 * Java's own exception messages for the most common connect failures are either blank or just the
 * raw hostname (e.g. [java.net.UnknownHostException]'s message is literally just the hostname with
 * no explanation) - not enough for [ConnectUiState.Failed] to show anything useful. This maps the
 * common cases to a message that actually explains what went wrong.
 */
private fun connectFailureMessage(t: Throwable, host: Host): String = when (t) {
    is java.net.UnknownHostException ->
        "Could not resolve host \"${host.hostname}\" - check the address is correct."
    is java.net.ConnectException ->
        "Could not connect to ${host.hostname}:${host.port} - ${t.message ?: "connection refused"}."
    is java.net.SocketTimeoutException ->
        "Connection to ${host.hostname}:${host.port} timed out."
    is HostKeyMismatchException ->
        t.message ?: "Host key mismatch."
    else -> t.message ?: t.javaClass.simpleName
}

/** The pieces of a successful connect that [ConnectionService] needs to build a [LiveConnection]. */
private class ConnectedResources(
    val transport: ReinsSshTransport,
    val dataChannel: DataChannelSession,
    val connectionScope: CoroutineScope,
    val connected: ConnectUiState.Connected,
)

/**
 * The full connect orchestration moved out of `MainActivity` (tickets 017/018) into this Service
 * (ticket 026): sshj transport, the plain-shell Data Channel, and `TerminalSession` wiring.
 * Preserves both transports (ticket 025's Mosh Data Channel alongside SSH's) and both Identity
 * variants (ticket 020's `KeystoreIdentity` alongside `ImportedKeyIdentity`) exactly as
 * `MainActivity.connectToHost` grew to handle them - only where this logic runs changed, not what
 * it does. [password] auth and the one-time key-setup-on-success it triggers are new here.
 *
 * Reins carries no herdr-specific protocol: both transports land the user in a plain interactive
 * shell, the same as typing `ssh user@host` (or `mosh user@host`) themselves. Whatever they want
 * to run remotely - `herdr attach`, `tmux attach`, anything else - they type once connected.
 */
private suspend fun openConnection(
    host: Host,
    password: String?,
    hostRepository: HostRepository,
    identityRepository: IdentityRepositoryImpl,
    appContext: Context,
    onStep: (ConnectUiState.Stepper) -> Unit,
): ConnectedResources = withContext(Dispatchers.IO) {
    onStep(ConnectUiState.Stepper.ResolvingHost)

    onStep(ConnectUiState.Stepper.OpeningTransport)
    var unpinnedFingerprint: String? = null
    var mismatch: HostKeyMismatchException? = null
    val hostKeyVerifier = TofuHostKeyVerifier(
        pinnedFingerprint = host.hostKeyFingerprint,
        onUnpinned = { fingerprint -> unpinnedFingerprint = fingerprint },
        onMismatch = { presented, pinned -> mismatch = HostKeyMismatchException(presented, pinned) },
    )
    val transport = try {
        when (val authMethod = host.authMethod) {
            is HostAuthMethod.Key -> {
                val identity = identityRepository.getIdentity(authMethod.identityId)
                    ?: error("This host's identity no longer exists.")
                val keyProvider = when (identity) {
                    is Identity.ImportedKeyIdentity -> {
                        val signingMaterial = identityRepository.loadSigningMaterial(identity.id)
                        SshKeyProviders.load(signingMaterial.privateKeyPem, signingMaterial.passphrase)
                    }
                    is Identity.KeystoreIdentity -> identityRepository.keyProviderFor(identity)
                }
                ReinsSshTransport.connect(
                    address = host.hostname,
                    port = host.port,
                    username = host.username,
                    keyProvider = keyProvider,
                    hostKeyVerifier = hostKeyVerifier,
                )
            }
            is HostAuthMethod.Password -> {
                requireNotNull(password) { "Password auth requires a password." }
                ReinsSshTransport.connectWithPassword(
                    address = host.hostname,
                    port = host.port,
                    username = host.username,
                    password = password,
                    hostKeyVerifier = hostKeyVerifier,
                )
            }
        }
    } catch (t: Throwable) {
        throw mismatch ?: t
    }
    if (host.hostKeyFingerprint == null) {
        unpinnedFingerprint?.let { hostRepository.pinHostKeyFingerprint(host.id, it) }
    }

    // One-time key setup: a password-authenticated connect generates a fresh on-device
    // Keystore identity, installs its public key on the remote, and upgrades the Host to
    // key-based auth - so the password is only ever needed for this first connect. Best-effort:
    // the connection the user is waiting on already succeeded via password, so a failure here
    // (no ~/.ssh write access, read-only home, ...) is swallowed rather than failing the connect.
    var keySetupNote: String? = null
    if (host.authMethod is HostAuthMethod.Password) {
        runCatching {
            val identity = identityRepository.createKeystoreIdentity("${host.displayName} (auto)") as Identity.KeystoreIdentity
            val authorizedKeysLine = identityRepository.exportAuthorizedKeysLine(identity)
            transport.installAuthorizedKey(authorizedKeysLine)
            hostRepository.updateHost(host.copy(authMethod = HostAuthMethod.Key(identity.id)))
            keySetupNote = "Key-based login set up - future connects won't need the password."
        }
    }

    onStep(ConnectUiState.Stepper.AttachingSession)
    val dataChannel: DataChannelSession = when (host.transport) {
        Transport.Ssh -> SshShellDataChannelSession.connect(
            transport = transport,
            cols = INITIAL_COLS,
            rows = INITIAL_ROWS,
        )
        Transport.Mosh -> MoshDataChannelSession.connect(
            transport = transport,
            hostAddress = host.hostname,
            cols = INITIAL_COLS,
            rows = INITIAL_ROWS,
        )
    }

    // TerminalSession's constructor creates an android.os.Handler, which requires a thread with a
    // prepared Looper - only the main thread qualifies here, unlike the rest of this function
    // which deliberately runs on Dispatchers.IO for the blocking SSH/socket calls above.
    val session = withContext(Dispatchers.Main) { TerminalSession(ReinsTerminalSessionClient(appContext), null) }
    session.updateSize(INITIAL_COLS, INITIAL_ROWS, 0, 0)
    session.setRemoteWriteCallback { data, offset, count ->
        dataChannel.sendInput(data.copyOfRange(offset, offset + count))
    }
    // TerminalView.updateSize() reflows the local emulator whenever its real measured size changes
    // (e.g. the IME showing/hiding, ExtraKeysRow appearing) - without also forwarding that here, the
    // remote PTY (and anything sizing its own layout off it, like herdr) never learns the phone's
    // actual, usually much narrower than 80x24, dimensions.
    session.setResizeCallback { cols, rows -> dataChannel.sendResize(cols, rows) }
    dataChannel.startReading(
        onTerminalBytes = { bytes -> session.feedIncoming(bytes, bytes.size) },
        onShutdown = { session.finishIfRunning() },
        onError = { session.finishIfRunning() },
    )

    // This Host's own scope (ticket 013/026: independent JNI threading per connection),
    // cancelled by LiveConnection.close() on disconnect without touching any other Host's scope.
    val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    ConnectedResources(
        transport = transport,
        dataChannel = dataChannel,
        connectionScope = connectionScope,
        connected = ConnectUiState.Connected(session, keySetupNote),
    )
}
