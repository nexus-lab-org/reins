package co.maxasif.reins.presentation.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import co.maxasif.reins.presentation.hostlist.SessionSummary
import co.maxasif.reins.presentation.settings.SwipeSessionSwitchState
import co.maxasif.reins.presentation.theme.IBMPlexMono
import co.maxasif.reins.presentation.theme.ReinsElevation
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import kotlin.math.roundToInt

/**
 * Where the user has dragged the session-count pill to, as a px offset from its default top-end
 * anchor - lets it be relocated off whatever button/content it happens to overlap on a given
 * screen or device (status bar height, notch cutouts, etc. vary). Same in-memory/process-lifetime
 * pattern as [co.maxasif.reins.presentation.settings.FontSizeState].
 */
object SessionPillPositionState {
    var offsetXPx: Float by mutableStateOf(0f)
    var offsetYPx: Float by mutableStateOf(0f)
}

/**
 * The ticket-030 "Minimal Chrome" session switcher: [content] (the terminal) stays full-bleed, with
 * a small session-count pill overlaid in the top-end corner (only shown once there's more than one
 * live session across *any* host - one session has nothing to switch to). Tapping the pill slides
 * down a strip listing every session (grouped visually by host name, since this spans hosts);
 * swiping left/right anywhere over [content] cycles sessions directly, with no persistent UI at all
 * for that gesture. Dragging the pill itself repositions it (see [SessionPillPositionState]) rather
 * than switching sessions, clamped so it can never be dragged fully off screen. Callers should wrap
 * only the terminal/stepper content, not chrome like a Disconnect button, so the pill never has to
 * share the top-end corner with anything else by default.
 *
 * The swipe-to-cycle gesture is attached to a `Box` wrapping only [content], as a *sibling* of the
 * pill/strip rather than an ancestor of them - if it wrapped the pill too, its `Initial`-pass
 * detector (needed to grab swipes before the terminal's own gesture handling, see
 * [swipeToCycleSessions]) would run before the pill even got a look at the touch, since `Initial`
 * always sweeps ancestor-to-descendant first. Keeping them as siblings means a touch that hits the
 * pill's bounds never enters the swipe-gesture's subtree at all.
 */
@Composable
fun SessionSwitcherOverlay(
    sessions: List<SessionSummary>,
    currentSessionId: String,
    onSwitchSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var stripExpanded by remember { mutableStateOf(false) }
    val sessionIds = remember(sessions) { sessions.map { it.sessionId } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .swipeToCycleSessions(
                    enabled = sessionIds.size > 1 && SwipeSessionSwitchState.enabled,
                    onSwipeLeft = { onSwitchSession(cycleSession(sessionIds, currentSessionId, 1)) },
                    onSwipeRight = { onSwitchSession(cycleSession(sessionIds, currentSessionId, -1)) },
                ),
        ) {
            content()
        }

        if (sessionIds.size > 1) {
            val density = LocalDensity.current
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val containerHeightPx = with(density) { maxHeight.toPx() }
            var pillSize by remember { mutableStateOf(IntSize.Zero) }
            val maxOffsetXPx = (containerWidthPx - pillSize.width).coerceAtLeast(0f)
            val maxOffsetYPx = (containerHeightPx - pillSize.height).coerceAtLeast(0f)

            fun applyDrag(delta: Offset) {
                SessionPillPositionState.offsetXPx = (SessionPillPositionState.offsetXPx + delta.x)
                    .coerceIn(-maxOffsetXPx, 0f)
                SessionPillPositionState.offsetYPx = (SessionPillPositionState.offsetYPx + delta.y)
                    .coerceIn(0f, maxOffsetYPx)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .onSizeChanged { pillSize = it }
                    .offset {
                        IntOffset(
                            SessionPillPositionState.offsetXPx.coerceIn(-maxOffsetXPx, 0f).roundToInt(),
                            SessionPillPositionState.offsetYPx.coerceIn(0f, maxOffsetYPx).roundToInt(),
                        )
                    }
                    .padding(ReinsSpacing.space3),
                horizontalAlignment = Alignment.End,
            ) {
                val currentIndex = sessionIds.indexOf(currentSessionId)
                SessionCountPill(
                    current = currentIndex + 1,
                    total = sessionIds.size,
                    onClick = { stripExpanded = !stripExpanded },
                    onDrag = ::applyDrag,
                )
                AnimatedVisibility(
                    visible = stripExpanded,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    SessionStrip(
                        sessions = sessions,
                        currentSessionId = currentSessionId,
                        onSelect = { sessionId ->
                            stripExpanded = false
                            onSwitchSession(sessionId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCountPill(current: Int, total: Int, onClick: () -> Unit, onDrag: (Offset) -> Unit) {
    var isDragging by remember { mutableStateOf(false) }
    Surface(
        // No `onClick` here deliberately - stacking a custom pointerInput drag detector on top of
        // Surface's own built-in clickable (a second, independent gesture detector on the same
        // node) fought over the pointer stream: once a drag past touch slop started consuming
        // move/up events, clickable's press/ripple state was left with no up event to resolve it,
        // which is what made dragging feel stuttery. Owning the whole down-to-up gesture here
        // instead - deciding tap vs. drag from a single stream - fixes that; onClick fires manually
        // on a release that never crossed slop.
        modifier = Modifier
            .semantics { contentDescription = "Session $current of $total. Tap to switch, drag to move." }
            .alpha(if (isDragging) 0.85f else 1f)
            .pointerInput(onClick, onDrag) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dragging = false
                    var accumulated = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val delta = change.positionChange()
                        accumulated += delta
                        if (!dragging && accumulated.getDistance() > touchSlop) {
                            dragging = true
                            isDragging = true
                        }
                        if (dragging) onDrag(delta)
                        change.consume()
                    }
                    isDragging = false
                    if (!dragging) onClick()
                }
            },
        shape = RoundedCornerShape(50),
        color = ReinsElevation.tint3(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
        shadowElevation = ReinsElevation.level3,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ReinsSpacing.space3, vertical = ReinsSpacing.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space1),
        ) {
            Icon(
                Lucide.Layers,
                contentDescription = null,
                modifier = Modifier.padding(top = 1.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$current/$total",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = IBMPlexMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionStrip(sessions: List<SessionSummary>, currentSessionId: String, onSelect: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .padding(top = ReinsSpacing.space2)
            .widthIn(min = 200.dp),
        shape = RoundedCornerShape(10.dp),
        color = ReinsElevation.tint2(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant),
        shadowElevation = ReinsElevation.level2,
    ) {
        Column(modifier = Modifier.padding(vertical = ReinsSpacing.space1)) {
            sessions.forEach { session ->
                val isCurrent = session.sessionId == currentSessionId
                Surface(
                    onClick = { onSelect(session.sessionId) },
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ReinsSpacing.space3, vertical = ReinsSpacing.space2),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = session.hostDisplayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = session.label,
                                fontFamily = IBMPlexMono,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = session.statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = ReinsSpacing.space3),
                        )
                    }
                }
            }
        }
    }
}
