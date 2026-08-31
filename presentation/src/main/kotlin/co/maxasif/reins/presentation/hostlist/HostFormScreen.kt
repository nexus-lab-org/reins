package co.maxasif.reins.presentation.hostlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.maxasif.reins.domain.model.Host
import co.maxasif.reins.domain.model.HostAuthMethod
import co.maxasif.reins.domain.model.Identity
import co.maxasif.reins.domain.model.Transport
import co.maxasif.reins.presentation.theme.ReinsSpacing
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

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
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ReinsSpacing.space4, vertical = ReinsSpacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Lucide.ArrowLeft, contentDescription = "Back")
                }
                Text(
                    text = if (initialHost == null) "Add host" else "Edit host",
                    style = MaterialTheme.typography.titleLarge,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space3),
            ) {
                OutlinedTextField(
                    value = hostname,
                    onValueChange = { hostname = it },
                    label = { Text("Host") },
                    placeholder = { Text("10.0.0.5") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(78.dp),
                )
            }

            Column {
                Text(
                    "TRANSPORT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = ReinsSpacing.space2),
                )
                SegmentedControl(
                    options = Transport.entries,
                    selected = transport,
                    onSelect = { transport = it },
                    label = { it.name },
                )
            }

            Column {
                Text(
                    "AUTHENTICATION",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = ReinsSpacing.space2),
                )
                SegmentedControl(
                    options = AuthChoice.entries,
                    selected = authChoice,
                    onSelect = { authChoice = it },
                    label = { if (it == AuthChoice.KEY) "Key" else "Password" },
                )
            }

            if (authChoice == AuthChoice.KEY) {
                val selectedIdentityName = identities.firstOrNull { it.id == identityId }?.displayName
                if (selectedIdentityName != null) {
                    Text(
                        text = selectedIdentityName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
                            .padding(horizontal = ReinsSpacing.space3, vertical = ReinsSpacing.space2),
                    )
                } else {
                    IdentityPicker(
                        identities = identities,
                        selectedId = identityId,
                        onSelect = { identityId = it },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space5)) {
                    Text(
                        text = "Import identity",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onAddIdentity),
                    )
                    Text(
                        text = "Generate identity",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onGenerateIdentity),
                    )
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
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    .padding(horizontal = ReinsSpacing.space5, vertical = ReinsSpacing.space4),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(
                    enabled = canSave,
                    shape = RoundedCornerShape(100.dp),
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
private fun <T> SegmentedControl(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(ReinsSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(ReinsSpacing.space2),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(9.dp),
                    )
                    .padding(vertical = ReinsSpacing.space3),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
