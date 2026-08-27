package co.maxasif.reins.presentation.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView

/**
 * Which input surface is overlaid on the terminal (ticket 008's toggle): the Ad Hoc Command
 * Keyboard field (023), the Ad Hoc Command Voice field (024), or neither - the bare terminal,
 * which is still directly typeable via the soft keyboard exactly as before any mode existed.
 */
private enum class InputMode { NONE, KEYBOARD, VOICE }

/**
 * Hosts the Data Channel PTY: a vendored Termux [TerminalView] wrapped in [AndroidView] (ticket
 * 002's recommended integration path - reuse the mature View rather than reimplementing terminal
 * rendering in Compose Canvas). [session] is constructed and wired to the remote SSH/Mosh shell by
 * `:app` (the only module that can see both `:data`'s SSH classes and this `:terminal-view`-backed
 * screen) - this composable only hosts it.
 *
 * [InputMode] switches the bottom of the screen between the keyboard-driven terminal alone, the Ad
 * Hoc Command Keyboard field (ticket 023), and the Ad Hoc Command Voice field (ticket 024,
 * [voiceTranscriber] hides the whisper.cpp/AudioRecord details) - both writing straight to
 * [session], the same [TerminalSession.write] path a physically-typed keystroke takes, so both
 * Keyboard and Voice mode are indistinguishable from typing on the remote end, and from each other
 * once the text is submitted (per CONTEXT.md's Ad Hoc Command definition: voice is a second way to
 * produce the text, not a separate command path). Switching modes never touches [session] or the
 * underlying [TerminalView] - only which overlay is drawn on top - so it can never disrupt the
 * live connection (ticket 008's "leaving Terminal is a UI-stack pop only" applies here too, even
 * more so since this doesn't even leave the screen).
 */
@Composable
fun TerminalScreen(
    session: TerminalSession,
    voiceTranscriber: VoiceTranscriber,
    modifier: Modifier = Modifier,
) {
    val viewClient = remember { ReinsTerminalViewClient() }
    var inputMode by remember { mutableStateOf(InputMode.NONE) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TerminalView(context, null).apply {
                    setTerminalViewClient(viewClient)
                    setTextSize(36)
                    attachSession(session)
                    // TerminalView never repaints on its own - something has to call
                    // onScreenUpdated() whenever new PTY bytes land. Upstream Termux does this by
                    // having its TerminalSessionClient.onTextChanged reach back into the attached
                    // view; ReinsTerminalSessionClient (built in :app, before any TerminalView
                    // exists to reference) is a no-op there, so wire it up here instead, once the
                    // view actually exists. feedIncoming runs off the Data Channel's reader thread,
                    // so hop back to the main thread via View.post before touching the view.
                    session.updateTerminalSessionClient(object : TerminalSessionClient by ReinsTerminalSessionClient() {
                        override fun onTextChanged(changedSession: TerminalSession) {
                            post { onScreenUpdated() }
                        }
                    })
                }
            },
        )

        when (inputMode) {
            InputMode.KEYBOARD -> AdHocCommandInput(
                onSubmit = { text -> session.write(text + "\r") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            InputMode.VOICE -> VoiceCommandInput(
                transcriber = voiceTranscriber,
                onSubmit = { text -> session.write(text + "\r") },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            InputMode.NONE -> Unit
        }

        InputModeToggle(
            mode = inputMode,
            onSelect = { tapped -> inputMode = if (inputMode == tapped) InputMode.NONE else tapped },
        )
    }
}

@Composable
private fun BoxScope.InputModeToggle(mode: InputMode, onSelect: (InputMode) -> Unit) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FloatingActionButton(onClick = { onSelect(InputMode.KEYBOARD) }) {
            Icon(
                imageVector = if (mode == InputMode.KEYBOARD) Icons.Filled.Close else Icons.Filled.Edit,
                contentDescription = if (mode == InputMode.KEYBOARD) "Hide Keyboard mode" else "Show Keyboard mode",
            )
        }
        FloatingActionButton(onClick = { onSelect(InputMode.VOICE) }) {
            Icon(
                imageVector = if (mode == InputMode.VOICE) Icons.Filled.Close else Icons.Filled.Mic,
                contentDescription = if (mode == InputMode.VOICE) "Hide Voice mode" else "Show Voice mode",
            )
        }
    }
}
