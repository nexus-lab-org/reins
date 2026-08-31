package co.maxasif.reins.presentation.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.Lucide
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Extra keys, split out of the main [SettingsScreen] into its own page (previously an inline
 * picker) since the reorderable list plus its explanation crowded the rest of Settings off the
 * first screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraKeysSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Extra keys") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Surface(
            modifier = Modifier.padding(padding).fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ReinsSpacing.space5),
                verticalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
            ) {
                Text(
                    text = "Which keys show above the keyboard, and in what order. Drag the " +
                        "handle to reorder, or use the arrows.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExtraKeysReorderableList()
            }
        }
    }
}

/**
 * [ExtraKeysState.orderedKeys] is a small fixed-size, non-lazy list, so this animates reordering
 * by hand rather than pulling in a `LazyColumn`-oriented reorder library: every row's Y offset is
 * an [Animatable] that eases toward `index * rowHeight` whenever the order changes underneath it
 * (a sibling being dragged past it), giving the "make room" slide a real reorderable list has
 * instead of rows popping straight to their new slot. The one row actually being dragged is the
 * exception - it tracks the finger directly (base position at drag-start plus raw pointer delta),
 * then eases into its final slot on release. Row height is measured once, since every row is the
 * same height. The chevron buttons stay alongside the handle for precise, one-step reordering
 * without a drag gesture.
 */
@Composable
private fun ExtraKeysReorderableList() {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingKey by remember { mutableStateOf<ExtraKey?>(null) }
    var dragBasePx by remember { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val offsets = remember { mutableStateMapOf<ExtraKey, Animatable<Float, AnimationVector1D>>() }
    val keys = ExtraKeysState.orderedKeys

    if (rowHeightPx > 0f) {
        LaunchedEffect(keys.toList(), rowHeightPx, draggingKey) {
            keys.forEachIndexed { index, key ->
                val target = index * rowHeightPx
                val anim = offsets.getOrPut(key) { Animatable(target) }
                if (key != draggingKey) launch { anim.animateTo(target, tween(180)) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (rowHeightPx > 0f) {
                    Modifier.height(with(density) { (rowHeightPx * keys.size).toDp() })
                } else {
                    Modifier
                },
            ),
    ) {
        keys.forEach { key ->
            val isDragging = key == draggingKey

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    // Both branches read state inside this layout-phase lambda (not the composable
                    // body above) so a drag's per-pixel updates only re-place this one Row instead
                    // of recomposing the whole reorderable list on every pointer move - reading
                    // dragBasePx/dragOffsetPx/offsets[key] in the body instead was the actual cause
                    // of the drag feeling laggy and occasionally rendering a row at a stale offset.
                    .offset {
                        val px = if (key == draggingKey) dragBasePx + dragOffsetPx else offsets[key]?.value ?: 0f
                        IntOffset(0, px.roundToInt())
                    }
                    .onSizeChanged { if (rowHeightPx == 0f) rowHeightPx = it.height.toFloat() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Lucide.GripVertical,
                    contentDescription = "Drag to reorder ${key.label}",
                    modifier = Modifier.pointerInput(key) {
                        detectDragGestures(
                            onDragStart = {
                                draggingKey = key
                                // The row's current grid slot, not the animatable's (possibly
                                // still mid-animation) live value - grabbing a row while a prior
                                // reorder is still settling should still start the drag from its
                                // correct resting position, not wherever the animation happened
                                // to be that frame.
                                dragBasePx = ExtraKeysState.orderedKeys.indexOf(key) * rowHeightPx
                                dragOffsetPx = 0f
                            },
                            onDragEnd = {
                                val settledKey = draggingKey
                                val finalPos = dragBasePx + dragOffsetPx
                                draggingKey = null
                                if (settledKey != null && rowHeightPx > 0f) {
                                    scope.launch {
                                        offsets[settledKey]?.snapTo(finalPos)
                                        val settleIndex = ExtraKeysState.orderedKeys.indexOf(settledKey)
                                        offsets[settledKey]?.animateTo(settleIndex * rowHeightPx, tween(150))
                                    }
                                }
                            },
                            onDragCancel = { draggingKey = null },
                        ) { change, delta ->
                            change.consume()
                            dragOffsetPx += delta.y
                            if (rowHeightPx <= 0f) return@detectDragGestures
                            val currentIndex = ExtraKeysState.orderedKeys.indexOf(key)
                            val targetIndex = ((dragBasePx + dragOffsetPx) / rowHeightPx)
                                .roundToInt()
                                .coerceIn(0, ExtraKeysState.orderedKeys.lastIndex)
                            if (targetIndex != currentIndex) {
                                ExtraKeysState.moveTo(key, targetIndex)
                            }
                        }
                    },
                )
                Checkbox(
                    checked = ExtraKeysState.isEnabled(key),
                    onCheckedChange = { ExtraKeysState.setEnabled(key, it) },
                )
                Text(
                    text = key.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                val index = ExtraKeysState.orderedKeys.indexOf(key)
                IconButton(onClick = { ExtraKeysState.moveUp(key) }, enabled = index != 0) {
                    Icon(imageVector = Lucide.ChevronUp, contentDescription = "Move ${key.label} up")
                }
                IconButton(
                    onClick = { ExtraKeysState.moveDown(key) },
                    enabled = index != ExtraKeysState.orderedKeys.lastIndex,
                ) {
                    Icon(imageVector = Lucide.ChevronDown, contentDescription = "Move ${key.label} down")
                }
            }
        }
    }
}
