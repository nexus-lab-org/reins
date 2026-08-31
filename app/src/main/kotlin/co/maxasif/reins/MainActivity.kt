package co.maxasif.reins

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import co.maxasif.reins.connection.ConnectionService
import co.maxasif.reins.data.repository.IdentityRepositoryImpl
import co.maxasif.reins.presentation.connect.ConnectScreen
import co.maxasif.reins.presentation.connect.ConnectUiState
import co.maxasif.reins.presentation.connect.PasswordPromptDialog
import co.maxasif.reins.presentation.hostlist.GenerateKeystoreIdentityScreen
import co.maxasif.reins.presentation.hostlist.HostFormScreen
import co.maxasif.reins.presentation.hostlist.HostListScreen
import co.maxasif.reins.presentation.hostlist.ImportIdentityScreen
import co.maxasif.reins.presentation.hostlist.SessionPickerSheet
import co.maxasif.reins.presentation.hostlist.SessionSummary
import co.maxasif.reins.presentation.nav.ReinsDestination
import co.maxasif.reins.presentation.settings.ExtraKeysSettingsScreen
import co.maxasif.reins.presentation.settings.SettingsScreen
import co.maxasif.reins.presentation.theme.ReinsTheme
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.data.ssh.InvalidPrivateKeyException
import co.maxasif.reins.domain.repository.HostRepository
import kotlinx.coroutines.launch

/**
 * Connection ownership lives in [ConnectionService] (ticket 013/026), not here - this Activity
 * only binds to it and renders whatever [ConnectionService.sessions] currently says for the
 * session the nav stack has open. A Host can have several concurrent sessions (ticket 030); which
 * one is open is decided at the point "Connect" is tapped (directly, or via the session-picker
 * sheet), never by this screen. Binding (not just the Service's own `startForegroundService` call
 * made from [ConnectionService.startNewSession]) is what lets this Activity call
 * [ConnectionService.startNewSession]/[ConnectionService.disconnect] and observe session state at all.
 */
