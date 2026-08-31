package co.maxasif.reins.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * The fixed catalog the extra-keys row (ticket 028) can show above the OS keyboard: Ctrl, Shift,
 * Esc, Tab, the 4 arrows, and 4 symbols mobile keyboards make awkward to type. Shift exists
 * specifically so Shift+Tab - the "back-tab" combo terminal UIs like Claude Code's plan-mode
 * toggle listen for - is reachable at all from a soft keyboard, which has no Tab key of its own to
 * hold Shift down for.
 */
enum class ExtraKey(val label: String) {
    CTRL("Ctrl"),
    SHIFT("Shift"),
    ALT("Alt"),
    ESC("Esc"),
    TAB("Tab"),
    HOME("Home"),
    END("End"),
    PAGE_UP("PgUp"),
    PAGE_DOWN("PgDn"),
    ARROW_UP("Up"),
    ARROW_DOWN("Down"),
    ARROW_LEFT("Left"),
    ARROW_RIGHT("Right"),
    PIPE("|"),
    SLASH("/"),
    DASH("-"),
    UNDERSCORE("_"),
}

/**
 * Which [ExtraKey]s the extra-keys row shows and in what order (ticket 029), following
 * [FontSizeState]'s pattern: in-memory/process-lifetime only, no settings-persistence layer
 * exists yet. Default: Ctrl/Esc/Tab/arrows on, the four symbols off, in that logical order -
 * matching ticket 028's original hardcoded row exactly, with the symbols appended off by default.
 */
object ExtraKeysState {
    private val DEFAULT_ORDER = listOf(
        ExtraKey.CTRL,
        ExtraKey.SHIFT,
        ExtraKey.ALT,
        ExtraKey.ESC,
        ExtraKey.TAB,
        ExtraKey.HOME,
        ExtraKey.END,
        ExtraKey.PAGE_UP,
        ExtraKey.PAGE_DOWN,
        ExtraKey.ARROW_UP,
        ExtraKey.ARROW_DOWN,
        ExtraKey.ARROW_LEFT,
        ExtraKey.ARROW_RIGHT,
        ExtraKey.PIPE,
        ExtraKey.SLASH,
        ExtraKey.DASH,
        ExtraKey.UNDERSCORE,
    )
    private val DEFAULT_ENABLED = setOf(
        ExtraKey.CTRL,
        ExtraKey.SHIFT,
        ExtraKey.ALT,
        ExtraKey.ESC,
        ExtraKey.TAB,
        ExtraKey.HOME,
        ExtraKey.END,
        ExtraKey.PAGE_UP,
        ExtraKey.PAGE_DOWN,
        ExtraKey.ARROW_UP,
        ExtraKey.ARROW_DOWN,
        ExtraKey.ARROW_LEFT,
        ExtraKey.ARROW_RIGHT,
    )

    /** Every catalog key, in display/reorder order - Settings shows a checkbox row per entry. */
    val orderedKeys: SnapshotStateList<ExtraKey> = DEFAULT_ORDER.toMutableStateList()

    private val enabledState = mutableStateOf(DEFAULT_ENABLED)

    fun isEnabled(key: ExtraKey): Boolean = key in enabledState.value

    fun setEnabled(key: ExtraKey, enabled: Boolean) {
        enabledState.value = if (enabled) enabledState.value + key else enabledState.value - key
    }

    /** The keys the extra-keys row should actually show, in the user's chosen order. */
    val enabledKeysInOrder: List<ExtraKey>
        get() = orderedKeys.filter { isEnabled(it) }

    fun moveUp(key: ExtraKey) {
        val index = orderedKeys.indexOf(key)
        if (index > 0) orderedKeys.apply { add(index - 1, removeAt(index)) }
    }

    fun moveDown(key: ExtraKey) {
        val index = orderedKeys.indexOf(key)
        if (index in 0 until orderedKeys.lastIndex) orderedKeys.apply { add(index + 1, removeAt(index)) }
    }

    /** Drives the Settings screen's drag-to-reorder list - moves [key] directly to [targetIndex]
     * rather than one step at a time, since a drag can cross several rows in one gesture update. */
    fun moveTo(key: ExtraKey, targetIndex: Int) {
        val fromIndex = orderedKeys.indexOf(key)
        val clampedTarget = targetIndex.coerceIn(0, orderedKeys.lastIndex)
        if (fromIndex == -1 || fromIndex == clampedTarget) return
        orderedKeys.add(clampedTarget, orderedKeys.removeAt(fromIndex))
    }
}

/**
 * The experimental "swipe/autocorrect" toggle (ticket 029), on by default. Turning it off tells
 * [com.termux.view.TerminalView] to use the suggestion-suppressing input type instead of a normal
 * text input type (swipe-typing/autocorrect capable) - the normal one risks composing-region bugs
 * against a terminal (no real text buffer for the IME to query), so it stays a toggle rather than
 * a fixed choice. Same in-memory/process-lifetime pattern as [FontSizeState] and [ExtraKeysState].
 */
object SwipeAutocorrectState {
    var enabled: Boolean by mutableStateOf(true)
}

/**
 * Toggle for ticket 030's swipe-left/right-to-switch-sessions gesture over the terminal body, on
 * by default. Off leaves the session-count pill/strip as the only way to switch - useful for
 * anyone whose remote shell work involves its own horizontal swipes/gestures that this would
 * otherwise compete with. Same in-memory/process-lifetime pattern as [SwipeAutocorrectState].
 */
object SwipeSessionSwitchState {
    var enabled: Boolean by mutableStateOf(true)
}
