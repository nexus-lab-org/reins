package co.maxasif.reins.presentation.connect

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/**
 * Prompts for a password before a [HostAuthMethod.Password][co.maxasif.reins.domain.model.HostAuthMethod.Password]
 * Host's first connect (or any connect before its one-time key setup - see `ConnectionService` -
 * has succeeded). The password is held only in this dialog's local state and handed to
 * [onSubmit]; nothing here persists it.
 */
@Composable
fun PasswordPromptDialog(hostDisplayName: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Password for $hostDisplayName") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password) }) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}
