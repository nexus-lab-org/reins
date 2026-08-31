package co.maxasif.reins.presentation.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.util.Patterns
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.maxasif.reins.presentation.settings.SwipeAutocorrectState
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient

private const val TAG = "ReinsTerminal"

/**
 * Minimal [TerminalSessionClient]: title/bell/clipboard/color callbacks are all follow-up-ticket
 * UI polish (a title bar, an "agent finished" bell indicator, ...) - this walking skeleton only
 * needs the PTY bytes to reach the screen, which flows through [TerminalSession.feedIncoming]
 * directly, not through this interface.
 */
class ReinsTerminalSessionClient(private val context: Context) : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) = Unit
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit

    // Reached from TerminalView's own text-selection Copy/Paste action mode - the "phone clipboard"
    // referred to here is the OS clipboard (ClipboardManager), not anything internal to Reins.
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal selection", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clipItem = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return
        val text = clipItem.coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) session?.emulator?.paste(text)
    }
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String?, message: String?) { Log.e(TAG, "$tag: $message") }
    override fun logWarn(tag: String?, message: String?) { Log.w(TAG, "$tag: $message") }
    override fun logInfo(tag: String?, message: String?) { Log.i(TAG, "$tag: $message") }
    override fun logDebug(tag: String?, message: String?) { Log.d(TAG, "$tag: $message") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(TAG, "$tag: $message") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(TAG, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(TAG, tag, e) }
}

/**
 * Minimal [TerminalViewClient]: no pinch-to-zoom, no Command Dial modifier keys yet (ticket 007),
 * char-based IME input so on-screen-keyboard composing text arrives as plain code points rather
 * than accumulating in the IME's own composing region - the default Termux recommends for exactly
 * this "not a real EditText" scenario.
 *
 * [ctrlArmed] backs the extra-keys row's sticky Ctrl modifier (ticket 028): tapping Ctrl calls
 * [armCtrl], and [readControlKey] - which [com.termux.view.TerminalView] already consults for
 * every code point it writes, whether typed on the OS keyboard or committed by the IME - reports
 * it once and disarms, giving single-shot Ctrl+<next key> behavior for free from the vendored
 * key-handling path, with no changes to TerminalView.java needed. [shiftArmed]/[armShift]/
 * [readShiftKey] mirror this exactly for Shift, needed so Shift+Tab (Claude Code's plan-mode
 * toggle listens for the resulting "\033[Z" back-tab sequence) is reachable from a soft keyboard.
 * [altArmed]/[armAlt]/[readAltKey] mirror the same pattern again for Alt (e.g. Alt+Left/Right word
 * navigation in shells/editors that bind it).
 */
class ReinsTerminalViewClient : TerminalViewClient {
    var ctrlArmed: Boolean by mutableStateOf(false)
        private set

    var shiftArmed: Boolean by mutableStateOf(false)
        private set

    var altArmed: Boolean by mutableStateOf(false)
        private set

    /**
     * Set by [TerminalScreen] to re-show the OS keyboard on tap - once dismissed (by back, or the
     * user tapping the IME's own dismiss affordance) nothing else asks for it again, since
     * [TerminalView] already has focus and requesting it a second time is a no-op.
     */
    var onTap: (() -> Unit)? = null

    /**
     * Set by [TerminalScreen] to check a long-pressed word for a URL and open it. Returning `true`
     * (a URL was found and opened) tells [com.termux.view.TerminalView] to skip its own default
     * long-press behavior (starting text-selection mode) for this press.
     */
    var onLongPressUrl: ((MotionEvent) -> Boolean)? = null

    fun armCtrl() { ctrlArmed = true }
    fun armShift() { shiftArmed = true }
    fun armAlt() { altArmed = true }

    override fun onScale(scale: Float): Float = 1f
    override fun onSingleTapUp(e: MotionEvent?) { onTap?.invoke() }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldEnableSwipeAutocorrect(): Boolean = SwipeAutocorrectState.enabled
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    override fun onLongPress(event: MotionEvent?): Boolean = event?.let { onLongPressUrl?.invoke(it) } ?: false
    override fun readControlKey(): Boolean {
        if (!ctrlArmed) return false
        ctrlArmed = false
        return true
    }
    override fun readAltKey(): Boolean {
        if (!altArmed) return false
        altArmed = false
        return true
    }
    override fun readShiftKey(): Boolean {
        if (!shiftArmed) return false
        shiftArmed = false
        return true
    }
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
    override fun onEmulatorSet() = Unit

    override fun logError(tag: String?, message: String?) { Log.e(TAG, "$tag: $message") }
    override fun logWarn(tag: String?, message: String?) { Log.w(TAG, "$tag: $message") }
    override fun logInfo(tag: String?, message: String?) { Log.i(TAG, "$tag: $message") }
    override fun logDebug(tag: String?, message: String?) { Log.d(TAG, "$tag: $message") }
    override fun logVerbose(tag: String?, message: String?) { Log.v(TAG, "$tag: $message") }
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) { Log.e(TAG, message, e) }
    override fun logStackTrace(tag: String?, e: Exception?) { Log.e(TAG, tag, e) }
}

/**
 * Finds a URL within a word grabbed off the terminal screen (whitespace-delimited, so it can carry
 * leading/trailing punctuation from surrounding prose or shell quoting - e.g. a URL in parens or
 * followed by a comma). Returns `null` when the word contains no recognizable URL.
 */
fun extractUrlFromWord(word: String): String? {
    val matcher = Patterns.WEB_URL.matcher(word)
    if (!matcher.find()) return null
    return matcher.group().trimEnd(')', ']', '}', '"', '\'', ',', '.', ';', ':')
}
