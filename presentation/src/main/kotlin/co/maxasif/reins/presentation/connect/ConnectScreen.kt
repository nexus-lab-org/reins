package co.maxasif.reins.presentation.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.maxasif.reins.presentation.terminal.TerminalScreen

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
 */
@Composable
fun ConnectScreen(state: ConnectUiState, onDisconnect: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is ConnectUiState.Stepper -> StepperContent(state)
            is ConnectUiState.Connected -> Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDisconnect) { Text("Disconnect") }
                }
                var keySetupNoteVisible by remember(state.keySetupNote) { mutableStateOf(state.keySetupNote != null) }
                if (keySetupNoteVisible && state.keySetupNote != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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
                TerminalScreen(
                    session = state.session,
                    voiceTranscriber = state.voiceTranscriber,
                    modifier = Modifier.weight(1f),
                )
            }
            is ConnectUiState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(24.dp),
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
            .padding(24.dp),
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
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isCurrent) {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp))
        } else {
            Text(text = if (isDone) "done" else "-", color = color, style = MaterialTheme.typography.titleMedium)
        }
        Text(text = label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