class MainActivity : ComponentActivity() {
    private var connectionService by mutableStateOf<ConnectionService?>(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            connectionService = (binder as ConnectionService.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required for Compose's WindowInsets.ime to report real IME visibility/height, and for
        // the terminal to actually shrink (not just get visually covered) when the keyboard shows
        // - see TerminalScreen's imePadding() usage.
        enableEdgeToEdge()
        bindService(Intent(this, ConnectionService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        val app = application as ReinsApplication
        setContent {
            ReinsTheme {
                val service = connectionService
                if (service == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    ReinsNavHost(
                        hostRepository = app.hostRepository,
                        identityRepository = app.identityRepository,
                        connectionService = service,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        super.onDestroy()
    }
}

@Composable
private fun ReinsNavHost(
    hostRepository: HostRepository,
    identityRepository: IdentityRepositoryImpl,
    connectionService: ConnectionService,
) {
    var backStack by remember { mutableStateOf(listOf<ReinsDestination>(ReinsDestination.HostList)) }
    val scope = rememberCoroutineScope()
    val sessions by connectionService.sessions.collectAsState()

    fun push(destination: ReinsDestination) {
        backStack = backStack + destination
    }
    /** Ticket 030's session switcher (pill/strip/swipe): replaces the current Connect entry in
     * place rather than pushing, so back still pops out of the terminal in one step regardless of
     * how many sibling sessions were visited via the switcher. */
    fun switchSession(sessionId: String) {
        backStack = backStack.dropLast(1) + ReinsDestination.Connect(sessionId)
    }
    fun pop() {
        if (backStack.size > 1) {
            // A session that failed to ever connect has nothing to preserve by staying in
            // ConnectionService's map - leaving it there would only clutter a future session-picker
            // sheet for this Host with a dead entry that can never be resumed.
            val leaving = backStack.last()
            if (leaving is ReinsDestination.Connect && sessions[leaving.sessionId]?.state is ConnectUiState.Failed) {
                connectionService.disconnect(leaving.sessionId)
            }
            backStack = backStack.dropLast(1)
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    val hosts by hostRepository.observeHosts().collectAsState(initial = emptyList())
    val identities by identityRepository.observeIdentities().collectAsState(initial = emptyList())

    var sessionPickerHost by remember { mutableStateOf<Host?>(null) }
    var passwordPromptHost by remember { mutableStateOf<Host?>(null) }

    fun beginNewSession(host: Host) {
        if (host.authMethod is HostAuthMethod.Password) {
            passwordPromptHost = host
        } else {
            push(ReinsDestination.Connect(connectionService.startNewSession(host)))
        }
    }

    /** Ticket 030: a Host with no existing session connects straight through; one with existing
     * sessions offers the picker sheet instead of silently reusing or blocking on the first. */
    fun requestConnect(host: Host) {
        if (sessions.values.none { it.hostId == host.id }) {
            beginNewSession(host)
        } else {
            sessionPickerHost = host
        }
    }

    when (val destination = backStack.last()) {
        is ReinsDestination.HostList -> HostListScreen(
            hosts = hosts,
            identities = identities,
            onAddHost = { push(ReinsDestination.HostForm(hostId = null)) },
            onEditHost = { hostId -> push(ReinsDestination.HostForm(hostId = hostId)) },
            onDeleteHost = { hostId -> scope.launch { hostRepository.deleteHost(hostId) } },
            onConnect = { hostId -> hosts.firstOrNull { it.id == hostId }?.let(::requestConnect) },
            onSettings = { push(ReinsDestination.Settings) },
            buildLabel = "v${BuildConfig.VERSION_NAME} · built ${BuildConfig.BUILD_TIMESTAMP}",
            sessionCounts = sessions.values.groupingBy { it.hostId }.eachCount(),
        )

        is ReinsDestination.Settings -> SettingsScreen(
            onOpenExtraKeys = { push(ReinsDestination.ExtraKeysSettings) },
            onBack = { pop() },
        )

        is ReinsDestination.ExtraKeysSettings -> ExtraKeysSettingsScreen(onBack = { pop() })

        is ReinsDestination.HostForm -> {
            val initialHost = hosts.firstOrNull { it.id == destination.hostId }
            HostFormScreen(
                initialHost = initialHost,
                identities = identities,
                onAddIdentity = { push(ReinsDestination.ImportIdentity) },
                onGenerateIdentity = { push(ReinsDestination.GenerateKeystoreIdentity) },
                onCancel = { pop() },
                onSave = { displayName, username, hostname, port, transport, authMethod ->
                    scope.launch {
                        if (initialHost == null) {
                            hostRepository.createHost(displayName, username, hostname, port, transport, authMethod)
                        } else {
                            hostRepository.updateHost(
                                initialHost.copy(
                                    displayName = displayName,
                                    username = username,
                                    hostname = hostname,
                                    port = port,
                                    transport = transport,
                                    authMethod = authMethod,
                                ),
                            )
                        }
                        pop()
                    }
                },
            )
        }

        is ReinsDestination.ImportIdentity -> {
            var errorMessage by remember { mutableStateOf<String?>(null) }
            ImportIdentityScreen(
                errorMessage = errorMessage,
                onCancel = { pop() },
                onImport = { displayName, privateKeyPem, passphrase ->
                    scope.launch {
                        try {
                            identityRepository.importKeyIdentity(displayName, privateKeyPem, passphrase)
                            errorMessage = null
                            pop()
                        } catch (e: InvalidPrivateKeyException) {
                            errorMessage = e.message
                        }
                    }
                },
            )
        }

        is ReinsDestination.GenerateKeystoreIdentity -> {
            var isGenerating by remember { mutableStateOf(false) }
            var authorizedKeysLine by remember { mutableStateOf<String?>(null) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            GenerateKeystoreIdentityScreen(
                isGenerating = isGenerating,
                authorizedKeysLine = authorizedKeysLine,
                errorMessage = errorMessage,
                onGenerate = { displayName ->
                    scope.launch {
                        isGenerating = true
                        errorMessage = null
                        try {
                            val identity = identityRepository.createKeystoreIdentity(displayName) as Identity.KeystoreIdentity
                            authorizedKeysLine = identityRepository.exportAuthorizedKeysLine(identity)
                        } catch (t: Throwable) {
                            errorMessage = t.message ?: t.javaClass.simpleName
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                onDone = { pop() },
                onCancel = { pop() },
            )
        }

        is ReinsDestination.Connect -> {
            val sessionId = destination.sessionId
            // The session already exists by the time this destination is pushed - startNewSession
            // registers it synchronously before returning the id `beginNewSession` pushes with -
            // this screen only observes, it never starts a connection itself (ticket 030).
            val state = sessions[sessionId]?.state ?: ConnectUiState.Stepper.ResolvingHost
            // Global, not per-host (ticket 030 follow-up): every live session across every host is
            // switchable from any terminal, sorted by host name then session label so the strip
            // reads in a stable order regardless of connection sequence.
            val hostNameById = hosts.associate { it.id to it.displayName }
            val allSessions = sessions.values.sortedWith(compareBy({ hostNameById[it.hostId] ?: "" }, { it.label }))

            ConnectScreen(
                state = state,
                sessions = allSessions.map {
                    SessionSummary(it.sessionId, hostNameById[it.hostId] ?: "Unknown host", it.label, it.state.statusLabel())
                },
                currentSessionId = sessionId,
                onSwitchSession = ::switchSession,
                onNewSession = {
                    val hostId = sessions[sessionId]?.hostId
                    hosts.find { it.id == hostId }?.let(::beginNewSession)
                },
                onDisconnect = {
                    connectionService.disconnect(sessionId)
                    pop()
                },
                onBack = { pop() },
            )
        }
    }

    sessionPickerHost?.let { host ->
        val hostSessions = sessions.values.filter { it.hostId == host.id }.sortedBy { it.label }
        SessionPickerSheet(
            hostDisplayName = host.displayName,
            sessions = hostSessions.map { SessionSummary(it.sessionId, host.displayName, it.label, it.state.statusLabel()) },
            onSelectSession = { sessionId ->
                sessionPickerHost = null
                push(ReinsDestination.Connect(sessionId))
            },
            onNewSession = {
                sessionPickerHost = null
                beginNewSession(host)
            },
            onDismiss = { sessionPickerHost = null },
        )
    }

    passwordPromptHost?.let { host ->
        PasswordPromptDialog(
            hostDisplayName = host.displayName,
            onSubmit = { password ->
                passwordPromptHost = null
                push(ReinsDestination.Connect(connectionService.startNewSession(host, password)))
            },
            onCancel = { passwordPromptHost = null },
        )
    }
}

private fun ConnectUiState.statusLabel(): String = when (this) {
    is ConnectUiState.Stepper -> "Connecting…"
    is ConnectUiState.Connected -> "Connected"
    is ConnectUiState.Failed -> "Failed"
}
