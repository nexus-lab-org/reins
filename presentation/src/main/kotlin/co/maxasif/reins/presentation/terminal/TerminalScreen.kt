package co.maxasif.reins.presentation.terminal

import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import co.maxasif.reins.presentation.settings.FontSizeState
import co.maxasif.reins.presentation.theme.ReinsElevation
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import kotlin.math.roundToInt

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
 *
 * [Modifier.imePadding] on the root [Column] is load-bearing, not cosmetic: without it the IME
 * only visually covers the terminal (MainActivity's `enableEdgeToEdge()` lets content draw behind
 * system bars/IME), so the [TerminalView] never actually shrinks, [TerminalView.onSizeChanged]
 * never fires with fewer rows, and the PTY is never told its viewport got smaller - the cursor
 * ends up rendered behind the keyboard, and a remote TUI (e.g. herdr) that sizes its own layout off
 * the reported terminal dimensions never sees the smaller size either. `imePadding()` makes the
 * Column's real measured height shrink when the IME shows, so the [AndroidView] below actually
 * resizes and [TerminalView]'s existing `onSizeChanged -> updateSize()` path (which already calls
 * through to [TerminalSession.updateSize], reaching the remote PTY resize) does the rest.
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
    var terminalFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    fun showKeyboard() {
        val view = terminalView ?: return
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

    // Focus without popping the IME: entering a terminal (or switching to one) should still route
    // key input to the right view once the user does bring the keyboard up (a tap, or typing on a
    // hardware keyboard), but shouldn't cover the screen with the IME unasked - see showKeyboard's
    // call site below vs. viewClient.onTap's.
    fun requestFocusOnly() {
        terminalView?.requestFocus()
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // AndroidView's `factory` only runs once per node - switching to a different [session]
        // (ticket 030's session switcher) would otherwise leave the old TerminalView attached to
        // the old session forever, since there's no `update` lambda re-running attachSession().
        // Keying on `session` forces Compose to dispose the old node and run `factory` fresh
        // against the new one instead of trying to patch an `update` block that would have to
        // duplicate all of this one-time callback wiring anyway.
        key(session) {
        // The vendored TerminalView is both scrollback and cursor-input surface at once (no
        // separate compose/draft-line widget exists in com.termux.view) - a literal second input
        // widget would mean forking that rendering/PTY-input pipeline, so "distinct draft bar" is
        // approximated instead with a border that glows in the theme's primary color exactly while
        // this view holds focus, via the plain View focus-change listener below.
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(ReinsSpacing.space1),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                width = if (terminalFocused) 2.dp else 1.dp,
                color = if (terminalFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
            shadowElevation = if (terminalFocused) ReinsElevation.level2 else ReinsElevation.level0,
            color = MaterialTheme.colorScheme.background,
        ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { factoryContext ->
                TerminalView(factoryContext, null).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setTerminalViewClient(viewClient)
                    setOnFocusChangeListener { _, hasFocus -> terminalFocused = hasFocus }
                    viewClient.onTap = { showKeyboard() }
                    // Long-press a URL to open it in the default browser app, matching the gesture
                    // real terminal apps use - a plain tap is already claimed for reshowing the
                    // keyboard, so opening on tap would fire constantly while typing/scrolling.
                    viewClient.onLongPressUrl = { event ->
                        val (column, row) = getColumnAndRow(event, true)
                        val word = session.emulator?.screen?.getWordAtLocation(column, row)
                        val url = word?.let { extractUrlFromWord(it) }
                        // A miss (no URL under the press) falls through to TerminalView's own
                        // default long-press behavior (text selection), so only act - and only
                        // consume the press - on a hit.
                        if (url != null) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        url != null
                    }
                    // TerminalView.setTextSize() takes raw Paint px, not sp - it does not scale with
                    // display density on its own, so the conversion has to happen here (a font size
                    // that looks right on one device's density would end up wrong-sized, and so
                    // fitting the wrong column count, on any other).
                    setTextSize(with(density) { FontSizeState.fontSizeSp.sp.toPx() }.roundToInt())
                    attachSession(session)
                    // TerminalView never repaints on its own - something has to call
                    // onScreenUpdated() whenever new PTY bytes land. Upstream Termux does this by
                    // having its TerminalSessionClient.onTextChanged reach back into the attached
                    // view; ReinsTerminalSessionClient (built in :app, before any TerminalView
                    // exists to reference) is a no-op there, so wire it up here instead, once the
                    // view actually exists. feedIncoming runs off the Data Channel's reader thread,
                    // so hop back to the main thread via View.post before touching the view.
                    session.updateTerminalSessionClient(object : TerminalSessionClient by ReinsTerminalSessionClient(context) {
                        override fun onTextChanged(changedSession: TerminalSession) {
                            post { onScreenUpdated() }
                        }
                    })
                    terminalView = this
                }
            },
        )
        }
        }

        if (imeVisible) {
            ExtraKeysRow(session = session, viewClient = viewClient)
        }
    }

    LaunchedEffect(FontSizeState.fontSizeSp) {
        terminalView?.setTextSize(with(density) { FontSizeState.fontSizeSp.sp.toPx() }.roundToInt())
    }

    // Keyed on session (not Unit): a session switch attaches a fresh TerminalView that starts
    // unfocused, so without this, typing would keep going to whichever view last had focus rather
    // than the one now on screen. Focus only, not showKeyboard() - the IME should start hidden
    // (tap the terminal to bring it up), not pop open every time a terminal is opened or switched to.
    LaunchedEffect(session) { requestFocusOnly() }

    BackHandler(enabled = imeVisible) {
        terminalView?.let { view ->
            ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
        }
    }
}
