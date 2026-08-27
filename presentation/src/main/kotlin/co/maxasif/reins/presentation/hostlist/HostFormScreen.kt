package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.domain.model.Transport

private enum class AuthChoice { KEY, PASSWORD }

/** Add/edit form for a [Host] (ticket 008/018, password auth follow-up). */
@Composable
fun HostFormScreen(
    initialHost: Host?,
    identities: List<Identity>,
    onSave: (
        displayName: String,
        username: String,
        hostname: String,
        port: Int,
        transport: Transport,
        authMethod: HostAuthMethod,
    ) -> Unit,
    onAddIdentity: () -> Unit,
    onGenerateIdentity: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayName by rememberSaveable { mutableStateOf(initialHost?.displayName ?: "") }
    var username by rememberSaveable { mutableStateOf(initialHost?.username ?: "") }
    var hostname by rememberSaveable { mutableStateOf(initialHost?.hostname ?: "") }
    var portText by rememberSaveable { mutableStateOf((initialHost?.port ?: 22).toString()) }
    var transport by rememberSaveable { mutableStateOf(initialHost?.transport ?: Transport.Ssh) }
    val initialAuthMethod = initialHost?.authMethod
    var authChoice by rememberSaveable {
        mutableStateOf(if (initialAuthMethod is HostAuthMethod.Password) AuthChoice.PASSWORD else AuthChoice.KEY)
    }
    var identityId by rememberSaveable {
        mutableStateOf((initialAuthMethod as? HostAuthMethod.Key)?.identityId ?: identities.firstOrNull()?.id)
    }
    val port = portText.toIntOrNull()
    val canSave = displayName.isNotBlank() && username.isNotBlank() && hostname.isNotBlank() && port != null &&
        (authChoice == AuthChoice.PASSWORD || identityId != null)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (initialHost == null) "Add host" else "Edit host",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("reins") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text("Host") },
                placeholder = { Text("10.0.0.5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it },
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Transport", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Transport.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { transport = option },
                    ) {
                        RadioButton(selected = transport == option, onClick = { transport = option })
                        Text(option.name, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            Text("Authentication", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { authChoice = AuthChoice.KEY },
                ) {
                    RadioButton(selected = authChoice == AuthChoice.KEY, onClick = { authChoice = AuthChoice.KEY })
                    Text("Key", color = MaterialTheme.colorScheme.onBackground)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { authChoice = AuthChoice.PASSWORD },
                ) {
                    RadioButton(selected = authChoice == AuthChoice.PASSWORD, onClick = { authChoice = AuthChoice.PASSWORD })
                    Text("Password", color = MaterialTheme.colorScheme.onBackground)
                }
            }

            if (authChoice == AuthChoice.KEY) {
                IdentityPicker(
                    identities = identities,
                    selectedId = identityId,
                    onSelect = { identityId = it },
                )
                Row {
                    TextButton(onClick = onAddIdentity) {
                        Text("Import a new identity")
                    }
                    TextButton(onClick = onGenerateIdentity) {
                        Text("Generate on-device identity")
                    }
                }
            } else {
                Text(
                    text = "You'll be asked for the password the first time you connect. Once it " +
                        "succeeds, Reins generates and installs a key automatically, so the " +
                        "password won't be needed again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(
                    enabled = canSave,
                    onClick = {
                        val authMethod = if (authChoice == AuthChoice.PASSWORD) {
                            HostAuthMethod.Password
                        } else {
                            HostAuthMethod.Key(identityId!!)
                        }
                        onSave(
                            displayName.trim(),
                            username.trim(),
                            hostname.trim(),
                            port ?: 22,
                            transport,
                            authMethod,
                        )
                    },
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun IdentityPicker(identities: List<Identity>, selectedId: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = identities.firstOrNull { it.id == selectedId }?.displayName ?: "Select an identity"

    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = identities.isNotEmpty()) {
            Text(if (identities.isEmpty()) "No identities yet" else selectedName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            identities.forEach { identity ->
                DropdownMenuItem(
                    text = { Text(identity.displayName) },
                    onClick = {
                        onSelect(identity.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
