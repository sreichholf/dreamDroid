package net.reichholf.dreamdroid.ui.backup

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.reichholf.dreamdroid.R

/**
 * Asks what to take from the picked backup before anything is written. Only the profile
 * list scrolls, so the password and settings choices stay in view; a tap outside does not
 * drop the choices. The rows are [ImportReviewBody], shared with the setup assistant.
 */
@Composable
fun ImportBackupDialog(
    review: ImportReview,
    onProfileCheckedChange: (index: Int, checked: Boolean) -> Unit,
    onAllProfilesCheckedChange: (Boolean) -> Unit,
    onPasswordsChange: (Boolean) -> Unit,
    onSettingsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            ImportReviewBody(
                review = review,
                onProfileCheckedChange = onProfileCheckedChange,
                onAllProfilesCheckedChange = onAllProfilesCheckedChange,
                onPasswordsChange = onPasswordsChange,
                onSettingsChange = onSettingsChange,
                scrollProfiles = true,
                modifier = Modifier.heightIn(max = 480.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = review.canImport) {
                Text(stringResource(R.string.backup_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
