package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.reichholf.dreamdroid.R

/**
 * One line of text to confirm. The caller owns [state] (a ViewModel's
 * [net.reichholf.dreamdroid.ui.text.SavedTextField]) and closes the dialog; [error] shows
 * under the field while the text is not accepted.
 */
@Composable
fun TextInputDialog(
    title: String,
    state: TextFieldState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    error: String? = null,
    confirmLabel: String = stringResource(R.string.ok)
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                lineLimits = TextFieldLineLimits.SingleLine,
                isError = error != null,
                supportingText = error?.let { { Text(it) } }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
