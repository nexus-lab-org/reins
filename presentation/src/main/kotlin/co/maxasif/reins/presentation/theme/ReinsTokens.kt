package co.maxasif.reins.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 8px-rooted spacing scale (ticket 031), replacing ad hoc per-screen dp values.
 */
object ReinsSpacing {
    val space1: Dp = 4.dp
    val space2: Dp = 8.dp
    val space3: Dp = 12.dp
    val space4: Dp = 16.dp
    val space5: Dp = 24.dp
    val space6: Dp = 32.dp
    val space7: Dp = 48.dp
}

/**
 * Elevation ramp reworked for Rosé Pine: higher surfaces tint toward [ReinsElevation.tint1]..[tint3]
 * (iris-mixed) rather than just going darker, per the locked artifact design (ticket 031).
 */
object ReinsElevation {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 6.dp
    val level3: Dp = 14.dp

    fun tint1(iris: Color, panel: Color): Color = lerp(panel, iris, 0.03f)
    fun tint2(iris: Color, overlay: Color): Color = lerp(overlay, iris, 0.06f)
    fun tint3(iris: Color, overlay: Color): Color = lerp(overlay, iris, 0.09f)

    private fun lerp(from: Color, to: Color, fraction: Float): Color = Color(
        red = from.red + (to.red - from.red) * fraction,
        green = from.green + (to.green - from.green) * fraction,
        blue = from.blue + (to.blue - from.blue) * fraction,
        alpha = from.alpha + (to.alpha - from.alpha) * fraction,
    )
}
