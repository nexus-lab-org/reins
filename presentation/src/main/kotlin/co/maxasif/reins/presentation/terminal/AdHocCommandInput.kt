package co.maxasif.reins.presentation.terminal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/**
 * The Keyboard mode text input for Ad Hoc Commands (ticket 023, per CONTEXT.md): free-form text
 * for anything the Command Dial doesn't cover. [onSubmit] is called with the entered text - never
 * with a trailing newline of its own, since the caller decides what "submitted, as if typed"
 * means for the Data Channel (a trailing carriage return, matching how Enter is sent when typed
 * directly - see [TerminalScreen]).
 */
@Composable
fun AdHocCommandInput(onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }

    fun submit() {
        if (text.isNotEmpty()) {
            onSubmit(text)
            text = ""
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ad hoc command") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            IconButton(onClick = { submit() }) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send ad hoc command")
            }
        }
    }
}
