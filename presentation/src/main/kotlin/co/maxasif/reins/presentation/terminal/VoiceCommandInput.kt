package co.maxasif.reins.presentation.terminal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/** Where [VoiceCommandInput] is in the tap mic -> listen -> review -> redo/send flow (ticket 024). */
private sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data object Transcribing : VoiceState
    data class Review(val transcript: String) : VoiceState
}

/**
 * The Voice mode input for Ad Hoc Commands (ticket 024, per CONTEXT.md): mic button -> listening
 * indicator -> transcript shown for review -> redo (discard, listen again) or send. [onSubmit] is
 * called with the reviewed transcript exactly like [AdHocCommandInput]'s [onSubmit] - the caller
 * decides what "submitted" means for the Data Channel (see [TerminalScreen]), so voice and
 * keyboard input converge on the identical send path per ticket 023.
 */
@Composable
fun VoiceCommandInput(transcriber: VoiceTranscriber, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<VoiceState>(VoiceState.Idle) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startListening(transcriber) { state = it } }

    fun requestAndStart() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startListening(transcriber) { state = it }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopListening() {
        state = VoiceState.Transcribing
        scope.launch {
            val transcript = transcriber.stopAndTranscribe()
            state = VoiceState.Review(transcript)
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when (val current = state) {
                is VoiceState.Idle -> {
                    Text("Tap to speak a command", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { requestAndStart() }) {
                        Icon(imageVector = Icons.Filled.Mic, contentDescription = "Start voice command")
                    }
                }

                is VoiceState.Listening -> {
                    Text("Listening…", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { stopListening() }) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop listening")
                    }
                }

                is VoiceState.Transcribing -> {
                    Text("Transcribing…", style = MaterialTheme.typography.bodyMedium)
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                }

                is VoiceState.Review -> {
                    Text(
                        text = current.transcript.ifBlank { "(no speech recognized)" },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = { requestAndStart() }) {
                        Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Redo voice command")
                    }
                    IconButton(
                        onClick = {
                            if (current.transcript.isNotBlank()) {
                                onSubmit(current.transcript)
                                state = VoiceState.Idle
                            }
                        },
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice command")
                    }
                }
            }
        }
    }
}

private fun startListening(transcriber: VoiceTranscriber, setState: (VoiceState) -> Unit) {
    transcriber.start()
    setState(VoiceState.Listening)
}
