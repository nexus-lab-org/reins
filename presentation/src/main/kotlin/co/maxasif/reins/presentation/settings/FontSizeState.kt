package co.maxasif.reins.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the terminal-view font size chosen on the Settings screen.
 *
 * In-memory only for now — settings persistence in :data isn't separately
 * ticketed yet, so this is process-lifetime state, not a DataStore-backed
 * repository. The Terminal screen (ticket 023) reads [fontSizeSp] the same
 * way Settings writes it.
 */
object FontSizeState {
    const val MIN_SP = 10f
    const val MAX_SP = 24f
    const val DEFAULT_SP = 14f

    var fontSizeSp by mutableFloatStateOf(DEFAULT_SP)
        private set

    fun setFontSize(sp: Float) {
        fontSizeSp = sp.coerceIn(MIN_SP, MAX_SP)
    }
}
