package co.maxasif.reins.presentation.terminal

import android.util.Log
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
class ReinsTerminalSessionClient : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) = Unit
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit
    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
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
 * key-handling path, with no changes to TerminalView.java needed.
 */
class ReinsTerminalViewClient : TerminalViewClient {
    var ctrlArmed: Boolean by mutableStateOf(false)
        private set

    fun armCtrl() { ctrlArmed = true }

    override fun onScale(scale: Float): Float = 1f
    override fun onSingleTapUp(e: MotionEvent?) = Unit
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldEnableSwipeAutocorrect(): Boolean = SwipeAutocorrectState.enabled
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    override fun onLongPress(event: MotionEvent?): Boolean = false
    override fun readControlKey(): Boolean {
        if (!ctrlArmed) return false
        ctrlArmed = false
        return true
    }
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
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
