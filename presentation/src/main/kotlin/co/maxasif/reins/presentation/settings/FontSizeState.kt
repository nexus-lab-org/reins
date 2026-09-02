package co.maxasif.reins.presentation.settings

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the terminal-view font size chosen on the Settings screen, persisted via
 * [SettingsPreferences] so it survives process death. The Terminal screen (ticket 023) reads
 * [fontSizeSp] the same way Settings writes it.
 */
object FontSizeState {
    const val MIN_SP = 10f
    const val MAX_SP = 24f
    const val DEFAULT_SP = 14f
    private const val KEY = "font_size_sp"

    var fontSizeSp by mutableFloatStateOf(DEFAULT_SP)
        private set

    fun setFontSize(sp: Float) {
        fontSizeSp = sp.coerceIn(MIN_SP, MAX_SP)
        SettingsPreferences.edit { putFloat(KEY, fontSizeSp) }
    }

    internal fun restore(prefs: SharedPreferences) {
        fontSizeSp = prefs.getFloat(KEY, DEFAULT_SP).coerceIn(MIN_SP, MAX_SP)
    }
}
