package co.maxasif.reins.presentation.terminal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView

/**
 * Hosts the Data Channel PTY: a vendored Termux [TerminalView] wrapped in [AndroidView] (ticket
 * 002's recommended integration path - reuse the mature View rather than reimplementing terminal
 * rendering in Compose Canvas). [session] is constructed and wired to the remote SSH/Mosh shell by
 * `:app` (the only module that can see both `:data`'s SSH classes and this `:terminal-view`-backed
 * screen) - this composable only hosts it.
 *
 * The terminal is the whole screen - no overlay UI, no input-mode switching (ticket 027 removed
 * the Ad Hoc Command Keyboard/Voice overlays). Typing reaches the remote shell directly through
 * [TerminalView]'s own IME handling, writing straight to [session].
 */
@Composable
fun TerminalScreen(
    session: TerminalSession,
    modifier: Modifier = Modifier,
) {
    val viewClient = remember { ReinsTerminalViewClient() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
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
}
