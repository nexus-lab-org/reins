package co.maxasif.reins.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Rosé Pine dark palette (https://rosepinetheme.com/palette) — fixed, no light variant.
private val RosePineBase = Color(0xFF191724)
private val RosePineSurface = Color(0xFF1F1D2E)
private val RosePineOverlay = Color(0xFF26233A)
private val RosePineMuted = Color(0xFF6E6A86)
private val RosePineSubtle = Color(0xFF908CAA)
private val RosePineText = Color(0xFFE0DEF4)
private val RosePineLove = Color(0xFFEB6F92)
private val RosePineGold = Color(0xFFF6C177)
private val RosePineRose = Color(0xFFEBBCBA)
private val RosePinePine = Color(0xFF31748F)
private val RosePineFoam = Color(0xFF9CCFD8)
private val RosePineIris = Color(0xFFC4A7E7)
private val RosePineHighlightHigh = Color(0xFF524F67)

private val RosePineDarkColorScheme = darkColorScheme(
    primary = RosePineIris,
    onPrimary = RosePineBase,
    secondary = RosePineFoam,
    onSecondary = RosePineBase,
    tertiary = RosePineGold,
    onTertiary = RosePineBase,
    background = RosePineBase,
    onBackground = RosePineText,
    surface = RosePineSurface,
    onSurface = RosePineText,
    surfaceVariant = RosePineOverlay,
    onSurfaceVariant = RosePineSubtle,
    outline = RosePineMuted,
    outlineVariant = RosePineHighlightHigh,
    error = RosePineLove,
    onError = RosePineBase,
    inversePrimary = RosePineRose,
    primaryContainer = RosePinePine,
    onPrimaryContainer = RosePineText,
)

/**
 * App-wide fixed dark theme (Rosé Pine dark). No theme picker or light variant —
 * theming was explicitly scoped to one fixed theme (ticket 027).
 */
@Composable
fun ReinsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RosePineDarkColorScheme,
        content = content,
    )
}
