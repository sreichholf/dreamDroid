package net.reichholf.dreamdroid.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.ListRow
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListSectionHeader
import net.reichholf.dreamdroid.ui.settings.SwitchPreferenceRow

/** Height of the shell's export FAB, so the last row can scroll clear of it. */
private val FabHeight = 56.dp

/**
 * Import as a card on top, then what goes into an export. The export action itself is the
 * shell FAB, bound by [BackupDestination].
 */
@Composable
fun BackupScreen(
    state: BackupUiState,
    onImport: () -> Unit,
    onProfileCheckedChange: (id: Int, checked: Boolean) -> Unit,
    onAllProfilesCheckedChange: (Boolean) -> Unit,
    onExportSettingsChange: (Boolean) -> Unit,
    onIncludePasswordsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val fabClearance = dimensionResource(R.dimen.fab_margin_bottom) + FabHeight + 16.dp
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = fabClearance)
    ) {
        ImportCard(onImport = onImport)

        ListSectionHeader(
            text = stringResource(R.string.backup_export),
            modifier = Modifier.padding(top = 16.dp)
        )
        ProfilesHeader(
            selected = state.selectedProfiles,
            total = state.profiles.size,
            onAllCheckedChange = onAllProfilesCheckedChange,
            modifier = Modifier.padding(
                start = ListRowHorizontalInset + 16.dp,
                end = ListRowHorizontalInset
            )
        )
        val currentLabel = stringResource(R.string.backup_current_profile)
        state.profiles.forEach { profile ->
            ProfileRow(
                profile = profile,
                currentLabel = currentLabel,
                onCheckedChange = { onProfileCheckedChange(profile.id, it) }
            )
        }

        SubHeader(text = stringResource(R.string.backup_also_include))
        SwitchPreferenceRow(
            title = stringResource(R.string.backup_include_passwords),
            summary = stringResource(
                if (state.passwordsInExport) {
                    R.string.backup_passwords_included
                } else {
                    R.string.backup_passwords_excluded
                }
            ),
            checked = state.passwordsInExport,
            onCheckedChange = onIncludePasswordsChange,
            enabled = state.selectedProfiles > 0
        )
        SwitchPreferenceRow(
            title = stringResource(R.string.backup_export_settings),
            summary = stringResource(R.string.backup_export_settings_summary),
            checked = state.exportSettings,
            onCheckedChange = onExportSettingsChange
        )
    }
}

@Composable
private fun ImportCard(onImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ListRowHorizontalInset)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.backup_import),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.backup_import_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onImport,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.backup_import_choose))
            }
        }
    }
}

/**
 * "Profiles  2 of 3", read as one heading, with a button that selects all of them, or none
 * once all are. Without profiles the count and the button are left out.
 */
@Composable
internal fun ProfilesHeader(
    selected: Int,
    total: Int,
    onAllCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() }
        ) {
            Text(
                text = stringResource(R.string.backup_profiles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (total > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.backup_profiles_selected, selected, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (total > 0) {
            val all = selected == total
            TextButton(onClick = { onAllCheckedChange(!all) }) {
                Text(
                    stringResource(
                        if (all) R.string.backup_select_none else R.string.backup_select_all
                    )
                )
            }
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(
                start = ListRowHorizontalInset + 16.dp,
                end = ListRowHorizontalInset + 16.dp,
                top = 16.dp,
                bottom = 8.dp
            )
            .semantics { heading() }
    )
}

@Composable
private fun ProfileRow(
    profile: BackupProfileToggle,
    currentLabel: String,
    onCheckedChange: (Boolean) -> Unit
) {
    ListRow(
        leadingContent = { Checkbox(checked = profile.checked, onCheckedChange = null) },
        headlineContent = { Text(profile.name.ifEmpty { profile.host }) },
        supportingContent = if (profile.name.isNotEmpty() && profile.host.isNotEmpty()) {
            { Text(profile.host) }
        } else {
            null
        },
        trailingContent = if (profile.current) {
            { LabelBadge(currentLabel) }
        } else {
            null
        },
        modifier = Modifier.toggleable(
            value = profile.checked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        )
    )
}

@Composable
internal fun LabelBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
