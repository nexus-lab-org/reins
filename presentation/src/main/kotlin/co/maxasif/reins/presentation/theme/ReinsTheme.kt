package co.maxasif.reins.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import co.maxasif.reins.presentation.R

// Near-black + electric blue palette, superseding Rosé Pine — the redesign explicitly reopens
// ticket 031's locked palette choice. Every value below is lifted verbatim from the exported
// Reins Redesign prototype snapshot (https://claude.ai/code/artifact/4d20b6a6-5dfd-41b7-ac4e-87eb1a9a08b2),
// not approximated: #0a0c10 base, #12151b surface, #4f7dfa accent, #34d399 live-status green,
// #f76b6b destructive red, #eef1f5/#838b98 primary/muted text all appear as literal hex in its markup.
private val NearBlackBase = Color(0xFF0A0C10)
private val NearBlackSurface = Color(0xFF12151B)
private val NearBlackOverlay = Color(0xFF1A1E26)
private val NearBlackBorder = Color(0xFF232830)
private val NearBlackMuted = Color(0xFF5B636F)
private val NearBlackSubtle = Color(0xFF838B98)
private val NearBlackText = Color(0xFFEEF1F5)
private val ElectricBlue = Color(0xFF4F7DFA)
private val ElectricBlueLight = Color(0xFF7D9DFB)
private val StatusRed = Color(0xFFF76B6B)
private val StatusGreen = Color(0xFF34D399)
private val StatusAmber = Color(0xFFF6C177)
// ~14% primary-over-surface blend, matching the prototype's `rgba(79,125,250,.14)` badge/pill fill.
private val ElectricBlueContainer = Color(0xFF1B243A)

private val NearBlackDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    secondary = StatusGreen,
    onSecondary = NearBlackBase,
    tertiary = StatusAmber,
    onTertiary = NearBlackBase,
    background = NearBlackBase,
    onBackground = NearBlackText,
    surface = NearBlackSurface,
    onSurface = NearBlackText,
    surfaceVariant = NearBlackOverlay,
    onSurfaceVariant = NearBlackSubtle,
    outline = NearBlackBorder,
    outlineVariant = NearBlackMuted,
    error = StatusRed,
    onError = NearBlackBase,
    inversePrimary = ElectricBlueLight,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = NearBlackText,
)

/**
 * Hanken Grotesk for UI chrome, IBM Plex Mono for anything machine-facing
 * (terminal content, host addresses/ports, session labels, build strings) - ticket 031.
 */
val HankenGrotesk = FontFamily(
    Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
    Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(R.font.hanken_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.hanken_grotesk_bold, FontWeight.Bold),
)

val IBMPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
)

private val baseTypography = Typography()

private fun TextStyle.withHankenGrotesk() = copy(fontFamily = HankenGrotesk)

private val ReinsTypography = Typography(
    displayLarge = baseTypography.displayLarge.withHankenGrotesk(),
    displayMedium = baseTypography.displayMedium.withHankenGrotesk(),
    displaySmall = baseTypography.displaySmall.withHankenGrotesk(),
    headlineLarge = baseTypography.headlineLarge.withHankenGrotesk(),
    headlineMedium = baseTypography.headlineMedium.withHankenGrotesk(),
    headlineSmall = baseTypography.headlineSmall.withHankenGrotesk(),
    titleLarge = baseTypography.titleLarge.withHankenGrotesk(),
    titleMedium = baseTypography.titleMedium.withHankenGrotesk(),
    titleSmall = baseTypography.titleSmall.withHankenGrotesk(),
    bodyLarge = baseTypography.bodyLarge.withHankenGrotesk(),
    bodyMedium = baseTypography.bodyMedium.withHankenGrotesk(),
    bodySmall = baseTypography.bodySmall.withHankenGrotesk(),
    labelLarge = baseTypography.labelLarge.withHankenGrotesk(),
    labelMedium = baseTypography.labelMedium.withHankenGrotesk(),
    labelSmall = baseTypography.labelSmall.withHankenGrotesk(),
)

/**
 * App-wide fixed dark theme (near-black + electric blue). No theme picker or light variant —
 * theming was explicitly scoped to one fixed theme (ticket 027).
 */
@Composable
fun ReinsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NearBlackDarkColorScheme,
        typography = ReinsTypography,
        content = content,
    )
}
