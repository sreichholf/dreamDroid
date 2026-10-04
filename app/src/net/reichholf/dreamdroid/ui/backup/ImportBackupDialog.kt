package net.reichholf.dreamdroid.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R

/** Text start of a [ListItem] inside the dialog's content padding. */
private val RowContentInset = 16.dp

/**
 * Asks what to take from the picked backup before anything is written. A part the file
 * does not hold is shown, switched off and disabled.
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
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ProfilesHeader(
                    selected = review.selectedProfiles,
                    total = review.profiles.size,
                    onAllCheckedChange = onAllProfilesCheckedChange,
                    modifier = Modifier.padding(start = RowContentInset)
                )
                if (review.profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.backup_import_no_profiles),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = RowContentInset)
                    )
                }
                val replacesLabel = stringResource(R.string.backup_import_replaces)
                review.profiles.forEach { profile ->
                    ListItem(
                        leadingContent = {
                            Checkbox(checked = profile.checked, onCheckedChange = null)
                        },
                        headlineContent = { Text(profile.name.ifEmpty { profile.host }) },
                        supportingContent = if (profile.name.isNotEmpty() &&
                            profile.host.isNotEmpty()
                        ) {
                            { Text(profile.host) }
                        } else {
                            null
                        },
                        trailingContent = if (profile.replaces) {
                            { LabelBadge(replacesLabel) }
                        } else {
                            null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(
                            value = profile.checked,
                            role = Role.Checkbox,
                            onValueChange = { onProfileCheckedChange(profile.index, it) }
                        )
                    )
                }

                Text(
                    text = stringResource(R.string.backup_import_also),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = RowContentInset, top = 16.dp, bottom = 4.dp)
                        .semantics { heading() }
                )
                SwitchRow(
                    title = stringResource(R.string.backup_include_passwords),
                    summary = stringResource(
                        when {
                            !review.passwordsAvailable -> R.string.backup_import_not_in_file
                            review.includePasswords -> R.string.backup_import_passwords_on
                            else -> R.string.backup_import_passwords_off
                        }
                    ),
                    checked = review.includePasswords,
                    enabled = review.passwordsSelectable,
                    onCheckedChange = onPasswordsChange
                )
                SwitchRow(
                    title = stringResource(R.string.backup_export_settings),
                    summary = stringResource(
                        if (review.settingsAvailable) {
                            R.string.backup_import_settings_summary
                        } else {
                            R.string.backup_import_not_in_file
                        }
                    ),
                    checked = review.includeSettings,
                    enabled = review.settingsAvailable,
                    onCheckedChange = onSettingsChange
                )
            }
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

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
        },
        supportingContent = {
            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        )
    )
}
