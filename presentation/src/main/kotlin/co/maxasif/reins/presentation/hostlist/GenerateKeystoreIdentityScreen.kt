package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Generate on-device identity",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel, enabled = !isGenerating) { Text("Cancel") }
                    Button(
                        enabled = displayName.isNotBlank() && !isGenerating,
                        onClick = { onGenerate(displayName.trim()) },
                    ) { Text(if (isGenerating) "Generating..." else "Generate") }
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(authorizedKeysLine)) }) {
                        Text("Copy to clipboard")
                    }
                    Button(onClick = onDone) { Text("Done") }
                }
            }
        }
    }
}
