package co.maxasif.reins.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * The fixed catalog the extra-keys row (ticket 028) can show above the OS keyboard: Ctrl, Esc,
 * Tab, the 4 arrows, and 4 symbols mobile keyboards make awkward to type.
 */
enum class ExtraKey(val label: String) {
    CTRL("Ctrl"),
    ESC("Esc"),
    TAB("Tab"),
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
        ExtraKey.ESC,
        ExtraKey.TAB,
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
        ExtraKey.ESC,
        ExtraKey.TAB,
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
}

/**
 * The experimental "swipe/autocorrect" toggle (ticket 029), off by default. Turning it on tells
 * [com.termux.view.TerminalView] to use a normal text input type (swipe-typing/autocorrect
 * capable) instead of the suggestion-suppressing one it uses by default - which risks
 * composing-region bugs against a terminal (no real text buffer for the IME to query), hence
 * opt-in. Same in-memory/process-lifetime pattern as [FontSizeState] and [ExtraKeysState].
 */
object SwipeAutocorrectState {
    var enabled: Boolean by mutableStateOf(false)
}
