package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A live session's picker-sheet row shape - deliberately not [co.maxasif.reins.connection.ConnectionSession]
 * itself, since `:presentation` cannot depend on `:app`; `MainActivity` maps to this. */
data class SessionSummary(
    val sessionId: String,
    val label: String,
    val statusLabel: String,
)

/**
 * Shown (ticket 030) when "Connect" is tapped on a Host that already has one or more live/
 * connecting sessions - lets the user jump back into one of those, or start another, rather than
 * silently reusing (or refusing to start) a session the way a single-session-per-Host model would.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionPickerSheet(
    hostDisplayName: String,
    sessions: List<SessionSummary>,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = hostDisplayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            sessions.forEach { session ->
                SessionRow(session = session, onClick = { onSelectSession(session.sessionId) })
            }
            SessionRow(
                label = "New session",
                statusLabel = null,
                icon = Icons.Filled.Add,
                onClick = onNewSession,
            )
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    SessionRow(label = session.label, statusLabel = session.statusLabel, icon = null, onClick = onClick)
}

@Composable
private fun SessionRow(label: String, statusLabel: String?, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (icon != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (statusLabel != null) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
