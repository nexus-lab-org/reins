package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.domain.model.Transport
import co.maxasif.reins.presentation.theme.HankenGrotesk
import co.maxasif.reins.presentation.theme.IBMPlexMono
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2

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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddHost,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Lucide.Plus, contentDescription = "Add host")
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ReinsSpacing.space5, vertical = ReinsSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Reins",
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = HankenGrotesk),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = onSettings) {
                            Icon(Lucide.Settings, contentDescription = "Settings")
                        }
                    }
                }
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
                            contentPadding = PaddingValues(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space3),
                            verticalArrangement = Arrangement.spacedBy(ReinsSpacing.space2),
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
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = IBMPlexMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space1),
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
    val isLive = sessionCount > 0
    val dotColor = if (isLive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    val statusLabel = if (!isLive) "Idle" else if (sessionCount == 1) "1 session" else "$sessionCount sessions"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space2)) {
                    Box(modifier = Modifier.size(7.dp).background(dotColor, CircleShape))
                    Text(
                        text = host.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = dotColor,
                        modifier = Modifier
                            .background(dotColor.copy(alpha = 0.14f), RoundedCornerShape(100.dp))
                            .padding(horizontal = ReinsSpacing.space2, vertical = 3.dp),
                    )
                }
                Text(
                    text = "${host.username}@${host.hostname}:${host.port} · ${host.transport.label()} · $authLabel",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = IBMPlexMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = ReinsSpacing.space1),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Lucide.Pencil,
                    contentDescription = "Edit ${host.displayName}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Lucide.Trash2,
                    contentDescription = "Delete ${host.displayName}",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val connectContainer = if (isLive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary
            val connectContent = if (isLive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary
            TextButton(
                onClick = onConnect,
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    containerColor = connectContainer,
                    contentColor = connectContent,
                ),
                contentPadding = PaddingValues(horizontal = ReinsSpacing.space3, vertical = ReinsSpacing.space1),
            ) {
                Text(if (isLive) "Open" else "Connect", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun Transport.label(): String = when (this) {
    Transport.Ssh -> "SSH"
    Transport.Mosh -> "Mosh"
}
