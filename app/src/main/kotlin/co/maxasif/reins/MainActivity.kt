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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import co.maxasif.reins.presentation.nav.ReinsDestination
import co.maxasif.reins.presentation.theme.ReinsTheme
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.data.ssh.InvalidPrivateKeyException
import co.maxasif.reins.domain.repository.HostRepository
import kotlinx.coroutines.launch

/**
 * Connection ownership lives in [ConnectionService] (ticket 013/026), not here - this Activity
 * only binds to it and renders whatever [ConnectionService.states] currently says for the Host
 * the nav stack has open. Binding (not just the Service's own `startForegroundService` call made
 * from [ConnectionService.connect]) is what lets this Activity call [ConnectionService.connect]/
 * [ConnectionService.disconnect] and observe per-Host state at all.
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

    fun push(destination: ReinsDestination) {
        backStack = backStack + destination
    }
    fun pop() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    val hosts by hostRepository.observeHosts().collectAsState(initial = emptyList())
    val identities by identityRepository.observeIdentities().collectAsState(initial = emptyList())

    when (val destination = backStack.last()) {
        is ReinsDestination.HostList -> HostListScreen(
            hosts = hosts,
            identities = identities,
            onAddHost = { push(ReinsDestination.HostForm(hostId = null)) },
            onEditHost = { hostId -> push(ReinsDestination.HostForm(hostId = hostId)) },
            onDeleteHost = { hostId -> scope.launch { hostRepository.deleteHost(hostId) } },
            onConnect = { hostId -> push(ReinsDestination.Connect(hostId = hostId)) },
            buildLabel = "v${BuildConfig.VERSION_NAME} · built ${BuildConfig.BUILD_TIMESTAMP}",
        )

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
            val hostId = destination.hostId
            var missingHostError by remember(hostId) { mutableStateOf<String?>(null) }
            var passwordPromptHost by remember(hostId) { mutableStateOf<Host?>(null) }

            LaunchedEffect(hostId) {
                val host = hostRepository.getHost(hostId)
                when {
                    host == null -> missingHostError = "This host no longer exists."
                    // Already connecting/live, or key-based - no password needed to (re)join.
                    connectionService.isLive(hostId) || host.authMethod !is HostAuthMethod.Password ->
                        connectionService.connect(host)
                    else -> passwordPromptHost = host
                }
            }

            passwordPromptHost?.let { host ->
                PasswordPromptDialog(
                    hostDisplayName = host.displayName,
                    onSubmit = { password ->
                        passwordPromptHost = null
                        connectionService.connect(host, password)
                    },
                    onCancel = {
                        passwordPromptHost = null
                        pop()
                    },
                )
            }

            val serviceStates by connectionService.states.collectAsState()
            val state = missingHostError?.let { ConnectUiState.Failed(it) }
                ?: serviceStates[hostId]
                ?: ConnectUiState.Stepper.ResolvingHost

            ConnectScreen(
                state = state,
                onDisconnect = {
                    connectionService.disconnect(hostId)
                    pop()
                },
            )
        }
    }
}
