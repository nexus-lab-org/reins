package co.maxasif.reins.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

/**
 * Placeholder screen for ticket 016 — proves the app shell launches.
 * Replaced by the real Host List / Connect / Terminal / Settings screens
 * (tickets 018, 021-024, 027).
 */
@Composable
fun BlankScreen() {
    Surface(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
    }
}
