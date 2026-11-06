package com.kuswa.clashtimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kuswa.clashtimer.core.AppSettings

/**
 * Asks for the name shown on the widget header. The village export has no player name, so the app
 * asks on first launch; it can be changed later in Settings. Empty = "Chief".
 *
 * [onDone] gets the cleaned name ("" for Chief). Dismissing without saving also counts as
 * answered on first launch, so the prompt doesn't come back on every start.
 */
@Composable
fun PlayerNameDialog(current: String?, firstLaunch: Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    val save = { onDone(AppSettings.cleanName(text)) }
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onDismiss,
        title = { Text(if (firstLaunch) "Welcome, Chief!" else "Player name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What name should the app and widget show? Leave it empty to use \"${AppSettings.DEFAULT_PLAYER_NAME}\".")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(AppSettings.MAX_PLAYER_NAME) },
                    singleLine = true,
                    placeholder = { Text(AppSettings.DEFAULT_PLAYER_NAME) },
                    supportingText = {
                        Text("${text.length}/${AppSettings.MAX_PLAYER_NAME}", style = MaterialTheme.typography.bodySmall)
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                )
            }
        },
        confirmButton = { TextButton(onClick = save) { Text("Save") } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (firstLaunch) "Skip" else "Cancel") }
        },
    )
}
