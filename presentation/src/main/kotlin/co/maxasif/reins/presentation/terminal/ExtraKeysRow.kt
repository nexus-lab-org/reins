package co.maxasif.reins.presentation.terminal

import android.view.KeyEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.maxasif.reins.presentation.settings.ExtraKey
import co.maxasif.reins.presentation.settings.ExtraKeysState
import co.maxasif.reins.presentation.theme.IBMPlexMono
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession

private val ARROW_ICONS: Map<ExtraKey, ImageVector> = mapOf(
    ExtraKey.ARROW_UP to Lucide.ChevronUp,
    ExtraKey.ARROW_DOWN to Lucide.ChevronDown,
    // Not the AutoMirrored variants: these send a literal left/right cursor movement to the
    // remote shell regardless of layout direction, so the icon must not flip under RTL either.
    ExtraKey.ARROW_LEFT to Lucide.ChevronLeft,
    ExtraKey.ARROW_RIGHT to Lucide.ChevronRight,
)

/**
 * A slim row of keys most mobile OS keyboards don't expose (Ctrl, Esc, Tab, arrows, and a few
 * shell-heavy symbols) - docked directly above the OS keyboard by the caller, visible only while
 * it's showing (ticket 028). Which keys show and in what order comes from [ExtraKeysState]
 * (ticket 029's Settings picker).
 *
 * Ctrl and Shift are sticky/armed modifiers ([ReinsTerminalViewClient.ctrlArmed]/[ReinsTerminalViewClient.armCtrl],
 * [ReinsTerminalViewClient.shiftArmed]/[ReinsTerminalViewClient.armShift]): tapping one arms it, and
 * the next key - whether typed on the OS keyboard (consumed via
 * [com.termux.view.TerminalViewClient.readControlKey]/[com.termux.view.TerminalViewClient.readShiftKey],
 * which [com.termux.view.TerminalView] already consults for every code point it writes) or tapped
 * here - is sent as that modifier+key, then it disarms. Shift+Tab is the main reason Shift exists
 * here at all: it's the only way to reach it from a soft keyboard, which has no Tab key of its own
 * to hold Shift for. Esc/Tab/arrows send their VT100/xterm byte sequences immediately via
 * [KeyHandler.getCode], the same encoding a physically-typed key combo on this vendored
 * terminal-emulator stack would produce.
 */
private val TOP_ROW_KEYS = setOf(
    ExtraKey.ALT,
    ExtraKey.HOME,
    ExtraKey.END,
    ExtraKey.PAGE_UP,
    ExtraKey.PAGE_DOWN,
)

@Composable
fun ExtraKeysRow(session: TerminalSession, viewClient: ReinsTerminalViewClient, modifier: Modifier = Modifier) {
    val enabledKeys = ExtraKeysState.enabledKeysInOrder
    // Split by kind rather than a fixed key count, so the row still reflects whatever subset/order
    // Settings > Extra Keys has: navigation-ish keys (Alt plus the 4 movement keys) form a top row,
    // everything else (Ctrl/Shift/Esc/Tab/arrows/symbols) stays in the original bottom row.
    val topRowKeys = enabledKeys.filter { it in TOP_ROW_KEYS }
    val mainRowKeys = enabledKeys.filter { it !in TOP_ROW_KEYS }

    Column(modifier = modifier.fillMaxWidth()) {
        if (topRowKeys.isNotEmpty()) {
            ExtraKeysSubRow(keys = topRowKeys, session = session, viewClient = viewClient, tonalElevation = 1.dp)
        }
        ExtraKeysSubRow(keys = mainRowKeys, session = session, viewClient = viewClient, tonalElevation = 2.dp)
    }
}

@Composable
private fun ExtraKeysSubRow(
    keys: List<ExtraKey>,
    session: TerminalSession,
    viewClient: ReinsTerminalViewClient,
    tonalElevation: Dp,
) {
    // Surface(onClick=...) below enforces Material3's 48dp minimum touch target by default, which
    // would blow the button height (and so this whole row) way past the 28dp these keys actually
    // need - dropping to 0dp here lets ExtraKeyButton's own sizeIn(minHeight=28dp) be the real floor,
    // which is what keeps this row (and the terminal above it) from eating unnecessary height.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = tonalElevation) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = ReinsSpacing.space1, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                keys.forEach { key ->
                    ExtraKeyButton(
                        key = key,
                        armed = (key == ExtraKey.CTRL && viewClient.ctrlArmed) ||
                            (key == ExtraKey.SHIFT && viewClient.shiftArmed) ||
                            (key == ExtraKey.ALT && viewClient.altArmed),
                        onClick = { onExtraKeyTapped(key, session, viewClient) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtraKeyButton(key: ExtraKey, armed: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = 34.dp, minHeight = 28.dp),
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = ReinsSpacing.space2, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            val icon = ARROW_ICONS[key]
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = key.label, tint = contentColor, modifier = Modifier.sizeIn(maxWidth = 18.dp, maxHeight = 18.dp))
            } else {
                Text(
                    text = key.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = IBMPlexMono),
                )
            }
        }
    }
}

private val KEY_CODES: Map<ExtraKey, Int> = mapOf(
    ExtraKey.ESC to KeyEvent.KEYCODE_ESCAPE,
    ExtraKey.TAB to KeyEvent.KEYCODE_TAB,
    ExtraKey.HOME to KeyEvent.KEYCODE_MOVE_HOME,
    ExtraKey.END to KeyEvent.KEYCODE_MOVE_END,
    ExtraKey.PAGE_UP to KeyEvent.KEYCODE_PAGE_UP,
    ExtraKey.PAGE_DOWN to KeyEvent.KEYCODE_PAGE_DOWN,
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
    if (key == ExtraKey.SHIFT) {
        viewClient.armShift()
        return
    }
    if (key == ExtraKey.ALT) {
        viewClient.armAlt()
        return
    }

    // readControlKey()/readShiftKey()/readAltKey() are the same single-shot arm/consume paths
    // TerminalView itself queries for every OS-keyboard code point - reusing them here means an
    // armed Ctrl/Shift/Alt applies uniformly whether the next key comes from the OS keyboard or
    // this row.
    val ctrl = viewClient.readControlKey()
    val shift = viewClient.readShiftKey()
    val alt = viewClient.readAltKey()

    KEY_CODES[key]?.let { keyCode ->
        var keyMode = 0
        if (ctrl) keyMode = keyMode or KeyHandler.KEYMOD_CTRL
        if (shift) keyMode = keyMode or KeyHandler.KEYMOD_SHIFT
        if (alt) keyMode = keyMode or KeyHandler.KEYMOD_ALT
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
