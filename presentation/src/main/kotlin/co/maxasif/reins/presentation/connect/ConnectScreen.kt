package co.maxasif.reins.presentation.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.maxasif.reins.presentation.hostlist.SessionSummary
import co.maxasif.reins.presentation.terminal.SessionSwitcherOverlay
import co.maxasif.reins.presentation.terminal.TerminalScreen
import co.maxasif.reins.presentation.theme.IBMPlexMono
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

private val STEPPER_LABELS = listOf(
    ConnectUiState.Stepper.ResolvingHost::class to "Resolving host",
    ConnectUiState.Stepper.OpeningTransport::class to "Opening transport",
    ConnectUiState.Stepper.AttachingSession::class to "Attaching shell",
)

/**
 * Connect screen: the stepper (ticket 008), the live terminal once [ConnectUiState.Connected], or
 * a fail-loud error. [onDisconnect] is the *only* thing that tears the connection down (ticket
 * 026) - a plain back-press just leaves this screen, never touches the live connection
 * [co.maxasif.reins.connection.ConnectionService] is holding.
 *
 * [sessions] and [currentSessionId] drive the ticket-030 "Minimal Chrome" session switcher: when
 * this Host has more than one session, a small pill/strip overlay lets the user jump to a sibling
 * session (or cycle with a swipe) without leaving this screen - see [SessionSwitcherOverlay]. A
 * dedicated tabs row scoped to the current Host's own sessions was tried and dropped - it duplicated
 * that overlay's pill for the same job, at the cost of real terminal height - so [onNewSession] is
 * reached instead via a plain "+" in the top chrome.
 */
@Composable
fun ConnectScreen(
    state: ConnectUiState,
    sessions: List<SessionSummary>,
    currentSessionId: String,
    onSwitchSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is ConnectUiState.Stepper -> StepperContent(state)
            is ConnectUiState.Connected -> Column(Modifier.fillMaxSize()) {
                // Disconnect and the tabs row live outside the session-switcher overlay so the pill
                // (top-end corner) never has to share space with them - see [SessionSwitcherOverlay]'s
                // doc. statusBarsPadding() here (not on the whole screen) because MainActivity's
                // enableEdgeToEdge() lets the terminal draw behind the status bar deliberately - only
                // this chrome, which sits above the terminal, needs to duck under it.
                Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                    val currentHostDisplayName = sessions.find { it.sessionId == currentSessionId }?.hostDisplayName
                    // Single-row chrome mirroring the redesign's terminal top bar: back arrow, a
                    // live-status dot, the host name, then Disconnect - a plain back press only pops
                    // this screen (the live connection survives), while Disconnect is the one action
                    // that actually tears it down (ticket 026), so the two stay visually distinct
                    // rather than both routing to the same "leave" affordance the mockup collapses
                    // them into.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ReinsSpacing.space2, vertical = ReinsSpacing.space1),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space2),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Lucide.ArrowLeft, contentDescription = "Back")
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                        Text(
                            text = currentHostDisplayName ?: "Unknown host",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = IBMPlexMono,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onNewSession) {
                            Icon(Lucide.Plus, contentDescription = "New session")
                        }
                        TextButton(onClick = onDisconnect) {
                            Text("Disconnect", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                var keySetupNoteVisible by remember(state.keySetupNote) { mutableStateOf(state.keySetupNote != null) }
                if (keySetupNoteVisible && state.keySetupNote != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space1),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.keySetupNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { keySetupNoteVisible = false }) { Text("Dismiss") }
                    }
                }
                SessionSwitcherOverlay(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    onSwitchSession = onSwitchSession,
                    modifier = Modifier.weight(1f),
                ) {
                    TerminalScreen(
                        session = state.session,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            is ConnectUiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(ReinsSpacing.space5),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StepperContent(current: ConnectUiState.Stepper) {
    val currentIndex = STEPPER_LABELS.indexOfFirst { it.first == current::class }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ReinsSpacing.space5),
        verticalArrangement = Arrangement.Center,
    ) {
        STEPPER_LABELS.forEachIndexed { index, (_, label) ->
            val color = when {
                index < currentIndex -> MaterialTheme.colorScheme.onSurfaceVariant
                index == currentIndex -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.outline
            }
            StepperRow(isCurrent = index == currentIndex, isDone = index < currentIndex, label = label, color = color)
        }
    }
}

@Composable
private fun StepperRow(isCurrent: Boolean, isDone: Boolean, label: String, color: Color) {
    Row(
        modifier = Modifier.padding(vertical = ReinsSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
    ) {
        if (isCurrent) {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp))
        } else {
            Text(text = if (isDone) "done" else "-", color = color, style = MaterialTheme.typography.titleMedium)
        }
        Text(text = label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
