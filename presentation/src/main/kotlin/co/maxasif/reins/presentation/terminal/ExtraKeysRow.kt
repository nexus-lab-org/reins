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
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession

/**
 * The fixed catalog of extra keys (ticket 029 drives which of these are enabled/ordered; ticket
 * 028 hardcodes all seven, Ctrl/Esc/Tab/arrows on, in this order).
 */
private enum class ExtraKey(val label: String, val icon: ImageVector? = null) {
    CTRL(label = "Ctrl"),
    ESC(label = "Esc"),
    TAB(label = "Tab"),
    ARROW_UP(label = "Up", icon = Icons.Filled.KeyboardArrowUp),
    ARROW_DOWN(label = "Down", icon = Icons.Filled.KeyboardArrowDown),
    // Not the AutoMirrored variants: these send a literal left/right cursor movement to the
    // remote shell regardless of layout direction, so the icon must not flip under RTL either.
    ARROW_LEFT(label = "Left", icon = Icons.Filled.KeyboardArrowLeft),
    ARROW_RIGHT(label = "Right", icon = Icons.Filled.KeyboardArrowRight),
}

private val EXTRA_KEY_ROW = listOf(
    ExtraKey.CTRL,
    ExtraKey.ESC,
    ExtraKey.TAB,
    ExtraKey.ARROW_UP,
    ExtraKey.ARROW_DOWN,
    ExtraKey.ARROW_LEFT,
    ExtraKey.ARROW_RIGHT,
)

/**
 * A slim row of keys most mobile OS keyboards don't expose (Ctrl, Esc, Tab, arrows) - docked
 * directly above the OS keyboard by the caller, visible only while it's showing (ticket 028).
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
            EXTRA_KEY_ROW.forEach { key ->
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
        if (key.icon != null) {
            Icon(imageVector = key.icon, contentDescription = key.label, tint = contentColor)
        } else {
            Text(text = key.label, color = contentColor)
        }
    }
}

private fun onExtraKeyTapped(key: ExtraKey, session: TerminalSession, viewClient: ReinsTerminalViewClient) {
    if (key == ExtraKey.CTRL) {
        viewClient.armCtrl()
        return
    }

    val keyCode = when (key) {
        ExtraKey.ESC -> KeyEvent.KEYCODE_ESCAPE
        ExtraKey.TAB -> KeyEvent.KEYCODE_TAB
        ExtraKey.ARROW_UP -> KeyEvent.KEYCODE_DPAD_UP
        ExtraKey.ARROW_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        ExtraKey.ARROW_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        ExtraKey.ARROW_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        ExtraKey.CTRL -> return
    }

    // readControlKey() is the same single-shot arm/consume path TerminalView itself queries for
    // every OS-keyboard code point - reusing it here means an armed Ctrl applies uniformly whether
    // the next key comes from the OS keyboard or from this row.
    val keyMode = if (viewClient.readControlKey()) KeyHandler.KEYMOD_CTRL else 0
    val emulator = session.emulator
    val code = KeyHandler.getCode(
        keyCode,
        keyMode,
        emulator?.isCursorKeysApplicationMode ?: false,
        emulator?.isKeypadApplicationMode ?: false,
    )
    if (code != null) session.write(code)
}
