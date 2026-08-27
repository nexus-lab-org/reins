package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.domain.model.Transport

/**
 * Host List screen (ticket 008/018): add/edit/delete a Host, connect to it.
 *
 * [buildLabel], when non-null, is shown as a small persistent line at the bottom of the screen -
 * a version/build-timestamp string (`BuildConfig.BUILD_TIMESTAMP` from `:app`, the only module
 * that sees `BuildConfig`) so a build installed on a device can be matched to exactly when it was
 * compiled, without needing `adb` or a package-info lookup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    hosts: List<Host>,
    identities: List<Identity>,
    onAddHost: () -> Unit,
    onEditHost: (String) -> Unit,
    onDeleteHost: (String) -> Unit,
    onConnect: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    buildLabel: String? = null,
    /** hostId -> live/connecting session count (ticket 030) - the small badge on each card. */
    sessionCounts: Map<String, Int> = emptyMap(),
) {
    val identityNameById = identities.associate { it.id to it.displayName }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reins") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHost) {
                Icon(Icons.Filled.Add, contentDescription = "Add host")
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (hosts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No hosts yet. Tap + to add one.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(hosts, key = { it.id }) { host ->
                                HostCard(
                                    host = host,
                                    authLabel = when (val authMethod = host.authMethod) {
                                        is HostAuthMethod.Key -> identityNameById[authMethod.identityId] ?: "Unknown identity"
                                        is HostAuthMethod.Password -> "Password"
                                    },
                                    sessionCount = sessionCounts[host.id] ?: 0,
                                    onEdit = { onEditHost(host.id) },
                                    onDelete = { onDeleteHost(host.id) },
                                    onConnect = { onConnect(host.id) },
                                )
                            }
                        }
                    }
                }
                if (buildLabel != null) {
                    Text(
                        text = buildLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HostCard(
    host: Host,
    authLabel: String,
    sessionCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = host.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${host.username}@${host.hostname}:${host.port} · ${host.transport.label()} · $authLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit ${host.displayName}",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete ${host.displayName}",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (sessionCount > 0) {
                    Text(
                        text = if (sessionCount == 1) "1 session" else "$sessionCount sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                TextButton(
                    onClick = onConnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

private fun Transport.label(): String = when (this) {
    Transport.Ssh -> "SSH"
    Transport.Mosh -> "Mosh"
}
