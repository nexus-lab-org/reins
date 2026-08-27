package co.maxasif.reins.presentation.terminal

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import co.maxasif.reins.presentation.settings.ExtraKey
import co.maxasif.reins.presentation.settings.ExtraKeysState
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession

private val ARROW_ICONS: Map<ExtraKey, ImageVector> = mapOf(
    ExtraKey.ARROW_UP to Icons.Filled.KeyboardArrowUp,
    ExtraKey.ARROW_DOWN to Icons.Filled.KeyboardArrowDown,
    // Not the AutoMirrored variants: these send a literal left/right cursor movement to the
    // remote shell regardless of layout direction, so the icon must not flip under RTL either.
    ExtraKey.ARROW_LEFT to Icons.Filled.KeyboardArrowLeft,
    ExtraKey.ARROW_RIGHT to Icons.Filled.KeyboardArrowRight,
)

/**
 * A slim row of keys most mobile OS keyboards don't expose (Ctrl, Esc, Tab, arrows, and a few
 * shell-heavy symbols) - docked directly above the OS keyboard by the caller, visible only while
 * it's showing (ticket 028). Which keys show and in what order comes from [ExtraKeysState]
 * (ticket 029's Settings picker).
 *
 * Ctrl is a sticky/armed modifier ([ReinsTerminalViewClient.ctrlArmed]/[ReinsTerminalViewClient.armCtrl]):
 * tapping it arms it, and the next key - whether typed on the OS keyboard (consumed via
 * [com.termux.view.TerminalViewClient.readControlKey], which [com.termux.view.TerminalView] already
 * ORs into every code point it writes) or tapped here - is sent as Ctrl+that key, then it disarms.
 * Esc/Tab/arrows send their VT100/xterm byte sequences immediately via [KeyHandler.getCode], the
 * same encoding a physically-typed key combo on this vendored terminal-emulator stack would produce.
 */
@Composable
fun ExtraKeysRow(session: TerminalSession, viewClient: ReinsTerminalViewClient, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ExtraKeysState.enabledKeysInOrder.forEach { key ->
                ExtraKeyButton(
                    key = key,
                    armed = key == ExtraKey.CTRL && viewClient.ctrlArmed,
                    onClick = { onExtraKeyTapped(key, session, viewClient) },
                )
            }
        }
    }
}

@Composable
private fun ExtraKeyButton(key: ExtraKey, armed: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = if (armed) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier,
    ) {
        val contentColor = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        val icon = ARROW_ICONS[key]
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = key.label, tint = contentColor)
        } else {
            Text(text = key.label, color = contentColor)
        }
    }
}

private val KEY_CODES: Map<ExtraKey, Int> = mapOf(
    ExtraKey.ESC to KeyEvent.KEYCODE_ESCAPE,
    ExtraKey.TAB to KeyEvent.KEYCODE_TAB,
    ExtraKey.ARROW_UP to KeyEvent.KEYCODE_DPAD_UP,
    ExtraKey.ARROW_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
    ExtraKey.ARROW_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
    ExtraKey.ARROW_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
)

private val SYMBOL_CODEPOINTS: Map<ExtraKey, Int> = mapOf(
    ExtraKey.PIPE to '|'.code,
    ExtraKey.SLASH to '/'.code,
    ExtraKey.DASH to '-'.code,
    ExtraKey.UNDERSCORE to '_'.code,
)

private fun onExtraKeyTapped(key: ExtraKey, session: TerminalSession, viewClient: ReinsTerminalViewClient) {
    if (key == ExtraKey.CTRL) {
        viewClient.armCtrl()
        return
    }

    // readControlKey() is the same single-shot arm/consume path TerminalView itself queries for
    // every OS-keyboard code point - reusing it here means an armed Ctrl applies uniformly whether
    // the next key comes from the OS keyboard or from this row.
    val ctrl = viewClient.readControlKey()

    KEY_CODES[key]?.let { keyCode ->
        val keyMode = if (ctrl) KeyHandler.KEYMOD_CTRL else 0
        val emulator = session.emulator
        val code = KeyHandler.getCode(
            keyCode,
            keyMode,
            emulator?.isCursorKeysApplicationMode ?: false,
            emulator?.isKeypadApplicationMode ?: false,
        )
        if (code != null) session.write(code)
        return
    }

    SYMBOL_CODEPOINTS[key]?.let { codePoint ->
        // Mirrors TerminalView.inputCodePoint's own ctrl-letter table for the handful of symbols
        // that have a real control-character meaning ('/' and '_' both send 0x1F, matching how a
        // physical Ctrl+/ is interpreted); the rest have no ctrl mapping so are sent as-is.
        val ctrlMapped = if (ctrl) {
            when (codePoint) {
                '_'.code, '/'.code -> 31
                else -> codePoint
            }
        } else {
            codePoint
        }
        session.writeCodePoint(false, ctrlMapped)
    }
}
