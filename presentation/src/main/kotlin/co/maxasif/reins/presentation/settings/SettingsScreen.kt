package co.maxasif.reins.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import co.maxasif.reins.presentation.theme.IBMPlexMono
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import kotlin.math.roundToInt

/**
 * Settings screen: a font-size control for the terminal view, a link into the extra-keys picker
 * (its own page - ticket 029/030), and the experimental swipe/autocorrect and swipe-to-switch-
 * sessions toggles. The fixed dark theme itself is applied above this screen, via ReinsTheme —
 * there is no theme-switching UI here (out of scope per the screen-flow map).
 */
@Composable
fun SettingsScreen(onOpenExtraKeys: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Lucide.ArrowLeft, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ReinsSpacing.space5, vertical = ReinsSpacing.space1),
                verticalArrangement = Arrangement.spacedBy(ReinsSpacing.space6),
            ) {
            BatteryOptimizationRow()

            Column {
                Text(
                    text = "Terminal font size",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = ReinsSpacing.space3),
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = IBMPlexMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = ReinsSpacing.space2),
                )

                Text(
                    text = "agent@remote:~$ echo \"hello from reins\"",
                    fontFamily = IBMPlexMono,
                    fontSize = FontSizeState.fontSizeSp.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .padding(horizontal = ReinsSpacing.space3, vertical = ReinsSpacing.space3),
                )
            }

            Surface(
                onClick = onOpenExtraKeys,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Extra keys",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Lucide.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Swipe / autocorrect",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Switch(
                        checked = SwipeAutocorrectState.enabled,
                        onCheckedChange = { SwipeAutocorrectState.enabled = it },
                    )
                }
                Text(
                    text = "On by default. The terminal isn't a real text box, so letting your " +
                        "keyboard swipe-type and autocorrect into it can occasionally misplace " +
                        "characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = ReinsSpacing.space2),
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Swipe to switch sessions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Switch(
                        checked = SwipeSessionSwitchState.enabled,
                        onCheckedChange = { SwipeSessionSwitchState.enabled = it },
                    )
                }
                Text(
                    text = "Swipe left/right over the terminal to cycle live sessions. The tab " +
                        "bar still lets you switch by tapping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = ReinsSpacing.space2),
                )
            }
            }
        }
    }
}

/**
 * A correctly-implemented foreground Service ([co.maxasif.reins.connection.ConnectionService])
 * still gets killed in the background on several OEM skins - OnePlus/Oppo/Realme's OxygenOS/
 * ColorOS chief among them - unless the app is explicitly exempted from their battery-
 * optimization/Doze standby bucket. This surfaces that exemption request directly (there's no
 * other way to reach it - it's not a permission a manifest declaration alone grants), and
 * rechecks the status on every resume since the system Settings screen it launches into doesn't
 * hand back a result.
 */
@Composable
private fun BatteryOptimizationRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isExempt = remember(resumeCount) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    if (!isExempt) {
        Text(
            text = "Keep connections alive in the background",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "This device's battery manager can kill live SSH/Mosh sessions when Reins isn't " +
                "on screen, even though they're designed to keep running. Exempt Reins from battery " +
                "optimization so sessions survive backgrounding and closing the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val intent = Intent(
                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            },
        ) {
            Text("Exempt Reins from battery optimization")
        }
    }
}
