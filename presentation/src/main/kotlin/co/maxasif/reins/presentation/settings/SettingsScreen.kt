package co.maxasif.reins.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Settings screen: a font-size control for the terminal view, the extra-keys picker (ticket 029),
 * and the experimental swipe/autocorrect toggle. The fixed dark theme itself is applied above
 * this screen, via ReinsTheme — there is no theme-switching UI here (out of scope per the
 * screen-flow map).
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = "Terminal font size",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Slider(
                value = FontSizeState.fontSizeSp,
                onValueChange = { FontSizeState.setFontSize(it) },
                valueRange = FontSizeState.MIN_SP..FontSizeState.MAX_SP,
                steps = (FontSizeState.MAX_SP - FontSizeState.MIN_SP).roundToInt() - 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "${FontSizeState.fontSizeSp.roundToInt()} sp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "agent@remote:~$ echo \"hello from reins\"",
                fontFamily = FontFamily.Monospace,
                fontSize = FontSizeState.fontSizeSp.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = "Extra keys",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            ExtraKeysState.orderedKeys.forEachIndexed { index, key ->
                ExtraKeyRow(
                    key = key,
                    isFirst = index == 0,
                    isLast = index == ExtraKeysState.orderedKeys.lastIndex,
                )
            }

            Text(
                text = "Enable swipe/autocorrect (experimental)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = SwipeAutocorrectState.enabled,
                    onCheckedChange = { SwipeAutocorrectState.enabled = it },
                )
                Text(
                    text = if (SwipeAutocorrectState.enabled) "On" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ExtraKeyRow(key: ExtraKey, isFirst: Boolean, isLast: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        IconButton(onClick = { ExtraKeysState.moveUp(key) }, enabled = !isFirst) {
            Icon(imageVector = Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${key.label} up")
        }
        IconButton(onClick = { ExtraKeysState.moveDown(key) }, enabled = !isLast) {
            Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${key.label} down")
        }
    }
}
