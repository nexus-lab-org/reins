package co.maxasif.reins.presentation.terminal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

/**
 * Cycles from [currentSessionId] to the next/previous entry in [sessionIds] (the same order shown
 * in the session strip), wrapping at both ends. [direction] is +1 for "next" (swipe left) or -1 for
 * "previous" (swipe right). Returns [currentSessionId] unchanged if there's nothing to cycle to.
 *
 * A plain function, not a Composable - the wrap-around/edge-case logic (single session, unknown id)
 * is exactly what ticket 030 flagged as needing to be verified carefully without an emulator, so it
 * is unit-testable on its own; see SessionSwitchingTest.
 */
fun cycleSession(sessionIds: List<String>, currentSessionId: String, direction: Int): String {
    if (sessionIds.size < 2) return currentSessionId
    val index = sessionIds.indexOf(currentSessionId)
    if (index == -1) return currentSessionId
    val nextIndex = ((index + direction) % sessionIds.size + sessionIds.size) % sessionIds.size
    return sessionIds[nextIndex]
}

/** How much more horizontal than vertical a drag must travel before it's claimed as a session-cycle swipe. */
private const val SWIPE_DIRECTION_LOCK_RATIO = 1.5f

/**
 * Attaches the ticket-030 "swipe left/right to cycle sessions" gesture over [content]'s subtree
 * (typically the terminal's [com.termux.view.TerminalView], hosted via `AndroidView`).
 *
 * Registers with [PointerEventPass.Initial] rather than the default Main pass, so this detector sees
 * every touch on its way *down* to the terminal, before the terminal's own `GestureAndScaleRecognizer`
 * gets a chance to consume it in the Main pass on the way back up - a Main-pass detector here would
 * simply never fire, since the vendored TerminalView already claims touches for its own scroll/tap/
 * selection handling.
 *
 * Only consumes the pointer stream once a drag is confirmed horizontal-dominant past touch slop
 * ([SWIPE_DIRECTION_LOCK_RATIO]), so a plain tap (which shows the keyboard) or a vertical drag
 * (terminal scrollback) still reaches the terminal completely untouched - only the direction is
 * decided at release, from the drag's total displacement, so a mid-gesture reversal resolves to
 * whichever way the finger was actually moving when it lifted.
 */
internal fun Modifier.swipeToCycleSessions(
    enabled: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(onSwipeLeft, onSwipeRight) {
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        var total = Offset.Zero
        var locked = false
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (locked) {
                    when {
                        total.x <= -touchSlop -> onSwipeLeft()
                        total.x >= touchSlop -> onSwipeRight()
                    }
                    change.consume()
                }
                break
            }
            total += change.positionChange()
            if (!locked && abs(total.x) > touchSlop && abs(total.x) > abs(total.y) * SWIPE_DIRECTION_LOCK_RATIO) {
                locked = true
            }
            if (locked) change.consume()
        }
    }
}
