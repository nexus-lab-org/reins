package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Composable
fun HostListScreen(
    hosts: List<Host>,
    identities: List<Identity>,
    onAddHost: () -> Unit,
    onEditHost: (String) -> Unit,
    onDeleteHost: (String) -> Unit,
    onConnect: (String) -> Unit,
    modifier: Modifier = Modifier,
    buildLabel: String? = null,
) {
    val identityNameById = identities.associate { it.id to it.displayName }

    Scaffold(
        modifier = modifier,
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
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(hosts, key = { it.id }) { host ->
                                HostCard(
                                    host = host,
                                    authLabel = when (val authMethod = host.authMethod) {
                                        is HostAuthMethod.Key -> identityNameById[authMethod.identityId] ?: "Unknown identity"
                                        is HostAuthMethod.Password -> "Password"
                                    },
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
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = host.displayName, style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${host.displayName}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${host.displayName}")
                    }
                }
            }

            Text(
                text = "${host.username}@${host.hostname}:${host.port} · ${host.transport.label()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Auth: $authLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onConnect) {
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
