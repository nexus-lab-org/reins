package co.maxasif.reins.connection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.AssetManager
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
import co.maxasif.reins.data.voice.VoiceRecorder
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.domain.model.Transport
import co.maxasif.reins.domain.repository.HostRepository
import co.maxasif.reins.presentation.connect.ConnectUiState
import co.maxasif.reins.presentation.terminal.ReinsTerminalSessionClient
import co.maxasif.reins.voice.AppVoiceTranscriber
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

/** Fixed geometry for the Data Channel handshake (ticket 017). Live resize-to-remote
 * synchronization as [com.termux.view.TerminalView] measures its own size is follow-up ticket work. */
private const val INITIAL_COLS = 80
private const val INITIAL_ROWS = 24

private const val NOTIFICATION_CHANNEL_ID = "reins_live_connections"
private const val NOTIFICATION_ID = 1

/**
 * The foreground `Service` decided in [ticket 013](013-connection-lifecycle-ownership.md) and
 * built out here (ticket 026): a connection manager keyed by [Host.id], owning every live
 * connection's [LiveConnection] - independent native Mosh/SSH handles and JNI threading per Host,
 * none of it tied to any Activity/ViewModel/Composable. Leaving the Terminal screen is a UI-stack
 * pop in `:app`'s nav host and never touches this map; only [disconnect] does.
 *
 * Bound by [MainActivity] for the [states] flow and the [connect]/[disconnect] calls, and started
 * (via `startForegroundService`) the first time a connection is requested so it outlives the
 * Activity being destroyed or the app being backgrounded - the whole point of ticket 013's
 * Service-over-ViewModel decision. Stops itself once the last live connection is gone.
 */
class ConnectionService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveConnections = mutableMapOf<String, LiveConnection>()

    private val _states = MutableStateFlow<Map<String, ConnectUiState>>(emptyMap())

    /** Per-Host UI state (stepper / connected / failed), keyed by [Host.id] - `:app`'s nav host observes this directly. */
    val states: StateFlow<Map<String, ConnectUiState>> = _states.asStateFlow()

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

    fun isLive(hostId: String): Boolean = liveConnections.containsKey(hostId)

    /**
     * Starts (or no-ops onto an already-live/connecting) [host]'s connection. Multiple Hosts can
     * be mid-connect or live at once - each gets its own entry in [states] and, once connected,
     * its own [LiveConnection] with independent native handles (ticket 026's core requirement).
     *
     * [password] is required (and only used) when [Host.authMethod] is [HostAuthMethod.Password] -
     * the caller (`MainActivity`'s Connect destination) collects it from the user right before
     * calling this, and it's never persisted. A successful password connect upgrades the Host to
     * key-based auth - see [openConnection]'s post-connect step.
     */
    fun connect(host: Host, password: String? = null) {
        if (liveConnections.containsKey(host.id)) {
            updateState(host.id, liveConnections.getValue(host.id).connected)
            return
        }
        if (_states.value[host.id] is ConnectUiState.Stepper) return

        // Marks the Service "started" (independent of any Activity's bind) so it survives
        // navigation, backgrounding, and the Activity being destroyed - ticket 013's whole point.
        startForegroundService(Intent(this, ConnectionService::class.java))

        val app = application as ReinsApplication
        updateState(host.id, ConnectUiState.Stepper.ResolvingHost)
        serviceScope.launch {
            try {
                val resources = openConnection(
                    host = host,
                    password = password,
                    hostRepository = app.hostRepository,
                    identityRepository = app.identityRepository,
                    assets = assets,
                    onStep = { step -> updateState(host.id, step) },
                )
                liveConnections[host.id] = LiveConnection(
                    hostId = host.id,
                    displayName = host.displayName,
                    connected = resources.connected,
                    transport = resources.transport,
                    dataChannel = resources.dataChannel,
                    connectionScope = resources.connectionScope,
                )
                updateState(host.id, resources.connected)
                refreshNotification()
            } catch (t: Throwable) {
                updateState(host.id, ConnectUiState.Failed(connectFailureMessage(t, host)))
            }
        }
    }

    /** Explicit per-Host disconnect (ticket 026) - tears down only [hostId]'s channels, never another live connection's. */
    fun disconnect(hostId: String) {
        liveConnections.remove(hostId)?.close()
        _states.update { it - hostId }
        if (liveConnections.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            refreshNotification()
        }
    }

    private fun updateState(hostId: String, state: ConnectUiState) {
        _states.update { it + (hostId to state) }
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

    /** Single expandable/summary notification listing active hosts, per ticket 013's resolution - not one notification per host. */
    private fun buildNotification(): Notification {
        val liveNames = liveConnections.values.map { it.displayName }
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
    assets: AssetManager,
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
    val session = withContext(Dispatchers.Main) { TerminalSession(ReinsTerminalSessionClient(), null) }
    session.updateSize(INITIAL_COLS, INITIAL_ROWS, 0, 0)
    session.setRemoteWriteCallback { data, offset, count ->
        dataChannel.sendInput(data.copyOfRange(offset, offset + count))
    }
    dataChannel.startReading(
        onTerminalBytes = { bytes -> session.feedIncoming(bytes, bytes.size) },
        onShutdown = { session.finishIfRunning() },
        onError = { session.finishIfRunning() },
    )

    // This Host's own scope (ticket 013/026: independent JNI threading per connection) - the
    // Voice controller below dispatches onto it, and it's cancelled by LiveConnection.close() on
    // disconnect without touching any other Host's scope.
    val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val voiceTranscriber = AppVoiceTranscriber(VoiceRecorder(assets), connectionScope)

    ConnectedResources(
        transport = transport,
        dataChannel = dataChannel,
        connectionScope = connectionScope,
        connected = ConnectUiState.Connected(session, voiceTranscriber, keySetupNote),
    )
}
