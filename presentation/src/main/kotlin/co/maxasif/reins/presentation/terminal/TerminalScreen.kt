package co.maxasif.reins.presentation.terminal

import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 * the Ad Hoc Command Keyboard/Voice overlays). It takes keyboard focus and shows the OS keyboard
 * as soon as it appears (ticket 028), the same feel as any other terminal app, and a back
 * press/gesture dismisses the keyboard first rather than leaving the screen while it's showing
 * (CONTEXT.md's "leaving Terminal is a UI-stack pop only" - dismissing the keyboard isn't leaving).
 */
@Composable
fun TerminalScreen(
    session: TerminalSession,
    modifier: Modifier = Modifier,
) {
    val viewClient = remember { ReinsTerminalViewClient() }
    val context = LocalContext.current
    val density = LocalDensity.current
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { factoryContext ->
            TerminalView(factoryContext, null).apply {
                isFocusable = true
                isFocusableInTouchMode = true
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
                terminalView = this
            }
        },
    )

    LaunchedEffect(Unit) {
        val view = terminalView ?: return@LaunchedEffect
        view.requestFocus()
        val controller = ViewCompat.getWindowInsetsController(view)
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.ime())
        } else {
            // No window insets controller yet (view not attached to a window) - fall back to the
            // classic InputMethodManager path, which works even before that attachment completes.
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    BackHandler(enabled = imeVisible) {
        terminalView?.let { view ->
            ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
        }
    }
}
