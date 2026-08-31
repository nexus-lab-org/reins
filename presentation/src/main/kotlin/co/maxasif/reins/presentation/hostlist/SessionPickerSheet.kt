package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import co.maxasif.reins.presentation.theme.IBMPlexMono
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/** A live session's picker-sheet row shape - deliberately not [co.maxasif.reins.connection.ConnectionSession]
 * itself, since `:presentation` cannot depend on `:app`; `MainActivity` maps to this.
 * [hostDisplayName] disambiguates sessions across different hosts in the terminal's global
 * session switcher (ticket 030) - the [SessionPickerSheet] itself doesn't need it since it's
 * already scoped to one host, but carries it anyway for a single shared shape. */
data class SessionSummary(
    val sessionId: String,
    val hostDisplayName: String,
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
        Column(modifier = Modifier.padding(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space2)) {
            Text(
                text = hostDisplayName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = ReinsSpacing.space3),
            )
            sessions.forEach { session ->
                SessionRow(session = session, onClick = { onSelectSession(session.sessionId) })
            }
            Surface(onClick = onNewSession, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ReinsSpacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
                ) {
                    Icon(imageVector = Lucide.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "New session",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit) {
    Column {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
            ) {
                Text(
                    text = session.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = session.statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
                        .padding(horizontal = ReinsSpacing.space2, vertical = 4.dp),
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}
