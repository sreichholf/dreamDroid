package net.reichholf.dreamdroid.ui.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.EditSwitchRow
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListRowSurface

@Composable
fun BackupScreen(
    state: BackupUiState,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onProfileCheckedChange: (id: Int, checked: Boolean) -> Unit,
    onExportSettingsChange: (Boolean) -> Unit,
    onIncludePasswordsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLabel = stringResource(R.string.backup_current_profile)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
    ) {
        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ListRowHorizontalInset)
        ) {
            Text(stringResource(R.string.backup_import))
        }
        Button(
            onClick = onExport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ListRowHorizontalInset)
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.backup_export))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_profiles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = ListRowHorizontalInset + 16.dp,
                    end = ListRowHorizontalInset + 16.dp,
                    bottom = 8.dp
                )
            )

            state.profiles.forEach { profile ->
                ListRowSurface {
                    EditSwitchRow(
                        checked = profile.checked,
                        onCheckedChange = { onProfileCheckedChange(profile.id, it) },
                        label = profile.label(currentLabel),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.backup_settings),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = ListRowHorizontalInset + 16.dp,
                    end = ListRowHorizontalInset + 16.dp,
                    top = 16.dp,
                    bottom = 8.dp
                )
            )

            ListRowSurface {
                EditSwitchRow(
                    checked = state.exportSettings,
                    onCheckedChange = onExportSettingsChange,
                    label = stringResource(R.string.backup_export_settings),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            ListRowSurface {
                EditSwitchRow(
                    checked = state.includePasswords,
                    onCheckedChange = onIncludePasswordsChange,
                    label = stringResource(R.string.backup_include_passwords),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/** "Living Room (10.0.0.1)", with " ([currentLabel])" for the active profile. */
private fun BackupProfileToggle.label(currentLabel: String): String {
    val label = "$name ($host)"
    return if (current) "$label ($currentLabel)" else label
}
