package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import co.maxasif.reins.presentation.theme.IBMPlexMono
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

/**
 * Generates a new on-device [co.maxasif.reins.domain.model.Identity.KeystoreIdentity] (ticket 020):
 * a display name, a "Generate" button that creates the Keystore key pair, and - once generated -
 * the resulting `authorized_keys` line with a copy affordance for pasting onto a remote host.
 */
@Composable
fun GenerateKeystoreIdentityScreen(
    isGenerating: Boolean,
    authorizedKeysLine: String?,
    errorMessage: String?,
    onGenerate: (displayName: String) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
            ) {
                IconButton(onClick = onCancel, enabled = !isGenerating) {
                    Icon(Lucide.ArrowLeft, contentDescription = "Back")
                }
                Text(
                    text = "Generate on-device identity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ReinsSpacing.space5),
                verticalArrangement = Arrangement.spacedBy(ReinsSpacing.space4),
            ) {
            Text(
                text = "Creates an EC P-256 key pair inside the Android Keystore (StrongBox-backed " +
                    "where available). The private key never leaves the device - only the public key " +
                    "below needs to be added to a remote host's authorized_keys.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (authorizedKeysLine == null) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage != null) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    text = "Public key",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                SelectionContainer {
                    Text(
                        text = authorizedKeysLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = IBMPlexMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ReinsSpacing.space5, vertical = ReinsSpacing.space4),
                horizontalArrangement = Arrangement.End,
            ) {
                if (authorizedKeysLine == null) {
                    TextButton(onClick = onCancel, enabled = !isGenerating) { Text("Cancel") }
                    Button(
                        enabled = displayName.isNotBlank() && !isGenerating,
                        shape = RoundedCornerShape(100.dp),
                        onClick = { onGenerate(displayName.trim()) },
                    ) { Text(if (isGenerating) "Generating..." else "Generate") }
                } else {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(authorizedKeysLine)) }) {
                        Text("Copy to clipboard")
                    }
                    Button(onClick = onDone, shape = RoundedCornerShape(100.dp)) { Text("Done") }
                }
            }
        }
    }
}
